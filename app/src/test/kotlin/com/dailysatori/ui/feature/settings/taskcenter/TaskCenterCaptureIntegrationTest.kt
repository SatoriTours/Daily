package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.core.task.TaskHttpRecordSink
import com.dailysatori.service.diagnostics.TASK_HTTP_CAPTURED
import com.dailysatori.service.diagnostics.TASK_HTTP_EMPTY
import com.dailysatori.service.diagnostics.TASK_HTTP_INCOMPLETE
import com.dailysatori.service.diagnostics.TASK_HTTP_NOT_CAPTURED
import com.dailysatori.service.diagnostics.TaskHttpDirection
import com.dailysatori.service.diagnostics.TaskHttpRecord
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real sink -> store.readHttp -> shared codec -> UI projection; no handcrafted log syntax. */
class TaskCenterCaptureIntegrationTest {
    private fun tempStore(): AsyncTaskLogStore =
        AsyncTaskLogStore(Files.createTempDirectory("task-http").toFile())

    private fun writeSample(store: AsyncTaskLogStore, taskId: Long = 990001L) {
        val sink = TaskHttpRecordSink(store)
        sink.write(
            taskId,
            "ai chat",
            TaskHttpRecord(
                requestId = "req-1",
                attempt = 1,
                direction = TaskHttpDirection.REQUEST,
                method = "POST",
                url = "https://api.example.com/v1/chat/completions?stream=true",
                headers = mapOf("X-Trace" to "a b", "Accept" to "text/event-stream"),
                body = """{"model":"m","stream":true}""",
                bodyState = TASK_HTTP_CAPTURED,
            ),
        )
        sink.write(
            taskId,
            "ai chat",
            TaskHttpRecord(
                requestId = "req-1",
                attempt = 1,
                direction = TaskHttpDirection.RESPONSE,
                status = 200,
                durationMs = 42L,
                headers = mapOf("content-type" to "text/event-stream; charset=utf-8"),
                body = null,
                bodyState = TASK_HTTP_EMPTY,
            ),
        )
        sink.write(
            taskId,
            "ai chat",
            TaskHttpRecord(
                requestId = "req-2",
                attempt = 1,
                direction = TaskHttpDirection.REQUEST,
                method = "GET",
                url = "https://api.example.com/v2/items",
                body = null,
                bodyState = TASK_HTTP_NOT_CAPTURED,
            ),
        )
        sink.write(
            taskId,
            "ai chat",
            TaskHttpRecord(
                requestId = "req-2",
                attempt = 1,
                direction = TaskHttpDirection.SSE_EVENT,
                body = "data: {",
                bodyState = TASK_HTTP_INCOMPLETE,
            ),
        )
    }

    @Test
    fun realSinkRecordsReachTheUiWithUrlHeadersAndStates() {
        val store = tempStore()
        writeSample(store)
        val httpLog = store.readHttp(990001L)
        assertTrue(httpLog.isNotBlank(), "real sink must write the capture file")
        assertTrue(httpLog.contains("\"schema\":2"), "records must be the frozen schema2 JSONL")

        val read = taskCenterCaptureRead(httpLog)
        assertEquals(0, read.unreadableLines)
        assertEquals(2, read.requests.size)

        val first = read.requests[0]
        assertEquals("req-1", first.requestId)
        assertEquals("POST", first.method)
        assertEquals("https://api.example.com/v1/chat/completions?stream=true", first.url)
        assertTrue(first.requestHeaders.contains("X-Trace" to "a b"))
        assertTrue(first.requestBody.collected)
        assertTrue(first.requestBody.text.contains("\"model\":\"m\""))
        assertEquals("200", first.status)
        assertTrue(first.responseBody.emptyByDesign)
        assertEquals(42L, first.durationMs)

        val second = read.requests[1]
        assertEquals("GET", second.method)
        assertFalse(second.requestBody.collected)
        assertEquals(1, second.sseEvents.size)
        assertTrue(second.sseEvents.single().startsWith("data:"))

        val details = taskCenterCapturedRequestDetails(read.requests)
        assertEquals("stream=true", details[0].queryRows.single().let { "${it.first}=${it.second}" })
        assertEquals("42ms", details[0].durationLabel(testStrings()))
        assertTrue(details[1].responseIncomplete)
        assertTrue(details[0].responseBody.emptyByDesign)
    }

