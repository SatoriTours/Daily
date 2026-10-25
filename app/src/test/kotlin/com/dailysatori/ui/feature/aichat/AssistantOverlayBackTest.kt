package com.dailysatori.ui.feature.aichat

import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantOverlayBackTest {
    @Test
    fun nativeDialogBackAlwaysHidesKeyboardBeforeLeavingEitherAssistantPage() {
        assertEquals(AssistantBackAction.HideKeyboard, assistantBackAction(imeVisible = true, sessionsVisible = false))
        assertEquals(AssistantBackAction.HideKeyboard, assistantBackAction(imeVisible = true, sessionsVisible = true))
    }

    @Test
    fun nativeDialogBackReturnsFromSessionsBeforeClosingTheAssistant() {
        assertEquals(AssistantBackAction.ReturnToChat, assistantBackAction(imeVisible = false, sessionsVisible = true))
        assertEquals(AssistantBackAction.Close, assistantBackAction(imeVisible = false, sessionsVisible = false))
    }
}
