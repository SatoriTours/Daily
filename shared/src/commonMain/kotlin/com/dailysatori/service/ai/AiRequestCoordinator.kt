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
import kotlin.time.TimeSource
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

/**
 * A request may never wait for a send permit without a bound: an unbounded wait silently consuming the
 * caller's own deadline is what turned ordinary rate limiting into hours of invisible stalling.
 */
class AiQueueWaitTimeout(val waitedMs: Long) : IllegalStateException(
    "AI 服务繁忙，排队等待 " + (waitedMs / 1000) + " 秒仍未获发送许可，请稍后重试",
)

/**
 * One absolute budget owned by the caller's item: content work, prompt building and every nested AI request
 * are charged to the same deadline. Remaining time uses a monotonic clock so a wall-clock change cannot move it.
 */
class AiCallDeadline(private val elapsedMs: () -> Long, val budgetMs: Long) {
    val reserveMs: Long = reserveForBudget(budgetMs)

    /** How long the gate may still queue before the reserved send time would be eaten. */
    fun remainingForAdmissionMs(): Long = (budgetMs - elapsedMs() - reserveMs).coerceAtLeast(0L)
}

/** Reserve the send a slice of the budget (a third, capped); never the whole budget, even when it is tiny. */
/** Cap on the send slice: a 90s item reserves 15s, a 400ms item reserves ~133ms. */
private const val MAX_ADMISSION_RESERVE_MS = 15_000L

internal fun reserveForBudget(budgetMs: Long): Long = when {
    budgetMs <= 2L -> 0L
    else -> minOf(budgetMs / 3, MAX_ADMISSION_RESERVE_MS).coerceAtLeast(1L).coerceAtMost(budgetMs - 1L)
}

private class AiCallDeadlineElement(val deadline: AiCallDeadline) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AiCallDeadlineElement>
}

/**
 * Starts the single budget for one caller item. Nested AI requests share this deadline, and the admission
 * gate may only wait while the reserved send time is untouched: congestion therefore defers deterministically
 * before the caller's own timeout, instead of racing two timers of the same length.
 */
suspend fun <T> withAiCallDeadline(
    budgetMs: Long,
    elapsedMs: (() -> Long)? = null,
    block: suspend () -> T,
): T {
    val budget = budgetMs.coerceAtLeast(1L)
    val clock: () -> Long = elapsedMs ?: monotonicElapsed()
    return withContext(AiCallDeadlineElement(AiCallDeadline(clock, budget))) { block() }
}

/** Elapsed time from a monotonic mark: wall-clock corrections cannot move the remaining budget. */
private fun monotonicElapsed(): () -> Long {
    val mark = TimeSource.Monotonic.markNow()
    return { mark.elapsedNow().inWholeMilliseconds }
}

/**
 * Advisory side effect: a failing database is tolerated, cancellation is never swallowed.
 */
