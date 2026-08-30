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

    @Test fun onceReminderCountsOnAnActiveDayAfterItsStartDate() {
        val reminder = reminder(
            status = ReminderStatus.ACTIVE,
            startDate = LocalDate(2026, 9, 1),
            endDate = LocalDate(2026, 9, 3),
        )

        assertEquals(1, ReminderSummary.todayPendingCount(listOf(reminder), today))
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
        startDate: LocalDate = today,
        endDate: LocalDate = LocalDate(9999, 12, 31),
    ) = Reminder(
        id = "reminder-$status-$recurrence-$dataIssue",
        content = "Pay bill",
        startDate = startDate,
        endDate = endDate,
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
