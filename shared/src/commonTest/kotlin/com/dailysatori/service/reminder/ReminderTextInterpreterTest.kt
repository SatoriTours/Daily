package com.dailysatori.service.reminder

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderTextInterpreterTest {
    private val now = Instant.parse("2026-08-31T00:00:00Z")
    private val zone = TimeZone.of("Asia/Shanghai")

    @Test
    fun parsesStrictYearlyDateAndTimeLocally() = runBlocking {
        val remote = FakeRemote()
        val interpretation = interpreter(remote).interpret("每年9月2日晚上8点提醒我充值", now, zone)

        assertEquals("充值", interpretation.draft.content)
        assertEquals("20:00", interpretation.draft.firstReminderTime.toString())
        assertEquals(ReminderRecurrence.Yearly(9, 2, LeapDayPolicy.FEBRUARY_28), interpretation.draft.recurrence)
        assertEquals(0, remote.calls)
    }

    @Test
    fun parsesStrictMonthlyDateAndTimeLocallyWithoutRemoteCall() = runBlocking {
        val remote = FakeRemote()
        val interpretation = interpreter(remote).interpret("每月2日晚上8点提醒我充值", now, zone)

        assertEquals(ReminderRecurrence.Monthly(2), interpretation.draft.recurrence)
        assertEquals(LocalDate(2026, 9, 2), interpretation.draft.startDate)
        assertEquals("20:00", interpretation.draft.firstReminderTime.toString())
        assertEquals(0, remote.calls)
    }

    @Test
    fun parsesStrictDateAndTimeLocallyWithoutRemoteCall() = runBlocking {
        val remote = FakeRemote()
        val interpretation = interpreter(remote).interpret("9月2日晚上8点提醒我充值", now, zone)

        assertEquals(ReminderRecurrence.Once, interpretation.draft.recurrence)
        assertEquals(LocalDate(2026, 9, 2), interpretation.draft.startDate)
        assertEquals(0, remote.calls)
    }

    @Test
    fun localDateUsesInterpretationTimezoneAcrossYearBoundary() = runBlocking {
        val utcNow = Instant.parse("2026-12-31T16:30:00Z")
        val interpretation = interpreter(FakeRemote()).interpret("每月2日提醒我充值", utcNow, TimeZone.of("Asia/Shanghai"))

        assertEquals(LocalDate(2027, 1, 2), interpretation.draft.startDate)
        assertEquals("Asia/Shanghai", interpretation.draft.timeZone.id)
    }

    @Test
    fun missingConfigurationRequiresConfirmationAndPreservesText() = runBlocking {
        val interpretation = ReminderTextInterpreter(ReminderDraftCodec({ now }, { TimeZone.UTC })).interpret("提醒我充值", now, zone)

        assertEquals("提醒我充值", interpretation.draft.content)
        assertEquals("Asia/Shanghai", interpretation.draft.timeZone.id)
        assertTrue(interpretation.requiresConfirmation)
        assertTrue(interpretation.failure != null)
    }

    @Test
    fun conflictingRemoteRangeRequiresConfirmation() = runBlocking {
        val remote = FakeRemote("""{"content":"充值","start_date":"2026-09-03","end_date":"2026-09-02","first_reminder_time":"20:00","active_day_rule":"daily","recurrence_rule":"once"}""")

        val interpretation = interpreter(remote).interpret("提醒我充值", now, zone)

        assertTrue(interpretation.requiresConfirmation)
        assertTrue(interpretation.draft.validationErrors.any { it.contains("end_date") })
    }

    @Test
    fun exactSameTextUsesCachedInterpretation() = runBlocking {
        val remote = FakeRemote("""{"content":"充值","start_date":"2026-09-02","end_date":"2026-09-02","first_reminder_time":"20:00","active_day_rule":"daily","recurrence_rule":"once"}""")
        val subject = interpreter(remote)

        subject.interpret("提醒我充值", now, zone)
        subject.interpret("提醒我充值", now, zone)

        assertEquals(1, remote.calls)
        assertEquals(now, remote.lastNow)
        assertEquals(zone, remote.lastZone)
    }

    @Test
    fun fallbackFailurePreservesOriginalTextForManualConfirmation() = runBlocking {
        val interpretation = interpreter(FakeRemote(IllegalStateException("offline"))).interpret("提醒我充值", now, zone)

        assertEquals("提醒我充值", interpretation.draft.content)
        assertTrue(interpretation.requiresConfirmation)
        assertTrue(interpretation.failure != null)
    }

    private fun interpreter(remote: ReminderInterpretationRemote) = ReminderTextInterpreter(
        codec = ReminderDraftCodec({ now }, { zone }),
        remote = remote,
    )

    private class FakeRemote(private val response: Any = "") : ReminderInterpretationRemote {
        var calls = 0
        var lastNow: Instant? = null
        var lastZone: TimeZone? = null
        override suspend fun interpret(text: String, now: Instant, zone: TimeZone): String {
            calls++
            lastNow = now
            lastZone = zone
            if (response is Throwable) throw response
            return response as String
        }
    }
}