    @Test
    fun codecToProjectionKeepsLabelsHonestAndTitlesOffOpaqueIds() {
        val store = tempStore()
        val sink = TaskHttpRecordSink(store)
        sink.write(
            990003L,
            "",
            TaskHttpRecord(
                requestId = "req-real-1",
                attempt = 2,
                direction = TaskHttpDirection.REQUEST,
                method = "POST",
                url = "https://api.example.com/v1/chat/completions?token=secret",
                headers = mapOf("Authorization" to "Bearer secret"),
                body = """{"model":"m"}""",
                bodyState = TASK_HTTP_CAPTURED,
                requestAtMs = 1_000L,
            ),
        )
        sink.write(
            990003L,
            "",
            TaskHttpRecord(
                requestId = "req-real-1",
                attempt = 2,
                direction = TaskHttpDirection.RESPONSE,
                status = 200,
                body = """{"ok":true}""",
                bodyState = TASK_HTTP_CAPTURED,
                responseAtMs = 1_500L,
            ),
        )

        val details = taskCenterCapturedRequestDetails(store.readHttp(990003L))
        val detail = details.single()
        assertEquals("", detail.label, "the capture layer has no business label; projection never fakes one")
        assertEquals("req-real-1", detail.requestId)
        assertTrue(detail.attemptKnown)
        assertEquals(2, detail.attemptNumber)
        assertEquals("POST https://api.example.com/v1/chat/completions", taskCenterRequestTitle(detail, "request"))
        assertFalse(taskCenterRequestTitle(detail, "request").contains("req-real-1"))
        assertEquals(1_000L, detail.requestedAtMs)
        assertEquals(1_500L, detail.respondedAtMs)
        assertEquals("500ms", detail.durationLabel(testStrings()))
        assertEquals("200", detail.responseStatus)
        assertFalse(detail.associationUnknown)
    }

    @Test
    fun legacyCaptureWithoutMethodFallsBackWithoutInventingGet() {
        val legacy = "# http schema=1 task=1 label=ai request=r1 attempt=1 dir=REQUEST state=CAPTURED method=- status=- durationMs=- bytes=1 droppedBytes=unknown"
        val details = taskCenterCapturedRequestDetails(legacy)
        val detail = details.single()
        assertEquals("", detail.method)
        assertEquals("request", taskCenterRequestTitle(detail, "request"))
        assertFalse(taskCenterRequestTitle(detail, "request").contains("GET"))
        assertFalse(taskCenterRequestTitle(detail, "request").contains("r1"))
        assertTrue(detail.associationUnknown)
        assertNull(detail.respondedAtMs)
        assertNull(detail.durationMs)
    }

    @Test
    fun unreadableLinesAreCountedAndSkippedInsteadOfGuessed() {
        val store = tempStore()
        writeSample(store, taskId = 990002L)
        store.appendHttp(990002L, "not-a-record")
        val read = taskCenterCaptureRead(store.readHttp(990002L))
        assertEquals(2, read.requests.size)
        assertEquals(1, read.unreadableLines)
    }

    @Test
    fun legacySchemaOneRecordKeepsMissingUrlExplicit() {
        val legacy = "# http schema=1 task=1 label=ai request=r1 attempt=1 dir=REQUEST state=CAPTURED method=GET status=- durationMs=- bytes=1 droppedBytes=unknown"
        val read = taskCenterCaptureRead(legacy)
        assertEquals(1, read.requests.size)
        assertTrue(read.requests.single().url.isEmpty())
        val details = taskCenterCapturedRequestDetails(read.requests)
        assertTrue(details.single().fullUrl.isEmpty())
    }
}
