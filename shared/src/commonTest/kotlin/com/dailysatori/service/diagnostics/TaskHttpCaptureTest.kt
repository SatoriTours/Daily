package com.dailysatori.service.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskHttpCaptureTest {
    private class RecordingSink : TaskHttpSink {
        val records = mutableListOf<Triple<Long, String, TaskHttpRecord>>()
        override fun write(taskId: Long, label: String, record: TaskHttpRecord) {
            records += Triple(taskId, label, record)
        }
    }

    private fun session(sink: RecordingSink, now: () -> Long = { 0L }) =
        TaskHttpCaptureSession(nowMs = now).also { it.bind(sink) }

    @Test
    fun captureIsOffUntilTheUserArmsIt() {
        val sink = RecordingSink()
        val capture = session(sink)

        assertNull(capture.openRequest(7L))
        assertFalse(capture.bodyRecord(7L, TaskHttpRequestHandle("h"), TaskHttpDirection.RESPONSE, "secret body"))
        assertTrue(sink.records.isEmpty())
    }

    @Test
    fun aSessionExpiresAndDisarmStopsAnInFlightRequest() {
        var now = 0L
        val sink = RecordingSink()
        val capture = session(sink) { now }
        capture.arm(7L, "news")
        val handle = assertNotNull(capture.openRequest(7L))

        capture.disarm(7L)
        assertFalse(capture.bodyRecord(7L, handle, TaskHttpDirection.RESPONSE, "late"))

        capture.arm(8L, "news")
        val second = assertNotNull(capture.openRequest(8L))
        now = TaskHttpCaptureSession.DEFAULT_TTL_MS + 1
        assertFalse(capture.isArmed(8L), "30 minute sessions expire")
        assertFalse(capture.bodyRecord(8L, second, TaskHttpDirection.RESPONSE, "late"))
    }

    @Test
    fun concurrentRequestsKeepTheirOwnPayloads() {
        val sink = RecordingSink()
        val capture = session(sink)
        capture.arm(7L, "news")
        val first = assertNotNull(capture.openRequest(7L))
        val second = assertNotNull(capture.openRequest(7L))

        // Interleaved writes: each body must stay bound to the handle that produced it.
        capture.bodyRecord(7L, first, TaskHttpDirection.RESPONSE, """{"articles":["first"]}""")
        capture.bodyRecord(7L, second, TaskHttpDirection.RESPONSE, """{"articles":["second"]}""")
        capture.bodyRecord(7L, first, TaskHttpDirection.RESPONSE, """{"articles":["first-2"]}""")

        assertEquals(listOf(first.requestId, second.requestId, first.requestId), sink.records.map { it.third.requestId })
        assertTrue(sink.records[0].third.body!!.contains("first"))
        assertTrue(sink.records[1].third.body!!.contains("second"))
    }

    @Test
    fun headersUrlsAndNestedJsonAreRedactedBeforeWriting() {
        assertEquals("已脱敏", TaskHttpRedaction.headers(mapOf("Authorization" to "Bearer abc")).getValue("Authorization"))
        assertEquals("application/json", TaskHttpRedaction.headers(mapOf("Content-Type" to "application/json")).getValue("Content-Type"))
        assertEquals("https://api.example.com/v1/news/1?token=已脱敏&page=2",
            TaskHttpRedaction.url("https://user:pass@api.example.com/v1/news/1?token=abc&page=2#frag"))

        val nested = TaskHttpRedaction.redactBody("""{"a":{"token":"t0ken","ok":"v"},"list":[{"secret":"s"}]}""")
        assertFalse(nested.contains("t0ken"))
        assertFalse(nested.contains("\"s\""))
        assertTrue(nested.contains("\"ok\""))
        val masked = TaskHttpRedaction.redactBody("token: abcdefghijklmnop")
        assertFalse(masked.contains("abcdefghijklmnop"))
        assertTrue(masked.contains("已脱敏"))
        val malformed = TaskHttpRedaction.redactBody("""{"token": "abcdefghijklmnop",""")
        assertFalse(malformed.contains("abcdefghijklmnop"), "malformed JSON must not leak its secret")
    }

    @Test
    fun sseEventsAreRedactedWholeAndAnUnfinishedTailIsNeverWritten() {
        val sink = RecordingSink()
        val capture = session(sink)
        capture.arm(9L, "ai")
        val handle = assertNotNull(capture.openRequest(9L))
        val sse = TaskHttpSseCapture(9L, handle) { direction, text, state ->
            capture.sseEvent(9L, handle, direction, text, state)
        }

        sse.accept("data: {\"token\":\"secret-value\"}")
        sse.accept("")
        sse.accept("data: {\"token\":\"split")
        sse.finish()

        val states = sink.records.map { it.third.bodyState }
        assertTrue(states.contains(TASK_HTTP_CAPTURED))
        assertTrue(states.contains(TASK_HTTP_INCOMPLETE), "an unterminated event is omitted, not written")
        assertTrue(sink.records.none { it.third.body?.contains("secret-value") == true })
        assertTrue(sink.records.none { it.third.body?.contains("split") == true })
        assertEquals(handle.requestId, sink.records.first().third.requestId)
    }

    @Test
    fun truncationCutsOnUtf8BoundariesAndReportsOnlyKnownDroppedBytes() {
        val text = "新闻内容" + "x".repeat(10)
        val (kept, state, dropped) = TaskHttpRedaction.truncateUtf8(text, 12)
        assertEquals(TASK_HTTP_TRUNCATED, state)
        assertTrue(kept.startsWith("新闻"))
        assertTrue(kept.endsWith("[截断]"))
        assertNotNull(dropped)

        val (_, wholeState, droppedWhole) = TaskHttpRedaction.truncateUtf8("ok", 10)
        assertEquals(TASK_HTTP_CAPTURED, wholeState)
        assertNull(droppedWhole)
    }

    @Test
    fun aFollowUpExecutionInheritsTheArmOnlyFromAnArmedSession() {
        val sink = RecordingSink()
        val capture = session(sink)
        capture.arm(20L, "news")

        assertTrue(capture.armFollowUp(21L, 20L))
        assertTrue(capture.isArmed(21L))
        assertFalse(capture.armFollowUp(22L, 99L), "an unknown or expired session arms nothing")
        assertFalse(capture.isArmed(22L))
    }
}

