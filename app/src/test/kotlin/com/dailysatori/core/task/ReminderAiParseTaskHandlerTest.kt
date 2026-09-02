package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ReminderAiBatchRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.core.navigation.ReminderAiBatchRoute
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskRunOutcome
import com.dailysatori.service.asynctask.AsyncTaskRunner
import com.dailysatori.service.asynctask.reminderAiRetryDecision
import com.dailysatori.service.reminder.ReminderBatchCodec
import com.dailysatori.service.reminder.ReminderDraftCodec
import com.dailysatori.service.reminder.ReminderInputFragment
import com.dailysatori.service.reminder.ReminderInterpretationRemote
import com.dailysatori.service.reminder.ReminderAiBatchStatus
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReminderAiParseTaskHandlerTest {
    @Test
    fun orphanRecoveryRunsToOneNotificationRouteAndIdempotentConfirmedReminder() = runBlocking {
        withDatabase { database, _ ->
            val batches = ReminderAiBatchRepository(database)
            val tasks = AsyncTaskRepository(database)
            val orphan = batches.enqueueOrReuse("pay card", TimeZone.UTC, LocalDate(2026, 9, 2))
            val notifier = RecordingNotifier()
            val taskIds = batches.reconcileOrphanedActiveBatches(
                ReminderAiParseTaskHandler.TYPE,
                ::reminderAiParseTaskPayloadJson,
                { "reminder_ai_parse:$it" },
            )
            val handler = ReminderAiParseTaskHandler(
                batches,
                RecordingRemote("""[{"source_index":0,"content":"pay card","start_date":"2026-09-02","end_date":"2026-09-02","first_reminder_time":"09:00","active_day_rule":"daily","recurrence_rule":"once"}]"""),
                ReminderBatchCodec(ReminderDraftCodec()), FixedClock, notifier,
            )
            val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)), nowMs = { FixedClock.now().toEpochMilliseconds() })

            assertEquals(AsyncTaskRunOutcome.Succeeded, runner.run(taskIds.single()))
            assertEquals(listOf(orphan.id), notifier.ready)
            assertEquals(orphan.id, ReminderAiBatchRoute(orphan.id).batchId)

            val ready = batches.getBatch(orphan.id)!!
            val reminderId = "${orphan.id}:0"
            val codec = ReminderDraftCodec(now = { FixedClock.now() }, currentTimeZone = { TimeZone.UTC })
            val draft = codec.decodeInterpretationResponse(ready.drafts.single().draftJson, TimeZone.UTC).copy(id = reminderId)
            val reminders = ReminderRepository(database, TimeZone.UTC)
            reminders.get(reminderId) ?: reminders.createConfirmed(draft, ReminderProfileSnapshot.standard())
            batches.markDraftConfirmed(orphan.id, 0, reminderId)
            reminders.get(reminderId) ?: reminders.createConfirmed(draft, ReminderProfileSnapshot.standard())

            assertEquals(reminderId, reminders.get(reminderId)?.id)
            assertEquals(ReminderAiBatchStatus.CONFIRMED, batches.getBatch(orphan.id)?.status)
            assertEquals(1, notifier.ready.size)
        }
    }

    @Test
    fun parsesAllFragmentsInOneAiCallAndPersistsReadyDraftsWithTimingCheckpoints() = runBlocking {
        withRepository { repository ->
            val batch = repository.enqueueOrReuse("first; second", TimeZone.UTC, LocalDate(2026, 9, 2))
            val remote = RecordingRemote("""
                [{"source_index":0,"content":"first","start_date":"2026-09-02","end_date":"2026-09-02","first_reminder_time":"09:00","active_day_rule":"daily","recurrence_rule":"once"},{"source_index":1,"content":"second","start_date":"2026-09-03","end_date":"2026-09-03","first_reminder_time":"10:00","active_day_rule":"daily","recurrence_rule":"once"}]
            """.trimIndent())
            val reporter = RecordingReporter()
            val notifier = RecordingNotifier()
            val handler = ReminderAiParseTaskHandler(repository, remote, ReminderBatchCodec(ReminderDraftCodec()), FixedClock, notifier)

            val result = handler.execute(91, reminderAiParseTaskPayloadJson(batch.id), "", reporter)

            assertIs<AsyncTaskExecutionResult.Success>(result)
            assertEquals(1, remote.calls)
            assertEquals(listOf(0, 1), remote.fragments.single().map(ReminderInputFragment::index))
            assertEquals(listOf("first", "second"), repository.getBatch(batch.id)?.drafts?.map { it.sourceText })
            assertEquals(listOf(batch.id), notifier.ready)
            assertTrue(reporter.checkpoints.any { it.contains("queue_wait_ms") })
            assertTrue(reporter.checkpoints.any { it.contains("persist_ms") })
        }
    }

    @Test
    fun terminalFailureClaimsAndPostsOnlyOnceAcrossDuplicateWorkerDelivery() = runBlocking {
        withRepository { repository ->
            val batch = repository.enqueueOrReuse("do not notify twice", TimeZone.UTC, LocalDate(2026, 9, 2))
            val notifier = RecordingNotifier()
            val handler = ReminderAiParseTaskHandler(
                repository,
                RecordingRemote(IllegalStateException("AI is not configured")),
                ReminderBatchCodec(ReminderDraftCodec()),
                FixedClock,
                notifier,
            )

            handler.execute(92, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())
            handler.execute(92, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())

            assertEquals(listOf(batch.id), notifier.failed)
            assertTrue(repository.getBatch(batch.id)?.terminalNotificationAt != null)
        }
    }

    @Test
    fun recoveredTerminalBatchClaimsAndPostsExactlyOnceAfterPersistenceBeforeClaim() = runBlocking {
        withRepository { repository ->
            val batch = repository.enqueueOrReuse("persisted before crash", TimeZone.UTC, LocalDate(2026, 9, 2))
            repository.markRunning(batch.id, 92, 1)
            repository.markReady(batch.id, listOf(com.dailysatori.service.reminder.ReminderAiBatchDraft(0, "persisted before crash", "{}")))
            val notifier = RecordingNotifier()
            val handler = ReminderAiParseTaskHandler(repository, RecordingRemote("[]"), ReminderBatchCodec(ReminderDraftCodec()), FixedClock, notifier)

            handler.execute(92, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())
            handler.execute(92, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())

            assertEquals(listOf(batch.id), notifier.ready)
            assertTrue(repository.getBatch(batch.id)?.terminalNotificationAt != null)
        }
    }

    @Test
    fun recoveredFailedBatchClaimsAndPostsOnceAfterPersistenceBeforeClaim() = runBlocking {
        withRepository { repository ->
            val batch = repository.enqueueOrReuse("failed before crash", TimeZone.UTC, LocalDate(2026, 9, 2))
            repository.markRunning(batch.id, 92, 1)
            repository.markFailed(batch.id, "AI is not configured")
            val notifier = RecordingNotifier()
            val handler = ReminderAiParseTaskHandler(repository, RecordingRemote("[]"), ReminderBatchCodec(ReminderDraftCodec()), FixedClock, notifier)

            handler.execute(92, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())
            handler.execute(92, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())

            assertEquals(listOf(batch.id), notifier.failed)
            assertTrue(repository.getBatch(batch.id)?.terminalNotificationAt != null)
        }
    }

    @Test
    fun retriesTransientFailuresAtThirtySecondsThenTwoMinutesAndPersistsFinalOriginalInputFailure() = runBlocking {
        withRepository { repository ->
            val batch = repository.enqueueOrReuse("do not lose this", TimeZone.UTC, LocalDate(2026, 9, 2))
            val handler = ReminderAiParseTaskHandler(repository, RecordingRemote(IllegalStateException("timeout")), ReminderBatchCodec(ReminderDraftCodec()), FixedClock)

            val first = handler.execute(92, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())
            val second = handler.execute(92, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())
            val third = handler.execute(92, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())

            assertEquals(30_000L, reminderAiRetryDecision(IllegalStateException("timeout"), 1).retryDelayMs)
            assertEquals(120_000L, reminderAiRetryDecision(IllegalStateException("timeout"), 2).retryDelayMs)
            assertTrue(assertIs<AsyncTaskExecutionResult.RetryableFailure>(first).retryAfterMs!! > 30_000L)
            assertTrue(assertIs<AsyncTaskExecutionResult.RetryableFailure>(second).retryAfterMs!! > 120_000L)
            assertIs<AsyncTaskExecutionResult.PermanentFailure>(third)
            assertEquals("do not lose this", repository.getBatch(batch.id)?.originalInput)
        }
    }

    @Test
    fun configurationAuthAndMalformedResponsesFailPermanentlyWithoutRetry() = runBlocking {
        withRepository { repository ->
            listOf(
                IllegalStateException("AI is not configured"),
                IllegalStateException("401 unauthorized"),
                "[{\"source_index\":0,\"content\":\"missing required fields\"}]",
            ).forEachIndexed { index, response ->
                val batch = repository.enqueueOrReuse("input $index", TimeZone.UTC, LocalDate(2026, 9, 2))
                val result = ReminderAiParseTaskHandler(
                    repository,
                    RecordingRemote(response),
                    ReminderBatchCodec(ReminderDraftCodec()),
                    FixedClock,
                ).execute(index.toLong() + 100, reminderAiParseTaskPayloadJson(batch.id), "", RecordingReporter())

                assertIs<AsyncTaskExecutionResult.PermanentFailure>(result)
                assertEquals(ReminderAiBatchStatus.PARSE_FAILED, repository.getBatch(batch.id)?.status)
                assertEquals("input $index", repository.getBatch(batch.id)?.originalInput)
            }
        }
    }

    @Test
    fun runnerTimeoutUsesReminderRetryScheduleAndTerminallyFailsOriginalBatch() = runBlocking {
        withDatabase { database, driver ->
            val batches = ReminderAiBatchRepository(database)
            val tasks = AsyncTaskRepository(database)
            val batch = batches.enqueueOrReuse("keep this input", TimeZone.UTC, LocalDate(2026, 9, 2))
            val handler = ReminderAiParseTaskHandler(batches, RecordingRemote("[]", delayMillis = 50), ReminderBatchCodec(ReminderDraftCodec()), FixedClock)
            val taskId = tasks.enqueue(ReminderAiParseTaskHandler.TYPE, reminderAiParseTaskPayloadJson(batch.id), maxAttempts = 5)
            val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)), nowMs = { FixedClock.now().toEpochMilliseconds() }, executionTimeoutMs = { 1 })

            assertEquals(AsyncTaskRunOutcome.RetryScheduled, runner.run(taskId))
            assertEquals(FixedClock.now().toEpochMilliseconds() + 30_000L, tasks.getById(taskId)?.run_after_ms)
            makeRunnable(driver, taskId)
            assertEquals(AsyncTaskRunOutcome.RetryScheduled, runner.run(taskId))
            assertEquals(FixedClock.now().toEpochMilliseconds() + 120_000L, tasks.getById(taskId)?.run_after_ms)
            makeRunnable(driver, taskId)
            assertEquals(AsyncTaskRunOutcome.Failed, runner.run(taskId))
            assertEquals("failed", tasks.getById(taskId)?.status)
            assertEquals(ReminderAiBatchStatus.PARSE_FAILED, batches.getBatch(batch.id)?.status)
            assertEquals("keep this input", batches.getBatch(batch.id)?.originalInput)
        }
    }

    private fun withRepository(block: suspend (ReminderAiBatchRepository) -> Unit) = withDatabase { database, _ ->
        block(ReminderAiBatchRepository(database))
    }

    private fun withDatabase(block: suspend (DailySatoriDatabase, JdbcSqliteDriver) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            block(DailySatoriDatabase(driver), driver)
        } finally {
            driver.close()
        }
    }

    private class RecordingReporter : AsyncTaskProgressReporter {
        val checkpoints = mutableListOf<String>()
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
            checkpoints += checkpointJson
        }
    }

    private class RecordingNotifier : com.dailysatori.core.reminder.ReminderAiParseNotifier {
        val ready = mutableListOf<String>()
        val failed = mutableListOf<String>()
        override fun notifyReady(batchId: String) { ready += batchId }
        override fun notifyFailed(batchId: String) { failed += batchId }
    }

    private fun makeRunnable(driver: JdbcSqliteDriver, taskId: Long) {
        driver.execute(null, "UPDATE async_task SET run_after_ms = 0 WHERE id = $taskId", 0)
    }

    private class RecordingRemote(private val response: Any, private val delayMillis: Long = 0) : ReminderInterpretationRemote {
        var calls = 0
        val fragments = mutableListOf<List<ReminderInputFragment>>()
        override suspend fun interpret(text: String, now: Instant, zone: TimeZone): String = error("single parser is not used")
        override suspend fun interpretBatch(fragments: List<ReminderInputFragment>, now: Instant, zone: TimeZone): String {
            calls += 1
            this.fragments += fragments
            if (delayMillis > 0) delay(delayMillis)
            if (response is Throwable) throw response
            return response as String
        }
    }

    private object FixedClock : Clock { override fun now(): Instant = Instant.parse("2026-09-02T00:00:00Z") }
}
