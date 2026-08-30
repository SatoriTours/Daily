package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderInterpretation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReminderAiParseStateTest {
    @Test
    fun typingDoesNotInterpretUntilExplicitSubmit() {
        val state = ReminderAiParseState().onPromptChanged("每年9月2日提醒我充值")

        assertEquals("每年9月2日提醒我充值", state.prompt)
        assertFalse(state.isInterpreting)
        assertEquals(0, state.submitCount)
    }

    @Test
    fun explicitSubmitKeepsFailedInterpretationAsManualDraft() {
        val draft = ReminderDraft("draft", "提醒我充值", LocalDate(2026, 9, 2), LocalDate(2026, 9, 2), LocalTime(20, 0))
        val completed = ReminderAiParseState()
            .onPromptChanged("提醒我充值")
            .onExplicitSubmit()
            .onInterpretationFinished(ReminderInterpretation(draft, requiresConfirmation = true, failure = "offline"))

        assertEquals(1, completed.submitCount)
        assertFalse(completed.isInterpreting)
        assertEquals("offline", completed.error)
        assertEquals(draft, completed.draft)
    }
}
