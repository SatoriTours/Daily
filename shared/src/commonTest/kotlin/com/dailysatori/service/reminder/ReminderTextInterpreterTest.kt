package com.dailysatori.service.reminder

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
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
    fun exactSameTextUsesCachedInterpretation() = runBlocking {
        val remote = FakeRemote("""{"content":"充值","start_date":"2026-09-02","end_date":"2026-09-02","first_reminder_time":"20:00","active_day_rule":"daily","recurrence_rule":"once"}""")
        val subject = interpreter(remote)

        subject.interpret("提醒我充值", now, zone)
        subject.interpret("提醒我充值", now, zone)

        assertEquals(1, remote.calls)
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
        override suspend fun interpret(text: String): String {
            calls++
            if (response is Throwable) throw response
            return response as String
        }
    }
}
