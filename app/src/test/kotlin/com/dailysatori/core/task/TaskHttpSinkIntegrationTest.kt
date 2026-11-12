package com.dailysatori.core.task

import com.dailysatori.service.diagnostics.TASK_HTTP_CAPTURED
import com.dailysatori.service.diagnostics.TASK_HTTP_EMPTY
import com.dailysatori.service.diagnostics.TASK_HTTP_TRUNCATED
import com.dailysatori.service.diagnostics.TaskHttpDirection
import com.dailysatori.service.diagnostics.TaskHttpRecord
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration contract: real TaskHttpRecord -> real sink -> real file -> decode. The UI reads this file,
 * so a field that is not written here is a broken producer/consumer chain even if a hand-made sink passes.
 */
class TaskHttpSinkIntegrationTest {
    private fun records(read: String) = read.lineSequence().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }
        .toList()

    @Test
    fun everyRecordIsADecodableJsonObjectWithTheFullContract() {
        val root = File.createTempFile("task-http-sink", "dir").also { it.delete(); it.mkdirs() }
        try {
            val store = AsyncTaskLogStore(root)
            val sink = TaskHttpRecordSink(store)

            sink.write(42L, "news", TaskHttpRecord(
                requestId = "req-1",
                attempt = 2,
                direction = TaskHttpDirection.REQUEST,
                method = "POST",
                url = "https://api.example.com/v1/news?token=已脱敏&page=2",
                headers = mapOf("Content-Type" to "application/json", "X-Note" to "value with space and\nnewline"),
                body = "中文 body with {\"token\":\"已脱敏\"}",
                bodyState = TASK_HTTP_CAPTURED,
                droppedBytes = 5L,
            ))
            sink.write(42L, "news", TaskHttpRecord(
                requestId = "req-1", attempt = 2, direction = TaskHttpDirection.RESPONSE,
                status = 200, durationMs = 812, bytes = 43713, bodyState = TASK_HTTP_EMPTY,
            ))
            sink.write(42L, "news", TaskHttpRecord(
                requestId = "req-2", attempt = 3, direction = TaskHttpDirection.SSE_EVENT,
                body = "数据片段", bodyState = TASK_HTTP_TRUNCATED, droppedBytes = null,
            ))

            val parsed = records(store.readHttp(42L))
            assertEquals(3, parsed.size, "each record is its own decodable line")
            val first = parsed[0]
            assertEquals(2, first["schema"]?.jsonPrimitive?.content?.toInt(), "the record carries its schema version")
            assertEquals("https://api.example.com/v1/news?token=已脱敏&page=2", first["url"]?.jsonPrimitive?.content)
            assertEquals("req-1", first["requestId"]?.jsonPrimitive?.content)
            assertEquals("2", first["attempt"]?.jsonPrimitive?.content)
            assertEquals(TASK_HTTP_CAPTURED, first["bodyState"]?.jsonPrimitive?.content)
            assertEquals("中文 body with {\"token\":\"已脱敏\"}", first["body"]?.jsonPrimitive?.content)
            val headers = assertNotNull(first["headers"]).jsonObject
            assertEquals("value with space and\nnewline", headers["X-Note"]?.jsonPrimitive?.content)
            assertEquals("application/json", headers["Content-Type"]?.jsonPrimitive?.content)
            assertEquals(TASK_HTTP_EMPTY, parsed[1]["bodyState"]?.jsonPrimitive?.content)
            assertEquals("200", parsed[1]["status"]?.jsonPrimitive?.content)
            assertEquals(TASK_HTTP_TRUNCATED, parsed[2]["bodyState"]?.jsonPrimitive?.content)
            assertEquals("数据片段", parsed[2]["body"]?.jsonPrimitive?.content)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun capacityTrimmingKeepsWholeRecordsReadable() {
        val root = File.createTempFile("task-http-sink-cap", "dir").also { it.delete(); it.mkdirs() }
        try {
            val store = AsyncTaskLogStore(root, maxHttpBytesPerTask = 512)
            val sink = TaskHttpRecordSink(store)
            repeat(20) { index ->
                sink.write(7L, "news", TaskHttpRecord(
                    requestId = "r" + index, attempt = index + 1, direction = TaskHttpDirection.RESPONSE,
                    body = "line " + index, bodyState = TASK_HTTP_CAPTURED,
                ))
            }
            val raw = store.readHttp(7L)
            assertTrue(raw.toByteArray().size <= 512, "the file keeps its cap")
            val parsed = records(raw)
            assertTrue(parsed.isNotEmpty(), "the trimmed file still has readable records")
            assertTrue(parsed.all { it["requestId"] != null }, "no half record is left behind: " + raw.take(80))
            assertTrue(parsed.last()["requestId"]?.jsonPrimitive?.content == "r19", "the newest record survives")
        } finally {
            root.deleteRecursively()
        }
    }
}

