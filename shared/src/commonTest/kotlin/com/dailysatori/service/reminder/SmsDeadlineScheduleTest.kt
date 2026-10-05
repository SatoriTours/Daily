package com.dailysatori.service.reminder

import kotlin.test.*
import kotlinx.datetime.*

class SmsDeadlineScheduleTest {
    private val deadline = Instant.parse("2026-10-06T15:00:00Z")
    private fun input(now: String, status: ReminderStatus, last: String? = null) = ReminderScheduleInput(
        now = Instant.parse(now), timeZone = TimeZone.UTC,
        startDate = LocalDate(2026, 10, 5), endDate = LocalDate(2026, 10, 6),
        firstReminderTime = LocalTime(15, 0), activeDayRule = ReminderActiveDayRule.Daily,
        profile = ReminderProfileSnapshot.standard(), status = status, expectedVersion = 1,
        deadlineAt = deadline, lastNotifiedAt = last?.let(Instant::parse),
    )

    @Test fun creationSchedulesOnlyBeforeDeadlineWithoutHourlyRepeats() {
        val engine = ReminderScheduleEngine()
        assertEquals(Instant.parse("2026-10-06T13:00:00Z"), assertIs<ReminderScheduleDecision.Schedule>(engine.next(input("2026-10-05T15:00:00Z", ReminderStatus.ACTIVE))).at)
        assertEquals(Instant.parse("2026-10-06T13:00:00Z"), assertIs<ReminderScheduleDecision.Schedule>(engine.next(input("2026-10-05T15:01:00Z", ReminderStatus.NOTIFIED, "2026-10-05T15:00:00Z"))).at)
        assertEquals(deadline, assertIs<ReminderScheduleDecision.Cutoff>(engine.next(input("2026-10-06T13:01:00Z", ReminderStatus.NOTIFIED, "2026-10-06T13:00:00Z"))).at)
    }

    @Test fun expiryAndCompletionPreventFurtherDelivery() {
        val engine = ReminderScheduleEngine()
        assertEquals(ReminderStatus.EXPIRED, assertIs<ReminderScheduleDecision.None>(engine.next(input("2026-10-06T15:00:00Z", ReminderStatus.ACTIVE))).status)
        assertIs<ReminderScheduleDecision.None>(engine.next(input("2026-10-05T15:00:00Z", ReminderStatus.COMPLETED)))
    }

    @Test fun timezoneChangesCannotShiftDeadlineOrLeadReminder() {
        val changed = input("2026-10-05T15:01:00Z", ReminderStatus.NOTIFIED, "2026-10-05T15:00:00Z").copy(timeZone = TimeZone.of("Pacific/Auckland"))
        assertEquals(Instant.parse("2026-10-06T13:00:00Z"), assertIs<ReminderScheduleDecision.Schedule>(ReminderScheduleEngine().next(changed)).at)
    }

    @Test fun deadlineReminderRemainsVisibleAfterReceiptDay() {
        val reminder = Reminder("sms", "充值", LocalDate(2026, 10, 5), LocalDate(2026, 10, 6), LocalTime(15, 0),
            ReminderActiveDayRule.Daily, ReminderProfileSnapshot.standard(), ReminderStatus.NOTIFIED, TimeZone.UTC, 1, deadlineAt = deadline)
        assertEquals(LocalDate(2026, 10, 6), reminder.nextOccurrenceOnOrAfter(LocalDate(2026, 10, 6)))
        assertNull(reminder.nextOccurrenceOnOrAfter(LocalDate(2026, 10, 7)))
    }
}
