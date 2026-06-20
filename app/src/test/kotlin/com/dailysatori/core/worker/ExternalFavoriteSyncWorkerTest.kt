package com.dailysatori.core.worker

import androidx.work.NetworkType
import androidx.work.ListenableWorker
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.XFavoriteAuthException
import com.dailysatori.service.externalfavorites.XFavoriteRateLimitException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalFavoriteSyncWorkerTest {
    @Test
    fun buildsUniqueWorkNamePerSourceAndMode() {
        assertEquals("external-favorite-sync-7-recent", externalFavoriteSyncWorkName(7, "recent"))
        assertEquals("external-favorite-sync-7-history", externalFavoriteSyncWorkName(7, "history"))
    }

    @Test
    fun oneTimeRequestCarriesSourceIdAndMode() {
        val request = buildExternalFavoriteSyncWorkRequest(42, "history")

        assertEquals(42L, request.workSpec.input.getLong(ExternalFavoriteSyncWorker.KEY_SOURCE_ID, -1L))
        assertEquals("history", request.workSpec.input.getString(ExternalFavoriteSyncWorker.KEY_MODE))
    }

    @Test
    fun progressDataCarriesPageItemAndPhaseFields() {
        val data = externalFavoriteSyncProgressData(
            phase = "backfill",
            pagesSeen = 2,
            maxPages = 3,
            itemsSeen = 168,
            historyComplete = false,
        )

        assertEquals("backfill", data.getString(ExternalFavoriteSyncWorker.PROGRESS_PHASE))
        assertEquals(2, data.getInt(ExternalFavoriteSyncWorker.PROGRESS_PAGES_SEEN, -1))
        assertEquals(3, data.getInt(ExternalFavoriteSyncWorker.PROGRESS_MAX_PAGES, -1))
        assertEquals(168, data.getInt(ExternalFavoriteSyncWorker.PROGRESS_ITEMS_SEEN, -1))
        assertFalse(data.getBoolean(ExternalFavoriteSyncWorker.PROGRESS_HISTORY_COMPLETE, true))
    }

    @Test
    fun periodicRequestCarriesUnifiedSyncModeAndNetworkConstraint() {
        val request = buildExternalFavoritePeriodicSyncWorkRequest(42, 30)

        assertEquals(42L, request.workSpec.input.getLong(ExternalFavoriteSyncWorker.KEY_SOURCE_ID, -1L))
        assertEquals("sync", request.workSpec.input.getString(ExternalFavoriteSyncWorker.KEY_MODE))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun periodicSchedulingOnlyRunsForEnabledSourcesWithPositiveInterval() {
        assertTrue(externalFavoriteShouldSchedulePeriodic(enabled = 1, intervalMinutes = 720))
        assertFalse(externalFavoriteShouldSchedulePeriodic(enabled = 0, intervalMinutes = 720))
        assertFalse(externalFavoriteShouldSchedulePeriodic(enabled = 1, intervalMinutes = 0))
    }

    @Test
    fun workerModeParsesKnownModes() {
        assertEquals(FavoriteSyncMode.sync, externalFavoriteSyncMode("sync"))
        assertEquals(FavoriteSyncMode.recent, externalFavoriteSyncMode("recent"))
        assertEquals(FavoriteSyncMode.history, externalFavoriteSyncMode("history"))
        assertEquals(FavoriteSyncMode.full_rescan, externalFavoriteSyncMode("full_rescan"))
        assertEquals(FavoriteSyncMode.retry_failed, externalFavoriteSyncMode("retry_failed"))
        assertEquals(null, externalFavoriteSyncMode("unknown"))
        assertEquals(null, externalFavoriteSyncMode(null))
    }

    @Test
    fun authAndRateLimitFailuresDoNotRequestImmediateWorkRetry() {
        assertEquals(
            ListenableWorker.Result.failure(),
            externalFavoriteSyncFailureResult(XFavoriteAuthException(statusCode = 401)),
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            externalFavoriteSyncFailureResult(XFavoriteRateLimitException(statusCode = 429, rateLimitResetAt = 1L)),
        )
    }

    @Test
    fun transientFailuresRequestWorkRetry() {
        assertEquals(
            ListenableWorker.Result.retry(),
            externalFavoriteSyncFailureResult(RuntimeException("temporary network failure")),
        )
    }

    @Test
    fun workerTriggersArticleProcessingResumeAfterExternalFavoriteSync() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/core/worker/ExternalFavoriteSyncWorker.kt").readText()

        assertEquals(true, source.contains("ArticleProcessingScheduler(applicationContext).enqueueResume()"))
    }

    @Test
    fun schedulerExposesCancelForCurrentUnifiedSync() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/core/worker/ExternalFavoriteSyncWorker.kt").readText()

        assertTrue(source.contains("fun cancelSync(sourceId: Long)"))
        assertTrue(source.contains("cancelUniqueWork(externalFavoriteSyncWorkName(sourceId, FavoriteSyncMode.sync.name))"))
    }

    @Test
    fun schedulerRegistersWorkInfoLiveDataObserverOnMainThread() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/core/worker/ExternalFavoriteSyncWorker.kt").readText()

        assertTrue(source.contains("val mainLooper = Looper.getMainLooper()"))
        assertTrue(source.contains("Handler(mainLooper).post(block)"))
        assertTrue(source.contains("runOnMainThread {\n            liveData.observeForever(observer)\n        }"))
        assertTrue(source.contains("runOnMainThread {\n                liveData.removeObserver(observer)\n            }"))
    }

    @Test
    fun mainActivitySchedulesPeriodicSyncOnStartupAndAfterOAuth() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/MainActivity.kt").readText()

        assertTrue(source.contains("scheduleExternalFavoritePeriodicSyncs()"))
        assertTrue(source.contains("enqueuePeriodic(source.id, source.sync_interval_minutes)"))
    }
}
