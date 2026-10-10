package com.dailysatori.service.reminder

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object ReminderSummary {
    fun todayPendingReminders(reminders: List<Reminder>, today: LocalDate): List<Reminder> =
        reminders.filter { pendingOccurrenceDate(it, today) != null }

    fun todayPendingCount(reminders: List<Reminder>, today: LocalDate): Int =
        todayPendingReminders(reminders, today).size

    /** Notification expiry/cutoff is not completion; retain only the latest unfinished occurrence. */
    fun pendingOccurrenceDate(reminder: Reminder, today: LocalDate): LocalDate? {
        if (reminder.dataIssue != null) return null
        if (reminder.isUnscheduledSmsTodo) return today
        if (reminder.status !in pendingStatuses) return null
        reminder.deadlineAt?.let { return it.toLocalDateTime(TimeZone.currentSystemDefault()).date }
        val latest = reminder.latestOccurrenceOnOrBefore(today) ?: return null
        if (reminder.recurrence == ReminderRecurrence.Once) {
            return if (today <= reminder.endDate) today else reminder.endDate
        }
        val completedDate = reminder.completedAt?.toLocalDateTime(reminder.timeZone)?.date
        return latest.takeUnless { completedDate != null && it <= completedDate }
    }

    private val pendingStatuses = setOf(ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED, ReminderStatus.EXPIRED)
}

fun nextCycleDate(reminder: Reminder, completedDate: LocalDate): LocalDate? =
    reminder.nextOccurrenceOnOrAfter(completedDate.plus(1, DateTimeUnit.DAY))
