package com.dailysatori.core.task

import com.dailysatori.data.repository.AsyncTaskEnqueueRequest
import com.dailysatori.data.repository.AsyncTaskFamilyEnqueueResult
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskType

internal fun enqueueUnifiedNewsRefresh(
    tasks: AsyncTaskRepository,
    force: Boolean,
    ignoreSourceTimeFilter: Boolean,
    mode: String,
    schedule: (Long) -> Unit,
): AsyncTaskFamilyEnqueueResult {
    val predecessor = if (mode == UnifiedNewsGenerateTaskHandler.MODE_MANUAL) null else AsyncTaskEnqueueRequest(
        type = AsyncTaskType.remote_article_sync.name,
        payloadJson = remoteArticleSyncTaskPayloadJson(mode = mode),
        uniqueKey = "remote_article_sync:$mode",
    )
    val result = tasks.enqueueUniqueFamilyChain(
        request = AsyncTaskEnqueueRequest(
            type = AsyncTaskType.remote_news_fetch.name,
            payloadJson = unifiedNewsGenerateTaskPayloadJson(force, ignoreSourceTimeFilter, mode),
            uniqueKey = "remote_news_fetch:$mode",
        ),
        uniqueKeyPrefix = "remote_news_fetch:",
        predecessor = predecessor,
    )
    result.predecessorId?.let(schedule)
    schedule(result.taskId)
    return result
}
