package com.dailysatori.service.asynctask

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AsyncTaskRunnerTest {
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

    private suspend fun withRunner(
        handlers: List<AsyncTaskHandler>,
        nowMs: () -> Long = { 1_000 },
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
