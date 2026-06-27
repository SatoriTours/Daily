package com.dailysatori.ui.feature.settings.externalfavorites

import androidx.work.WorkInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExternalFavoriteSyncStateTest {
    @Test
    fun staleFinishedTaskDoesNotClearNewlyQueuedSyncState() {
        val queued = ExternalFavoriteSyncWorkUi(
            taskId = null,
            createdAt = 2_000,
            state = WorkInfo.State.ENQUEUED,
            pagesSeen = 0,
            maxPages = 3,
            itemsSeen = 0,
            phase = "",
        )
        val staleFinished = ExternalFavoriteSyncWorkUi(
            taskId = 7,
            createdAt = 1_000,
            state = WorkInfo.State.SUCCEEDED,
            pagesSeen = 1,
            maxPages = 3,
            itemsSeen = 90,
            phase = "complete",
        )

        val next = externalFavoriteApplySyncWorkState(
            state = ExternalFavoritesSettingsState(
                syncingSourceId = 42,
                syncWorkBySourceId = mapOf(42L to queued),
            ),
            sourceId = 42,
            workUi = staleFinished,
        )

        assertEquals(42, next.syncingSourceId)
        assertSame(queued, next.syncWorkBySourceId[42])
        assertTrue(next.syncWorkBySourceId[42]?.active == true)
    }

    @Test
    fun finishedTaskDoesNotClearFreshManualPlaceholderBeforeTaskIdIsBound() {
        val queuedPlaceholder = ExternalFavoriteSyncWorkUi(
            taskId = null,
            createdAt = 2_000,
            state = WorkInfo.State.ENQUEUED,
            pagesSeen = 0,
            maxPages = 3,
            itemsSeen = 0,
            phase = "",
        )
        val previousFinished = ExternalFavoriteSyncWorkUi(
            taskId = 8,
            createdAt = 2_001,
            state = WorkInfo.State.SUCCEEDED,
            pagesSeen = 1,
            maxPages = 3,
            itemsSeen = 27,
            phase = "complete",
        )

        val next = externalFavoriteApplySyncWorkState(
            state = ExternalFavoritesSettingsState(
                syncingSourceId = 42,
                syncWorkBySourceId = mapOf(42L to queuedPlaceholder),
            ),
            sourceId = 42,
            workUi = previousFinished,
        )

        assertEquals(42, next.syncingSourceId)
        assertSame(queuedPlaceholder, next.syncWorkBySourceId[42])
    }

    @Test
    fun currentFinishedTaskClearsSyncState() {
        val running = ExternalFavoriteSyncWorkUi(
            taskId = 8,
            state = WorkInfo.State.RUNNING,
            pagesSeen = 2,
            maxPages = 3,
            itemsSeen = 180,
            phase = "backfill",
        )
        val finished = running.copy(state = WorkInfo.State.SUCCEEDED, phase = "complete")

        val next = externalFavoriteApplySyncWorkState(
            state = ExternalFavoritesSettingsState(
                syncingSourceId = 42,
                syncWorkBySourceId = mapOf(42L to running),
            ),
            sourceId = 42,
            workUi = finished,
        )

        assertEquals(null, next.syncingSourceId)
        assertEquals(null, next.syncWorkBySourceId[42])
    }

    @Test
    fun finishedTaskDoesNotClearQueuedPlaceholderBeforeRealTaskIsObserved() {
        val queuedPlaceholder = ExternalFavoriteSyncWorkUi(
            taskId = null,
            createdAt = 1_000,
            state = WorkInfo.State.ENQUEUED,
            pagesSeen = 0,
            maxPages = 3,
            itemsSeen = 0,
            phase = "",
        )
        val finished = ExternalFavoriteSyncWorkUi(
            taskId = 8,
            createdAt = 1_001,
            state = WorkInfo.State.SUCCEEDED,
            pagesSeen = 1,
            maxPages = 3,
            itemsSeen = 90,
            phase = "complete",
        )

        val next = externalFavoriteApplySyncWorkState(
            state = ExternalFavoritesSettingsState(
                syncingSourceId = 42,
                syncWorkBySourceId = mapOf(42L to queuedPlaceholder),
            ),
            sourceId = 42,
            workUi = finished,
        )

        assertEquals(42, next.syncingSourceId)
        assertSame(queuedPlaceholder, next.syncWorkBySourceId[42])
    }
}
