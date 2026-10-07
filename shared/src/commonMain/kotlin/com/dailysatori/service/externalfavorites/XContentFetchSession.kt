package com.dailysatori.service.externalfavorites

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val DEFAULT_X_OFFICIAL_CONTENT_LIMIT = 10

internal class XContentDeferredException(val reason: String) : RuntimeException("X 正文官方兜底额度已用完或接口限流，将稍后重试")

/** One owner per sync/organization run; child coroutines share its atomic HTTP-request budget. */
internal class XContentFetchSession(private val limit: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<XContentFetchSession>
    private val mutex = Mutex()
    private var officialBlocked = false
    private val counts = linkedMapOf("fx_success" to 0, "fx_backoff" to 0, "fx_errors" to 0,
        "official_requests" to 0, "official_success" to 0, "body_missing" to 0, "budget_deferred" to 0, "cache_hit" to 0)

    suspend fun record(event: String) = mutex.withLock { counts[event] = counts.getValue(event) + 1 }
    suspend fun requireCapacity(claim: Boolean = false) = mutex.withLock {
        if (officialBlocked || counts.getValue("official_requests") >= limit) {
            counts["budget_deferred"] = counts.getValue("budget_deferred") + 1
            throw XContentDeferredException(if (officialBlocked) "official_rate_limited" else "official_budget")
        }
        if (claim) counts["official_requests"] = counts.getValue("official_requests") + 1
    }
    suspend fun blockOfficial() = mutex.withLock { officialBlocked = true }
    suspend fun summary(): Map<String, String> = mutex.withLock { counts.mapValues { it.value.toString() } }
}

internal suspend fun currentXContentFetchSession(): XContentFetchSession? = coroutineContext[XContentFetchSession]

internal fun xOfficialContentRequestLimit(configJson: String): Int =
    favoriteMetadata(configJson).textValue("x_official_content_request_limit")?.toIntOrNull()
        ?.coerceIn(0, 100) ?: DEFAULT_X_OFFICIAL_CONTENT_LIMIT

internal suspend fun <T> withXContentFetchSession(
    limit: Int = DEFAULT_X_OFFICIAL_CONTENT_LIMIT,
    logger: FavoriteSyncHttpLogger = NoopFavoriteSyncHttpLogger,
    taskId: Long? = null,
    block: suspend () -> T,
): T {
    if (currentXContentFetchSession() != null) return block()
    val session = XContentFetchSession(limit.coerceIn(0, 100))
    return withContext(session) {
        try { block() } finally {
            withContext(NonCancellable) {
                val counts = session.summary()
                runCatching { logger.logRequest(taskId, "x_content_fetch_summary", "SUMMARY", "local://x-content-fetch", counts) }
            }
        }
    }
}
