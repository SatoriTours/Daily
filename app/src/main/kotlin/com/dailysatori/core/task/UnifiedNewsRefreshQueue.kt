package com.dailysatori.core.task

import com.dailysatori.data.repository.AsyncTaskEnqueueRequest
import com.dailysatori.data.repository.AsyncTaskFamilyEnqueueResult
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.shared.db.Async_task
import kotlinx.serialization.json.Json

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

/** Pending generation identity decoded from its durable payload, never from execution time. */
private data class PendingNewsWindow(val manual: Boolean, val tickDueAtMs: Long?, val decoded: Boolean)

private fun decodePendingNewsWindow(task: Async_task): PendingNewsWindow {
    val payload = runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString<UnifiedNewsGenerateTaskPayload>(task.payload_json)
    }.getOrNull() ?: return PendingNewsWindow(manual = false, tickDueAtMs = null, decoded = false)
    return PendingNewsWindow(
        manual = payload.mode == UnifiedNewsGenerateTaskHandler.MODE_MANUAL ||
            task.unique_key == unifiedNewsRefreshUniqueKey(UnifiedNewsGenerateTaskHandler.MODE_MANUAL, null),
        tickDueAtMs = payload.tickDueAtMs,
        decoded = true,
    )
}

internal fun supersededNewsWindowPredicate(currentTickDueAtMs: Long?): (Async_task) -> Boolean = { task ->
    val pending = decodePendingNewsWindow(task)
    when {
        // An unknown identity is never guessed at, so a newer window cannot be retired by mistake.
        !pending.decoded -> false
        pending.manual -> false
        currentTickDueAtMs == null -> false
        // Legacy or unticked scheduling predates any ticked window.
        pending.tickDueAtMs == null -> true
        else -> pending.tickDueAtMs < currentTickDueAtMs
    }
}

internal fun enqueueUnifiedNewsRefresh(
    tasks: AsyncTaskRepository,
    force: Boolean,
    ignoreSourceTimeFilter: Boolean,
    mode: String,
    tickDueAtMs: Long? = null,
    cancelWork: (Long) -> Unit = {},
    schedule: (Long) -> Unit,
): AsyncTaskFamilyEnqueueResult {
    if (mode != UnifiedNewsGenerateTaskHandler.MODE_MANUAL) {
        // A newer scheduled window supersedes an older pending generation: the older one may wait on a
        // sync that never resolves, and this task type is serial, so it would block every later window.
        // Only a strictly older request identity qualifies; a re-entrant or concurrent older window
        // never retires the newer one.
        val supersededWindow = supersededNewsWindowPredicate(tickDueAtMs)
        val type = AsyncTaskType.remote_news_fetch.name
        tasks.failSupersededPendingTasks(
            type = type,
            code = "superseded_window",
            message = "已由更新的新闻窗口取代",
            isSuperseded = supersededWindow,
        )
        // A superseded run must stop spending network/AI time; clearing its lease is what makes the
        // old execution abort and its late result unwritable.
        tasks.cancelSupersededRunningTasks(
            type = type,
            code = "superseded_window",
            message = "已由更新的新闻窗口取代",
            isSuperseded = supersededWindow,
        ).forEach(cancelWork)
    }
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
