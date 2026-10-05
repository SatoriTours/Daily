package com.dailysatori.service.reminder

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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

    @Test
    fun aiMonthlyReminderWithoutTimeUsesNineAndAdvancesExpiredOccurrence() {
        val subject = ReminderDraftCodec(now = { Instant.parse("2026-10-05T08:00:00Z") })
        val response = """{"content":"给 DMIT 的日本服务器续费","start_date":"2026-10-02","end_date":"2026-10-02","first_reminder_time":"","active_day_rule":"daily","recurrence_rule":"monthly:2"}"""

        val draft = ReminderBatchCodec(subject).decode("[{\"source_index\":0,${response.drop(1)}]", TimeZone.UTC).drafts.single().draft

        assertEquals(LocalDate(2026, 11, 2), draft.startDate)
        assertEquals(LocalDate(2026, 11, 2), draft.endDate)
        assertEquals(LocalTime(9, 0), draft.firstReminderTime)
        assertEquals(ReminderRecurrence.Monthly(2), draft.recurrence)
        assertTrue(draft.validationErrors.isEmpty(), draft.validationErrors.toString())
        assertTrue(subject.create(response, TimeZone.UTC).validationErrors.isNotEmpty())
    }

    @Test
    fun aiRecurringReminderSkipsElapsedTimeTodayInDeviceTimezone() {
        val subject = ReminderDraftCodec(now = { Instant.parse("2026-09-02T02:00:00Z") })
        val draft = subject.decodeInterpretationResponse("""{"content":"续费","start_date":"2026-09-02","end_date":"2026-09-02","first_reminder_time":"09:00","active_day_rule":"daily","recurrence_rule":"monthly:2"}""", TimeZone.of("Asia/Shanghai"))

        assertEquals(LocalDate(2026, 10, 2), draft.startDate)
        assertEquals(LocalTime(9, 0), draft.firstReminderTime)
        assertTrue(draft.validationErrors.isEmpty())
    }

    @Test
    fun aiDefaultsMissingTimeButRetainsExplicitInvalidTimeAndExpiredOneOffErrors() {
        val response = """{"content":"续费","start_date":"2026-09-02","end_date":"2026-09-02","active_day_rule":"daily","recurrence_rule":"once"}"""

        val draft = codec.decodeInterpretationResponse(response, TimeZone.UTC)
        assertEquals(LocalTime(9, 0), draft.firstReminderTime)
        assertTrue(draft.validationErrors.isEmpty())
        val invalidTime = codec.decodeInterpretationResponse(response.replaceFirst("{", "{\"first_reminder_time\":\"25:00\","), TimeZone.UTC)
        assertTrue(invalidTime.validationErrors.any { it.contains("first_reminder_time") })
        val expired = codec.decodeInterpretationResponse(response.replace("2026-09-02", "2026-08-02"), TimeZone.UTC)
        assertTrue(expired.validationErrors.any { it.contains("已过期") })
    }

    @Test
    fun storedRecurringDraftDoesNotMoveWhenReopenedAfterItsDate() {
        val original = codec.decodeInterpretationResponse("""{"content":"续费","start_date":"2026-09-02","end_date":"2026-09-02","first_reminder_time":"09:00","active_day_rule":"daily","recurrence_rule":"monthly:2"}""", TimeZone.UTC)
        val later = ReminderDraftCodec(now = { Instant.parse("2026-10-05T08:00:00Z") })
        val decoded = later.decodeInterpretationResponse(codec.encode(original), TimeZone.UTC)

        assertEquals(LocalDate(2026, 9, 2), decoded.startDate)
        assertTrue(decoded.validationErrors.any { it.contains("已过期") })
    }

    @Test
    fun aiExpiredYearlyLeapDayRetainsItsPolicyAndExplicitTime() {
        val subject = ReminderDraftCodec(now = { Instant.parse("2026-10-05T08:00:00Z") })
        val draft = subject.decodeInterpretationResponse("""{"content":"续费","start_date":"2026-03-01","end_date":"2026-03-01","first_reminder_time":"20:00","active_day_rule":"daily","recurrence_rule":"yearly:2:29:MARCH_1"}""", TimeZone.UTC)

        assertEquals(LocalDate(2027, 3, 1), draft.startDate)
        assertEquals(LocalTime(20, 0), draft.firstReminderTime)
        assertEquals(ReminderRecurrence.Yearly(2, 29, LeapDayPolicy.MARCH_1), draft.recurrence)
        assertTrue(draft.validationErrors.isEmpty())
    }
}
