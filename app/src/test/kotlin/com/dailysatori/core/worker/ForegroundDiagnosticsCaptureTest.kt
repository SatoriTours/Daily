package com.dailysatori.core.worker

import com.dailysatori.core.diagnostics.DiagnosticExporter
import com.dailysatori.core.diagnostics.DiagnosticStore
import com.dailysatori.service.asynctask.AsyncTaskRunOutcome
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.Diagnostics
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** 真实 capture→存储→导出：同一任务的前台三事件必须带同一 taskId，且只保留白名单值。 */
class ForegroundDiagnosticsCaptureTest {
    private var previous: Diagnostics? = null

    @BeforeTest
    fun isolateGlobalDiagnostics() {
        previous = DiagnosticLog.diagnostics
    }

    @AfterTest
    fun restoreGlobalDiagnostics() {
        previous?.let { DiagnosticLog.diagnostics = it }
    }

    @Test
    fun requestEventsShareTheSameTaskIdAndStayWhitelisted() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diag-foreground-capture").toFile()
        var time = 2_000_000L
        DiagnosticStore(root, clock = { time }).use { store ->
            DiagnosticLog.diagnostics = Diagnostics(store, now = { time })
            ForegroundDiagnostics.workerStarted(ForegroundDiagnostics.ASYNC_TASK, 42L, "save_article", 2)
            ForegroundDiagnostics.requestStarted(
                ForegroundDiagnostics.ASYNC_TASK, 2000, 2, 42L, "save_article", 2,
            )
            ForegroundDiagnostics.requestSucceeded(ForegroundDiagnostics.ASYNC_TASK, 2000, 42L, "save_article")
            ForegroundDiagnostics.requestFailed(
                ForegroundDiagnostics.ASYNC_TASK, 2000, 42L, "save_article", IllegalStateException("token=secret-canary"),
            )
            ForegroundDiagnostics.requestStarted(
                ForegroundDiagnostics.ASYNC_TASK, 2001, 2, 43L, "unknown_type_from_future", 1,
            )
            ForegroundDiagnostics.workerFinished(
                ForegroundDiagnostics.ASYNC_TASK,
                AsyncTaskRunOutcome.Skipped.foregroundOutcomeName(), 42L, "save_article",
            )

            store.snapshot(time).use { snapshot ->
                val events = snapshot.events().toList()
                val requestEvents = events.filter {
                    it.attributes["foregroundStage"] in setOf("request", "request_ok", "request_failed")
                }
                assertEquals(4, requestEvents.size)
                val correlated = requestEvents.filter { it.attributes["taskId"] == "42" }
                assertEquals(3, correlated.size, "同一任务的三条前台事件都必须带 taskId")
                correlated.forEach {
                    assertEquals("2000", it.attributes["notificationId"])
                    assertEquals("save_article", it.attributes["foregroundTaskType"])
                }
                val finished = events.single { it.attributes["workerOutcome"] == "skipped" }
                assertEquals(
                    "save_article", finished.attributes["foregroundTaskType"],
                    "终态事件必须与请求事件同任务类型（设备实测曾退化为 unknown）",
                )
                assertEquals("42", finished.attributes["taskId"])
                assertTrue(events.any { it.attributes["foregroundTaskType"] == "unknown" })
                assertEquals(
                    "illegal_state",
                    requestEvents.single { it.attributes["foregroundStage"] == "request_failed" }
                        .attributes["foregroundStartFailure"],
                )

                val output = java.io.ByteArrayOutputStream()
                DiagnosticExporter("5.1.66", 175117, 37).write(snapshot, output)
                assertFalse(output.toString("UTF-8").contains("secret-canary"))
            }
        }
        root.deleteRecursively()
    }

    @Test
    fun taskOutcomeMappingNeverPretendsCompletion() {
        assertEquals("completed", AsyncTaskRunOutcome.Succeeded.foregroundOutcomeName())
        assertEquals("skipped", AsyncTaskRunOutcome.Skipped.foregroundOutcomeName())
        assertEquals("failed", AsyncTaskRunOutcome.Failed.foregroundOutcomeName())
        assertEquals("retry_scheduled", AsyncTaskRunOutcome.RetryScheduled.foregroundOutcomeName())
        assertEquals("retry_scheduled", AsyncTaskRunOutcome.Blocked("serial_owner", 7L).foregroundOutcomeName())
    }
}
