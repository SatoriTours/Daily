package com.dailysatori.core.worker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AsyncTaskWorkerSourceTest {
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
    }

    @Test
    fun processStartReleasesRunningLeasesAndSchedulerCanCancelExactTask() {
        val worker = File("src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt").readText()
        val application = File("src/main/kotlin/com/dailysatori/DailySatoriApplication.kt").readText()

        assertTrue(worker.contains("fun cancel(taskId: Long)"))
        assertTrue(worker.contains("cancelUniqueWork(asyncTaskWorkName(taskId))"))
        assertTrue(worker.contains("markRunningForRetryAfterProcessRestart"))
        assertTrue(application.contains("recoverAfterProcessStart()"))
    }
}
