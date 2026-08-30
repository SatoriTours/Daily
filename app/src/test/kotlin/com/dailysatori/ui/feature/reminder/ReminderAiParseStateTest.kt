package com.dailysatori.ui.feature.reminder

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
}
