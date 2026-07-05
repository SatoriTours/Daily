package com.dailysatori.core.task

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

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
    fun httpLoggerTruncatesLargeResponseBodyBeforeTaskLogCap() {
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
        assertFalse(log.contains(body))
        assertTrue(log.contains("...[truncated "))
        assertTrue(log.length < 8_000)
    }

    @Test
    fun defaultTaskLogCapKeepsLargeHttpBodySummaryForDiagnostics() {
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
        assertFalse(log.contains(""""tail":"complete""""))
        assertFalse(log.contains(body))
        assertTrue(log.contains("...[truncated "))
        assertTrue(log.length < 8_000)
    }

    @Test
    fun concurrentAppendsDoNotCorruptTaskLog() = runBlocking {
        val root = createTempDir(prefix = "daily-task-logs")
        val store = AsyncTaskLogStore(root, maxBytesPerTask = 120_000)

        (0 until 20).map { index ->
            async {
                repeat(10) { step ->
                    store.append(12, "entry-$index-$step")
                }
            }
        }.forEach { it.await() }

        val log = store.read(12)
        assertFalse(log.contains('\u0000'))
        assertEquals(200, log.lineSequence().filter { it.contains("entry-") }.count())
    }

    @Test
    fun deleteRemovesTaskLogFilesById() {
        val root = createTempDir(prefix = "daily-task-logs")
        val store = AsyncTaskLogStore(root)
        store.append(1, "keep")
        store.append(2, "delete")

        store.delete(listOf(2, 3))

        assertTrue(File(root, "task-1.log").exists())
        assertFalse(File(root, "task-2.log").exists())
    }

    @Test
    fun observeEmitsWhenTaskLogFileChanges() = runBlocking {
        val root = createTempDir(prefix = "daily-task-logs")
        val store = AsyncTaskLogStore(root)

        val observed = async {
            withTimeout(1_000) {
                store.observe(11, pollIntervalMs = 10).first { it.contains("TASK started") }
            }
        }
        delay(30)
        store.append(11, "TASK started type=fake")

        assertTrue(observed.await().contains("TASK started type=fake"))
    }

    @Test
    fun observeReadsTaskLogOffMainThread() {
        val source = File("src/main/kotlin/com/dailysatori/core/task/AsyncTaskLogStore.kt").readText()

        assertTrue(source.contains("Dispatchers.IO"))
        assertTrue(source.contains(".flowOn(Dispatchers.IO)"))
    }
}
