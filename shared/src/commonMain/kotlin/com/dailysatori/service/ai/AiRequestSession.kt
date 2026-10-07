package com.dailysatori.service.ai

import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.data.repository.SettingRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

/** Persist opaque conversation IDs separately from content, without changing the database schema. */
class AiConversationSessionStore(private val settings: SettingRepository) {
    private val mutex = Mutex()

    suspend fun getOrCreate(conversationKey: String): String = mutex.withLock {
        val key = "ai.request.session.$conversationKey"
        settings.get(key) ?: DiagnosticLog.newId().also { settings.upsert(key, it) }
    }
}

private class AiRequestSession(val id: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AiRequestSession>
}

/** A logical task owns its session; nested requests/retries inherit it, unrelated tasks do not. */
suspend fun <T> withAiRequestSession(sessionId: String? = null, block: suspend () -> T): T {
    val inherited = coroutineContext[AiRequestSession]
    if (sessionId == null && inherited != null) return block()
    val id = sessionId ?: DiagnosticLog.newId()
    require(id.isNotBlank() && id.length <= 128 && id.none { it == '\r' || it == '\n' }) { "Invalid AI session ID" }
    return withContext(AiRequestSession(id)) { block() }
}

internal suspend fun currentAiRequestSessionId(): String =
    checkNotNull(coroutineContext[AiRequestSession]) { "AI request requires a session scope" }.id

internal suspend fun aiRequestHeaders(provider: String): Map<String, String> =
    if (provider.trim().equals(OpenCodeGoProviderId, ignoreCase = true)) mapOf(
        "User-Agent" to AiClientUserAgent,
        "x-opencode-session" to currentAiRequestSessionId(),
    ) else emptyMap()
