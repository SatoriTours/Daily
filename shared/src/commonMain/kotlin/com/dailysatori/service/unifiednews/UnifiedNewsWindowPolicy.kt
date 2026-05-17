package com.dailysatori.service.unifiednews

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private val RunOrder = listOf(
    UnifiedNewsWindowKey.W0800,
    UnifiedNewsWindowKey.W1330,
    UnifiedNewsWindowKey.W1800,
    UnifiedNewsWindowKey.W2100,
    UnifiedNewsWindowKey.FINAL,
)

fun dailyUnifiedNewsWindowFor(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): UnifiedNewsWindow {
    val date = now.toLocalDateTime(timeZone).date
    val start = date.atStartOfDayIn(timeZone)
    val end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).minus(1, DateTimeUnit.MILLISECOND)
    return UnifiedNewsWindow(
        key = UnifiedNewsWindowKey.DAILY,
        summaryDate = date.toString(),
        startMs = start.toEpochMilliseconds(),
        endMs = end.toEpochMilliseconds(),
    )
}

fun unifiedNewsWindowFor(
    key: UnifiedNewsWindowKey,
    dueAt: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): UnifiedNewsWindow {
    val dueLocal = dueAt.toLocalDateTime(timeZone)
    val summaryDate = if (key == UnifiedNewsWindowKey.FINAL) {
        dueLocal.date.minus(1, DateTimeUnit.DAY)
    } else if (dueLocal.time < key.localTime()) {
        dueLocal.date.minus(1, DateTimeUnit.DAY)
    } else {
        dueLocal.date
    }
    val start = summaryDate.atStartOfDayIn(timeZone)
    val end = if (key == UnifiedNewsWindowKey.FINAL) {
        summaryDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).minus(1, DateTimeUnit.MILLISECOND)
    } else {
        key.dueInstantOn(summaryDate, timeZone)
    }
    return UnifiedNewsWindow(
        key = key,
        summaryDate = summaryDate.toString(),
        startMs = start.toEpochMilliseconds(),
        endMs = end.toEpochMilliseconds(),
    )
}

fun nextUnifiedNewsWindow(
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): NextUnifiedNewsWindow {
    val localNow = now.toLocalDateTime(timeZone)
    val today = localNow.date
    for (key in RunOrder) {
        val due = key.dueInstantOn(if (key == UnifiedNewsWindowKey.FINAL) today.plus(1, DateTimeUnit.DAY) else today, timeZone)
        if (due > now) return NextUnifiedNewsWindow(key, due)
    }
    return NextUnifiedNewsWindow(UnifiedNewsWindowKey.W0800, UnifiedNewsWindowKey.W0800.dueInstantOn(today.plus(1, DateTimeUnit.DAY), timeZone))
}

fun dueUnifiedNewsWindows(
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<UnifiedNewsWindow> {
    val localNow = now.toLocalDateTime(timeZone)
    val today = localNow.date
    val due = mutableListOf<UnifiedNewsWindow>()
    val todayFinalDue = UnifiedNewsWindowKey.FINAL.dueInstantOn(today, timeZone)
    if (todayFinalDue <= now) due += unifiedNewsWindowFor(UnifiedNewsWindowKey.FINAL, todayFinalDue, timeZone)
    RunOrder.filterNot { it == UnifiedNewsWindowKey.FINAL }.forEach { key ->
        val dueAt = key.dueInstantOn(today, timeZone)
        if (dueAt <= now) due += unifiedNewsWindowFor(key, dueAt, timeZone)
    }
    return due
}

fun backfillUnifiedNewsWindows(
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    daysBack: Int = 2,
): List<UnifiedNewsWindow> {
    val today = now.toLocalDateTime(timeZone).date
    val windows = mutableListOf<UnifiedNewsWindow>()
    for (offset in daysBack downTo 1) {
        val summaryDate = today.minus(offset, DateTimeUnit.DAY)
        RunOrder.forEach { key ->
            val dueDate = if (key == UnifiedNewsWindowKey.FINAL) summaryDate.plus(1, DateTimeUnit.DAY) else summaryDate
            val dueAt = key.dueInstantOn(dueDate, timeZone)
            if (dueAt <= now) windows += unifiedNewsWindowFor(key, dueAt, timeZone)
        }
    }
    windows += dueUnifiedNewsWindows(now, timeZone)
    return windows.distinctBy { window -> window.summaryDate to window.key }
}

private fun UnifiedNewsWindowKey.dueInstantOn(date: LocalDate, timeZone: TimeZone): Instant =
    LocalDateTime(date, localTime()).toInstant(timeZone)

private fun UnifiedNewsWindowKey.localTime(): LocalTime = LocalTime(hour, minute)
