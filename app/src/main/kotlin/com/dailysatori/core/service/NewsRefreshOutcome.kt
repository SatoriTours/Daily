package com.dailysatori.core.service

/**
 * LAN refresh response contract. A refresh that is still running is reported as accepted, a terminal
 * task maps to the summary it produced, and a terminal task that produced no usable summary is a
 * failure: cancelled or interrupted work must never be reported as success.
 */
internal sealed interface NewsRefreshOutcome {
    /** Still running when the bounded wait expired; the client shows "accepted" and stops there. */
    data class Accepted(val status: String, val taskId: Long) : NewsRefreshOutcome

    /** The window finished and its summary is displayable. */
    data class Completed(val status: String) : NewsRefreshOutcome

    /** Terminal without a usable summary, or an explicit failure. */
    data class Failed(val message: String) : NewsRefreshOutcome
}

internal const val NEWS_REFRESH_ACCEPTED_QUEUED = "queued"
internal const val NEWS_REFRESH_ACCEPTED_RUNNING = "running"

/** Accepted states the client may show as "已受理"; anything terminal is mapped explicitly. */
internal fun newsRefreshAcceptedStatus(taskStatus: String): String? = when (taskStatus) {
    "queued", "retrying", "" -> NEWS_REFRESH_ACCEPTED_QUEUED
    "running" -> NEWS_REFRESH_ACCEPTED_RUNNING
    else -> null
}

internal fun newsRefreshOutcome(
    taskId: Long,
    taskStatus: String,
    taskError: String,
    summaryStatus: String?,
    summaryError: String?,
): NewsRefreshOutcome {
    newsRefreshAcceptedStatus(taskStatus)?.let { return NewsRefreshOutcome.Accepted(it, taskId) }
    if (taskStatus != "succeeded") {
        val message = taskError.ifBlank { summaryError.orEmpty() }
            .ifBlank {
                when (taskStatus) {
                    "cancelled" -> "新闻汇总生成已取消"
                    else -> "新闻汇总生成失败，请稍后重试"
                }
            }
        return NewsRefreshOutcome.Failed(message)
    }
    return when (summaryStatus) {
        "success", "empty" -> NewsRefreshOutcome.Completed(summaryStatus)
        else -> NewsRefreshOutcome.Failed(
            summaryError.orEmpty().ifBlank { "本次未生成可显示的新闻汇总" },
        )
    }
}
