package com.dailysatori.service.reminder

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReminderScheduleEngineTest {
    private val engine = ReminderScheduleEngine()
    private val zone = TimeZone.UTC

    @Test fun untouchedNotificationRepeatsHourly() {
        assertSchedule("2026-09-02T13:00", engine.next(input(now = local("2026-09-02T12:00"), status = ReminderStatus.NOTIFIED)))
    }

    @Test fun swipeBackoffAdvancesFromTwoHoursToCappedFourHours() {
        assertSchedule("2026-09-02T14:00", engine.next(input(now = local("2026-09-02T12:00"), dismissals = 1)))
        assertSchedule("2026-09-02T16:00", engine.next(input(now = local("2026-09-02T12:00"), dismissals = 2)))
        assertSchedule("2026-09-02T16:00", engine.next(input(now = local("2026-09-02T12:00"), dismissals = 3)))
    }

    @Test fun strongDismissalBackoffAndEveningOverride() {
        assertSchedule("2026-09-02T14:00", engine.next(input(now = local("2026-09-02T12:00"), dismissals = 1)))
        assertSchedule("2026-09-02T23:00", engine.next(input(now = local("2026-09-02T22:05"), dismissals = 2)))
    }

    @Test fun dailyCutoffRollsToNextActiveDateOrExpiresAfterFinalDate() {
        val multiDay = input(now = local("2026-09-03T00:00"), endDate = LocalDate.parse("2026-09-04"))
        assertSchedule("2026-09-03T10:00", engine.next(multiDay))
        assertEquals(ReminderStatus.EXPIRED, assertIs<ReminderScheduleDecision.None>(
            engine.next(input(now = local("2026-09-03T00:00"), endDate = LocalDate.parse("2026-09-02"))),
        ).status)
    }

    @Test fun eachActiveDayStartsWithFreshFirstReminder() {
        val decision = engine.next(input(
            now = local("2026-09-03T00:00"),
            endDate = LocalDate.parse("2026-09-04"),
            dismissals = 3,
            stateDate = LocalDate.parse("2026-09-02"),
        ))
        assertSchedule("2026-09-03T10:00", decision)
    }

    @Test fun sleepHoursSuppressRepeatsAndRecoverOnceAtNine() {
        val decision = engine.next(input(now = local("2026-09-02T02:00"), status = ReminderStatus.NOTIFIED))
        assertSchedule("2026-09-02T09:00", decision)
        assertEquals(ReminderDeliveryReason.WAKE_RECOVERY, assertIs<ReminderScheduleDecision.Schedule>(decision).reason)
    }

    @Test fun initialReminderInsideSleepHoursMovesToNine() {
        val decision = engine.next(input(
            now = local("2026-09-02T01:00"),
            firstTime = LocalTime.parse("08:00"),
            status = ReminderStatus.ACTIVE,
        ))
        assertSchedule("2026-09-02T09:00", decision)
        assertEquals(ReminderDeliveryReason.WAKE_RECOVERY, assertIs<ReminderScheduleDecision.Schedule>(decision).reason)
    }

    @Test fun weekdayWorkHoursAreSilent() {
        val decision = engine.next(input(now = local("2026-09-02T10:00"), status = ReminderStatus.NOTIFIED))
        assertEquals(true, assertIs<ReminderScheduleDecision.Schedule>(decision).silent)
    }

    @Test fun gentleProfileIsSilentOutsideWorkHours() {
        val decision = assertIs<ReminderScheduleDecision.Schedule>(engine.next(input(
            now = local("2026-09-06T10:00"), startDate = LocalDate.parse("2026-09-06"), status = ReminderStatus.ACTIVE, profile = ReminderProfileSnapshot.gentle(),
        )))
        assertEquals(true, decision.silent)
        assertEquals(false, decision.soundEnabled)
        assertEquals(false, decision.vibrationEnabled)
    }

    @Test fun standardOnlyUsesItsTwentyTwoAndTwentyThreeEveningSlots() {
        val profile = ReminderProfileSnapshot.standard()
        assertSchedule("2026-09-02T22:00", engine.next(input(
            now = local("2026-09-02T21:00"), dismissals = 2, profile = profile,
        )))
        assertSchedule("2026-09-02T23:00", engine.next(input(
            now = local("2026-09-02T22:05"), dismissals = 2, profile = profile,
        )))
        assertEquals(ReminderStatus.EXPIRED, assertIs<ReminderScheduleDecision.None>(engine.next(input(
            now = local("2026-09-02T23:05"), dismissals = 2, profile = profile,
        ))).status)
    }

    @Test fun customProfileDrivesBackoffAndEveningConfiguration() {
        val profile = ReminderProfileSnapshot(
            kind = ReminderProfileKind.CUSTOM,
            daytimeDismissalBackoffMinutes = listOf(30),
            eveningStart = LocalTime.parse("20:00"),
            eveningIntervalMinutes = 30,
            dailyCutoff = LocalTime.parse("22:00"),
        )
        assertSchedule("2026-09-02T12:30", engine.next(input(
            now = local("2026-09-02T12:00"), dismissals = 1, profile = profile,
        )))
        assertSchedule("2026-09-02T21:00", engine.next(input(
            now = local("2026-09-02T20:40"), dismissals = 1, profile = profile,
        )))
    }

    @Test fun pausedCompletedAndExpiredAreTerminal() {
        listOf(ReminderStatus.PAUSED, ReminderStatus.COMPLETED, ReminderStatus.EXPIRED).forEach { status ->
            assertEquals(status, assertIs<ReminderScheduleDecision.None>(engine.next(input(status = status))).status)
        }
    }

    @Test fun timezoneChangesKeepTheConfiguredLocalWallClock() {
        val tokyo = TimeZone.of("Asia/Tokyo")
        val decision = engine.next(input(now = local("2026-09-02T08:00", tokyo), timeZone = tokyo))
        assertEquals(LocalDateTime.parse("2026-09-02T10:00"), assertIs<ReminderScheduleDecision.Schedule>(decision).at.toLocalDateTime(tokyo))
    }

    @Test fun dstGapMovesForwardAndOverlapUsesFirstOccurrence() {
        val losAngeles = TimeZone.of("America/Los_Angeles")
        val gap = engine.next(input(
            now = local("2026-03-08T00:00", losAngeles),
            startDate = LocalDate.parse("2026-03-08"), endDate = LocalDate.parse("2026-03-08"),
            firstTime = LocalTime.parse("02:30"), timeZone = losAngeles, profile = dstProfile(),
        ))
        assertEquals(Instant.parse("2026-03-08T10:30:00Z"), assertIs<ReminderScheduleDecision.Schedule>(gap).at)
        val overlap = engine.next(input(
            now = local("2026-11-01T00:00", losAngeles),
            startDate = LocalDate.parse("2026-11-01"), endDate = LocalDate.parse("2026-11-01"),
            firstTime = LocalTime.parse("01:30"), timeZone = losAngeles, profile = dstProfile(),
        ))
        assertEquals(Instant.parse("2026-11-01T08:30:00Z"), assertIs<ReminderScheduleDecision.Schedule>(overlap).at)
    }

    private fun input(
        now: Instant = local("2026-09-02T10:00"), startDate: LocalDate = LocalDate.parse("2026-09-02"),
        endDate: LocalDate = startDate, firstTime: LocalTime = LocalTime.parse("10:00"),
        status: ReminderStatus = ReminderStatus.DISMISSED, dismissals: Int = 0, stateDate: LocalDate? = null, timeZone: TimeZone = zone,
        profile: ReminderProfileSnapshot = ReminderProfileSnapshot.strong(),
    ) = ReminderScheduleInput(now, timeZone, startDate, endDate, firstTime, ReminderActiveDayRule.Daily, profile, status, dismissals, stateDate, expectedVersion = 7)

    private fun local(value: String, timeZone: TimeZone = zone): Instant = LocalDateTime.parse(value).toInstant(timeZone)

    private fun dstProfile() = ReminderProfileSnapshot.strong().copy(sleepStart = LocalTime.parse("12:00"), sleepEnd = LocalTime.parse("13:00"))

    private fun assertSchedule(expected: String, actual: ReminderScheduleDecision) {
        assertEquals(local(expected), assertIs<ReminderScheduleDecision.Schedule>(actual).at)
    }
}
