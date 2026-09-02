package com.dailysatori.core.task

import com.dailysatori.service.asynctask.AsyncTaskType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult

class RemoteArticleSyncTaskSourceTest {
    @Test
    fun allSourceFailuresRetryButPartialFailuresKeepUsableResults() {
        assertIs<AsyncTaskExecutionResult.RetryableFailure>(
            remoteArticleSyncOutcome(successfulSources = 0, failures = listOf("source failed"), resultJson = "{}"),
        )
        assertIs<AsyncTaskExecutionResult.Success>(
            remoteArticleSyncOutcome(successfulSources = 1, failures = listOf("other failed"), resultJson = "{}"),
        )
    }

    @Test
    fun taskTypeAndHandlerUseAsyncTaskFramework() {
        assertEquals("remote_article_sync", AsyncTaskType.remote_article_sync.name)
        assertEquals("remote_article_sync", RemoteArticleSyncTaskHandler.TYPE)

        val appModule = File("src/main/kotlin/com/dailysatori/core/di/AppModule.kt").readText()
        assertTrue(appModule.contains("single { RemoteArticleSyncTaskHandler(get(), get(), get(), get()) }"))
        assertTrue(appModule.contains("get<RemoteArticleSyncTaskHandler>()"))
    }

    @Test
    fun unifiedNewsWorkerEnqueuesRemoteArticleSyncTask() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()

        assertTrue(source.contains("AsyncTaskType.remote_article_sync.name"))
        assertTrue(source.contains("remoteArticleSyncTaskPayloadJson("))
        assertTrue(source.contains("\"remote_article_sync:\${mode.name.lowercase()}\""))
        assertTrue(source.contains("asyncTaskScheduler.enqueueSequential("))
        assertTrue(source.contains("taskIds = listOf(syncTaskId, summaryTaskId)"))
    }

    @Test
    fun remoteArticleSyncTaskPayloadSupportsSingleSourceSync() {
        val handler = File("src/main/kotlin/com/dailysatori/core/task/RemoteArticleSyncTaskHandler.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

        assertTrue(handler.contains("val sourceId: Long? = null"))
        assertTrue(handler.contains("payload.sourceId?.let"))
        assertTrue(viewModel.contains("remoteArticleSyncTaskPayloadJson(mode = \"manual_source\", sourceId = sourceId)"))
        assertTrue(viewModel.contains("uniqueKey = \"remote_article_sync:source:${'$'}sourceId\""))
    }
}
