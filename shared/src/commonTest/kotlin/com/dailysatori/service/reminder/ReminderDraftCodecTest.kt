package com.dailysatori.service.reminder

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderDraftCodecTest {
    private val codec = ReminderDraftCodec(
        now = { Instant.parse("2026-09-01T01:00:00Z") },
        currentTimeZone = { TimeZone.UTC },
    )

    @Test
    fun encodesAndDecodesYearlyRecurrenceRule() {
        val draft = codec.create("""{"content":"Annual review","start_date":"2026-09-02","end_date":"2026-09-02","first_reminder_time":"09:00","active_day_rule":"daily","recurrence_rule":"yearly:2:29:MARCH_1"}""")

        assertEquals(ReminderRecurrence.Yearly(2, 29, LeapDayPolicy.MARCH_1), draft.recurrence)
        assertTrue(codec.encode(draft).contains("\"recurrence_rule\":\"yearly:2:29:MARCH_1\""))
    }

    @Test
    fun rejectsMalformedRecurrenceRuleInsteadOfSilentlyTreatingItAsOnce() {
        val draft = codec.create("""{"content":"Annual review","start_date":"2026-09-02","end_date":"2026-09-02","first_reminder_time":"09:00","active_day_rule":"daily","recurrence_rule":"yearly:2:30:MARCH_1"}""")

        assertTrue(draft.validationErrors.any { it.contains("recurrence_rule") })
    }
}
