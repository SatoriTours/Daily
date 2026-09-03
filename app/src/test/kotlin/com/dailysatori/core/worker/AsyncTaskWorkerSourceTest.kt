package com.dailysatori.core.worker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AsyncTaskWorkerSourceTest {
    @Test
    fun retrySchedulingUsesDatabaseDeadlineWithoutBlockingSequentialChain() {
        assertEquals(3_000L, asyncTaskRetryDelayMs(runAfterMs = 5_000L, nowMs = 2_000L))
        assertEquals(0L, asyncTaskRetryDelayMs(runAfterMs = 1_000L, nowMs = 2_000L))

        val source = File("src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt").readText()
        assertTrue(source.contains("enqueueRetry(taskId"))
        val retryBranch = source.substringAfter("AsyncTaskRunOutcome.RetryScheduled ->").substringBefore("\n        }")
        assertTrue(retryBranch.contains("Result.success()"))
        assertTrue(!retryBranch.contains("Result.retry()"))
    }

    @Test
    fun genericWorkerWritesTaskLifecycleLogs() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt").readText()

        assertTrue(source.contains("AsyncTaskRunner"))
        assertTrue(source.contains("AsyncTaskLogStore"))
        assertTrue(source.contains("runner.run(taskId)"))
        assertTrue(source.contains("AsyncTaskRunOutcome.Succeeded"))
        assertTrue(source.contains("AsyncTaskRunOutcome.Failed"))
        assertTrue(source.contains("AsyncTaskRunOutcome.RetryScheduled"))
        assertTrue(source.contains("AsyncTaskRunOutcome.Skipped"))
        assertTrue(!source.contains("handler.execute("))
    }

    @Test
    fun diaryTaskHandlersAreRegisteredAndChainedTasksAreScheduled() {
        val appModule = File("src/main/kotlin/com/dailysatori/core/di/AppModule.kt").readText()
        val persistence = File("src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingRepositoryPersistence.kt").readText()
        val worker = File("src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt").readText()

        assertTrue(appModule.contains("get<DiaryTranscriptionCoordinator>()"))
        assertTrue(appModule.contains("get<DiaryKnowledgeCoordinator>()"))
        assertTrue(persistence.contains("transcriptionCoordinator.enqueue(attachmentId)"))
        assertTrue(persistence.contains("taskScheduler.enqueue(taskId)"))
        assertTrue(worker.contains("recoverAndEnqueueRunnable()"))
    }

    @Test
    fun networkAndLongRunningTasksHaveBackgroundExecutionGuards() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt").readText()

        assertTrue(source.contains("NetworkType.CONNECTED"))
        assertTrue(source.contains("setForeground(createAsyncTaskForegroundInfo"))
        assertTrue(source.contains("enqueueSequential("))
        assertTrue(source.contains("KEY_CONTINUE_CHAIN_ON_FAILURE"))
        assertTrue(isLongRunningAsyncTask("remote_article_sync"))
        assertTrue(isLongRunningAsyncTask("remote_news_fetch"))
    }

    @Test
    fun processStartReleasesRunningLeasesAndSchedulerCanCancelExactTask() {
        val worker = File("src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt").readText()
        val application = File("src/main/kotlin/com/dailysatori/DailySatoriApplication.kt").readText()

        assertTrue(worker.contains("fun cancel(taskId: Long)"))
        assertTrue(worker.contains("cancelAllWorkByTag(asyncTaskWorkTag(taskId))"))
        assertTrue(worker.contains(".addTag(asyncTaskWorkTag(taskId))"))
        assertTrue(worker.contains("markRunningForRetryAfterProcessRestart"))
        assertTrue(application.contains("recoverAfterProcessStart()"))
    }

    @Test
    fun reminderAiRecoveryReconcilesOrphansBeforeResumingDurableQueuedWork() {
        val worker = File("src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt").readText()
        val batches = File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ReminderAiBatchRepository.kt").readText()
        val sharedModule = File("../shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt").readText()

        assertTrue(sharedModule.contains("single { ReminderAiBatchRepository(get()) }"))

        val recovery = worker.substringAfter("fun recoverAfterProcessStart()")
            .substringBefore("fun recoverAndEnqueueRunnable()")
        assertTrue(recovery.contains("reconcileOrphanedActiveBatches("))
        assertTrue(recovery.indexOf("reconcileOrphanedActiveBatches(") < recovery.indexOf("enqueueRunnable(repo, now)"))
        assertTrue(recovery.contains("reminderAiParseTaskPayloadJson"))
        assertTrue(recovery.contains("uniqueKeyForBatch = { \"reminder_ai_parse:${'$'}it\" }"))

        val submission = batches.substringAfter("fun submitOrReuseWithTask(")
            .substringBefore("fun reconcileOrphanedActiveBatches(")
        assertTrue(submission.contains("q.transactionWithResult"))
        assertTrue(submission.contains("attachReminderAiBatchTask(taskId"))
        assertTrue(submission.contains("ReminderAiBatchSubmission"))
    }

    @Test
    fun systemInterruptionSchedulesTheDatabaseRetryBeforeWorkerStops() {
        val worker = File("src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt").readText()

        val cancellationBranch = worker
            .substringAfter("catch (error: CancellationException)")
            .substringBefore("throw error")
        assertTrue(cancellationBranch.contains("enqueueRetry(taskId"))
    }
}
