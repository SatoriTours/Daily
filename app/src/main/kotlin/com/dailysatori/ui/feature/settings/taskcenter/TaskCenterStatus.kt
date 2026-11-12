package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.asynctask.asyncTaskStatusDisplayName

enum class TaskCenterTab { Current, Pending, History }

internal fun taskCenterTabOf(status: String): TaskCenterTab = when (status) {
    AsyncTaskStatus.queued.name,
    AsyncTaskStatus.running.name,
    AsyncTaskStatus.retrying.name,
    -> TaskCenterTab.Current
    AsyncTaskStatus.failed.name -> TaskCenterTab.Pending
    else -> TaskCenterTab.History
}

internal fun taskCenterTabStatuses(tab: TaskCenterTab): Set<String> = when (tab) {
    TaskCenterTab.Current -> setOf(
        AsyncTaskStatus.queued.name,
        AsyncTaskStatus.running.name,
        AsyncTaskStatus.retrying.name,
    )
    TaskCenterTab.Pending -> setOf(AsyncTaskStatus.failed.name)
    TaskCenterTab.History -> taskCenterHistoryStatuses()
}

internal fun taskCenterHistoryStatuses(): Set<String> = setOf(
    AsyncTaskStatus.succeeded.name,
    AsyncTaskStatus.failed.name,
    AsyncTaskStatus.cancelled.name,
)

/**
 * Tab counters come from repository totals. When a real total is unavailable nothing is shown:
 * the size of the currently loaded page is never presented as the number of tasks.
 */
internal fun taskCenterTabCountText(loaded: Int, total: Long?): String = when {
    total == null -> ""
    else -> total.toString()
}

internal data class TaskCenterProgress(val current: Long, val total: Long) {
    val fraction: Float
        get() = if (total <= 0L) 0f else (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    val text: String
        get() = "$current / $total"
}

internal fun taskCenterProgress(item: AsyncTaskListItem): TaskCenterProgress? =
    if (item.progressTotal > 0L) TaskCenterProgress(item.progressCurrent, item.progressTotal) else null

internal data class TaskCenterRowText(
    val headline: String,
    val detail: String,
    val retryAtMs: Long? = null,
)

internal fun taskCenterRowText(item: AsyncTaskListItem, strings: TaskCenterStrings): TaskCenterRowText {
    if (item.type == AsyncTaskType.external_favorite_sync.name) {
        val base = item.progressMessage.ifBlank { asyncTaskStatusDisplayName(item.status) }
        val itemsSeen = taskCenterCheckpointLong(item.checkpointJson, "itemsSeen")
        val headline = if (itemsSeen != null) strings.favoriteItems(base, itemsSeen) else base
        val detail = if (item.progressTotal > 0L) {
            strings.favoritePages(item.progressCurrent, item.progressTotal)
        } else {
            ""
        }
        return TaskCenterRowText(headline, detail, item.runAfterMs)
    }
    return when (item.status) {
        AsyncTaskStatus.queued.name -> TaskCenterRowText(
            headline = item.progressMessage.ifBlank { strings.waiting },
            detail = "",
        )
        AsyncTaskStatus.running.name -> TaskCenterRowText(
            headline = item.progressMessage.ifBlank { strings.running },
            detail = "",
        )
        AsyncTaskStatus.retrying.name -> TaskCenterRowText(
            headline = strings.retryingTitle,
            detail = item.lastErrorMessage.ifBlank { strings.retryingDetail },
            retryAtMs = item.runAfterMs,
        )
        AsyncTaskStatus.failed.name -> TaskCenterRowText(
            headline = strings.failedTitle,
            detail = item.lastErrorMessage.ifBlank { strings.failedDetail },
        )
        AsyncTaskStatus.cancelled.name -> TaskCenterRowText(strings.cancelled, item.lastErrorMessage)
        AsyncTaskStatus.succeeded.name -> TaskCenterRowText(
            headline = item.progressMessage.ifBlank { strings.succeeded },
            detail = "",
        )
        else -> TaskCenterRowText(
            headline = item.progressMessage.ifBlank { asyncTaskStatusDisplayName(item.status) },
            detail = "",
        )
    }
}

/** True when the task still has work in flight and can be cancelled from the detail screen. */
internal fun taskCenterIsActive(status: String): Boolean = taskCenterTabOf(status) == TaskCenterTab.Current
