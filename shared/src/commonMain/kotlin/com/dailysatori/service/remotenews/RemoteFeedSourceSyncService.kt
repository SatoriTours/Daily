package com.dailysatori.service.remotenews

import com.dailysatori.data.repository.RemoteFeedSyncStateRepository
import com.dailysatori.service.externalfavorites.sha256Hex
import kotlin.time.TimeSource

/**
 * Resource ceilings of one sync run and of one gap generation. Per-run limits bound a single round;
 * [maxGapPages] and [maxStagedBytes] are cumulative across rounds, so a gap that cannot be closed is
 * eventually blocked instead of crawling the same chain forever.
 */
data class RemoteFeedSyncBudget(
    val maxRequests: Int = 4,
    val maxBytes: Int = 6_000_000,
    val maxDurationMillis: Long = 20_000,
    val maxGapPages: Int = 30,
    val maxStagedBytes: Int = 4_000_000,
    val checkpointSize: Int = 32,
)

/**
 * Persisted state of one subscription. The four facts never overwrite each other:
 * - [checkpointIds] is the confirmed checkpoint: the oldest entries of the entry document that was
 *   current when the last increment was completely closed. It only moves after a closure.
 * - [gapOpen]/[generation]/[scanStartUrl]/[cursorUrl]/[scanPages]/[stagedBytes]/[closureReached] are the
 *   active gap generation and its progress; the target is fixed when the generation opens.
 * - [blocked] records that the generation could not be closed within its cumulative budget.
 */
data class RemoteFeedSyncState(
    val entryUrl: String = "",
    val mode: RemoteFeedPagingMode = RemoteFeedPagingMode.PLAIN,
    val baselineReady: Boolean = false,
    /** Ordered checkpoint: the oldest entries of the entry document at the last complete closure. */
    val checkpointIds: List<Long> = emptyList(),
    /** Ordered target of the active generation, fixed when it opened and never overwritten by a later entry document. */
    val targetEntryIds: List<Long> = emptyList(),
    /** Archive mode: newest archive document that was completely processed. */
    val confirmedAnchorUrl: String? = null,
    val confirmedAt: Long? = null,
    val generation: Long = 0,
    val gapOpen: Boolean = false,
    val scanStartUrl: String? = null,
    val scanStartFingerprint: String? = null,
    val cursorUrl: String? = null,
    val lastPageUrl: String? = null,
    val lastPageFingerprint: String? = null,
    val scanPages: Int = 0,
    val stagedBytes: Int = 0,
    val closureReached: Boolean = false,
    val blocked: Boolean = false,
    val blockedReason: String = "",
)

/** A candidate downloaded outside the entry document; not an article until it is promoted. */
data class RemoteFeedStagedArticle(
    val id: Long,
    val generation: Long,
    val sequence: Int,
    val stale: Boolean,
    val pageUrl: String,
    val article: RemoteArticle,
)

data class RemoteFeedSyncOutcome(
    val inserted: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val failures: List<String> = emptyList(),
    /** The configured entry document was read and stored; the subscription itself is reachable. */
    val entryRead: Boolean = false,
    /** A gap generation is open and still making progress; the next run continues it. */
    val pending: Boolean = false,
    /** The gap could not be closed (cycle, lost boundary, cumulative budget) or candidates are staged. */
    val gap: Boolean = false,
    val blocked: Boolean = false,
    val transient: Boolean = false,
    val requests: Int = 0,
    val stagedArticles: Int = 0,
    val message: String? = null,
    /** State of the first-import history walk after this run. */
    val historyStatus: RemoteFeedHistoryStatus = RemoteFeedHistoryStatus.OFF,
    /** Another bounded history round can continue the same walk. */
    val historyPending: Boolean = false,
    val historyImported: Int = 0,
    val historyMessage: String? = null,
    /** The history round failed on something that may heal later; the task may retry it. */
    val historyTransient: Boolean = false,
    /** The history round failed (never "completed"); the reason is in [historyMessage]. */
    val historyFailed: Boolean = false,
)

/**
 * Syncs one RSS subscription against RFC 5005 documents.
 *
 * The entry document is read and stored on every run, so the newest entries are never starved. Entries
 * from *older* documents are only candidates: they are staged, kept across rounds and promoted into the
 * normal article store (and the existing AI queue) only after the scan provably reaches already
 * confirmed content (paged) or the recorded archive anchor (archived). A gap that cannot be closed within
 * its cumulative budget is reported and blocked instead of quietly importing subscription history.
 */
