package com.dailysatori.service.remotenews

import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteFeedHistoryRepository
import com.dailysatori.service.externalfavorites.sha256Hex
import kotlin.time.TimeSource

/**
 * Mapping date of every article imported by the history walk. History entries are deliberately outside
 * the daily unified-news window: importing them must never queue a bulk AI summary of old content.
 */
const val REMOTE_FEED_HISTORY_SOURCE_DATE = "history"

/** How much of a subscription's first import is stored: the whole published history or the newest 500. */
enum class RemoteFeedHistoryStrategy(val targetCount: Int?) {
    ALL_HISTORY(null),
    LATEST_500(500);

    val dbValue: String get() = name.lowercase()

    companion object {
        fun ofDbValue(value: String?): RemoteFeedHistoryStrategy? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

/** Stored first-import policy of a subscription; [NONE] keeps an existing source exactly as it was. */
object RemoteFeedHistoryPolicy {
    const val NONE = "none"

    fun strategyOf(policy: String?): RemoteFeedHistoryStrategy? =
        policy?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(NONE, ignoreCase = true) }
            ?.let(RemoteFeedHistoryStrategy::ofDbValue)
}

/**
 * Lifecycle of one history walk. [COMPLETE] means the chain provably ended (or the chosen range was
 * filled); [COMPLETE_WINDOW] means the subscription is a single document with no older material, so a
 * full history is never claimed.
 */
enum class RemoteFeedHistoryStatus {
    OFF,
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETE,
    COMPLETE_WINDOW,
    BLOCKED;

    val dbValue: String get() = name.lowercase()
    val open: Boolean get() = this == PENDING || this == RUNNING
    val finished: Boolean get() = this == COMPLETE || this == COMPLETE_WINDOW

