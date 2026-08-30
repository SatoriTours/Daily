package com.dailysatori.service.reminder

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

object ReminderSummary {
    fun todayPendingReminders(reminders: List<Reminder>, today: LocalDate): List<Reminder> = reminders.filter {
        it.dataIssue == null && it.status in pendingStatuses && it.isActiveOccurrenceOn(today)
    }

    fun todayPendingCount(reminders: List<Reminder>, today: LocalDate): Int =
        todayPendingReminders(reminders, today).size

    private fun Reminder.isActiveOccurrenceOn(today: LocalDate): Boolean =
        today in startDate..endDate && activeDayRule.includes(today) && when (recurrence) {
            ReminderRecurrence.Once -> true
            else -> nextOccurrenceOnOrAfter(today) == today
        }

    private fun ReminderActiveDayRule.includes(date: LocalDate): Boolean = when (this) {
        ReminderActiveDayRule.Daily, ReminderActiveDayRule.ConsecutiveDateRange -> true
        ReminderActiveDayRule.Weekdays -> date.dayOfWeek.value <= 5
        is ReminderActiveDayRule.SelectedWeekdays -> date.dayOfWeek in days
    }

    private val pendingStatuses = setOf(ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED)
}

fun nextCycleDate(reminder: Reminder, completedDate: LocalDate): LocalDate? =
    reminder.nextOccurrenceOnOrAfter(completedDate.plus(1, DateTimeUnit.DAY))
