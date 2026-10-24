package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.*
import kotlin.test.*

class AsyncTaskLeaseFencingTest {
    private suspend fun fixture(block: suspend (AssistantTurnRepository, AsyncTaskRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(AssistantTurnRepository(driver), AsyncTaskRepository(DailySatoriDatabase(driver)))
        }
    }

    private fun takeOver(tasks: AsyncTaskRepository, id: Long) {
        tasks.markRunningForRetryAfterProcessRestart(1L)
        check(tasks.claimForRun(id, "new-worker", Long.MAX_VALUE))
    }

    @Test fun delayedHandlerCannotBorrowNewWorkersIdentity() = runBlocking {
        fixture { turns, tasks ->
            val turn = turns.accept("s", "client", "原文", 1L, "UTC")
            val handler = AssistantTurnTaskHandler(turns, tasks) { "旧执行端不应生成此回答" }
            val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)),
                logger = object : AsyncTaskLogger {
                    override fun append(taskId: Long, message: String) {
                        if (message.startsWith("TASK started")) takeOver(tasks, taskId)
                    }
                }, leaseOwnerProvider = { "old-worker" })
            assertEquals(AsyncTaskRunOutcome.Skipped, runner.run(turn.taskId))
            assertNull(turns.get(turn.id)!!.responseMessageId)
            assertEquals("running", tasks.getById(turn.taskId)!!.status)
            assertEquals("new-worker", tasks.getById(turn.taskId)!!.lease_owner)
        }
    }

    @Test fun lateAssistantReplyCannotFailNewLeaseAndNewWorkerCanFinish() = runBlocking {
        fixture { turns, tasks ->
            val turn = turns.accept("s", "client", "原文", 1L, "UTC")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val handler = AssistantTurnTaskHandler(turns, tasks) { entered.complete(Unit); release.await(); "过期回答" }
            val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)), leaseOwnerProvider = { "old-worker" })
            val old = async { runner.run(turn.taskId) }
            entered.await()
            takeOver(tasks, turn.taskId)
            release.complete(Unit)
            assertEquals(AsyncTaskRunOutcome.Skipped, old.await())
            assertNull(turns.get(turn.id)!!.responseMessageId)
            assertEquals("running", tasks.getById(turn.taskId)!!.status)
            assertEquals("new-worker", tasks.getById(turn.taskId)!!.lease_owner)
            val active = AssistantTurnTaskHandler(turns, tasks) { "新执行端的有效回答" }
            // Hand the still-owned task back to the ordinary runner, not a new assistant queue.
            tasks.markRunningForRetryAfterProcessRestart(2L)
            assertEquals(AsyncTaskRunOutcome.Succeeded, AsyncTaskRunner(tasks,
                AsyncTaskHandlerRegistry(listOf(active)), leaseOwnerProvider = { "current-worker" }).run(turn.taskId))
            assertNotNull(turns.get(turn.id)!!.responseMessageId)
        }
    }

    @Test fun staleResultsExceptionsProgressAndTimeoutNeverMutateNewLease() = runBlocking {
        for (mode in listOf("success", "failure", "retry", "exception", "cancel", "progress", "timeout")) {
            fixture { _, tasks ->
                val handler = object : AsyncTaskHandler {
                    override val type = "fencing-probe"
                    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
                        reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
                        takeOver(tasks, taskId)
                        return when (mode) {
                            "success" -> AsyncTaskExecutionResult.Success("stale-result")
                            "failure" -> AsyncTaskExecutionResult.PermanentFailure("stale", "stale")
                            "retry" -> AsyncTaskExecutionResult.RetryableFailure("stale", "stale")
                            "exception" -> error("stale")
                            "cancel" -> throw CancellationException("stale")
                            "progress" -> { reporter.report(99, 100, "stale-progress"); AsyncTaskExecutionResult.Success() }
                            else -> awaitCancellation()
                        }
                    }
                    override suspend fun onExecutionTimeout(taskId: Long, payloadJson: String, checkpointJson: String,
                        reporter: AsyncTaskProgressReporter) = AsyncTaskExecutionResult.PermanentFailure("stale-timeout", "stale")
                }
                val id = tasks.enqueue(handler.type, "{}")
                val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)),
                    leaseOwnerProvider = { "old-worker" }, executionTimeoutMs = { if (mode == "timeout") 20L else 60_000L })
                if (mode == "cancel") assertFailsWith<CancellationException> { runner.run(id) }
                else assertEquals(AsyncTaskRunOutcome.Skipped, runner.run(id), mode)
                val task = tasks.getById(id)!!
                assertEquals("running", task.status, mode)
                assertEquals("new-worker", task.lease_owner, mode)
                assertEquals(0L, task.attempt_count, mode)
                assertEquals(0L, task.progress_current, mode)
                assertEquals("", task.result_json, mode)
                assertEquals("", task.last_error_code, mode)
            }
        }
    }
}