internal inline fun <T> tolerateAdvisory(block: () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

/** One shared admission gate, not a second task queue. Conservatively share quotas per origin. */
class AiRequestCoordinator(
    private val settings: SettingRepository? = null,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val backgroundIntervalMs: Long = 3_000,
    /**
     * Last-resort anti-hang guard, not a throughput control: a request that cannot be admitted for this
     * long must fail visibly instead of silently occupying its caller's whole deadline.
     */
    private val maxQueueWaitMs: Long = DEFAULT_MAX_QUEUE_WAIT_MS,
) {
    private class Group {
        var active = 0
        var background = 0
        var nextBackgroundAt = 0L
        var cooldownUntil = 0L
        // Highest value already handed to persistence: an older or weaker write can never win later.
        var persistedBackgroundAt = 0L
        var persistedCooldownUntil = 0L
        val waiting = mutableListOf<Ticket>()
        var changed = CompletableDeferred<Unit>()
        fun signal() { changed.complete(Unit); changed = CompletableDeferred() }
    }
    private class Ticket(val background: Boolean)

    /** Returned to the caller so releasing a permit never depends on a separate flag being written. */
    private class Permit(val background: Boolean)

    private sealed interface Admission {
        class Granted(val permit: Permit, val persistBackgroundAt: Long?) : Admission
        class Waiting(val changed: CompletableDeferred<Unit>, val delayMs: Long?) : Admission
    }
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
                // A failing read must cost neither a permit nor a lock: persisted values are advisory.
                cooldownUntil = readSetting("ai.cooldown.$key")?.toLongOrNull() ?: 0
                nextBackgroundAt = readSetting("ai.next-background.$key")?.toLongOrNull() ?: 0
                persistedCooldownUntil = cooldownUntil
                persistedBackgroundAt = nextBackgroundAt
            } }.also { it.waiting += ticket; it.signal() }
        }
        var permit: Permit? = null
        try {
            // Ownership first: the permit must already belong to this scope before anything else can throw,
            // otherwise a cancellation during persistence would orphan an already-counted permit.
            val granted = acquire(group, ticket)
            permit = granted.permit
            // Persistence is advisory and stays inside the protected region: a cancellation here releases the
            // permit through the finally, and it is never swallowed or wrapped in a non-cancellable scope.
            granted.persistBackgroundAt?.let { persistBestEffort("ai.next-background.$key", it) }
            observer?.beforeSend?.invoke()
            requestState("requesting", observer)
            return block().also { observer?.progress?.invoke(); requestState("received", observer) }
        } catch (error: AiHttpFailure) {
            if (error.status == 429) {
                // In-memory cooldown first: a failing store must never weaken rate limiting.
                val persist = mutex.withLock {
                    val cooldown = maxOf(group.cooldownUntil, error.retryAtMs ?: nowMs() + 60_000)
                    group.cooldownUntil = cooldown
                    val newer = cooldown > group.persistedCooldownUntil
                    if (newer) group.persistedCooldownUntil = cooldown
                    group.signal()
                    cooldown.takeIf { newer }
                }
                persist?.let { persistBestEffort("ai.cooldown.$key", it) }
            }
            throw error
        } finally {
            withContext(NonCancellable) { mutex.withLock {
                group.waiting.remove(ticket)
                permit?.let { held ->
                    group.active--
                    if (held.background) group.background--
                }
                group.signal()
            } }
        }
    }

    private fun requestState(stage: String, observer: AiRequestObserver?) {
        observer?.state?.invoke(stage)
        DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.AI,
            fields = mapOf("aiStage" to stage))
    }

    private suspend fun acquire(group: Group, ticket: Ticket): Admission.Granted {
        val startedAt = nowMs()
        val hardDeadline = startedAt + maxQueueWaitMs.coerceAtLeast(1L)
        val callDeadline = coroutineContext[AiCallDeadlineElement]?.deadline
        while (true) {
            val step = mutex.withLock {
                val now = nowMs()
                if (group.cooldownUntil > now) throw AiHttpFailure(429, group.cooldownUntil)
                val first = group.waiting.firstOrNull { !it.background } ?: group.waiting.firstOrNull()
                val available = group.active < 2 && (!ticket.background || group.background == 0)
                val delayMs = if (ticket.background) (group.nextBackgroundAt - now).coerceAtLeast(0) else 0
                if (first === ticket && available && delayMs == 0L) {
                    group.waiting.remove(ticket)
                    group.active++
                    val persistAt = if (ticket.background) {
                        group.background++
                        group.nextBackgroundAt = now + backgroundIntervalMs
                        group.nextBackgroundAt.takeIf { it > group.persistedBackgroundAt }
                            ?.also { group.persistedBackgroundAt = it }
                    } else null
                    group.signal()
                    Admission.Granted(Permit(ticket.background), persistAt)
                } else {
                    Admission.Waiting(
                        group.changed,
                        if (first === ticket && available && delayMs > 0) delayMs else null,
                    )
                }
            }
            when (step) {
                is Admission.Granted -> return step
                is Admission.Waiting -> {
                    // Deterministic exit: the caller's own deadline (minus the send reserve) and the gate's
                    // absolute anti-hang bound, whichever is tighter. Never two timers of equal length.
                    val remaining = minOf(
                        callDeadline?.remainingForAdmissionMs() ?: Long.MAX_VALUE,
                        hardDeadline - nowMs(),
                    )
                    if (remaining <= 0L) throw AiQueueWaitTimeout(nowMs() - startedAt)
                    val wakeUpIn = step.delayMs?.let { minOf(it, remaining) } ?: remaining
                    withTimeoutOrNull(wakeUpIn) { step.changed.await() }
                }
            }
        }
    }

    private fun readSetting(key: String): String? {
        val store = settings ?: return null
        return tolerateAdvisory { store.get(key) }
    }

    /**
     * Persistence is advisory: a locked or failing database must cost neither a permit nor the call, and the
     * stored value may only be raised (see SettingRepository.upsertNumericFloor), so write order cannot rewind it.
     */
    private fun persistBestEffort(key: String, value: Long) {
        val store = settings ?: return
        tolerateAdvisory { store.upsertNumericFloor(key, value) }
    }

    private companion object {
        /** Generous on purpose: normal background work may legitimately queue behind long requests. */
        const val DEFAULT_MAX_QUEUE_WAIT_MS = 5 * 60_000L
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
