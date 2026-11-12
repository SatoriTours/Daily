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
