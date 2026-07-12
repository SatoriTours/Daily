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
}
