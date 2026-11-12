package com.dailysatori.core.task

import com.dailysatori.service.diagnostics.TaskHttpCapture
import com.dailysatori.service.diagnostics.TaskHttpRecord
import com.dailysatori.service.diagnostics.TaskHttpSink
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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