    companion object {
        fun ofDbValue(value: String?): RemoteFeedHistoryStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: OFF
    }
}

data class RemoteFeedHistoryState(
    val entryUrl: String = "",
    val mode: RemoteFeedPagingMode = RemoteFeedPagingMode.PLAIN,
    val strategy: RemoteFeedHistoryStrategy = RemoteFeedHistoryStrategy.ALL_HISTORY,
    val status: RemoteFeedHistoryStatus = RemoteFeedHistoryStatus.OFF,
    /**
     * Ownership token of this walk. Every user control action (start / pause / resume) replaces it, so a
     * run that captured an older token can never persist progress over the newer intent.
     */
    val controlEpoch: Long = 0,
    /** First older document of the walk, fixed when the walk really starts. */
    val startUrl: String? = null,
    /** Next document to read; advanced only after a page was stored successfully. */
    val cursorUrl: String? = null,
    val lastPageUrl: String? = null,
    val lastPageFingerprint: String? = null,
    /** Articles newly stored by this walk, after de-duplication against the article store. */
    val importedCount: Int = 0,
    /** 0 means "the whole chain". */
    val targetCount: Int = 0,
    val pagesRead: Int = 0,
    val requests: Int = 0,
    val lastError: String = "",
    val blockedReason: String = "",
    val startedAt: Long? = null,
    val completedAt: Long? = null,
) {
    val target: Int? get() = targetCount.takeIf { it > 0 }
}

/** Per-run ceiling of the history walk; it is never the cumulative incremental gap ceiling. */
data class RemoteFeedHistoryBudget(
    val maxPages: Int = 8,
    val maxRequests: Int = 9,
    val maxBytes: Int = 4_000_000,
    val maxDurationMillis: Long = 20_000,
)

data class RemoteFeedHistoryOutcome(
    val status: RemoteFeedHistoryStatus = RemoteFeedHistoryStatus.OFF,
    val imported: Int = 0,
    val pages: Int = 0,
    val requests: Int = 0,
    /** True only when another bounded round can safely continue the same walk. */
    val pending: Boolean = false,
    /** The round failed on something that may heal later; the task may use its bounded retry budget. */
    val transient: Boolean = false,
    /** A real failure happened this round; it is never reported as a completed walk. */
    val failed: Boolean = false,
    val message: String? = null,
)

/**
 * Walks the older documents of one subscription once, newest first: paged feeds follow "next", archived
 * feeds follow "prev-archive". The walk is independent from the incremental gap state (a stored gap is
 * still only promoted after closure), persists every page after it was written, de-duplicates against the
 * article store and resumes from its stored cursor after a restart.
 */
class RemoteFeedHistoryImportService(
    private val historyRepo: RemoteFeedHistoryRepository,
    private val articleSyncRepo: RemoteArticleSyncRepository,
    private val articleSync: RemoteArticleSyncService,
    private val remoteNews: RemoteNewsService,
) {
    suspend fun advance(
        remoteSourceId: Long,
        entryUrl: String,
        config: RemoteNewsConfigValues,
        mode: RemoteFeedPagingMode,
        entryOlderUrl: String?,
        autoStrategy: RemoteFeedHistoryStrategy?,
        entryImported: Int,
        now: Long,
        budget: RemoteFeedHistoryBudget = RemoteFeedHistoryBudget(),
        onArticleProgress: (suspend (RemoteArticleSyncProgress) -> Unit)? = null,
    ): RemoteFeedHistoryOutcome {
        // Reconcile address/layout/auto-start in one transaction; a user pause is returned untouched.
        var state = historyRepo.prepareWalk(remoteSourceId, entryUrl, mode, autoStrategy, now)
            ?: return RemoteFeedHistoryOutcome()
        if (state.status == RemoteFeedHistoryStatus.PAUSED ||
            state.status.finished ||
            state.status == RemoteFeedHistoryStatus.BLOCKED ||
            state.status == RemoteFeedHistoryStatus.OFF
        ) {
            return outcomeOf(state)
        }
        val epoch = state.controlEpoch
        val strategy = state.strategy
        if (state.status == RemoteFeedHistoryStatus.PENDING) {
            val start = entryOlderUrl?.takeIf { mode != RemoteFeedPagingMode.PLAIN }
            val imported = maxOf(state.importedCount, entryImported)
            val target = strategy.targetCount ?: 0
            val begun = when {
                // A single window cannot prove that older documents exist, so a full history is never claimed.
                mode == RemoteFeedPagingMode.PLAIN || start == null -> state.copy(
                    status = RemoteFeedHistoryStatus.COMPLETE_WINDOW,
                    entryUrl = entryUrl,
                    importedCount = imported,
                    completedAt = now,
                    lastError = "",
                )
                target > 0 && imported >= target -> state.copy(
                    status = RemoteFeedHistoryStatus.COMPLETE,
                    entryUrl = entryUrl,
                    importedCount = imported,
                    targetCount = target,
                    completedAt = now,
                    lastError = "",
                )
                else -> state.copy(
                    entryUrl = entryUrl,
                    mode = mode,
                    status = RemoteFeedHistoryStatus.RUNNING,
                    // A resumed walk keeps its stored start, cursor and last page verification; only a
                    // fresh walk begins at the entry document's older link.
                    startUrl = state.startUrl ?: start,
                    cursorUrl = state.cursorUrl ?: start,
                    importedCount = imported,
                    targetCount = target,
                    lastError = "",
                    blockedReason = "",
                    startedAt = state.startedAt ?: now,
                    completedAt = null,
                )
            }
            if (!historyRepo.compareAndSetProgress(remoteSourceId, epoch, begun, now)) {
                // The user paused or restarted between the reconciliation and this write.
                return outcomeOf(historyRepo.find(remoteSourceId))
            }
            state = begun
        }

        val startedAt = TimeSource.Monotonic.markNow()
        var requests = 0
        var pages = 0
        var pageBytes = 0
        var importedThisRound = 0
        var message: String? = null
        val visited = mutableSetOf<String>()

        // The single ownership check: every request and every progress write is fenced by this epoch.
        suspend fun stillOwned(): RemoteFeedHistoryState? =
            historyRepo.find(remoteSourceId)
                ?.takeIf { it.controlEpoch == epoch && it.status == RemoteFeedHistoryStatus.RUNNING }

        suspend fun stopHere(): RemoteFeedHistoryOutcome =
            outcomeOf(historyRepo.find(remoteSourceId), importedThisRound, pages, requests)

        suspend fun persist(next: RemoteFeedHistoryState): Boolean =
            historyRepo.compareAndSetProgress(remoteSourceId, epoch, next, now)

        // Page addresses are not stable identities: verify the stored continuation before trusting it.
        val resumeUrl = state.lastPageUrl
        if (resumeUrl != null && state.cursorUrl != null) {
            if (stillOwned() == null) return stopHere()
            when (val check = read(config, resumeUrl, budget, startedAt, requests, pageBytes)) {
                is HistoryRead.Exhausted -> {
                    return outcomeOf(state, message = "本轮历史预算已用尽，稍后继续", pending = false)
                }
                is HistoryRead.Failed -> {
                    // A failed page is never skipped; the cursor stays and the same page is retried later.
                    state = failureState(state, check)
                    persist(state)
                    return outcomeOf(
                        historyRepo.find(remoteSourceId),
                        importedThisRound, pages, requests,
                        message = check.message,
                        transient = check.transient,
                        failed = true,
                    )
                }
                is HistoryRead.Page -> {
                    requests += 1
                    if (fingerprint(check.page) != state.lastPageFingerprint) {
                        // The publisher shifted entries between pages: re-walk from the top, dedupe does the rest.
                        state = state.copy(
                            cursorUrl = state.startUrl,
                            lastPageUrl = null,
                            lastPageFingerprint = null,
                        )
                    }
                }
            }
        }

        var cursor = state.cursorUrl ?: state.startUrl
        while (cursor != null) {
            if (!visited.add(cursor)) {
                val blocked = state.copy(
                    status = RemoteFeedHistoryStatus.BLOCKED,
                    blockedReason = "历史分页出现循环，已停止",
                    lastError = "",
                )
                persist(blocked)
                return outcomeOf(
                    historyRepo.find(remoteSourceId),
                    importedThisRound, pages, requests,
                    message = blocked.blockedReason,
                    failed = true,
                )
            }
            if (stillOwned() == null) return stopHere()
            if (pages >= budget.maxPages || requests >= budget.maxRequests ||
                startedAt.elapsedNow().inWholeMilliseconds >= budget.maxDurationMillis
            ) {
                message = "本轮历史分页预算已用尽，稍后继续"
                break
            }
            val read = read(config, cursor, budget, startedAt, requests, pageBytes)
            when (read) {
                is HistoryRead.Exhausted -> {
                    message = "本轮历史预算已用尽，稍后继续"
                    break
                }
                is HistoryRead.Failed -> {
                    state = failureState(state, read)
                    persist(state)
                    return outcomeOf(
                        historyRepo.find(remoteSourceId),
                        importedThisRound, pages, requests,
                        message = read.message,
                        transient = read.transient,
                        failed = true,
                    )
                }
                is HistoryRead.Page -> {
                    requests += 1
                    pages += 1
                    pageBytes += read.page.bytes
                    val page = read.page
                    val next = remoteFeedFollowableUrl(remoteFeedOlderDocumentUrl(page, mode), entryUrl)
                    if (next != null && next == cursor) {
                        val blocked = state.copy(
                            status = RemoteFeedHistoryStatus.BLOCKED,
                            blockedReason = "历史分页链接指向自身，已停止",
                            lastError = "",
                        )
                        persist(blocked)
                        return outcomeOf(
                            historyRepo.find(remoteSourceId),
                            importedThisRound, pages, requests,
                            message = blocked.blockedReason,
                            failed = true,
                        )
                    }
                    val known = articleSyncRepo.knownRemoteArticleIds(remoteSourceId, page.articles.map { it.id })
                    val fresh = page.articles.filterNot { it.id in known }
                    val remaining = strategy.targetCount?.let { (it - state.importedCount).coerceAtLeast(0) }
                    val batch = remaining?.let(fresh::take) ?: fresh
                    val stored = if (batch.isEmpty()) {
                        null
                    } else {
                        articleSync.syncSourceArticles(
                            remoteSourceId = remoteSourceId,
                            sourceDate = REMOTE_FEED_HISTORY_SOURCE_DATE,
                            articles = batch,
                            now = now,
                            onArticleProgress = onArticleProgress,
                        )
                    }
                    val storedCount = stored?.inserted ?: 0
                    importedThisRound += storedCount
                    val readPage = state.copy(
                        importedCount = state.importedCount + storedCount,
                        pagesRead = state.pagesRead + 1,
                        requests = state.requests + 1,
                    )
                    if (stored != null && stored.failures.isNotEmpty()) {
                        // The committed articles stay counted; the cursor does not move, so the same page is
                        // retried and the already stored entries are de-duplicated.
                        state = readPage.copy(lastError = stored.failures.joinToString("；"))
                        persist(state)
                        return outcomeOf(
                            historyRepo.find(remoteSourceId),
                            importedThisRound, pages, requests,
                            message = "历史文章保存失败，稍后继续",
                            // A storage failure may heal by waiting; the bounded task retry covers it.
                            transient = true,
                            failed = true,
                        )
                    }
                    val advanced = readPage.copy(
                        cursorUrl = next,
                        lastPageUrl = cursor,
                        lastPageFingerprint = fingerprint(page),
                        lastError = "",
                    )
                    val finished = when {
                        strategy.targetCount?.let { advanced.importedCount >= it } == true ->
                            advanced.copy(status = RemoteFeedHistoryStatus.COMPLETE, completedAt = now)
                        next == null -> advanced.copy(
                            // Only an explicit chain end completes a walk; a budget stop never does.
                            status = if (mode == RemoteFeedPagingMode.PLAIN) {
                                RemoteFeedHistoryStatus.COMPLETE_WINDOW
                            } else {
                                RemoteFeedHistoryStatus.COMPLETE
                            },
                            completedAt = now,
                        )
                        else -> null
                    }
                    if (finished != null) {
                        persist(finished)
                        return outcomeOf(
                            historyRepo.find(remoteSourceId),
                            importedThisRound, pages, requests,
                        )
                    }
                    // The cursor advances only after this page was written; a rejected write means the user
                    // paused or restarted while the page was in flight, so the walk stops right here.
                    if (!persist(advanced)) {
                        return outcomeOf(
                            historyRepo.find(remoteSourceId),
                            importedThisRound, pages, requests,
                        )
                    }
                    state = advanced
                    cursor = next
                }
            }
        }

        // Round end: the ownership check keeps the counters of a walk the user already paused.
        if (!persist(state)) {
            return outcomeOf(historyRepo.find(remoteSourceId), importedThisRound, pages, requests)
        }
        return outcomeOf(
            state = state,
            imported = importedThisRound,
            pages = pages,
            requests = requests,
            message = message,
            // Only a bounded round that really stored pages may continue automatically; a failure stays
            // open but waits for the next scheduled or manual run instead of retrying in a tight loop.
            pending = state.status == RemoteFeedHistoryStatus.RUNNING && pages > 0 && state.lastError.isEmpty(),
        )
    }

    /**
     * One failure keeps the walk recoverable: a transient page is retried by the ordinary bounded task
     * retry, while a permanent one (credentials, permission, format) blocks the walk with its real reason
     * and waits for an explicit user resume.
     */
    private fun failureState(state: RemoteFeedHistoryState, read: HistoryRead.Failed): RemoteFeedHistoryState =
        if (read.transient) {
            state.copy(lastError = read.message)
        } else {
            state.copy(status = RemoteFeedHistoryStatus.BLOCKED, blockedReason = read.message, lastError = read.message)
        }

    private sealed interface HistoryRead {
        class Page(val page: RemoteFeedPage) : HistoryRead
        class Failed(val message: String, val transient: Boolean) : HistoryRead
        data object Exhausted : HistoryRead
    }

    private suspend fun read(
        config: RemoteNewsConfigValues,
        url: String,
        budget: RemoteFeedHistoryBudget,
        startedAt: TimeSource.Monotonic.ValueTimeMark,
        requests: Int,
        consumedBytes: Int,
    ): HistoryRead {
        val remainingMillis = budget.maxDurationMillis - startedAt.elapsedNow().inWholeMilliseconds
        val remainingBytes = budget.maxBytes - consumedBytes
        if (requests >= budget.maxRequests || remainingMillis <= 0 || remainingBytes <= 0) return HistoryRead.Exhausted
        return when (val result = remoteNews.fetchFeedPage(config, url, remainingMillis, remainingBytes)) {
            is RemoteNewsResult.Success -> HistoryRead.Page(result.value)
            is RemoteNewsResult.Failure -> HistoryRead.Failed(result.message, result.transient)
        }
    }

    private fun fingerprint(page: RemoteFeedPage): String {
        val ids = page.articles.joinToString(",") { it.id.toString() }
        val older = remoteFeedOlderDocumentUrl(page, remoteFeedPagingMode(page)).orEmpty()
        return sha256Hex(page.url + "|" + ids + "|" + older)
    }

    private fun outcomeOf(
        state: RemoteFeedHistoryState?,
        imported: Int = 0,
        pages: Int = 0,
        requests: Int = 0,
        message: String? = null,
        pending: Boolean = false,
        transient: Boolean = false,
        failed: Boolean = false,
    ): RemoteFeedHistoryOutcome = RemoteFeedHistoryOutcome(
        status = state?.status ?: RemoteFeedHistoryStatus.OFF,
        imported = imported,
        pages = pages,
        requests = requests,
        pending = pending,
        transient = transient,
        failed = failed,
        message = message ?: state?.blockedReason?.takeIf { state.status == RemoteFeedHistoryStatus.BLOCKED },
    )
}

