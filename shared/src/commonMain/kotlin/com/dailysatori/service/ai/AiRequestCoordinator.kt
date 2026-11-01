package com.dailysatori.service.ai

import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.service.diagnostics.*
import io.ktor.http.Url
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.toInstant
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Only safe status metadata escapes the transport; provider bodies are never persisted. */
class AiHttpFailure(val status: Int, val retryAtMs: Long? = null) :
    IllegalStateException("AI HTTP $status：" + when (status) {
        401, 403 -> "认证或权限失败，请检查 AI 配置"
        429 -> "服务限流，已暂停请求并等待冷却"
        else -> "请求失败"
    })

internal class AiRequestObserver(
    val state: (String) -> Unit,
    val beforeSend: () -> Unit,
    val progress: () -> Unit = {},
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AiRequestObserver>
}

/** One shared admission gate, not a second task queue. Conservatively share quotas per origin. */
class AiRequestCoordinator(
    private val settings: SettingRepository? = null,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val backgroundIntervalMs: Long = 3_000,
) {
    private class Group {
        var active = 0
        var background = 0
        var nextBackgroundAt = 0L
        var cooldownUntil = 0L
        val waiting = mutableListOf<Ticket>()
        var changed = CompletableDeferred<Unit>()
        fun signal() { changed.complete(Unit); changed = CompletableDeferred() }
    }
    private class Ticket(val background: Boolean)
    private val mutex = Mutex()
    private val groups = mutableMapOf<String, Group>()

    suspend fun <T> withRequest(apiAddress: String, purpose: AiPurpose?, block: suspend () -> T): T {
        val url = Url(apiAddress.trim())
        // No API key, private path or prompt participates in persisted throttle keys.
        val key = sha256Hex("${url.protocol.name}://${url.host.lowercase()}:${url.port}")
        val ticket = Ticket(purpose != null && purpose != AiPurpose.INTERACTIVE)
        val observer = coroutineContext[AiRequestObserver]
        requestState("queued", observer)
        val group = mutex.withLock {
            groups.getOrPut(key) { Group().apply {
                cooldownUntil = settings?.get("ai.cooldown.$key")?.toLongOrNull() ?: 0
                nextBackgroundAt = settings?.get("ai.next-background.$key")?.toLongOrNull() ?: 0
            } }.also { it.waiting += ticket; it.signal() }
        }
        var admitted = false
        try {
            acquire(group, ticket, key)
            admitted = true
            observer?.beforeSend?.invoke()
            requestState("requesting", observer)
            return block().also { observer?.progress?.invoke(); requestState("received", observer) }
        } catch (error: AiHttpFailure) {
            if (error.status == 429) mutex.withLock {
                group.cooldownUntil = maxOf(group.cooldownUntil, error.retryAtMs ?: nowMs() + 60_000)
                settings?.upsert("ai.cooldown.$key", group.cooldownUntil.toString())
                group.signal()
            }
            throw error
        } finally {
            withContext(NonCancellable) { mutex.withLock {
                group.waiting.remove(ticket)
                if (admitted) { group.active--; if (ticket.background) group.background-- }
                group.signal()
            } }
        }
    }

    private fun requestState(stage: String, observer: AiRequestObserver?) {
        observer?.state?.invoke(stage)
        DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.AI,
            fields = mapOf("aiStage" to stage))
    }

    private suspend fun acquire(group: Group, ticket: Ticket, key: String) {
        while (true) {
            val wait = mutex.withLock {
                val now = nowMs()
                if (group.cooldownUntil > now) throw AiHttpFailure(429, group.cooldownUntil)
                val first = group.waiting.firstOrNull { !it.background } ?: group.waiting.firstOrNull()
                val available = group.active < 2 && (!ticket.background || group.background == 0)
                val delayMs = if (ticket.background) (group.nextBackgroundAt - now).coerceAtLeast(0) else 0
                if (first === ticket && available && delayMs == 0L) {
                    group.waiting.remove(ticket)
                    group.active++
                    if (ticket.background) {
                        group.background++
                        group.nextBackgroundAt = now + backgroundIntervalMs
                        settings?.upsert("ai.next-background.$key", group.nextBackgroundAt.toString())
                    }
                    group.signal()
                    return
                }
                group.changed to if (first === ticket && available && delayMs > 0) delayMs else null
            }
            if (wait.second == null) wait.first.await()
            else withTimeoutOrNull(wait.second!!) { wait.first.await() }
        }
    }
}

internal fun aiRetryAtMs(retryAfter: String?, date: String?, now: Long = Clock.System.now().toEpochMilliseconds()): Long {
    val seconds = retryAfter?.trim()?.toLongOrNull()
    if (seconds != null) return now + seconds.coerceIn(1, 86_400) * 1_000
    val until = retryAfter?.let(::httpDateMillis)
    val serverNow = date?.let(::httpDateMillis) ?: now
    return now + (until?.minus(serverNow) ?: 60_000).coerceIn(1_000, 86_400_000)
}

private fun httpDateMillis(value: String): Long? = runCatching {
    val parts = value.trim().split(Regex("\\s+"))
    require(parts.size == 6 && parts[5] == "GMT")
    val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").indexOf(parts[2]) + 1
    kotlinx.datetime.LocalDateTime.parse("${parts[3]}-${month.toString().padStart(2, '0')}-${parts[1].padStart(2, '0')}T${parts[4]}")
        .let { kotlinx.datetime.TimeZone.UTC.let { zone -> it.toInstant(zone).toEpochMilliseconds() } }
}.getOrNull()
