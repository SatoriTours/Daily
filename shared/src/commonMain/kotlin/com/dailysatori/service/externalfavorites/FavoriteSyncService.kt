package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import kotlinx.coroutines.CancellationException
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
        if (source.enabled == 0L) {
            sourceRepo.markPaused(sourceId)
            return
        }

        val connector = registry.get(source.provider)
            ?: error("No external favorite connector registered for provider ${source.provider}")
        val policy = syncPolicy(mode, connector.capabilities)
        var result = SyncRunResult(itemsSeen = 0, pagesSeen = 0, changedItems = 0)

        sourceRepo.markSyncStarted(sourceId, mode.name)
        try {
            if (policy.shouldFetch) {
                refreshSourceAuth(sourceId, connector)
            }

            if (policy.shouldFetch) {
                result = fetchAndUpsert(sourceId, connector, policy)
            }

            sourceRepo.markSyncSucceeded(
                id = sourceId,
                itemsSeen = result.itemsSeen.toLong(),
                pagesSeen = result.pagesSeen.toLong(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val status = error.syncFailureStatus()
            sourceRepo.markSyncFailed(
                id = sourceId,
                code = error.syncFailureCode(),
                message = error.message.orEmpty().ifBlank { "External favorite sync failed." },
                status = status.name,
                rateLimitResetAt = error.syncFailureRateLimitResetAt(),
            )
            throw error
        }

        runLocalWork(sourceId, policy, result)
    }

    private fun runLocalWork(sourceId: Long, policy: SyncPolicy, result: SyncRunResult) {
        runCatching {
            val importLimit = policy.importLimit(result.changedItems)
            if (importLimit > 0) {
                importPendingForSource(sourceId, importLimit)
            }
        }
        runCatching {
            organizePending(policy.aiBudget)
        }
    }

    private suspend fun refreshSourceAuth(sourceId: Long, connector: FavoriteConnector) {
        val current = sourceRepo.getById(sourceId) ?: error("External favorite source $sourceId was not found")
        val refreshed = connector.refreshAuth(current)
        if (refreshed.auth_json != current.auth_json) {
            sourceRepo.updateAuthJson(sourceId, refreshed.auth_json)
        }
    }

    private suspend fun fetchAndUpsert(
        sourceId: Long,
        connector: FavoriteConnector,
        policy: SyncPolicy,
    ): SyncRunResult {
        val capabilities = connector.capabilities
        var cursor: String? = null
        var pagesSeen = 0
        var itemsSeen = 0
        var changedItems = 0

        while (pagesSeen < policy.maxPages && itemsSeen < policy.maxItems) {
            val remaining = policy.maxItems - itemsSeen
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
            if (page.exhausted || itemsSeen >= policy.maxItems || (policy.earlyStopOnUnchanged && pageChangedItems == 0)) {
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

    private fun Throwable.syncFailureRateLimitResetAt(): Long? = when (this) {
        is XFavoriteRateLimitException -> rateLimitResetAt
        else -> null
    }

    private fun syncPolicy(
        mode: FavoriteSyncMode,
        capabilities: FavoriteConnectorCapabilities,
    ): SyncPolicy {
        val cappedPages = capabilities.maxPagesPerRun.coerceAtLeast(1)
        val cappedItems = capabilities.maxItemsPerRun.coerceAtLeast(1)
        return when (mode) {
            FavoriteSyncMode.recent -> SyncPolicy(
                shouldFetch = true,
                maxPages = cappedPages,
                maxItems = cappedItems,
                earlyStopOnUnchanged = true,
                importLimit = { changedItems -> changedItems.toLong() },
                aiBudget = DEFAULT_AI_ORGANIZE_LIMIT,
            )
            FavoriteSyncMode.history -> SyncPolicy(
                shouldFetch = true,
                maxPages = cappedPages,
                maxItems = cappedItems,
                earlyStopOnUnchanged = false,
                importLimit = { changedItems -> changedItems.toLong() },
                aiBudget = DEFAULT_AI_ORGANIZE_LIMIT,
            )
            FavoriteSyncMode.full_rescan -> SyncPolicy(
                shouldFetch = true,
                maxPages = cappedPages,
                maxItems = cappedItems,
                earlyStopOnUnchanged = false,
                importLimit = { changedItems -> changedItems.toLong() },
                aiBudget = DEFAULT_AI_ORGANIZE_LIMIT,
            )
            FavoriteSyncMode.retry_failed -> SyncPolicy(
                shouldFetch = false,
                maxPages = 0,
                maxItems = 0,
                earlyStopOnUnchanged = false,
                importLimit = { IMPORT_RETRY_LIMIT },
                aiBudget = DEFAULT_AI_ORGANIZE_LIMIT,
            )
        }
    }

    private data class SyncPolicy(
        val shouldFetch: Boolean,
        val maxPages: Int,
        val maxItems: Int,
        val earlyStopOnUnchanged: Boolean,
        val importLimit: (changedItems: Int) -> Long,
        val aiBudget: Long,
    )

    private data class SyncRunResult(
        val itemsSeen: Int,
        val pagesSeen: Int,
        val changedItems: Int,
    )

    private companion object {
        const val IMPORT_RETRY_LIMIT = 50L
        const val DEFAULT_AI_ORGANIZE_LIMIT = 10L
    }
}
