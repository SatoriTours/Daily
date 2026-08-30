package com.dailysatori.service.reminder

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderSummaryTest {
    private val today = LocalDate(2026, 9, 2)

    @Test fun dismissedReminderStillCountsAsPendingToday() {
        assertEquals(1, ReminderSummary.todayPendingCount(listOf(reminder(ReminderStatus.DISMISSED)), today))
    }

    @Test fun excludesTerminalFutureAndQuarantinedReminders() {
        val reminders = listOf(
            reminder(ReminderStatus.ACTIVE, recurrence = ReminderRecurrence.Yearly(9, 3, LeapDayPolicy.FEBRUARY_28)),
            reminder(ReminderStatus.COMPLETED),
            reminder(ReminderStatus.ACTIVE, dataIssue = ReminderDataIssue.CORRUPT_PROFILE),
        )

        assertEquals(0, ReminderSummary.todayPendingCount(reminders, today))
    }

    private fun reminder(
        status: ReminderStatus,
        recurrence: ReminderRecurrence = ReminderRecurrence.Once,
        dataIssue: ReminderDataIssue? = null,
    ) = Reminder(
        id = "reminder-$status-$recurrence-$dataIssue",
        content = "Pay bill",
        startDate = today,
        endDate = LocalDate(9999, 12, 31),
        firstReminderTime = LocalTime(10, 0),
        activeDayRule = ReminderActiveDayRule.Daily,
        profile = ReminderProfileSnapshot.strong(),
        status = status,
        timeZone = TimeZone.UTC,
        version = 1,
        dataIssue = dataIssue,
        recurrence = recurrence,
    )
}
