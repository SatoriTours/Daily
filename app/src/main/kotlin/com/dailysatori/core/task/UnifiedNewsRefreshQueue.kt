package com.dailysatori.core.task

import com.dailysatori.data.repository.AsyncTaskEnqueueRequest
import com.dailysatori.data.repository.AsyncTaskFamilyEnqueueResult
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskType

/**
 * Task identity is one tick of one mode. The family prefix always ends with ':' right after the
 * identity, so an older and a newer tick can never absorb each other, and a manual refresh never
 * merges with a scheduled one. De-duplication stays inside the repository's atomic family
 * transaction; only a task of the same identity may be reused.
 */
internal fun unifiedNewsRefreshUniqueKey(mode: String, tickDueAtMs: Long?): String {
    val identity = unifiedNewsRefreshIdentity(mode, tickDueAtMs)
    return "remote_news_fetch:$identity:news"
}

internal fun unifiedNewsRefreshFamilyPrefix(mode: String, tickDueAtMs: Long?): String =
    "remote_news_fetch:${unifiedNewsRefreshIdentity(mode, tickDueAtMs)}:"

private fun unifiedNewsRefreshIdentity(mode: String, tickDueAtMs: Long?): String = when {
    mode == UnifiedNewsGenerateTaskHandler.MODE_DUE && tickDueAtMs != null -> "$mode:$tickDueAtMs"
    // Legacy or unticked scheduling keeps its own isolated family instead of absorbing newer ticks.
    mode == UnifiedNewsGenerateTaskHandler.MODE_DUE -> "$mode:unticked"
    else -> mode
}

internal fun enqueueUnifiedNewsRefresh(
    tasks: AsyncTaskRepository,
    force: Boolean,
    ignoreSourceTimeFilter: Boolean,
    mode: String,
    tickDueAtMs: Long? = null,
    schedule: (Long) -> Unit,
): AsyncTaskFamilyEnqueueResult {
    val predecessor = if (mode == UnifiedNewsGenerateTaskHandler.MODE_MANUAL) null else AsyncTaskEnqueueRequest(
        type = AsyncTaskType.remote_article_sync.name,
        payloadJson = remoteArticleSyncTaskPayloadJson(mode = mode),
        uniqueKey = "remote_article_sync:${unifiedNewsRefreshIdentity(mode, tickDueAtMs)}:sync",
    )
    val result = tasks.enqueueUniqueFamilyChain(
        request = AsyncTaskEnqueueRequest(
            type = AsyncTaskType.remote_news_fetch.name,
            payloadJson = unifiedNewsGenerateTaskPayloadJson(force, ignoreSourceTimeFilter, mode, tickDueAtMs),
            uniqueKey = unifiedNewsRefreshUniqueKey(mode, tickDueAtMs),
        ),
        uniqueKeyPrefix = unifiedNewsRefreshFamilyPrefix(mode, tickDueAtMs),
        predecessor = predecessor,
    )
    result.predecessorId?.let(schedule)
    schedule(result.taskId)
    return result
}
