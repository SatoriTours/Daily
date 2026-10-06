package com.dailysatori.service.asynctask

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlin.test.assertFailsWith

class AsyncTaskRunnerTest {
    @Test
    fun timeoutHandlerWritesTheActualTerminalReason() = runBlocking {
        val handler = object : AsyncTaskHandler {
            override val type = FakeHandler.TYPE
            override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult = awaitCancellation()
            override suspend fun onExecutionTimeout(taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter) =
                AsyncTaskExecutionResult.PermanentFailure("model_timeout", "模型未及时返回，请稍后重试")
        }
        withRunner(handlers = listOf(handler), executionTimeoutMs = { 10L }) { repository, runner, logger ->
            val id = repository.enqueue(FakeHandler.TYPE, "{}")
            assertIs<AsyncTaskRunOutcome.Failed>(runner.run(id))
            assertTrue(logger.lines.any { it.contains("TASK failed code=model_timeout message=模型未及时返回") })
        }
    }

    @Test
    fun systemInterruptionRetriesWithoutConsumingTheOnlyAttemptOrLeakingCoroutineText() = runBlocking {
        withRunner(handlers = listOf(FakeHandler { _, _, _ ->
            throw CancellationException("Job was cancelled")
        })) { repository, runner, _ ->
            val id = repository.enqueue(FakeHandler.TYPE, "{}", maxAttempts = 1)
            assertFailsWith<CancellationException> { runner.run(id) }
            val task = repository.getById(id)!!
            assertEquals("retrying", task.status)
            assertEquals(0L, task.attempt_count)
            assertEquals("interrupted", task.last_error_code)
            assertEquals("任务被系统中断，将自动重试", task.last_error_message)
        }
    }

    @Test
    fun userCancellationDoesNotScheduleRetry() = runBlocking {
        lateinit var tasks: AsyncTaskRepository
        var id = 0L
        withRunner(handlers = listOf(FakeHandler { _, _, _ ->
            tasks.cancel(id)
            throw CancellationException("Job was cancelled")
        })) { repository, runner, _ ->
            tasks = repository
            id = tasks.enqueue(FakeHandler.TYPE, "{}", maxAttempts = 1)
            assertFailsWith<CancellationException> { runner.run(id) }
            assertEquals("cancelled", tasks.getById(id)!!.status)
            assertEquals("", tasks.getById(id)!!.last_error_message)
        }
    }

