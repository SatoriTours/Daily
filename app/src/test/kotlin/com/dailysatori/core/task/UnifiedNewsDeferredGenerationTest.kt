package com.dailysatori.core.task

import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.unifiednews.UnifiedNewsGenerationResult
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * task 1504 follow-up: the fetch task must show collection as its first stage, and a congested AI gate must
 * yield (slow resume, no attempt spent, no success reported) instead of sitting at 0/2 until the task timeout.
 */
class UnifiedNewsDeferredGenerationTest {
    private class RecordingReporter : AsyncTaskProgressReporter {
        val reports = mutableListOf<String>()
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
            reports += current.toString() + "/" + total + ":" + message
        }
    }

    @Test
    fun aDeferredGenerationYieldsWithoutReportingSuccessOrSpendingAnAttempt() = runBlocking {
        val reporter = RecordingReporter()
        val handler = UnifiedNewsGenerateTaskHandler(
            nowMs = { 1_000L },
            latestSuccessfulAt = { null },
            generateDaily = { _, _, _, onStage ->
                onStage?.invoke("collected")
                onStage?.invoke("ai_waiting")
                UnifiedNewsGenerationResult(
                    success = false,
                    status = UnifiedNewsSummaryStatus.FAILED,
                    message = "AI 服务繁忙，排队等待 0 秒仍未获发送许可，请稍后重试",
                    retryable = true,
                    deferred = true,
                )
            },
        )
        val payload = unifiedNewsGenerateTaskPayloadJson(force = true, ignoreSourceTimeFilter = false, mode = "manual")

        val result = handler.execute(1L, payload, "", reporter)

        assertEquals(AsyncTaskExecutionResult.Yield(delayMs = 5 * 60_000L), result, "a deferral yields slowly")
        assertTrue(reporter.reports.any { it.startsWith("0/2") && it.contains("正在收集") })
        assertTrue(reporter.reports.any { it.startsWith("1/2") && it.contains("已收集来源") }, "collection is reported")
        assertTrue(reporter.reports.any { it.contains("AI 服务繁忙") }, "the deferral reason stays visible")
        assertFalse(reporter.reports.any { it.startsWith("2/2") }, "a deferral never reports success")
    }

    @Test
    fun theRealRunnerKeepsADeferredGenerationAtZeroAttemptsAndResumesInFiveMinutes() = runBlocking {
        app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(
            app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY,
        ).use { driver ->
            com.dailysatori.shared.db.DailySatoriDatabase.Schema.create(driver)
            val repository = com.dailysatori.data.repository.AsyncTaskRepository(
                com.dailysatori.shared.db.DailySatoriDatabase(driver),
            )
            val now = 1_000_000L
            val handler = UnifiedNewsGenerateTaskHandler(
                nowMs = { now },
                latestSuccessfulAt = { null },
                generateDaily = { _, _, _, _ ->
                    UnifiedNewsGenerationResult(
                        success = false,
                        status = UnifiedNewsSummaryStatus.FAILED,
                        message = "AI 服务繁忙，稍后自动继续",
                        retryable = true,
                        deferred = true,
                    )
                },
            )
            val runner = com.dailysatori.service.asynctask.AsyncTaskRunner(
                repository = repository,
                registry = com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry(listOf(handler)),
                leaseOwnerProvider = { "defer-test" },
                nowMs = { now },
                executionTimeoutMs = { 60_000L },
            )
            val taskId = repository.enqueue(handler.type, unifiedNewsGenerateTaskPayloadJson(force = true,
                ignoreSourceTimeFilter = false, mode = "manual"), maxAttempts = 3)

            val outcome = runner.run(taskId)

            assertEquals(com.dailysatori.service.asynctask.AsyncTaskRunOutcome.RetryScheduled, outcome)
            val task = repository.getById(taskId)!!
            assertEquals("retrying", task.status)
            assertEquals(0L, task.attempt_count, "a deferral never spends the retry budget")
            assertEquals(now + 5 * 60_000L, task.run_after_ms, "resume after the five minute cadence")
        }
    }

    @Test
    fun aSuccessfulGenerationReportsTwoOfTwoOnlyAtTheEnd() = runBlocking {
        val reporter = RecordingReporter()
        val handler = UnifiedNewsGenerateTaskHandler(
            nowMs = { 1_000L },
            latestSuccessfulAt = { null },
            generateDaily = { _, _, _, onStage ->
                onStage?.invoke("collected")
                onStage?.invoke("ai_waiting")
                onStage?.invoke("ai_received")
                onStage?.invoke("validated")
                onStage?.invoke("persisted")
                UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.SUCCESS, "远程新闻已更新")
            },
        )
        val payload = unifiedNewsGenerateTaskPayloadJson(force = true, ignoreSourceTimeFilter = false, mode = "manual")

        val result = handler.execute(2L, payload, "", reporter)

        assertTrue(result is AsyncTaskExecutionResult.Success)
        assertEquals("2/2:远程新闻已更新", reporter.reports.last(), "only a completed run reports 2/2")
        assertTrue(reporter.reports.count { it.startsWith("1/2") } >= 3, "AI stages share the 1/2 slot")
    }
}
