package com.dailysatori.core.task

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalFavoriteSyncTaskSourceTest {
    @Test
    fun payloadJsonCarriesSourceIdAndMode() {
        val json = externalFavoriteSyncTaskPayloadJson(sourceId = 42, mode = "history", automatic = true)

        assertTrue(json.contains("\"sourceId\":42"))
        assertTrue(json.contains("\"mode\":\"history\""))
        assertTrue(json.contains("\"automatic\":true"))
    }

    @Test
    fun taskHandlerUsesExternalFavoriteTaskType() {
        assertEquals("external_favorite_sync", ExternalFavoriteSyncTaskHandler.TYPE)
    }

    @Test
    fun schedulerEnqueuesManualSyncIntoAsyncTaskFramework() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/ExternalFavoriteSyncWorker.kt").readText()

        assertTrue(source.contains("AsyncTaskType.external_favorite_sync.name"))
        assertTrue(source.contains("externalFavoriteSyncTaskPayloadJson(sourceId, mode, automatic)"))
        assertTrue(source.contains("\"external_favorite_sync:\$sourceId:\$mode\""))
        assertTrue(source.contains("asyncTaskRepo.enqueueUniqueFamily("))
        assertTrue(source.contains("ExistingWorkPolicy.APPEND_OR_REPLACE"))
        assertTrue(source.contains("ExternalFavoriteQueueWorker"))
        assertTrue(source.contains("runnableTasksByType"))
    }

    @Test
    fun settingsPageObservesManualSyncAsyncTaskState() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt").readText()

        assertTrue(source.contains("AsyncTaskRepository"))
        assertTrue(source.contains("observeSyncWork(sourceId, mode)"))
        assertTrue(source.contains("FavoriteSyncMode.entries.forEach { mode -> observeSyncWork(it.id, mode) }"))
        assertTrue(source.contains("observeLatestByUniqueKey(externalFavoriteSyncUniqueKey(sourceId, mode.name))"))
        assertTrue(source.contains("observeExactSyncTask(sourceId, taskId)"))
        assertTrue(source.contains("observeTaskById(taskId)"))
        assertTrue(source.contains("externalFavoriteSyncWorkFromAsyncTask"))
    }

    @Test
    fun appModuleRegistersExternalFavoriteSyncHandler() {
        val source = File("src/main/kotlin/com/dailysatori/core/di/AppModule.kt").readText()

        assertTrue(source.contains("single { ExternalFavoriteSyncTaskHandler(get(), get()) }"))
        assertTrue(source.contains("get<ExternalFavoriteSyncTaskHandler>()"))
    }

    @Test
    fun taskHandlerResumesArticleProcessingAfterSuccessfulSync() {
        val source = File("src/main/kotlin/com/dailysatori/core/task/ExternalFavoriteSyncTaskHandler.kt").readText()

        assertTrue(source.contains("ArticleProcessingScheduler"))
        assertTrue(source.contains("articleProcessingScheduler.enqueueResume()"))
        assertTrue(source.indexOf("syncService.syncSource") < source.indexOf("articleProcessingScheduler.enqueueResume()"))
    }
}