    @Test
    fun legacyQueuedNewsRefreshTasksNeverExecuteConcurrently() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val handler = object : AsyncTaskHandler {
            override val type = "remote_news_fetch"
            override suspend fun execute(
                taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter,
            ): AsyncTaskExecutionResult {
                calls++
                if (calls == 1) { started.complete(Unit); release.await() }
                return AsyncTaskExecutionResult.Success()
            }
        }
        withRunner(handlers = listOf(handler)) { tasks, runner, _ ->
            val first = tasks.enqueue(handler.type, "{}", uniqueKey = "remote_news_fetch:manual")
            val second = tasks.enqueue(handler.type, "{}", uniqueKey = "remote_news_fetch:due")
            val firstRun = async { runner.run(first) }
            started.await()
            try {
                assertIs<AsyncTaskRunOutcome.RetryScheduled>(runner.run(second))
                assertEquals(1, calls)
            } finally {
                release.complete(Unit)
            }
            assertIs<AsyncTaskRunOutcome.Succeeded>(firstRun.await())
            assertIs<AsyncTaskRunOutcome.Succeeded>(runner.run(second))
            assertEquals(2, calls)
        }
    }

    @Test
    fun dependentTaskWaitsWithoutExecutingOrConsumingAttempts() = runBlocking {
        var calls = 0
        withRunner(handlers = listOf(FakeHandler { payload, _, _ ->
            assertEquals("{}", payload)
            calls++
            AsyncTaskExecutionResult.Success()
        })) { repository, runner, _ ->
            val first = repository.enqueue(FakeHandler.TYPE, "{}")
            val second = repository.enqueue(FakeHandler.TYPE, "{}")
            repository.linkSequentialTasks(listOf(first, second))
            assertIs<AsyncTaskRunOutcome.RetryScheduled>(runner.run(second))
            assertEquals(0, calls)
            assertEquals(0L, repository.getById(second)!!.attempt_count)
            assertIs<AsyncTaskRunOutcome.Succeeded>(runner.run(first))
            assertIs<AsyncTaskRunOutcome.Succeeded>(runner.run(second))
            assertEquals(2, calls)
        }
    }

    @Test
    fun handlerThatNeverCompletesTimesOutIntoControlledRetry() = runBlocking {
        withRunner(
            handlers = listOf(FakeHandler { _, _, _ -> awaitCancellation() }),
            executionTimeoutMs = { 10L },
        ) { repository, runner, logger ->
            val taskId = repository.enqueue(type = FakeHandler.TYPE, payloadJson = "{}")

            val outcome = runner.run(taskId)

            assertIs<AsyncTaskRunOutcome.RetryScheduled>(outcome)
            assertEquals(AsyncTaskStatus.retrying.name, repository.getById(taskId)!!.status)
            assertEquals("timeout", repository.getById(taskId)!!.last_error_code)
            assertTrue(logger.lines.any { it.contains("TASK retry code=timeout") })
        }
    }

    @Test
    fun successfulHandlerFinishesTaskAndWritesLifecycleLogs() = runBlocking {
        withRunner(
            handlers = listOf(
                FakeHandler { _, _, reporter ->
                    reporter.report(1, 2, "half", """{"step":1}""")
                    AsyncTaskExecutionResult.Success("""{"ok":true}""")
                },
            ),
        ) { repository, runner, logger ->
            val taskId = repository.enqueue(type = FakeHandler.TYPE, payloadJson = "{}")

            val outcome = runner.run(taskId)

            val task = repository.getById(taskId)!!
            assertIs<AsyncTaskRunOutcome.Succeeded>(outcome)
            assertEquals(AsyncTaskStatus.succeeded.name, task.status)
            assertEquals(1, task.progress_current)
            assertEquals("""{"ok":true}""", task.result_json)
            assertTrue(logger.lines.any { it.contains("TASK started type=${FakeHandler.TYPE}") })
            assertTrue(logger.lines.any { it.contains("TASK progress current=1 total=2 message=half") })
            assertTrue(logger.lines.any { it.contains("TASK succeeded") })
        }
    }

    @Test
    fun permanentFailureFinishesTaskWithoutRetry() = runBlocking {
        withRunner(
            handlers = listOf(
                FakeHandler { _, _, _ ->
                    AsyncTaskExecutionResult.PermanentFailure("bad_input", "Bad input")
                },
            ),
        ) { repository, runner, logger ->
            val taskId = repository.enqueue(type = FakeHandler.TYPE, payloadJson = "{}")

            val outcome = runner.run(taskId)

            val task = repository.getById(taskId)!!
            assertIs<AsyncTaskRunOutcome.Failed>(outcome)
            assertEquals(AsyncTaskStatus.failed.name, task.status)
            assertEquals("bad_input", task.last_error_code)
            assertEquals(0, task.attempt_count)
            assertTrue(logger.lines.any { it.contains("TASK failed code=bad_input message=Bad input") })
        }
    }

    @Test
    fun retryableFailureMarksRetryWithRunAfter() = runBlocking {
        withRunner(
            nowMs = { 1_000 },
            handlers = listOf(
                FakeHandler { _, _, _ ->
                    AsyncTaskExecutionResult.RetryableFailure("network", "Network down")
                },
            ),
        ) { repository, runner, logger ->
            val taskId = repository.enqueue(type = FakeHandler.TYPE, payloadJson = "{}")

            val outcome = runner.run(taskId)

            val task = repository.getById(taskId)!!
            assertIs<AsyncTaskRunOutcome.RetryScheduled>(outcome)
            assertEquals(AsyncTaskStatus.retrying.name, task.status)
            assertEquals(1, task.attempt_count)
            assertEquals(31_000, task.run_after_ms)
            assertTrue(logger.lines.any { it.contains("TASK retry code=network message=Network down runAfterMs=31000") })
        }
    }

    @Test
    fun thrownExceptionBecomesRetryableFailure() = runBlocking {
        withRunner(
            nowMs = { 2_000 },
            handlers = listOf(
                FakeHandler { _, _, _ ->
                    error("boom")
                },
            ),
        ) { repository, runner, logger ->
            val taskId = repository.enqueue(type = FakeHandler.TYPE, payloadJson = "{}")

            val outcome = runner.run(taskId)

            val task = repository.getById(taskId)!!
            assertIs<AsyncTaskRunOutcome.RetryScheduled>(outcome)
            assertEquals(AsyncTaskStatus.retrying.name, task.status)
            assertEquals("exception", task.last_error_code)
            assertEquals(32_000, task.run_after_ms)
            assertTrue(logger.lines.any { it.contains("TASK retry code=exception message=boom") })
        }
    }

    @Test
    fun missingHandlerFailsTask() = runBlocking {
        withRunner(handlers = emptyList()) { repository, runner, logger ->
            val taskId = repository.enqueue(type = "missing", payloadJson = "{}")

            val outcome = runner.run(taskId)

            val task = repository.getById(taskId)!!
            assertIs<AsyncTaskRunOutcome.Failed>(outcome)
            assertEquals(AsyncTaskStatus.failed.name, task.status)
            assertEquals("handler_missing", task.last_error_code)
            assertTrue(logger.lines.any { it.contains("TASK failed code=handler_missing") })
        }
    }

    @Test
    fun retryableFailureAtMaxAttemptsFailsTask() = runBlocking {
        withRunner(
            handlers = listOf(
                FakeHandler { _, _, _ ->
                    AsyncTaskExecutionResult.RetryableFailure("network", "Network down")
                },
            ),
        ) { repository, runner, logger ->
            val taskId = repository.enqueue(type = FakeHandler.TYPE, payloadJson = "{}", maxAttempts = 1)

            val outcome = runner.run(taskId)

            val task = repository.getById(taskId)!!
            assertIs<AsyncTaskRunOutcome.Failed>(outcome)
            assertEquals(AsyncTaskStatus.failed.name, task.status)
            assertEquals("network", task.last_error_code)
            assertEquals(0, task.attempt_count)
            assertTrue(logger.lines.any { it.contains("TASK failed code=network message=Network down") })
        }
    }

    @Test
    fun expiredRunningTaskIsRecoveredBeforeClaim() = runBlocking {
        withRunner(
            nowMs = { 2_000 },
            handlers = listOf(FakeHandler { _, _, _ -> AsyncTaskExecutionResult.Success() }),
        ) { repository, runner, _ ->
            val taskId = repository.enqueue(type = FakeHandler.TYPE, payloadJson = "{}")
            repository.claimForRun(taskId, leaseOwner = "dead-worker", leaseUntilMs = 1_000)

            val outcome = runner.run(taskId)

            assertIs<AsyncTaskRunOutcome.Succeeded>(outcome)
            assertEquals(AsyncTaskStatus.succeeded.name, repository.getById(taskId)!!.status)
        }
    }

    @Test
    fun cancellationWinningAfterHandlerStartsCannotBeOverwrittenBySuccess() = runBlocking {
        lateinit var repository: AsyncTaskRepository
        var taskId = -1L
        withRunner(
            handlers = listOf(
                FakeHandler { _, _, _ ->
                    repository.cancel(taskId)
                    AsyncTaskExecutionResult.Success()
                },
            ),
        ) { repo, runner, logger ->
            repository = repo
            taskId = repository.enqueue(type = FakeHandler.TYPE, payloadJson = "{}")

            val outcome = runner.run(taskId)

            assertIs<AsyncTaskRunOutcome.Skipped>(outcome)
            assertEquals(AsyncTaskStatus.cancelled.name, repository.getById(taskId)!!.status)
            assertTrue(logger.lines.any { it.contains("TASK cancelled") })
            assertTrue(logger.lines.none { it.contains("TASK succeeded") })
        }
    }

    private suspend fun withRunner(
        handlers: List<AsyncTaskHandler>,
        nowMs: () -> Long = { 1_000 },
        executionTimeoutMs: (String) -> Long = { 60_000L },
        block: suspend (AsyncTaskRepository, AsyncTaskRunner, RecordingTaskLogger) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val repository = AsyncTaskRepository(DailySatoriDatabase(driver))
        val logger = RecordingTaskLogger()
        val runner = AsyncTaskRunner(
            repository = repository,
            registry = AsyncTaskHandlerRegistry(handlers),
            logger = logger,
            leaseOwnerProvider = { "test-worker" },
            nowMs = nowMs,
            executionTimeoutMs = executionTimeoutMs,
        )
        block(repository, runner, logger)
        driver.close()
    }

    private class RecordingTaskLogger : AsyncTaskLogger {
        val lines = mutableListOf<String>()

        override fun append(taskId: Long, message: String) {
            lines += "$taskId $message"
        }
    }

    private class FakeHandler(
        private val block: suspend (
            payloadJson: String,
            checkpointJson: String,
            reporter: AsyncTaskProgressReporter,
        ) -> AsyncTaskExecutionResult,
    ) : AsyncTaskHandler {
        override val type: String = TYPE

        override suspend fun execute(
            taskId: Long,
            payloadJson: String,
            checkpointJson: String,
            reporter: AsyncTaskProgressReporter,
        ): AsyncTaskExecutionResult = block(payloadJson, checkpointJson, reporter)

        companion object {
            const val TYPE = "fake_task"
        }
    }
}
