package com.dailysatori.ui.feature.settings.taskcenter

/**
 * Localized copy for the task center. Pure helpers never hardcode user visible text: callers pass
 * the values resolved from Android string resources so zh/en stay in sync.
 */
internal data class TaskCenterStrings(
    val unknown: String,
    val notStarted: String,
    val todayTemplate: String,
    val waiting: String,
    val running: String,
    val retryingTitle: String,
    val retryingDetail: String,
    val failedTitle: String,
    val failedDetail: String,
    val cancelled: String,
    val succeeded: String,
    val favoriteItemsTemplate: String,
    val favoritePagesTemplate: String,
    val payloadEmpty: String,
    val checkpointEmpty: String,
    val resultEmpty: String,
    val logEmpty: String,
    val requestsEmptyLog: String,
    val requestsEmptyNoCapture: String,
    val evStarted: String,
    val evProgress: String,
    val evSucceeded: String,
    val evFailed: String,
    val evRetry: String,
    val evRetryAt: String,
    val evCancelled: String,
    val evInterrupted: String,
    val evUnknown: String,
    val evReasonFallback: String,
    val summaryQueued: String,
    val summaryRetrying: String,
    val summaryRunning: String,
    val summaryFavoritePages: String,
    val summaryFavoriteItems: String,
    val summaryProgress: String,
    val summaryRetryAt: String,
    val summaryLastReason: String,
    val subjectArticle: String,
    val subjectSource: String,
    val subjectDiary: String,
    val subjectAttachment: String,
    val subjectBatch: String,
) {
    fun favoriteItems(base: String, itemsSeen: Long): String =
        String.format(favoriteItemsTemplate, base, itemsSeen)

    fun favoritePages(current: Long, total: Long): String =
        String.format(favoritePagesTemplate, current, total)

    fun today(clock: String): String = String.format(todayTemplate, clock)
}
