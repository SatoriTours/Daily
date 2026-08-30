package com.dailysatori.service.reminder

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReminderOccurrenceTest {
    @Test fun yearlyReminderRollsIntoNextYear() {
        val reminder = reminder(recurrence = ReminderRecurrence.Yearly(9, 2, LeapDayPolicy.FEBRUARY_28))

        assertEquals(LocalDate(2027, 9, 2), reminder.nextOccurrenceOnOrAfter(LocalDate(2026, 9, 3)))
    }

    @Test fun leapDayUsesSelectedFallback() {
        val reminder = reminder(recurrence = ReminderRecurrence.Yearly(2, 29, LeapDayPolicy.MARCH_1))

        assertEquals(LocalDate(2027, 3, 1), reminder.nextOccurrenceOnOrAfter(LocalDate(2027, 1, 1)))
    }

    @Test fun onceReminderDoesNotProjectPastItsConfiguredDate() {
        val reminder = reminder()

        assertNull(reminder.nextOccurrenceOnOrAfter(LocalDate(2026, 9, 3)))
    }

    @Test fun monthlyReminderSkipsMonthsWithoutItsConfiguredDay() {
        val reminder = reminder(recurrence = ReminderRecurrence.Monthly(31))

        assertEquals(LocalDate(2026, 3, 31), reminder.nextOccurrenceOnOrAfter(LocalDate(2026, 2, 1)))
    }

    @Test fun recurrenceRejectsInvalidCalendarValues() {
        assertFailsWith<IllegalArgumentException> { ReminderRecurrence.Monthly(0) }
        assertFailsWith<IllegalArgumentException> { ReminderRecurrence.Yearly(13, 1, LeapDayPolicy.FEBRUARY_28) }
        assertFailsWith<IllegalArgumentException> { ReminderRecurrence.Yearly(4, 31, LeapDayPolicy.FEBRUARY_28) }
    }

    private fun reminder(
        recurrence: ReminderRecurrence = ReminderRecurrence.Once,
    ) = Reminder(
        id = "reminder",
        content = "Pay bill",
        startDate = LocalDate(2026, 9, 2),
        endDate = LocalDate(9999, 12, 31),
        firstReminderTime = LocalTime(10, 0),
        activeDayRule = ReminderActiveDayRule.Daily,
        profile = ReminderProfileSnapshot.strong(),
        status = ReminderStatus.ACTIVE,
        timeZone = TimeZone.UTC,
        version = 1,
        recurrence = recurrence,
    )
}
