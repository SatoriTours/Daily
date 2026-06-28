package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
    private val organizePending: suspend (Long) -> Int = { limit -> organizer?.organizePending(limit) ?: 0 },
    private val organizePendingForSource: suspend (Long, Long) -> Int = { scopedSourceId, limit ->
        organizer?.organizePendingForSource(scopedSourceId, limit) ?: organizePending(limit)
    },
    private val importPendingForSource: (Long, Long) -> Int = { scopedSourceId, limit ->
        importer?.importPendingForSource(scopedSourceId, limit) ?: importPending(limit)
    },
    private val repairImportedArticleCovers: (Long) -> Int = { limit ->
        importer?.repairImportedArticleCovers(limit) ?: 0
    },
    private val repairImportedPlaceholderArticles: (Long) -> Int = { limit ->
        importer?.repairImportedPlaceholderArticles(limit) ?: 0
    },
    private val repairImportedXLongArticlePendingArticles: (Long) -> Int = { limit ->
        importer?.repairImportedXLongArticlePendingArticles(limit) ?: 0
    },
    private val httpLogger: FavoriteSyncHttpLogger = NoopFavoriteSyncHttpLogger,
) {
    private val guards = mutableMapOf<Long, Mutex>()
    private val guardsMutex = Mutex()

    suspend fun syncSource(
        sourceId: Long,
        mode: FavoriteSyncMode,
        taskId: Long? = null,
        onProgress: suspend (FavoriteSyncProgress) -> Unit = {},
    ) {
        sourceGuard(sourceId).withLock {
            syncSourceGuarded(sourceId, mode, taskId, onProgress)
        }
    }

    private suspend fun syncSourceGuarded(
        sourceId: Long,
        mode: FavoriteSyncMode,
        taskId: Long?,
        onProgress: suspend (FavoriteSyncProgress) -> Unit,
    ) {
        val source = sourceRepo.getById(sourceId) ?: error("External favorite source $sourceId was not found")
        if (source.enabled == 0L) {
            sourceRepo.markPaused(sourceId)
            return
        }

        val connector = registry.get(source.provider)
            ?: error("No external favorite connector registered for provider ${source.provider}")
        val policy = syncPolicy(mode, connector.capabilities, source.config_json)
        var result = SyncRunResult(itemsSeen = 0, pagesSeen = 0, changedItems = 0, historyComplete = false)

        sourceRepo.markSyncStarted(sourceId, mode.name)
        try {
            if (policy.shouldFetch) {
                refreshSourceAuth(sourceId, connector)
            }

            if (policy.shouldFetch) {
                result = fetchAndUpsert(sourceId, connector, policy, taskId, onProgress)
            }

            runLocalWork(sourceId, policy, result, taskId, onProgress)
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
    }

    private suspend fun runLocalWork(
        sourceId: Long,
        policy: SyncPolicy,
        result: SyncRunResult,
        taskId: Long?,
        onProgress: suspend (FavoriteSyncProgress) -> Unit,
    ) {
        suspend fun reportLocalProgress(phase: String) {
            onProgress(
                FavoriteSyncProgress(
                    phase = phase,
                    pagesSeen = result.pagesSeen,
                    maxPages = policy.maxPages.coerceAtLeast(1),
                    itemsSeen = result.itemsSeen,
                    historyComplete = false,
                ),
            )
        }
        var localWorkItems = result.changedItems.toLong()
        runLocalWorkStep {
            val importLimit = policy.importLimit(result.changedItems)
            if (importLimit > 0) {
                reportLocalProgress("import")
                localWorkItems += importPendingForSource(sourceId, importLimit)
            }
        }
        runLocalWorkStep {
            reportLocalProgress("repair")
            localWorkItems += repairImportedPlaceholderArticles(IMPORT_RETRY_LIMIT)
        }
        runLocalWorkStep {
            reportLocalProgress("repair")
            localWorkItems += repairImportedXLongArticlePendingArticles(IMPORT_RETRY_LIMIT)
        }
        runLocalWorkStep {
            reportLocalProgress("repair")
            localWorkItems += repairImportedArticleCovers(IMPORT_RETRY_LIMIT)
        }
        runLocalWorkStep {
            val aiBudget = policy.aiBudget(localWorkItems)
            if (aiBudget <= 0) return@runLocalWorkStep
            if (organizer != null) {
                reportLocalProgress("organize")
                organizer.organizePendingForSource(
                    sourceId = sourceId,
                    limit = aiBudget,
                    includeFailed = policy.includeFailedAi,
                    httpLogger = httpLogger,
                    taskId = taskId,
                )
            } else {
                reportLocalProgress("organize")
                organizePendingForSource(sourceId, aiBudget)
            }
        }
        onProgress(
            FavoriteSyncProgress(
                phase = "complete",
                pagesSeen = result.pagesSeen,
                maxPages = policy.maxPages.coerceAtLeast(1),
                itemsSeen = result.itemsSeen,
                historyComplete = result.historyComplete,
            ),
        )
    }

    private suspend fun runLocalWorkStep(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Local import/repair/AI failures should not invalidate the provider fetch.
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
        taskId: Long?,
        onProgress: suspend (FavoriteSyncProgress) -> Unit,
    ): SyncRunResult {
        val capabilities = connector.capabilities
        val initialSource = sourceRepo.getById(sourceId) ?: error("External favorite source $sourceId was not found")
        var progress = readSyncProgress(initialSource.config_json)
        if (policy.resetHistory && progress.historyComplete) {
            progress = ExternalFavoriteSyncProgress(
                historyCursor = null,
                historyComplete = false,
                historyCompleteAnchorCursor = null,
            )
        }
        var pagesSeen = 0
        var itemsSeen = 0
        var changedItems = 0
        var historyCursor = progress.historyCursor
        var historyComplete = progress.historyComplete
        var historyCompleteAnchorCursor = progress.historyCompleteAnchorCursor
        var latestPageAnchorCursor: String? = null
        val sinceExternalId = if (policy.useSinceExternalId) {
            itemRepo.latestNumericExternalIdBySource(sourceId)
        } else {
            null
        }

        fun hasBudget(): Boolean = pagesSeen < policy.maxPages && itemsSeen < policy.maxItems

        fun verifiedHistoryComplete(): Boolean =
            historyComplete && historyCompleteAnchorCursor == latestPageAnchorCursor

        suspend fun reportProgress(phase: String) {
            onProgress(
                FavoriteSyncProgress(
                    phase = phase,
                    pagesSeen = pagesSeen,
                    maxPages = policy.maxPages,
                    itemsSeen = itemsSeen,
                    historyComplete = verifiedHistoryComplete(),
                ),
            )
        }

        fun persistProgress() {
            val latestConfig = sourceRepo.getById(sourceId)?.config_json.orEmpty()
            sourceRepo.updateConfigJson(
                sourceId,
                renderSyncProgressConfig(
                    configJson = latestConfig,
                    progress = ExternalFavoriteSyncProgress(
                        historyCursor = historyCursor,
                        historyComplete = historyComplete,
                        historyCompleteAnchorCursor = historyCompleteAnchorCursor,
                    ),
                ),
            )
        }

        suspend fun fetchOne(cursor: String?): FavoriteFetchPageResult {
            val remaining = policy.maxItems - itemsSeen
            val page = connector.fetchPage(
                source = sourceRepo.getById(sourceId) ?: error("External favorite source $sourceId was not found"),
                cursor = cursor,
                pageSize = policy.pageSize.coerceAtMost(capabilities.maxPageSize).coerceAtMost(remaining).coerceAtLeast(1),
                httpLogger = httpLogger,
                taskId = taskId,
                shouldFetchDetail = { draft -> shouldFetchRemoteDetail(sourceId, draft) },
                sinceExternalId = sinceExternalId,
            )
            pagesSeen += 1

            var changedOnPage = 0
            val pageItems = page.items.take(remaining)
            val reachedSinceAnchor = sinceExternalId != null && pageItems.any { it.externalId == sinceExternalId }
            pageItems.forEach { draft ->
                val existing = itemRepo.getBySourceExternalId(sourceId, draft.externalId)
                if (existing != null && !existing.shouldFetchRemoteDetail()) {
                    itemRepo.markSeen(existing.id, draft.favoritedAt)
                    itemsSeen += 1
                    return@forEach
                }
                val (_, changed) = itemRepo.upsertDraft(sourceId, draft)
                itemsSeen += 1
                if (changed) {
                    changedItems += 1
                    changedOnPage += 1
                }
            }
            return FavoriteFetchPageResult(page = page, changedItems = changedOnPage, reachedSinceAnchor = reachedSinceAnchor)
        }

        if (hasBudget()) {
            val latest = fetchOne(cursor = null)
            val latestAnchorCursor = latest.page.nextCursor
            latestPageAnchorCursor = latestAnchorCursor
            reportProgress("latest")

            fun markHistoryComplete() {
                historyCursor = null
                historyComplete = true
                historyCompleteAnchorCursor = latestAnchorCursor
            }

            fun markHistoryIncomplete(cursor: String?) {
                historyCursor = cursor
                historyComplete = false
                historyCompleteAnchorCursor = null
            }

            suspend fun backfillHistoryFrom(startCursor: String?) {
                var cursor = startCursor
                while (cursor != null && hasBudget()) {
                    val pageResult = fetchOne(cursor)
                    cursor = pageResult.page.nextCursor
                    reportProgress("backfill")
                    if (pageResult.page.exhausted) {
                        markHistoryComplete()
                        persistProgress()
                        reportProgress("complete")
                        return
                    }
                    markHistoryIncomplete(cursor)
                    persistProgress()
                }
                if (cursor == null) {
                    markHistoryComplete()
                    persistProgress()
                    reportProgress("complete")
                } else {
                    markHistoryIncomplete(cursor)
                    persistProgress()
                }
            }

            suspend fun fetchIncrementalUntilKnownItems(startCursor: String?) {
                var cursor = startCursor
                while (cursor != null && hasBudget()) {
                    val pageResult = fetchOne(cursor)
                    cursor = pageResult.page.nextCursor
                    reportProgress("latest")
                    if (pageResult.page.exhausted || pageResult.reachedSinceAnchor || pageResult.changedItems == 0) {
                        markHistoryComplete()
                        persistProgress()
                        reportProgress("complete")
                        return
                    }
                }
                markHistoryComplete()
                persistProgress()
            }

            if (!policy.scanHistory) {
                when {
                    latest.page.exhausted || latest.reachedSinceAnchor || latest.changedItems == 0 -> {
                        markHistoryComplete()
                        persistProgress()
                        reportProgress("complete")
                    }
                    latest.changedItems > 0 -> {
                        fetchIncrementalUntilKnownItems(latest.page.nextCursor)
                    }
                    else -> {
                        persistProgress()
                    }
                }
            } else when {
                latest.page.exhausted -> {
                    markHistoryComplete()
                    persistProgress()
                    reportProgress("complete")
                }
                else -> {
                    val cursor = historyCursor ?: latest.page.nextCursor
                    if (cursor == null) {
                        markHistoryComplete()
                        persistProgress()
                        reportProgress("complete")
                    } else {
                        markHistoryIncomplete(cursor)
                        persistProgress()
                        backfillHistoryFrom(cursor)
                    }
                }
            }
        }

        persistProgress()

        return SyncRunResult(
            itemsSeen = itemsSeen,
            pagesSeen = pagesSeen,
            changedItems = changedItems,
            historyComplete = verifiedHistoryComplete(),
        )
    }

    private suspend fun sourceGuard(sourceId: Long): Mutex =
        guardsMutex.withLock {
            guards.getOrPut(sourceId) { Mutex() }
        }

    private fun shouldFetchRemoteDetail(sourceId: Long, draft: ExternalFavoriteItemDraft): Boolean {
        val existing = itemRepo.getBySourceExternalId(sourceId, draft.externalId) ?: return true
        return existing.shouldFetchRemoteDetail()
    }

    private fun com.dailysatori.shared.db.External_favorite_item.shouldFetchRemoteDetail(): Boolean =
        text.isBlank() ||
            normalized_json.isBlank() ||
            content_hash.isBlank() ||
            ai_input_hash.isBlank()

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
        configJson: String,
    ): SyncPolicy {
        val cappedItems = configuredMaxItemsPerSync(configJson)
            ?.coerceIn(1, capabilities.maxItemsPerRun.coerceAtLeast(1))
            ?: capabilities.maxItemsPerRun.coerceAtLeast(1)
        val cappedPages = capabilities.maxPagesPerRun.coerceAtLeast(1)
        fun itemBudgetPagesFor(pageSize: Int): Int =
            ((cappedItems + pageSize - 1) / pageSize).coerceAtLeast(1)
        return when (mode) {
            FavoriteSyncMode.sync,
            FavoriteSyncMode.recent -> SyncPolicy(
                shouldFetch = true,
                pageSize = 20,
                maxPages = itemBudgetPagesFor(20),
                maxItems = cappedItems,
                scanHistory = false,
                resetHistory = false,
                useSinceExternalId = true,
                importLimit = { changedItems -> maxOf(changedItems.toLong(), IMPORT_RETRY_LIMIT) },
                aiBudget = ::changedItemAiBudget,
                includeFailedAi = false,
            )
            FavoriteSyncMode.history -> SyncPolicy(
                shouldFetch = true,
                pageSize = capabilities.maxPageSize.coerceAtLeast(1),
                maxPages = cappedPages,
                maxItems = cappedItems,
                scanHistory = true,
                resetHistory = false,
                useSinceExternalId = false,
                importLimit = { changedItems -> maxOf(changedItems.toLong(), IMPORT_RETRY_LIMIT) },
                aiBudget = ::changedItemAiBudget,
                includeFailedAi = false,
            )
            FavoriteSyncMode.full_rescan -> SyncPolicy(
                shouldFetch = true,
                pageSize = capabilities.maxPageSize.coerceAtLeast(1),
                maxPages = cappedPages,
                maxItems = cappedItems,
                scanHistory = true,
                resetHistory = true,
                useSinceExternalId = false,
                importLimit = { changedItems -> maxOf(changedItems.toLong(), IMPORT_RETRY_LIMIT) },
                aiBudget = ::changedItemAiBudget,
                includeFailedAi = false,
            )
            FavoriteSyncMode.retry_failed -> SyncPolicy(
                shouldFetch = false,
                pageSize = 0,
                maxPages = 0,
                maxItems = 0,
                scanHistory = false,
                resetHistory = false,
                useSinceExternalId = false,
                importLimit = { IMPORT_RETRY_LIMIT },
                aiBudget = { DEFAULT_AI_ORGANIZE_LIMIT },
                includeFailedAi = true,
            )
        }
    }

    private fun changedItemAiBudget(localWorkItems: Long): Long =
        localWorkItems.coerceAtMost(DEFAULT_AI_ORGANIZE_LIMIT)

    private data class SyncPolicy(
        val shouldFetch: Boolean,
        val pageSize: Int,
        val maxPages: Int,
        val maxItems: Int,
        val scanHistory: Boolean,
        val resetHistory: Boolean,
        val useSinceExternalId: Boolean,
        val importLimit: (changedItems: Int) -> Long,
        val aiBudget: (localWorkItems: Long) -> Long,
        val includeFailedAi: Boolean,
    )

    private data class SyncRunResult(
        val itemsSeen: Int,
        val pagesSeen: Int,
        val changedItems: Int,
        val historyComplete: Boolean,
    )

    private data class FavoriteFetchPageResult(
        val page: FavoriteFetchPage,
        val changedItems: Int,
        val reachedSinceAnchor: Boolean,
    )

    private companion object {
        const val IMPORT_RETRY_LIMIT = 50L
        const val DEFAULT_AI_ORGANIZE_LIMIT = 10L
    }
}