class RemoteFeedSourceSyncService(
    private val stateRepo: RemoteFeedSyncStateRepository,
    private val articleSync: RemoteArticleSyncService,
    private val remoteNews: RemoteNewsService,
    /** First-import history; null keeps the plain incremental behaviour used by isolated tests. */
    private val historyImport: RemoteFeedHistoryImportService? = null,
) {
    suspend fun sync(
        remoteSourceId: Long,
        entryUrl: String,
        config: RemoteNewsConfigValues,
        sourceDate: String,
        now: Long,
        budget: RemoteFeedSyncBudget = RemoteFeedSyncBudget(),
        onArticleProgress: (suspend (RemoteArticleSyncProgress) -> Unit)? = null,
        /** First-import policy of this subscription; null when the source keeps its current articles. */
        historyPolicy: RemoteFeedHistoryStrategy? = null,
        historyBudget: RemoteFeedHistoryBudget = RemoteFeedHistoryBudget(),
    ): RemoteFeedSyncOutcome = RemoteFeedRun(
        stateRepo = stateRepo,
        articleSync = articleSync,
        remoteNews = remoteNews,
        historyImport = historyImport,
        historyPolicy = historyPolicy,
        historyBudget = historyBudget,
        remoteSourceId = remoteSourceId,
        entryUrl = entryUrl.trim(),
        config = config,
        sourceDate = sourceDate,
        now = now,
        budget = budget,
        onArticleProgress = onArticleProgress,
    ).sync()
}

private sealed interface FeedRead {
    class Page(val page: RemoteFeedPage) : FeedRead
    class Failed(val message: String, val transient: Boolean) : FeedRead
    data object Exhausted : FeedRead
}

private class ReadStop(val message: String)

