package com.dailysatori.core.diagnostics

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.core.task.UnifiedNewsGenerateTaskHandler
import com.dailysatori.core.task.unifiedNewsGenerateTaskPayloadJson
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskRunner
import com.dailysatori.service.unifiednews.UnifiedNewsGenerationResult
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryStatus
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The user's actual export entry is the contract here: only the task file is exported, HTTP capture stays
 * off, and it must still explain the last real stage, the plan and the reason without leaking content.
 */
class TaskDiagnosticsExportContentTest {
    private class Harness(
        val tasks: AsyncTaskRepository,
        val store: AsyncTaskLogStore,
        val runner: Any,
    )

    private fun withExport(
        generation: suspend (suspend (String) -> Unit) -> UnifiedNewsGenerationResult,
        maxBytes: Int = 64 * 1024,
        block: (text: String, log: String) -> Unit,
    ) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val tasks = AsyncTaskRepository(DailySatoriDatabase(driver))
            val root = Files.createTempDirectory("task-export").toFile()
            val store = AsyncTaskLogStore(root, maxBytesPerTask = maxBytes)
            val handler = UnifiedNewsGenerateTaskHandler(
                nowMs = { 1_000_000L },
                latestSuccessfulAt = { null },
                generateDaily = { _, _, _, onStage -> generation(onStage ?: {}) },
            )
            val runner = AsyncTaskRunner(
                repository = tasks,
                registry = AsyncTaskHandlerRegistry(listOf(handler)),
                logger = store,
                leaseOwnerProvider = { "export-test" },
                nowMs = { 1_000_000L },
                executionTimeoutMs = { 60_000L },
            )
            val taskId = tasks.enqueue(handler.type, unifiedNewsGenerateTaskPayloadJson(
                force = true, ignoreSourceTimeFilter = false, mode = "manual"), maxAttempts = 3)
            runner.run(taskId)

            val out = ByteArrayOutputStream()
            TaskDiagnosticsExportService(tasks, store, clock = { 1_000_500L },
                appVersionName = "5.1.66", appVersionCode = 175119).write(taskId, out)
            val text = out.toString(Charsets.UTF_8.name())
            block(text, store.read(taskId))
            root.deleteRecursively()
        }
    }

    @Test
    fun theTaskExportExplainsStagesVersionAndPlanWithCaptureDisabled() {
        withExport({ onStage ->
            listOf("collected", "ai_queued", "ai_permit", "ai_requesting", "ai_received",
                "validated", "persisted").forEach { onStage(it) }
            UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.SUCCESS, "远程新闻已更新")
        }) { text, _ ->
            assertTrue(text.contains("version=5.1.66 versionCode=175119"), "exporting build is named: " + text)
            assertFalse(text.contains("per-event source"), "no unimplemented per-event source is claimed")
            assertTrue(text.contains("exportedAtMs=1000500"), "export time is machine readable")
            assertTrue(text.contains("progress=2/2"), "the final progress unit is visible")
            listOf("等待 AI 发送许可", "已获得 AI 发送许可", "AI 请求进行中", "AI 已返回", "已收集来源")
                .forEach { assertTrue(text.contains(it), "missing stage: " + it) }
            assertTrue(text.contains("TASK succeeded"), "the terminal outcome stays visible")
            assertTrue(text.contains("Task HTTP capture: [未采集]"), "capture stays off by default")
            assertTrue(text.contains("\"runElapsedMs\":"), "each stage carries a monotonic run elapsed")
        }
    }

    @Test
    fun aRawFailureMessageNeverReachesTheExport() {
        withExport({
            throw IllegalStateException("boom https://api.test/v1?token=sk-secret#frag article body 正文不应出现")
        }) { text, _ ->
            assertTrue(text.contains("code=generation_error"), "a safe classification is recorded: " + text)
            assertTrue(text.contains("新闻汇总生成失败"), "a fixed safe message is recorded")
            listOf("sk-secret", "boom", "正文不应出现", "token=").forEach {
                assertFalse(text.contains(it), "leaked into the export: " + it)
            }
            assertTrue(text.contains("TASK retry"), "the retry reason is visible")
        }
    }

    @Test
    fun aSanitizedSampleCanBeWrittenForReview() {
        val target = System.getenv("DAILY_EXPORT_SAMPLE")?.takeIf { it.isNotBlank() } ?: return
        withExport({ onStage ->
            listOf("collected", "ai_queued", "ai_permit", "ai_requesting", "ai_received",
                "validated", "persisted").forEach { onStage(it) }
            UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.SUCCESS, "远程新闻已更新")
        }) { text, _ ->
            java.io.File(target).writeText(text)
            assertTrue(java.io.File(target).readText().contains("version="))
        }
    }

    @Test
    fun capacityTrimmingIsAnnouncedAndTheTailStaysReadable() {
        val root = Files.createTempDirectory("task-trim").toFile()
        try {
            val store = AsyncTaskLogStore(root, maxBytesPerTask = 220)
            repeat(40) { store.append(7L, "TASK stage line " + it) }
            val text = store.read(7L)
            assertTrue(text.startsWith("[已裁剪较早日志]"), "trimming is announced: " + text.take(40))
            assertTrue(text.contains("TASK stage line 39"), "the newest lines stay readable")
            assertTrue(text.toByteArray().size <= 220, "the marker stays inside the budget: " + text.toByteArray().size)
            assertFalse(text.contains('\uFFFD'), "a cut never leaves a broken character behind")
        } finally {
            root.deleteRecursively()
        }
    }
}
