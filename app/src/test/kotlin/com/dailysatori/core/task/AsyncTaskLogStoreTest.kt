package com.dailysatori.core.task

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsyncTaskLogStoreTest {
    @Test
    fun appendsTaskLogsToFilesOutsideDatabaseSchema() {
        val root = createTempDir(prefix = "daily-task-logs")
        val store = AsyncTaskLogStore(root, maxBytesPerTask = 160)

        store.append(taskId = 42, message = "GET /2/users/account/bookmarks")
        store.append(taskId = 42, message = "HTTP 200 {\"meta\":{\"result_count\":95,\"next_token\":\"cursor\"}}")

        val log = store.read(42)

        assertTrue(log.contains("GET /2/users/account/bookmarks"))
        assertTrue(log.contains("result_count"))
        assertTrue(File(root, "task-42.log").exists())
        assertFalse(File(root, "DailySatori.db").exists())
    }

    @Test
    fun capsIndividualTaskLogSize() {
        val root = createTempDir(prefix = "daily-task-logs")
        val store = AsyncTaskLogStore(root, maxBytesPerTask = 64)

        store.append(taskId = 7, message = "a".repeat(80))

        assertEquals(64, store.read(7).encodeToByteArray().size)
    }

    @Test
    fun httpLoggerDoesNotTruncateResponseBodyBeforeTaskLogCap() {
        val root = createTempDir(prefix = "daily-task-logs")
        val store = AsyncTaskLogStore(root, maxBytesPerTask = 120_000)
        val logger = AsyncTaskHttpLogWriter(store)
        val body = """{"data":"${"x".repeat(40_000)}"}"""

        logger.logResponse(
            taskId = 9,
            label = "bookmarks",
            statusCode = 200,
            headers = emptyMap(),
            body = body,
        )

        val log = store.read(9)
        assertTrue(log.contains(body))
        assertFalse(log.contains("truncated"))
        assertFalse(log.contains("TRUNCATED"))
    }

    @Test
    fun defaultTaskLogCapKeepsLargeHttpBodiesForDiagnostics() {
        val root = createTempDir(prefix = "daily-task-logs")
        val store = AsyncTaskLogStore(root)
        val logger = AsyncTaskHttpLogWriter(store)
        val body = """{"data":"${"x".repeat(3 * 1024 * 1024)}","tail":"complete"}"""

        logger.logResponse(
            taskId = 10,
            label = "bookmarks",
            statusCode = 200,
            headers = emptyMap(),
            body = body,
        )

        val log = store.read(10)
        assertTrue(log.contains(""""tail":"complete""""))
        assertTrue(log.contains(body))
        assertFalse(log.contains("TRUNCATED"))
    }
}
