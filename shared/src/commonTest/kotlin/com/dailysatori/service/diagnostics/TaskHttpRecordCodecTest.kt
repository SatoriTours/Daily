package com.dailysatori.service.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskHttpRecordCodecTest {
    @Test
    fun aRecordRoundTripsThroughTheSharedContract() {
        val record = TaskHttpRecord(
            requestId = "r-1",
            attempt = 3,
            direction = TaskHttpDirection.REQUEST,
            method = "POST",
            url = "https://api.example.com/v1/chat?token=已脱敏&page=2",
            headers = mapOf("Content-Type" to "application/json", "X-Note" to "space and\nnewline"),
            body = "中文 {\"token\":\"已脱敏\"}",
            bodyState = TASK_HTTP_CAPTURED,
            droppedBytes = 12L,
        )
        val line = TaskHttpRecordCodec.encode(record, taskId = 42L, label = "ai")
        assertTrue(!line.contains("\n"), "one record is exactly one line")

        val decoded = assertNotNull(TaskHttpRecordCodec.decode(line))
        assertEquals(record, decoded)
        assertEquals(2, TaskHttpRecordCodec.SCHEMA_VERSION)
        assertTrue(line.contains("\"schema\":2"))
    }

    @Test
    fun emptyTruncatedAndNotCapturedStayDistinct() {
        for (state in listOf(TASK_HTTP_EMPTY, TASK_HTTP_TRUNCATED, TASK_HTTP_NOT_CAPTURED, TASK_HTTP_OMITTED, TASK_HTTP_INCOMPLETE)) {
            val decoded = assertNotNull(TaskHttpRecordCodec.decode(
                TaskHttpRecordCodec.encode(TaskHttpRecord("r", 1, TaskHttpDirection.RESPONSE, bodyState = state)),
            ))
            assertEquals(state, decoded.bodyState)
        }
    }

    @Test
    fun aLegacyLineIsReadableWithoutInventingMissingFields() {
        val legacy = "# http schema=1 task=7 label=news request=req-9 attempt=2 dir=RESPONSE state=CAPTURED " +
            "method=- service=custom-1 route=[dynamic-path-omitted] status=200 durationMs=812 bytes=4 droppedBytes=unknown\n" +
            "  | {\"ok\":true}"
        val decoded = assertNotNull(TaskHttpRecordCodec.decode(legacy))
        assertEquals("req-9", decoded.requestId)
        assertEquals(2, decoded.attempt)
        assertEquals(200, decoded.status)
        assertNull(decoded.url, "version 1 never stored a url, so it must not be guessed")
        assertTrue(decoded.headers.isEmpty())
        assertEquals("{\"ok\":true}", decoded.body)
    }

    @Test
    fun malformedOrForeignLinesAreNotRecords() {
        assertNull(TaskHttpRecordCodec.decode("not a record"))
        assertNull(TaskHttpRecordCodec.decode("{\"requestId\":\"r\"}"))
        assertNull(TaskHttpRecordCodec.decode(""))
        assertEquals(1, TaskHttpRecordCodec.decodeAll(legacyWithGarbage()).size)
    }

    private fun legacyWithGarbage(): String = listOf(
        "garbage",
        TaskHttpRecordCodec.encode(TaskHttpRecord("r2", 1, TaskHttpDirection.SSE_EVENT, bodyState = TASK_HTTP_CAPTURED)),
    ).joinToString("\n")
}

