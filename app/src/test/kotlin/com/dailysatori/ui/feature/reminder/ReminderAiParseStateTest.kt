package com.dailysatori.ui.feature.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderAiParseStateTest {
    @Test
    fun typingDoesNotInterpretUntilExplicitSubmit() {
        val state = ReminderAiParseState().onPromptChanged("每年9月2日提醒我充值")

        assertEquals("每年9月2日提醒我充值", state.prompt)
        assertFalse(state.isInterpreting)
        assertEquals(0, state.submitCount)
    }

    @Test
    fun explicitSubmitKeepsInterpretationErrorWithoutAParallelSingleDraft() {
        val completed = ReminderAiParseState()
            .onPromptChanged("提醒我充值")
            .onExplicitSubmit()
            .onInterpretationFinished("offline")

        assertEquals(1, completed.submitCount)
        assertFalse(completed.isInterpreting)
        assertEquals("offline", completed.error)
    }

    @Test
    fun openingAnotherEditorClearsPreviousPromptAndError() {
        val previous = ReminderAiParseState(prompt = "旧输入", error = "offline")

        assertEquals(ReminderAiParseState(), previous.resetForEditor())
    }
}
