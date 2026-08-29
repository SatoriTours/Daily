package com.dailysatori.service.mcp

import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class McpAgentReminderDraftFallbackTest {
    @Test fun streamingToolDraftSurvivesFinalFailureFallbackWithoutDuplicateCard() {
        val drafts = mutableListOf<ReminderDraft>()
        val streamingDraft = draft("stream-1")
        val fallbackDraft = draft("fallback-2")

        addReminderDraftIfNew(drafts, streamingDraft)
        addReminderDraftIfNew(drafts, fallbackDraft)

        assertEquals(listOf(streamingDraft), drafts)
    }

    private fun draft(id: String) = ReminderDraft(
        id, "还信用卡", LocalDate.parse("2026-09-02"), LocalDate.parse("2026-09-04"),
        LocalTime.parse("18:00"), ReminderActiveDayRule.SelectedWeekdays(emptySet()), ReminderProfileSnapshot.gentle(),
    )
}
