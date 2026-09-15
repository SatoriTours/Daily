package com.dailysatori.service.diagnostics

import com.dailysatori.service.ai.AiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class DiagnosticBusinessFlowTest {
    @Test
    fun httpSuccessFollowedByParsingFailureIsBusinessFailure() = runBlocking<Unit> {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val previous = DiagnosticLog.diagnostics
        DiagnosticLog.diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
        val client = HttpClient(MockEngine { respond("not-json-private", HttpStatusCode.OK) })
        try {
            assertFails {
                AiService(client).chatCompletion(emptyList(), "https://example.com", "token-canary", "gpt-test")
            }
            assertEquals(DiagnosticCode.OPERATION_START, events.first().eventCode)
            assertEquals(DiagnosticCode.OPERATION_FAILED, events.last().eventCode)
            assertEquals(events.first().traceId, events.last().traceId)
        } finally { client.close(); DiagnosticLog.diagnostics = previous }
    }

    @Test
    fun availableTokenUsageIsRecordedWithoutResponseContent() = runBlocking<Unit> {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val previous = DiagnosticLog.diagnostics
        DiagnosticLog.diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
        val client = HttpClient(MockEngine { respond("""{"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":3},"private":"secret-canary"}""") })
        try {
            AiService(client).chatCompletion(emptyList(), "https://example.com", "token-canary", "gpt-test")
            val usage = events.firstOrNull { it.attributes.containsKey("inputTokens") }
            assertNotNull(usage)
            assertEquals("12", usage.attributes["inputTokens"])
            assertEquals("3", usage.attributes["outputTokens"])
        } finally { client.close(); DiagnosticLog.diagnostics = previous }
    }

    @Test
    fun streamedTextIsDeliveredButNeverWrittenToLogs() = runBlocking<Unit> {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val previous = DiagnosticLog.diagnostics
        DiagnosticLog.diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
        val client = HttpClient(MockEngine {
            respond("data: {\"choices\":[{\"delta\":{\"content\":\"private-answer\"}}]}\n\ndata: [DONE]\n")
        })
        try {
            val received = mutableListOf<String>()
            AiService(client).chatCompletionStreaming(emptyList(), "https://example.com", "token-canary", "gpt-test", onChunk = { received.add(it) })
            assertEquals(listOf("private-answer"), received)
            assertEquals(1, events.count { it.eventCode == DiagnosticCode.AI_FIRST_CHUNK })
            assertEquals(DiagnosticCode.OPERATION_END, events.last().eventCode)
            assertFalse(events.any { e -> e.attributes.values.any { it.contains("private") || it.contains("canary") } })
        } finally { client.close(); DiagnosticLog.diagnostics = previous }
    }
}
