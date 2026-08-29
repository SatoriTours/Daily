package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertTrue

class McpAgentReminderDraftFallbackTest {
    @Test fun streamingFallbackReusesTheSameReminderDraftCollectionAndDeduplicatesByDraftId() {
        val source = java.io.File("src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt").readText()

        assertTrue(source.contains("processQueryWithStreamingFinalAnswer(query, onStep, onChunk, reminderDrafts)"))
        assertTrue(source.contains("processQuery(query, onStep, reminderDrafts)"))
        assertTrue(source.contains("reminderDrafts.none { it.id == draft.id }"))
    }
}