private class RemoteFeedRun(
    private val stateRepo: RemoteFeedSyncStateRepository,
    private val articleSync: RemoteArticleSyncService,
    private val remoteNews: RemoteNewsService,
    private val historyImport: RemoteFeedHistoryImportService?,
    private val historyPolicy: RemoteFeedHistoryStrategy?,
    private val historyBudget: RemoteFeedHistoryBudget,
    private val remoteSourceId: Long,
    private val entryUrl: String,
    private val config: RemoteNewsConfigValues,
    private val sourceDate: String,
    private val now: Long,
    private val budget: RemoteFeedSyncBudget,
    private val onArticleProgress: (suspend (RemoteArticleSyncProgress) -> Unit)?,
) {
    private val startedAt = TimeSource.Monotonic.markNow()
    private var state = RemoteFeedSyncState(entryUrl = entryUrl)
    private var requests = 0
    private var inserted = 0
    private var updated = 0
    private var skipped = 0
    private var transient = false
    private val failures = mutableListOf<String>()
    private var entryPage: RemoteFeedPage? = null
    private var entryMode: RemoteFeedPagingMode? = null
    private var entryOlderUrl: String? = null
    private var entryImported = 0

    /**
     * One run: the newest document and the incremental catch-up first, then the independent first-import
     * history walk. History never blocks or rewrites the incremental state, and its own per-round budget
     * decides how far it gets; the next run continues from the stored cursor.
     */
    suspend fun sync(): RemoteFeedSyncOutcome {
        val base = incremental()
        if (!base.entryRead) return base
        val history = historyImport ?: return base
        val mode = entryMode ?: return base
        val walked = history.advance(
            remoteSourceId = remoteSourceId,
            entryUrl = entryUrl,
            config = config,
            mode = mode,
            entryOlderUrl = entryOlderUrl,
            autoStrategy = historyPolicy,
            entryImported = entryImported,
            now = now,
            budget = historyBudget,
            onArticleProgress = onArticleProgress,
        )
        return base.copy(
            inserted = base.inserted + walked.imported,
            historyStatus = walked.status,
            historyPending = walked.pending,
            historyImported = walked.imported,
            historyMessage = walked.message,
            historyTransient = walked.transient,
            historyFailed = walked.failed,
        )
    }

    private suspend fun incremental(): RemoteFeedSyncOutcome {
        loadState()
        val entry = read(entryUrl)
        if (entry is FeedRead.Exhausted) return outcome(entryRead = false, message = "本轮请求或时间预算已用尽")
        if (entry is FeedRead.Failed) {
            transient = entry.transient
            return outcome(entryRead = false, message = entry.message)
        }
        val page = (entry as FeedRead.Page).page
        entryPage = page
        val mode = remoteFeedPagingMode(page)
        val entryOlder = remoteFeedFollowableUrl(remoteFeedOlderDocumentUrl(page, mode), entryUrl)
        entryMode = mode
        entryOlderUrl = entryOlder
        val entryBoundary = remoteFeedOverlap(state.checkpointIds, page.articles.map { it.id })?.runStart
            ?: if (mode == RemoteFeedPagingMode.PLAIN) {
                page.articles.indexOfFirst { it.id in state.checkpointIds }.takeIf { it >= 0 }
            } else null
        val entryAboveCheckpoint = if (entryBoundary != null) page.articles.take(entryBoundary) else page.articles
        // The chosen first-import range also caps the entry document itself; a resumed walk never
        // re-truncates new entries, because the baseline is already stored by then.
        val entryArticles = if (historyPolicy != null && !state.baselineReady) {
            historyPolicy.targetCount?.let(entryAboveCheckpoint::take) ?: entryAboveCheckpoint
        } else {
            entryAboveCheckpoint
        }
        val insertedBeforeEntry = inserted
        if (!store(entryArticles, enqueueAi = state.baselineReady)) return outcome(entryRead = true, message = "入口文档有文章未能保存")
        entryImported = inserted - insertedBeforeEntry
        if (state.baselineReady && entryBoundary != null) {
            val mapped = articleSync.refreshMapped(remoteSourceId, sourceDate, page.articles.drop(entryBoundary), now)
            updated += mapped.updated
            failures += mapped.failures
            if (mapped.failures.isNotEmpty()) return outcome(entryRead = true, message = "入口文档已存文章未能更新")
        }
        if (mode == RemoteFeedPagingMode.PLAIN) {
            discardGeneration()
            state = state.copy(
                mode = mode,
                baselineReady = state.baselineReady || entryArticles.isNotEmpty(),
                checkpointIds = when {
                    entryArticles.isEmpty() -> state.checkpointIds
                    state.baselineReady -> checkpointOf(page)
                    else -> checkpointOf(entryArticles)
                },
                targetEntryIds = emptyList(),
                confirmedAnchorUrl = null,
                confirmedAt = now,
            )
            persist()
            return outcome(entryRead = true)
        }
        if (mode != state.mode) {
            // A different document layout invalidates the old cursor, visited set, candidates and the
            // archive anchor: this entry document becomes the new verifiable start.
            discardGeneration()
            state = state.copy(
                mode = mode,
                baselineReady = entryArticles.isNotEmpty(),
                checkpointIds = checkpointOf(entryArticles),
                targetEntryIds = emptyList(),
                confirmedAnchorUrl = entryOlder,
                confirmedAt = now,
            )
            persist()
            return outcome(entryRead = true)
        }
        if (!state.baselineReady) {
            // The first readable entry document is the start point: nothing older is imported.
            discardGeneration()
            state = state.copy(
                baselineReady = entryArticles.isNotEmpty(),
                checkpointIds = checkpointOf(entryArticles),
                targetEntryIds = emptyList(),
                confirmedAnchorUrl = entryOlder,
                confirmedAt = now,
            )
            persist()
            return outcome(entryRead = true)
        }
        if (state.gapOpen && state.closureReached) {
            // The boundary was reached in an earlier round; finish the promotion before anything else.
            // Only the generation's fixed target is confirmed here: the entry document may have grown
            // since, and that newer content was never scanned.
            if (promoteStaged()) closeGeneration(mode, claimCurrentEntry = false) else state = state.copy(closureReached = true)
            persist()
            return outcome(entryRead = true)
        }
        if (state.gapOpen && state.blocked) {
            if (!reopenEvidence(mode, entryOlder)) {
                persist()
                return outcome(entryRead = true)
            }
        }
        if (mode == RemoteFeedPagingMode.ARCHIVE && (entryOlder == null || entryOlder == state.confirmedAnchorUrl)) {
            discardGeneration()
            persist()
            return outcome(entryRead = true)
        }
        if (mode != RemoteFeedPagingMode.ARCHIVE) {
            val entryOverlap = remoteFeedOverlap(state.checkpointIds, page.articles.map { it.id })
            if (entryOverlap != null) {
                // The entry document itself proves continuity with the confirmed region: nothing was missed.
                discardGeneration()
                state = state.copy(checkpointIds = checkpointOf(page), targetEntryIds = emptyList())
                persist()
                return outcome(entryRead = true)
            }
        }
        if (entryOlder == null) {
            block("入口文档没有可遍历的旧文档，缺口无法确认")
            persist()
            return outcome(entryRead = true)
        }
        if (!state.gapOpen) openGeneration(mode, entryOlder, checkpointOf(page))
        prepareScan(mode, entryOlder)?.let { stop ->
            persist()
            return outcome(entryRead = true, message = stop.message)
        }
        scan(mode)
        // The scan started at this entry document's older link, so its own content is proven contiguous.
        if (state.gapOpen && state.closureReached && promoteStaged()) closeGeneration(mode, claimCurrentEntry = true)
        persist()
        return outcome(entryRead = true)
    }

    private fun loadState() {
        val stored = stateRepo.find(remoteSourceId) ?: return
        if (stored.entryUrl != entryUrl) {
            // A reconfigured address starts over; the old candidates must not reach the new source.
            stateRepo.deleteGenerationStaging(remoteSourceId, stored.generation)
            stateRepo.deleteScanNodes(remoteSourceId, stored.generation)
            return
        }
        state = stored
    }

    private fun openGeneration(mode: RemoteFeedPagingMode, startUrl: String, targetEntryIds: List<Long>) {
        stateRepo.deleteGenerationStaging(remoteSourceId, state.generation)
        stateRepo.deleteScanNodes(remoteSourceId, state.generation)
        state = state.copy(
            mode = mode,
            // The target is fixed for the whole generation; a later entry document never widens it.
            targetEntryIds = targetEntryIds,
            gapOpen = true,
            blocked = false,
            blockedReason = "",
            closureReached = false,
            scanStartUrl = startUrl,
            scanStartFingerprint = null,
            cursorUrl = startUrl,
            lastPageUrl = null,
            lastPageFingerprint = null,
            scanPages = 0,
            stagedBytes = 0,
        )
    }

    /** Verifies the stored continuation before trusting it, and re-establishes the scan when unsure. */
    private suspend fun prepareScan(mode: RemoteFeedPagingMode, entryOlder: String): ReadStop? {
        var reestablish = state.scanStartUrl != entryOlder
        val lastUrl = state.lastPageUrl
        if (!reestablish && lastUrl != null && state.cursorUrl != null) {
            when (val check = read(lastUrl)) {
                is FeedRead.Exhausted -> return ReadStop("本轮预算不足以重校验续传位置")
                is FeedRead.Failed -> {
                    transient = transient || check.transient
                    return ReadStop(check.message)
                }
                is FeedRead.Page -> if (fingerprint(check.page) != state.lastPageFingerprint) reestablish = true
            }
        }
        if (reestablish) {
            // Page URLs are not stable document identities: keep the candidates but re-walk from the top.
            stateRepo.markStagedStale(remoteSourceId, state.generation)
            stateRepo.deleteScanNodes(remoteSourceId, state.generation)
            state = state.copy(
                scanStartUrl = entryOlder,
                cursorUrl = entryOlder,
                lastPageUrl = null,
                lastPageFingerprint = null,
                closureReached = false,
                blocked = false,
                blockedReason = "",
            )
        }
        state = state.copy(mode = mode)
        return null
    }

    private suspend fun scan(mode: RemoteFeedPagingMode) {
        val visited = stateRepo.scanNodeUrls(remoteSourceId, state.generation).toMutableSet()
        var cursor = state.cursorUrl ?: return
        while (true) {
            if (visited.contains(cursor)) {
                block("分页或归档链接出现循环，已停止")
                return
            }
            if (state.scanPages >= budget.maxGapPages) {
                block("累计扫描文档数达到上限")
                return
            }
            val read = read(cursor)
            if (read is FeedRead.Exhausted) {
                state = state.copy(cursorUrl = cursor)
                return
            }
            if (read is FeedRead.Failed) {
                transient = transient || read.transient
                failures += read.message
                state = state.copy(cursorUrl = cursor)
                return
            }
            val page = (read as FeedRead.Page).page
            val fingerprint = fingerprint(page)
            stateRepo.recordScanNode(remoteSourceId, state.generation, cursor, fingerprint, now)
            visited += cursor
            state = state.copy(scanPages = state.scanPages + 1, lastPageUrl = cursor, lastPageFingerprint = fingerprint)
            // One ordered judgement for every caller: the page must carry a contiguous run that ends at the
            // confirmed region's bottom. A pinned old entry above the hole, or a single matching id, proves
            // nothing, and the entries below the matched run are never promoted.
            val pageIds = page.articles.map { it.id }
            val overlap = remoteFeedOverlap(state.checkpointIds, pageIds)
            val next = remoteFeedFollowableUrl(remoteFeedOlderDocumentUrl(page, mode), entryUrl)
            val archiveBoundary = mode == RemoteFeedPagingMode.ARCHIVE &&
                (overlap != null || (next != null && next == state.confirmedAnchorUrl))
            val importable = if (mode == RemoteFeedPagingMode.ARCHIVE) {
                page.articles
            } else {
                if (overlap == null) page.articles else page.articles.take(overlap.runStart)
            }
            stage(cursor, importable)
            if (stateRepo.stagedBytes(remoteSourceId, state.generation) > budget.maxStagedBytes) {
                block("暂存候选达到累计上限")
                return
            }
            state = state.copy(stagedBytes = stateRepo.stagedBytes(remoteSourceId, state.generation))
            val closed = if (mode == RemoteFeedPagingMode.ARCHIVE) archiveBoundary else overlap != null
            if (closed) {
                state = state.copy(closureReached = true, stagedBytes = stateRepo.stagedBytes(remoteSourceId, state.generation))
                return
            }
            if (next == null) {
                block("链结束仍未到达已确认边界")
                return
            }
            state = state.copy(cursorUrl = next)
            cursor = next
        }
    }

    private suspend fun reopenEvidence(mode: RemoteFeedPagingMode, entryOlder: String?): Boolean {
        if (entryOlder == null) return false
        val evidence = if (mode == RemoteFeedPagingMode.ARCHIVE) {
            // Only a newly published archive above the blocked one is new linkage; the same chain is not
            // crawled again just because the round is scheduled again.
            entryOlder != state.confirmedAnchorUrl && entryOlder != state.scanStartUrl
        } else {
            when (val check = read(entryOlder)) {
                is FeedRead.Page -> remoteFeedOverlap(state.checkpointIds, check.page.articles.map { it.id }) != null
                is FeedRead.Failed -> {
                    transient = transient || check.transient
                    false
                }
                is FeedRead.Exhausted -> false
            }
        }
        if (!evidence) return false
        // New linkage exists: the cumulative counters may start over, the candidates stay (stale) for dedupe.
        stateRepo.markStagedStale(remoteSourceId, state.generation)
        stateRepo.deleteScanNodes(remoteSourceId, state.generation)
        state = state.copy(
            blocked = false,
            blockedReason = "",
            closureReached = false,
            scanPages = 0,
            scanStartUrl = entryOlder,
            cursorUrl = entryOlder,
            lastPageUrl = null,
            lastPageFingerprint = null,
        )
        return true
    }

    private fun stage(pageUrl: String, articles: List<RemoteArticle>) {
        if (articles.isEmpty()) return
        // Entries that already reached the article store need no candidate row: promoting them again would
        // only rewrite the confirmed article and could move it into the current news window.
        val known = stateRepo.confirmedArticleIds(remoteSourceId, articles.map { it.id })
        val fresh = articles.filterNot { it.id in known }
        if (fresh.isEmpty()) return
        stateRepo.stageArticles(
            remoteSourceId = remoteSourceId,
            generation = state.generation,
            pageUrl = pageUrl,
            sequenceBase = state.scanPages * 1_000_000,
            articles = fresh,
            now = now,
        )
    }

    /** Promotes staged candidates in scan order; a partial failure keeps the rest for an idempotent retry. */
    private suspend fun promoteStaged(): Boolean {
        val rows = stateRepo.stagedArticles(remoteSourceId, state.generation)
        for (row in rows) {
            val result = articleSync.syncSourceArticles(remoteSourceId, sourceDate, listOf(row.article), now, onArticleProgress, enqueueAi = true)
            inserted += result.inserted
            updated += result.updated
            skipped += result.skipped
            failures += result.failures
            if (result.failures.isNotEmpty()) {
                state = state.copy(closureReached = true, stagedBytes = stateRepo.stagedBytes(remoteSourceId, state.generation))
                return false
            }
            stateRepo.deleteStagedArticle(row.id)
        }
        return true
    }

    private fun closeGeneration(mode: RemoteFeedPagingMode, claimCurrentEntry: Boolean) {
        // A scan that ran in this round starts at the current entry document's older link, so that document
        // is proven contiguous. A promotion retry only proves the generation's fixed target.
        val confirmedByTarget = if (claimCurrentEntry) {
            checkpointOf(entryPage ?: return)
        } else {
            state.targetEntryIds.ifEmpty { checkpointOf(entryPage ?: return) }
        }
        state = state.copy(
            baselineReady = true,
            checkpointIds = confirmedByTarget,
            targetEntryIds = emptyList(),
            confirmedAt = now,
            confirmedAnchorUrl = if (mode == RemoteFeedPagingMode.ARCHIVE) state.scanStartUrl else state.confirmedAnchorUrl,
            gapOpen = false,
            closureReached = false,
            blocked = false,
            blockedReason = "",
            scanStartUrl = null,
            scanStartFingerprint = null,
            cursorUrl = null,
            lastPageUrl = null,
            lastPageFingerprint = null,
            scanPages = 0,
            stagedBytes = 0,
        )
        stateRepo.deleteGenerationStaging(remoteSourceId, state.generation)
        stateRepo.deleteScanNodes(remoteSourceId, state.generation)
    }

    private fun discardGeneration() {
        if (!state.gapOpen && !state.blocked && state.scanPages == 0 && state.cursorUrl == null) return
        stateRepo.deleteGenerationStaging(remoteSourceId, state.generation)
        stateRepo.deleteScanNodes(remoteSourceId, state.generation)
        state = state.copy(
            gapOpen = false,
            closureReached = false,
            blocked = false,
            blockedReason = "",
            scanStartUrl = null,
            scanStartFingerprint = null,
            cursorUrl = null,
            lastPageUrl = null,
            lastPageFingerprint = null,
            scanPages = 0,
            stagedBytes = 0,
            targetEntryIds = emptyList(),
        )
    }

    private fun block(reason: String) {
        state = state.copy(blocked = true, blockedReason = reason, closureReached = false)
    }

    private suspend fun store(articles: List<RemoteArticle>, enqueueAi: Boolean): Boolean {
        if (articles.isEmpty()) return true
        val result = articleSync.syncSourceArticles(remoteSourceId, sourceDate, articles, now, onArticleProgress, enqueueAi)
        inserted += result.inserted
        updated += result.updated
        skipped += result.skipped
        failures += result.failures
        return result.failures.isEmpty()
    }

    private suspend fun read(url: String): FeedRead {
        val remainingMillis = budget.maxDurationMillis - startedAt.elapsedNow().inWholeMilliseconds
        if (requests >= budget.maxRequests || remainingMillis <= 0) return FeedRead.Exhausted
        requests += 1
        return when (val result = remoteNews.fetchFeedPage(config, url, remainingMillis, budget.maxBytes)) {
            is RemoteNewsResult.Success -> FeedRead.Page(result.value)
            is RemoteNewsResult.Failure -> FeedRead.Failed(result.message, result.transient)
        }
    }

    private fun checkpointOf(page: RemoteFeedPage): List<Long> = checkpointOf(page.articles)

    private fun checkpointOf(articles: List<RemoteArticle>): List<Long> =
        articles.takeLast(budget.checkpointSize).map { it.id }

    private fun fingerprint(page: RemoteFeedPage): String {
        val ids = page.articles.joinToString(",") { it.id.toString() }
        val older = remoteFeedOlderDocumentUrl(page, remoteFeedPagingMode(page)).orEmpty()
        return sha256Hex(page.url + "|" + ids + "|" + older)
    }

    private suspend fun persist() = stateRepo.save(remoteSourceId, state, now)

    private suspend fun outcome(
        entryRead: Boolean,
        message: String? = null,
    ): RemoteFeedSyncOutcome {
        val staged = stateRepo.stagedArticles(remoteSourceId, state.generation).size
        return RemoteFeedSyncOutcome(
            inserted = inserted,
            updated = updated,
            skipped = skipped,
            failures = failures.toList(),
            entryRead = entryRead,
            pending = entryRead && state.gapOpen && !state.blocked,
            gap = state.blocked || staged > 0,
            blocked = state.blocked,
            transient = transient,
            requests = requests,
            stagedArticles = staged,
            message = message ?: state.blockedReason.takeIf { state.blocked },
        )
    }
}
