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

    @Test
    fun newerPromptInvalidatesAnOlderInterpretationRequest() {
        val first = ReminderAiParseState().onPromptChanged("第一个").onExplicitSubmit()
        val newer = first.onPromptChanged("第二个").onExplicitSubmit()

        assertFalse(newer.acceptsInterpretation(first.requestToken))
        assertTrue(newer.acceptsInterpretation(newer.requestToken))
    }

    @Test
    fun changingThePromptStopsTheInvalidatedInterpretationIndicator() {
        val changed = ReminderAiParseState().onExplicitSubmit().onPromptChanged("新输入")

        assertFalse(changed.isInterpreting)
    }

    @Test
    fun replacementBatchWithTheSameIdInvalidatesOlderSaveResults() {
        val batch = ReminderBatchUiState(batchId = "same-id", items = emptyMap())
        val first = ReminderAiParseState().onExplicitSubmit().onInterpretationSucceeded(batch)
        val replacement = first.onExplicitSubmit().onInterpretationSucceeded(batch)

        assertFalse(replacement.acceptsBatchGeneration(first.batchGeneration))
        assertTrue(replacement.acceptsBatchGeneration(replacement.batchGeneration))
    }
}
