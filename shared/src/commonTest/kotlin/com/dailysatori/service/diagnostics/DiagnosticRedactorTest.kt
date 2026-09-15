package com.dailysatori.service.diagnostics

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class DiagnosticRedactorTest {
    @Test
    fun credentialsAndPrivateContentNeverEnterSerializedEvents() {
        val recorded = mutableListOf<SafeDiagnosticEvent>()
        val diagnostics = Diagnostics(DiagnosticSink { recorded.add(it) })
        diagnostics.emit(DiagnosticCode.HTTP_HEADERS, DiagnosticSource.HTTP, fields = mapOf(
            "url" to "https://user:password-canary@example.com/path-secret?api_key=query-secret#fragment-secret",
            "Authorization" to "Bearer auth-secret", "Cookie" to "cookie-secret",
            "body" to "private-diary", "message" to "private-chat", "status" to "401",
        ), error = IllegalStateException("exception-secret"))
        val encoded = Json.encodeToString(SafeDiagnosticEvent.serializer(), recorded.single())
        listOf("password-canary", "path-secret", "query-secret", "fragment-secret", "auth-secret",
            "cookie-secret", "private-diary", "private-chat", "exception-secret").forEach {
            assertFalse(encoded.contains(it), it)
        }
        assertEquals("401", recorded.single().attributes["status"])
        assertTrue(encoded.contains("IllegalStateException"))
        assertTrue(encoded.encodeToByteArray().size <= 16 * 1024)
    }

    @Test
    fun arbitraryValuesCannotBeSmuggledThroughAllowedFieldNames() {
        val recorded = mutableListOf<SafeDiagnosticEvent>()
        val diagnostics = Diagnostics(DiagnosticSink { recorded.add(it) })
        diagnostics.emit(DiagnosticCode.LEGACY_LOG, DiagnosticSource.LEGACY, fields = mapOf(
            "status" to "secret-status", "method" to "secret-method", "provider" to "secret-provider",
            "operation" to "secret-operation", "model" to "secret-model", "tag" to "secret-tag",
        ))
        assertFalse(Json.encodeToString(SafeDiagnosticEvent.serializer(), recorded.single()).contains("secret-"))
    }

    @Test
    fun exceptionsAreBoundedAndMessagesAreNeverCaptured() {
        val recorded = mutableListOf<SafeDiagnosticEvent>()
        val diagnostics = Diagnostics(DiagnosticSink { recorded.add(it) })
        var error: Throwable = IllegalArgumentException("private-root")
        repeat(20) { error = IllegalStateException("私".repeat(30_000), error) }
        diagnostics.emit(DiagnosticCode.OPERATION_FAILED, DiagnosticSource.AI, error = error)
        val encoded = Json.encodeToString(SafeDiagnosticEvent.serializer(), recorded.single())
        assertTrue(encoded.encodeToByteArray().size <= 16 * 1024)
        assertFalse(encoded.contains("private-root"))
        assertFalse(encoded.contains("私"))
    }

    @Test
    fun operationsPropagateTraceAndPreserveCancellation() = runBlocking {
        val recorded = mutableListOf<SafeDiagnosticEvent>()
        val diagnostics = Diagnostics(DiagnosticSink { recorded.add(it) })
        val cancellation = CancellationException("private-cancellation")
        try {
            diagnostics.operation(DiagnosticSource.TASK, taskId = 27L) {
                diagnostics.emit(DiagnosticCode.HTTP_START, DiagnosticSource.HTTP)
                throw cancellation
            }
            fail("Cancellation must escape")
        } catch (actual: CancellationException) {
            // Coroutine stack-trace recovery can copy exceptions at withContext boundaries.
            assertEquals("private-cancellation", actual.message)
            assertTrue(actual === cancellation || actual.cause === cancellation)
        }
        assertEquals(3, recorded.size)
        assertEquals(1, recorded.map { it.traceId }.distinct().size)
        assertNotNull(recorded.first().traceId)
        assertEquals(27L, recorded.last().taskId)
        assertEquals(DiagnosticCode.OPERATION_CANCELLED, recorded.last().eventCode)
        assertNull(DiagnosticLog.currentTrace())
    }

    @Test
    fun toolNamesAreAllowListedButArgumentsAndCustomNamesAreNotStored() {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
        diagnostics.emit(DiagnosticCode.OPERATION_START, DiagnosticSource.TOOL,
            fields = mapOf("tool" to "query_local_database", "arguments" to "private-sql"))
        diagnostics.emit(DiagnosticCode.OPERATION_START, DiagnosticSource.TOOL,
            fields = mapOf("tool" to "private-custom-tool"))
        assertEquals("query_local_database", events.first().attributes["tool"])
        assertFalse(Json.encodeToString(events).contains("private-"))
    }

    @Test
    fun loggingFailureCannotChangeBusinessResult() = runBlocking {
        val diagnostics = Diagnostics(DiagnosticSink { throw IllegalStateException("disk failed") })
        assertEquals(42, diagnostics.operation(DiagnosticSource.BACKUP) { 42 })
    }
}
