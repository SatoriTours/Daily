package com.dailysatori.core.task

import com.dailysatori.service.diagnostics.TaskHttpCapture
import com.dailysatori.service.diagnostics.TaskHttpRecord
import com.dailysatori.service.diagnostics.TaskHttpSink
import java.io.File
import kotlin.test.Test
import com.dailysatori.service.diagnostics.TASK_HTTP_KIND_BUSINESS_RESULT
import com.dailysatori.service.diagnostics.TASK_HTTP_KIND_HTTP
import com.dailysatori.service.diagnostics.TaskHttpDirection
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncTaskHttpLogWriterCaptureTest {
    private class RecordingSink : TaskHttpSink {
        val records = mutableListOf<TaskHttpRecord>()
        override fun write(taskId: Long, label: String, record: TaskHttpRecord) {
            records += record
        }
    }

    @Test
    fun aFavouriteRequestAndItsResponseShareTheExplicitIdentity() {
        val sink = RecordingSink()
        val root = File.createTempFile("task-http-writer", "dir").also { it.delete(); it.mkdirs() }
        TaskHttpCapture.session.bind(sink)
        try {
            TaskHttpCapture.session.arm(77L, "favorites")
            val writer = AsyncTaskHttpLogWriter(AsyncTaskLogStore(root))

            writer.logRequest(77L, "github_stars", "GET", "https://api.github.com/user/starred?page=1",
                mapOf("token" to "secret-value"), requestId = "req-1", attempt = 1)
            writer.logResponse(77L, "github_stars", 200, mapOf("Content-Type" to "application/json"),
                """{"items":[{"name":"repo"}]}""", requestId = "req-1", attempt = 1)

            val captured = sink.records
            assertTrue(captured.isNotEmpty(), "an armed task records its call")
            assertTrue(captured.all { it.requestId == "req-1" }, "request and response keep one explicit identity")
            assertTrue(captured.none { it.body?.contains("secret-value") == true })
            assertEquals(1, captured.map { it.attempt }.distinct().size)
        } finally {
            TaskHttpCapture.session.disarm(77L)
            TaskHttpCapture.session.bind(null)
            root.deleteRecursively()
        }
    }

    @Test
    fun capturedRequestAndResponseCarryTheirOwnRealEventTimes() {
        val sink = RecordingSink()
        val root = File.createTempFile("task-http-times", "dir").also { it.delete(); it.mkdirs() }
        TaskHttpCapture.session.bind(sink)
        try {
            TaskHttpCapture.session.arm(80L, "favorites")
            val writer = AsyncTaskHttpLogWriter(AsyncTaskLogStore(root))
            writer.logRequest(80L, "github_stars", "GET", "https://api.github.com/user/starred",
                emptyMap(), requestId = "req-times", attempt = 1)
            writer.logResponse(80L, "github_stars", 200, emptyMap(), "body", requestId = "req-times", attempt = 1)

            val requestRecord = sink.records.first { it.direction == TaskHttpDirection.REQUEST }
            val responseRecord = sink.records.first { it.direction == TaskHttpDirection.RESPONSE }
            assertNotNull(requestRecord.requestAtMs)
            assertNull(requestRecord.responseAtMs)
            assertNotNull(responseRecord.responseAtMs)
        } finally {
            TaskHttpCapture.session.disarm(80L)
            TaskHttpCapture.session.bind(null)
            root.deleteRecursively()
        }
    }

    @Test
    fun businessAndHttpRecordsKeepTheirExplicitSourceInTextAndCapture() {
        val sink = RecordingSink()
        val root = File.createTempFile("task-http-kind", "dir").also { it.delete(); it.mkdirs() }
        TaskHttpCapture.session.bind(sink)
        try {
            TaskHttpCapture.session.arm(81L, "favorites")
            val store = AsyncTaskLogStore(root)
            val writer = AsyncTaskHttpLogWriter(store)
            writer.logRequest(81L, "external_favorite_ai", "POST", "https://api.example.com/v1/chat/completions",
                emptyMap(), requestId = "11111111-1111-1111-1111-111111111111", attempt = 1,
                kind = TASK_HTTP_KIND_BUSINESS_RESULT)
            writer.logResponse(81L, "external_favorite_ai", 200, emptyMap(), "{}",
                requestId = "11111111-1111-1111-1111-111111111111", attempt = 1,
                kind = TASK_HTTP_KIND_BUSINESS_RESULT)
            writer.logRequest(81L, "bookmarks", "GET", "https://api.x.com/2/users/me/bookmarks",
                emptyMap(), requestId = "22222222-2222-2222-2222-222222222222", attempt = 1)

            val text = store.read(81L)
            assertTrue(text.contains("kind=business_result"))
            assertTrue(text.contains("kind=http"))
            assertTrue(sink.records.filter { it.requestId.startsWith("1111") }.all {
                it.kind == TASK_HTTP_KIND_BUSINESS_RESULT
            })
            assertTrue(sink.records.filter { it.requestId.startsWith("2222") }.all {
                it.kind == TASK_HTTP_KIND_HTTP
            })
        } finally {
            TaskHttpCapture.session.disarm(81L)
            TaskHttpCapture.session.bind(null)
            root.deleteRecursively()
        }
    }

    @Test
    fun aMissingIdentityIsReportedAsUnpairedInsteadOfGuessed() {
        val sink = RecordingSink()
        val root = File.createTempFile("task-http-writer2", "dir").also { it.delete(); it.mkdirs() }
        TaskHttpCapture.session.bind(sink)
        try {
            TaskHttpCapture.session.arm(78L, "favorites")
            val writer = AsyncTaskHttpLogWriter(AsyncTaskLogStore(root))

            writer.logResponse(78L, "github_stars", 200, emptyMap(), "body", requestId = null, attempt = 1)
            writer.logResponse(78L, "github_stars", 200, emptyMap(), "body2", requestId = null, attempt = 1)

            val ids = sink.records.map { it.requestId }
            assertEquals(2, ids.distinct().size, "each unpaired event keeps its own identity")
            assertTrue(ids.all { it.startsWith("unpaired-") }, "the fallback is still flagged: " + ids)
        } finally {
            TaskHttpCapture.session.disarm(78L)
            TaskHttpCapture.session.bind(null)
            root.deleteRecursively()
        }
    }

    @Test
    fun anUnarmedTaskWritesNothingToTheCaptureLayer() {
        val sink = RecordingSink()
        val root = File.createTempFile("task-http-writer3", "dir").also { it.delete(); it.mkdirs() }
        TaskHttpCapture.session.bind(sink)
        try {
            val writer = AsyncTaskHttpLogWriter(AsyncTaskLogStore(root))
            writer.logRequest(79L, "github_stars", "GET", "https://api.github.com/x", emptyMap(), requestId = "r", attempt = 1)
            writer.logResponse(79L, "github_stars", 200, emptyMap(), "body", requestId = "r", attempt = 1)

            assertTrue(sink.records.isEmpty(), "capture stays off until the user arms the task")
        } finally {
            TaskHttpCapture.session.bind(null)
            root.deleteRecursively()
        }
    }
}

