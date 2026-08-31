package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.nextOccurrenceOnOrAfter
import kotlinx.datetime.LocalDate

data class ReminderTimelineStep(val title: String, val detail: String)

data class ReminderTimelineUi(
    val occurrenceDate: LocalDate?,
    val steps: List<ReminderTimelineStep>,
)

fun buildReminderTimeline(reminder: Reminder, today: LocalDate): ReminderTimelineUi {
    val occurrence = reminder.nextOccurrenceOnOrAfter(today)
    val profile = reminder.profile
    val backoff = profile.daytimeDismissalBackoffMinutes
        .joinToString("、") { minutes -> if (minutes % 60 == 0) "${minutes / 60} 小时" else "$minutes 分钟" }
        .ifBlank { "不再重复" }
    val evening = when {
        profile.eveningIntervalMinutes != null ->
            "${profile.eveningStart}–24:00 每 ${profile.eveningIntervalMinutes} 分钟提醒一次"
        profile.eveningTimes.isNotEmpty() -> profile.eveningTimes.sorted().joinToString("、") + " 提醒"
        else -> "不额外加强"
    }
    val cutoff = if (profile.dailyCutoff.hour == 0 && profile.dailyCutoff.minute == 0) "24:00 永久结束" else "${profile.dailyCutoff} 结束"
    return ReminderTimelineUi(
        occurrenceDate = occurrence,
        steps = listOf(
            ReminderTimelineStep("首次提醒", "${occurrence ?: reminder.startDate} ${reminder.firstReminderTime}"),
            ReminderTimelineStep("划掉后", backoff),
            ReminderTimelineStep("晚间加强", evening),
            ReminderTimelineStep("当天结束", cutoff),
        ),
    )
}
