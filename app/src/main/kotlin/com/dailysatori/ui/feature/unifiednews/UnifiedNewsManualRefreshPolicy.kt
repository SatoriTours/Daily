package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.unifiednews.UnifiedNewsWindow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

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
