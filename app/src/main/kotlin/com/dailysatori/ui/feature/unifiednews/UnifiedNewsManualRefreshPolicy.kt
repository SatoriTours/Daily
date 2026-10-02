package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.core.task.NewsOpportunityTaskHandler
import com.dailysatori.core.task.UnifiedNewsGenerateTaskHandler
import com.dailysatori.core.task.enqueueUnifiedNewsRefresh
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryStatus
import com.dailysatori.service.unifiednews.UnifiedNewsWindow
import com.dailysatori.shared.db.Async_task
import com.dailysatori.shared.db.Unified_news_summary
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

enum class NewsManualRefreshNotice { STARTED, ALREADY_RUNNING }

internal fun hasActiveNewsRefreshTask(statuses: List<String?>): Boolean =
    statuses.any { it in setOf("queued", "running", "retrying") }

internal data class NewsManualRefreshSubmission(val taskId: Long?, val notice: NewsManualRefreshNotice)

internal fun enqueueManualNewsRefresh(
    tasks: AsyncTaskRepository,
    isDebugBuild: Boolean,
    schedule: (Long) -> Unit,
): NewsManualRefreshSubmission {
    if (hasActiveNewsRefreshTask(listOf(tasks.getLatestByUniqueKey(NewsOpportunityTaskHandler.TYPE)?.status))) {
        return NewsManualRefreshSubmission(null, NewsManualRefreshNotice.ALREADY_RUNNING)
    }
    val result = enqueueUnifiedNewsRefresh(
        tasks = tasks,
        force = true,
        ignoreSourceTimeFilter = isDebugBuild,
        mode = UnifiedNewsGenerateTaskHandler.MODE_MANUAL,
        schedule = schedule,
    )
    return NewsManualRefreshSubmission(result.taskId,
        if (result.created) NewsManualRefreshNotice.STARTED else NewsManualRefreshNotice.ALREADY_RUNNING)
}

internal fun UnifiedNewsState.withManualNewsRefreshFinished(
    task: Async_task,
    summary: Unified_news_summary?,
): UnifiedNewsState = copy(
    isRegenerating = false,
    regeneratingSummaryDate = null,
    summaryRefreshCompletedToken = summaryRefreshCompletedToken + 1,
    manualRefreshMessage = if (task.status == "succeeded" && summary?.status == UnifiedNewsSummaryStatus.EMPTY.value)
        summary.error_message ?: "当前时间窗口暂无可总结新闻" else null,
    error = if (task.status in setOf("failed", "cancelled"))
        task.last_error_message.ifBlank { "新闻汇总重新生成失败，请稍后重试" } else null,
)

fun manualRefreshWindowForEnvironment(
    currentWindow: UnifiedNewsWindow,
    isDebugBuild: Boolean,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): UnifiedNewsWindow {
    if (!isDebugBuild) return currentWindow
    val endDate = Instant.fromEpochMilliseconds(currentWindow.endMs).toLocalDateTime(timeZone).date
    return currentWindow.copy(
        startMs = endDate.minus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds(),
    )
}
