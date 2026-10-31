package com.dailysatori.service.diagnostics

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class DiagnosticRedactorTest {
    @Test
    fun startupTimingsKeepStageOnCompletionWithoutAllowingArbitraryText() = runBlocking {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
        diagnostics.operation(DiagnosticSource.APP, fields = mapOf("startupStage" to "database_prepare")) { }
        assertEquals(listOf("database_prepare", "database_prepare"), events.map { it.attributes["startupStage"] })
        assertNotNull(events.last().attributes["durationMs"])
        assertTrue(DiagnosticRedactor.fields(mapOf("startupStage" to "private-path-canary")).isEmpty())
    }

    @Test
    fun hotNewsDiagnosticsKeepOnlyPublicIdentifiersAndBoundedMetadata() {
        val fields = DiagnosticRedactor.fields(mapOf(
            "provider" to "deepseek", "model" to "deepseek-v4-flash",
            "url" to "https://user:password-canary@api.deepseek.com/v1/chat/completions?key=query-canary#fragment-canary",
            "aiPurpose" to "external_content", "hotNewsPlatform" to "github",
            "hotNewsStage" to "translation_request", "retryState" to "accepted",
            "hotNewsFailure" to "network", "operation" to "hot_news_retry",
            "attempt" to "2", "count" to "5", "translatedCount" to "3", "pendingCount" to "2",
            "cacheUsed" to "true", "requestBody" to "private-request", "responseBody" to "private-response",
        ))
        assertEquals(mapOf(
            "provider" to "deepseek", "model" to "deepseek-v4-flash", "service" to "api.deepseek.com",
            "route" to "/chat/completions", "aiPurpose" to "external_content", "hotNewsPlatform" to "github",
            "hotNewsStage" to "translation_request", "retryState" to "accepted",
            "hotNewsFailure" to "network", "operation" to "hot_news_retry",
            "attempt" to "2", "count" to "5", "translatedCount" to "3", "pendingCount" to "2", "cacheUsed" to "true",
        ), fields)
    }

    @Test
    fun hotNewsMetadataCannotExposeCustomAddressesModelsOrArbitraryStageText() {
        val events = mutableListOf<SafeDiagnosticEvent>()
        Diagnostics(DiagnosticSink { events.add(it) }).emit(DiagnosticCode.OPERATION_FAILED, DiagnosticSource.AI,
            fields = mapOf(
                "url" to "https://user:password-canary@private-host-canary.invalid/private-path-canary?key=query-canary",
                "model" to "private-model-canary", "provider" to "private-provider-canary",
                "aiPurpose" to "private-purpose-canary", "hotNewsPlatform" to "private-feed-canary",
                "hotNewsStage" to "private-stage-canary", "retryState" to "private-state-canary",
                "hotNewsFailure" to "private-error-canary", "operation" to "private-operation-canary",
                "translatedCount" to "private-count-canary", "pendingCount" to "-1", "cacheUsed" to "private-flag-canary",
                "headers" to "authorization-canary", "prompt" to "private-prompt-canary",
                "requestBody" to "private-request-canary", "responseBody" to "private-response-canary",
            ), error = IllegalStateException("private-exception-canary"))
        val event = events.single()
        val encoded = Json.encodeToString(SafeDiagnosticEvent.serializer(), event)
        assertFalse(encoded.contains("canary"))
        assertTrue(event.attributes.getValue("model").startsWith("custom-"))
        assertTrue(event.attributes.getValue("service").startsWith("custom-"))
        assertEquals(setOf("model", "service", "route"), event.attributes.keys)
    }

    @Test
    fun backupAttachmentEvidenceRecordsOnlyKnownSourcesAndCounts() {
        assertEquals(mapOf("backupAttachmentSource" to "diary.images", "count" to "2"),
            DiagnosticRedactor.fields(mapOf("backupAttachmentSource" to "diary.images", "count" to "2",
                "path" to "/private/photo.jpg", "recordId" to "23")))
        assertTrue(DiagnosticRedactor.fields(mapOf("backupAttachmentSource" to "/private/photo.jpg")).isEmpty())
    }

    @Test
    fun reminderValidationRecordsOnlyAllowListedFieldNames() {
        assertEquals(mapOf("reminderField" to "first_reminder_time"),
            DiagnosticRedactor.fields(mapOf("reminderField" to "first_reminder_time", "response" to "private-body")))
        assertTrue(DiagnosticRedactor.fields(mapOf("reminderField" to "private-body")).isEmpty())
    }

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