private const val CONFIG_HISTORY_CURSOR = "history_cursor"
private const val CONFIG_HISTORY_COMPLETE = "history_complete"
private const val CONFIG_HISTORY_COMPLETE_ANCHOR_CURSOR = "history_complete_anchor_cursor"
private const val CONFIG_MAX_ITEMS_PER_SYNC = "max_items_per_sync"

private data class ExternalFavoriteSyncProgress(
    val historyCursor: String?,
    val historyComplete: Boolean,
    val historyCompleteAnchorCursor: String?,
)

private fun readSyncProgress(configJson: String): ExternalFavoriteSyncProgress {
    val root = runCatching { Json.parseToJsonElement(configJson).jsonObject }.getOrNull()
    return ExternalFavoriteSyncProgress(
        historyCursor = root
            ?.get(CONFIG_HISTORY_CURSOR)
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() },
        historyComplete = root
            ?.get(CONFIG_HISTORY_COMPLETE)
            ?.jsonPrimitive
            ?.booleanOrNull ?: false,
        historyCompleteAnchorCursor = root
            ?.get(CONFIG_HISTORY_COMPLETE_ANCHOR_CURSOR)
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() },
    )
}

private fun renderSyncProgressConfig(
    configJson: String,
    progress: ExternalFavoriteSyncProgress,
): String {
    val existing = runCatching { Json.parseToJsonElement(configJson).jsonObject }.getOrNull()
    return buildJsonObject {
        existing?.forEach { (key, value) ->
            if (key != CONFIG_HISTORY_CURSOR &&
                key != CONFIG_HISTORY_COMPLETE &&
                key != CONFIG_HISTORY_COMPLETE_ANCHOR_CURSOR
            ) {
                put(key, value)
            }
        }
        progress.historyCursor?.takeIf { it.isNotBlank() }?.let { put(CONFIG_HISTORY_CURSOR, it) }
        put(CONFIG_HISTORY_COMPLETE, progress.historyComplete)
        progress.historyCompleteAnchorCursor
            ?.takeIf { progress.historyComplete && it.isNotBlank() }
            ?.let { put(CONFIG_HISTORY_COMPLETE_ANCHOR_CURSOR, it) }
    }.toString()
}

private fun configuredMaxItemsPerSync(configJson: String): Int? =
    runCatching {
        Json.parseToJsonElement(configJson)
            .jsonObject[CONFIG_MAX_ITEMS_PER_SYNC]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
    }.getOrNull()
