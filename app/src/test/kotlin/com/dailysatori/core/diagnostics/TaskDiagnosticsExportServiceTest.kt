package com.dailysatori.core.diagnostics

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TaskDiagnosticsExportServiceTest {
    @Test
    fun aTaskExportCarriesOnlyItsOwnSanitizedLogAndCapture() {
        val root = File.createTempFile("task-export", "dir").also { it.delete(); it.mkdirs() }
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val tasks = AsyncTaskRepository(db)
            val store = AsyncTaskLogStore(root)
            val taskId = tasks.enqueue("remote_news_fetch", """{"mode":"due"}""", uniqueKey = "remote_news_fetch:due:1:news")
            val other = tasks.enqueue("remote_news_fetch", """{"mode":"due"}""", uniqueKey = "remote_news_fetch:due:2:news")
            store.append(taskId, "TASK started type=remote_news_fetch")
            store.appendHttp(taskId, "# http schema=1 task=" + taskId + " state=CAPTURED\n  | {example}")
            store.appendHttp(other, "# http schema=1 task=" + other + " secret-other-task")

            val output = ByteArrayOutputStream()
            assertTrue(TaskDiagnosticsExportService(tasks, store, clock = { 0L }).write(taskId, output))
            val text = output.toString("UTF-8")

            assertTrue(text.contains("task=" + taskId + " type=remote_news_fetch"))
            assertTrue(text.contains("TASK started type=remote_news_fetch"))
            assertTrue(text.contains("# http schema=1 task=" + taskId))
            assertTrue(text.contains("explicitly exported by the user"))
            assertTrue(!text.contains("secret-other-task"), "another task's capture never leaks into this export")
            assertTrue(!text.contains("payload_json"), "raw task payload is not part of the export")

            val noCapture = ByteArrayOutputStream()
            assertTrue(TaskDiagnosticsExportService(tasks, store, clock = { 0L }).write(other, noCapture) == true)
            assertTrue(TaskDiagnosticsExportService(tasks, store, clock = { 0L }).write(999_999L, ByteArrayOutputStream()) == false,
                "an unknown task is reported instead of producing an empty file")
        }
        root.deleteRecursively()
    }
}
