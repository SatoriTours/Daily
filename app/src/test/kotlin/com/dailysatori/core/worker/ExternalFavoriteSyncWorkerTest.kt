package com.dailysatori.core.worker

import androidx.work.NetworkType
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun periodicRequestCarriesRecentModeAndNetworkConstraint() {
        val request = buildExternalFavoritePeriodicSyncWorkRequest(42, 30)

        assertEquals(42L, request.workSpec.input.getLong(ExternalFavoriteSyncWorker.KEY_SOURCE_ID, -1L))
        assertEquals("recent", request.workSpec.input.getString(ExternalFavoriteSyncWorker.KEY_MODE))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun workerModeParsesKnownModes() {
        assertEquals(FavoriteSyncMode.recent, externalFavoriteSyncMode("recent"))
        assertEquals(FavoriteSyncMode.history, externalFavoriteSyncMode("history"))
        assertEquals(FavoriteSyncMode.full_rescan, externalFavoriteSyncMode("full_rescan"))
        assertEquals(FavoriteSyncMode.retry_failed, externalFavoriteSyncMode("retry_failed"))
        assertEquals(null, externalFavoriteSyncMode("unknown"))
        assertEquals(null, externalFavoriteSyncMode(null))
    }
}
