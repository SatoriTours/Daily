package com.dailysatori.ui.feature.aichat

import com.dailysatori.service.reminder.ReminderDraft
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderDraftChatStateTest {
    @Test fun cancelledRequestFinalizesItsOwnStreamingMessageWithoutTouchingNewRequest() {
        val cancelled = AiChatState(messages = listOf(ChatMessageUi("old", "assistant", "partial", 1L, isStreaming = true)))
            .cancelledStreamingAssistant("old")
        val newRequest = cancelled.withStreamingAssistantChunk("new", "new partial", now = 2L)

        assertEquals("new", newRequest.messages.last().id)
        assertTrue(newRequest.messages.last().isStreaming)
        assertTrue(newRequest.messages.none { it.id == "old" })
    }
    @Test fun finalizationAttachesDraftOnlyToItsStreamingAssistantMessage() {
        val draft = draft("draft-1")
        val state = AiChatState(messages = listOf(
            ChatMessageUi("old", "assistant", "old answer", 1L),
            ChatMessageUi("current", "assistant", "drafting", 2L, isStreaming = true),
        ), isProcessing = true)

        val finished = state.finishedStreamingAssistant("current", "done", emptyList(), emptyList(), listOf(draft))

        assertTrue(finished.messages.first().reminderDrafts.isEmpty())
        assertEquals(listOf(draft), finished.messages.last().reminderDrafts)
    }

    @Test fun cancelledOrStaleStreamingDoesNotAttachDraftToAnotherMessage() {
        val state = AiChatState(messages = listOf(ChatMessageUi("kept", "assistant", "done", 1L)))

        val stale = state.finishedStreamingAssistant("cancelled", "", emptyList(), emptyList(), listOf(draft("wrong")))

        assertEquals(1, stale.messages.size)
        assertTrue(stale.messages.single().reminderDrafts.isEmpty())
    }

    @Test fun persistedTextHistoryNeverRestoresPendingDrafts() {
        val restored = persistedChatMessage("assistant", "done", 1L)

        assertTrue(restored.reminderDrafts.isEmpty())
    }

    private fun draft(id: String) = ReminderDraft(id, "还信用卡", LocalDate.parse("2026-09-02"), LocalDate.parse("2026-09-02"), LocalTime.parse("18:00"))
}
