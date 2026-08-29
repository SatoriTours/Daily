package com.dailysatori.service.reminder

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderDraftToolTest {
    private val codec = ReminderDraftCodec(
        now = { Instant.parse("2026-09-01T01:00:00Z") },
        currentTimeZone = { TimeZone.of("Asia/Shanghai") },
    )

    @Test fun createsBoundedAbsoluteLocalDraftWithCapturedTimeZone() {
        val result = codec.create("""{
            "content":"还信用卡",
            "start_date":"2026-09-02",
            "end_date":"2026-09-04",
            "first_reminder_time":"18:00",
            "active_day_rule":"consecutive_date_range",
            "profile":"strong"
        }""".trimIndent())

        assertEquals("还信用卡", result.content)
        assertEquals("2026-09-02", result.startDate.toString())
        assertEquals("2026-09-04", result.endDate.toString())
        assertEquals("18:00", result.firstReminderTime.toString())
        assertEquals("Asia/Shanghai", result.timeZone.id)
        assertTrue(result.validationErrors.isEmpty())
        assertTrue(codec.encode(result).contains("consecutive_date_range"))
    }

    @Test fun normalizesWeekdayRulesAndKeepsMissingOrAmbiguousInputsEditable() {
        val weekdays = codec.create("""{"content":"周报","start_date":"2026-09-01","end_date":"2026-09-30","first_reminder_time":"09:00","active_day_rule":"selected_weekdays","selected_weekdays":["monday","friday"]}""")
        val incomplete = codec.create("""{"content":"提醒我一下","start_date":"tomorrow","first_reminder_time":"09:00","timezone":"UTC"}""")

        assertEquals("SelectedWeekdays", weekdays.activeDayRule::class.simpleName)
        assertTrue(weekdays.validationErrors.isEmpty())
        assertTrue(incomplete.validationErrors.any { it.contains("start_date") })
        assertTrue(incomplete.validationErrors.any { it.contains("end_date") })
        assertTrue(incomplete.validationErrors.any { it.contains("timezone") })
    }

    @Test fun rejectsInvalidRangesAndOverlongContentWithStrictBoundedJson() {
        val tooLong = "x".repeat(501)
        val result = codec.create("""{"content":"$tooLong","start_date":"2026-09-03","end_date":"2026-09-02","first_reminder_time":"18:00","active_day_rule":"every_day","unexpected":true}""")

        assertTrue(result.validationErrors.any { it.contains("content") })
        assertTrue(result.validationErrors.any { it.contains("end_date") })
        assertTrue(result.validationErrors.any { it.contains("active_day_rule") })
        assertTrue(result.validationErrors.any { it.contains("unexpected") })
        assertFalse(codec.encode(result).contains(tooLong))
    }
}
