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
    private val organizePending: (Long) -> Int = { limit -> organizer?.organizePending(limit) ?: 0 },
    private val importPendingForSource: (Long, Long) -> Int = { scopedSourceId, limit ->
        importer?.importPendingForSource(scopedSourceId, limit) ?: importPending(limit)
    },
    private val repairImportedArticleCovers: (Long) -> Int = { limit ->
        importer?.repairImportedArticleCovers(limit) ?: 0
    },
    private val repairImportedPlaceholderArticles: (Long) -> Int = { limit ->
        importer?.repairImportedPlaceholderArticles(limit) ?: 0
    },
) {
    private val guards = mutableMapOf<Long, Mutex>()
    private val guardsMutex = Mutex()

    suspend fun syncSource(
        sourceId: Long,
        mode: FavoriteSyncMode,
        onProgress: suspend (FavoriteSyncProgress) -> Unit = {},
    ) {
        sourceGuard(sourceId).withLock {
            syncSourceGuarded(sourceId, mode, onProgress)
        }
    }

    private suspend fun syncSourceGuarded(
        sourceId: Long,
        mode: FavoriteSyncMode,
        onProgress: suspend (FavoriteSyncProgress) -> Unit,
    ) {
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
                result = fetchAndUpsert(sourceId, connector, policy, onProgress)
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
        runCatching {
            repairImportedArticleCovers(IMPORT_RETRY_LIMIT)
        }
        runCatching {
            repairImportedPlaceholderArticles(IMPORT_RETRY_LIMIT)
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
        onProgress: suspend (FavoriteSyncProgress) -> Unit,
    ): SyncRunResult {
        val capabilities = connector.capabilities
        val initialSource = sourceRepo.getById(sourceId) ?: error("External favorite source $sourceId was not found")
        var progress = readSyncProgress(initialSource.config_json)
        var pagesSeen = 0
        var itemsSeen = 0
        var changedItems = 0
        var historyCursor = progress.historyCursor
        var historyComplete = progress.historyComplete

        fun hasBudget(): Boolean = pagesSeen < policy.maxPages && itemsSeen < policy.maxItems

        suspend fun reportProgress(phase: String) {
            onProgress(
                FavoriteSyncProgress(
                    phase = phase,
                    pagesSeen = pagesSeen,
                    maxPages = policy.maxPages,
                    itemsSeen = itemsSeen,
                    historyComplete = historyComplete,
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
                    ),
                ),
            )
        }

        suspend fun fetchOne(cursor: String?): FavoriteFetchPageResult {
            val remaining = policy.maxItems - itemsSeen
            val page = connector.fetchPage(
                source = sourceRepo.getById(sourceId) ?: error("External favorite source $sourceId was not found"),
                cursor = cursor,
                pageSize = capabilities.maxPageSize.coerceAtMost(remaining).coerceAtLeast(1),
            )
            pagesSeen += 1

            var changedOnPage = 0
            page.items.take(remaining).forEach { draft ->
                val (_, changed) = itemRepo.upsertDraft(sourceId, draft)
                itemsSeen += 1
                if (changed) {
                    changedItems += 1
                    changedOnPage += 1
                }
            }
            return FavoriteFetchPageResult(page = page, changedItems = changedOnPage)
        }

        if (hasBudget()) {
            val latest = fetchOne(cursor = null)
            reportProgress("latest")
            when {
                latest.page.exhausted -> {
                    historyCursor = null
                    historyComplete = true
                    persistProgress()
                    reportProgress("complete")
                }
                latest.changedItems == 0 -> {
                    if (!historyComplete && historyCursor != null && hasBudget()) {
                        var cursor = historyCursor
                        while (cursor != null && hasBudget()) {
                            val pageResult = fetchOne(cursor)
                            cursor = pageResult.page.nextCursor
                            reportProgress("backfill")
                            if (pageResult.page.exhausted) {
                                historyCursor = null
                                historyComplete = true
                                persistProgress()
                                reportProgress("complete")
                                break
                            }
                            historyCursor = cursor
                            historyComplete = false
                            persistProgress()
                        }
                    } else if (!historyComplete && historyCursor == null) {
                        historyCursor = latest.page.nextCursor
                        historyComplete = latest.page.nextCursor == null
                        persistProgress()
                        reportProgress(if (historyComplete) "complete" else "backfill")
                    }
                }
                else -> {
                    var cursor = latest.page.nextCursor
                    historyCursor = cursor
                    historyComplete = false
                    persistProgress()
                    while (cursor != null && hasBudget()) {
                        val pageResult = fetchOne(cursor)
                        cursor = pageResult.page.nextCursor
                        reportProgress("latest")
                        if (pageResult.page.exhausted) {
                            historyCursor = null
                            historyComplete = true
                            persistProgress()
                            reportProgress("complete")
                            break
                        }
                        historyCursor = cursor
                        historyComplete = false
                        persistProgress()
                        if (policy.earlyStopOnUnchanged && pageResult.changedItems == 0) {
                            break
                        }
                    }
                    if (cursor != null && !historyComplete) {
                        historyCursor = cursor
                        persistProgress()
                    }
                }
            }
        }

        persistProgress()

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
            FavoriteSyncMode.sync,
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
                earlyStopOnUnchanged = true,
                importLimit = { changedItems -> changedItems.toLong() },
                aiBudget = DEFAULT_AI_ORGANIZE_LIMIT,
            )
            FavoriteSyncMode.full_rescan -> SyncPolicy(
                shouldFetch = true,
                maxPages = cappedPages,
                maxItems = cappedItems,
                earlyStopOnUnchanged = true,
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

    private data class FavoriteFetchPageResult(
        val page: FavoriteFetchPage,
        val changedItems: Int,
    )

    private companion object {
        const val IMPORT_RETRY_LIMIT = 50L
        const val DEFAULT_AI_ORGANIZE_LIMIT = 10L
    }
}

private const val CONFIG_HISTORY_CURSOR = "history_cursor"
private const val CONFIG_HISTORY_COMPLETE = "history_complete"

private data class ExternalFavoriteSyncProgress(
    val historyCursor: String?,
    val historyComplete: Boolean,
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
    )
}

private fun renderSyncProgressConfig(
    configJson: String,
    progress: ExternalFavoriteSyncProgress,
): String {
    val existing = runCatching { Json.parseToJsonElement(configJson).jsonObject }.getOrNull()
    return buildJsonObject {
        existing?.forEach { (key, value) ->
            if (key != CONFIG_HISTORY_CURSOR && key != CONFIG_HISTORY_COMPLETE) {
                put(key, value)
            }
        }
        progress.historyCursor?.takeIf { it.isNotBlank() }?.let { put(CONFIG_HISTORY_CURSOR, it) }
        put(CONFIG_HISTORY_COMPLETE, progress.historyComplete)
    }.toString()
}
