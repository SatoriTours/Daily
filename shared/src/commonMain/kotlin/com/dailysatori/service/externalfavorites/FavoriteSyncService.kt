package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FavoriteSyncService(
    private val sourceRepo: ExternalFavoriteSourceRepository,
    private val itemRepo: ExternalFavoriteItemRepository,
    private val registry: FavoriteConnectorRegistry,
    private val importer: ExternalFavoriteImporter? = null,
    private val organizer: ExternalFavoriteAiOrganizer? = null,
    private val importPending: (Long) -> Int = { limit -> importer?.importPending(limit) ?: 0 },
    private val organizePending: (Long) -> Int = { limit -> organizer?.organizePending(limit) ?: 0 },
    private val importPendingForSource: (Long, Long) -> Int = { scopedSourceId, limit ->
        importer?.importPendingForSource(scopedSourceId, limit) ?: importPending(limit)
    },
) {
    private val guards = mutableMapOf<Long, Mutex>()
    private val guardsMutex = Mutex()

    suspend fun syncSource(sourceId: Long, mode: FavoriteSyncMode) {
        sourceGuard(sourceId).withLock {
            syncSourceGuarded(sourceId, mode)
        }
    }

    private suspend fun syncSourceGuarded(sourceId: Long, mode: FavoriteSyncMode) {
        val source = sourceRepo.getById(sourceId) ?: error("External favorite source $sourceId was not found")
        val connector = registry.get(source.provider)
            ?: error("No external favorite connector registered for provider ${source.provider}")

        sourceRepo.markSyncStarted(sourceId, mode.name)
        try {
            val result = if (mode == FavoriteSyncMode.retry_failed) {
                SyncRunResult(itemsSeen = 0, pagesSeen = 0, changedItems = 0)
            } else {
                fetchAndUpsert(sourceId, connector, mode)
            }

            val importLimit = if (mode == FavoriteSyncMode.retry_failed) {
                IMPORT_RETRY_LIMIT
            } else {
                result.changedItems.toLong()
            }
            if (importLimit > 0) {
                importPendingForSource(sourceId, importLimit)
            }
            organizePending(AI_ORGANIZE_LIMIT)
            sourceRepo.markSyncSucceeded(
                id = sourceId,
                itemsSeen = result.itemsSeen.toLong(),
                pagesSeen = result.pagesSeen.toLong(),
            )
        } catch (error: Throwable) {
            val status = error.syncFailureStatus()
            sourceRepo.markSyncFailed(
                id = sourceId,
                code = error.syncFailureCode(),
                message = error.message.orEmpty().ifBlank { "External favorite sync failed." },
                status = status.name,
            )
            throw error
        }
    }

    private suspend fun fetchAndUpsert(
        sourceId: Long,
        connector: FavoriteConnector,
        mode: FavoriteSyncMode,
    ): SyncRunResult {
        val capabilities = connector.capabilities
        val maxPages = capabilities.maxPagesPerRun.coerceAtLeast(1)
        val maxItems = capabilities.maxItemsPerRun.coerceAtLeast(1)
        var cursor: String? = null
        var pagesSeen = 0
        var itemsSeen = 0
        var changedItems = 0

        while (pagesSeen < maxPages && itemsSeen < maxItems) {
            val remaining = maxItems - itemsSeen
            val page = connector.fetchPage(
                source = sourceRepo.getById(sourceId) ?: error("External favorite source $sourceId was not found"),
                cursor = cursor,
                pageSize = capabilities.maxPageSize.coerceAtMost(remaining).coerceAtLeast(1),
            )
            pagesSeen += 1

            var pageChangedItems = 0
            page.items.take(remaining).forEach { draft ->
                val (_, changed) = itemRepo.upsertDraft(sourceId, draft)
                itemsSeen += 1
                if (changed) {
                    changedItems += 1
                    pageChangedItems += 1
                }
            }

            cursor = page.nextCursor
            if (page.exhausted || itemsSeen >= maxItems || (mode == FavoriteSyncMode.recent && pageChangedItems == 0)) {
                break
            }
        }

        return SyncRunResult(
            itemsSeen = itemsSeen,
            pagesSeen = pagesSeen,
            changedItems = changedItems,
        )
    }

    private suspend fun sourceGuard(sourceId: Long): Mutex =
        guardsMutex.withLock {
            guards.getOrPut(sourceId) { Mutex() }
        }

    private fun Throwable.syncFailureStatus(): ExternalSourceStatus = when (this) {
        is XFavoriteAuthException -> ExternalSourceStatus.auth_required
        is XFavoriteRateLimitException -> ExternalSourceStatus.rate_limited
        else -> ExternalSourceStatus.failed
    }

    private fun Throwable.syncFailureCode(): String = when (this) {
        is XFavoriteAuthException -> "auth_failed"
        is XFavoriteRateLimitException -> "rate_limited"
        is XFavoriteProviderException -> "provider_${statusCode}"
        else -> "sync_failed"
    }

    private data class SyncRunResult(
        val itemsSeen: Int,
        val pagesSeen: Int,
        val changedItems: Int,
    )

    private companion object {
        const val IMPORT_RETRY_LIMIT = 50L
        const val AI_ORGANIZE_LIMIT = 10L
    }
}
