package com.dailysatori.service.opportunity

import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLevel
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.ai.AiQueueWaitTimeout
import com.dailysatori.service.ai.withAiCallDeadline
import com.dailysatori.service.externalfavorites.sha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class NewsOpportunityService(
    private val store: NewsOpportunityStore,
    private val analyzer: OpportunityAnalyzer,
    private val context: NewsOpportunityContext,
    private val candidateSource: OpportunityCandidateSource? = null,
    private val analysisTimeoutMs: Long = 90_000L,
) {
    // Only batches queue behind network work. All archive and UI-state writes belong to stateLock.
    private val analysisExecution = Mutex()
    private val stateLock = Mutex()
    private var archive = OpportunityArchive()
    private var inputRevision = 0L
    private var loaded = false
    private val _state = MutableStateFlow(OpportunityState())
    val state = _state.asStateFlow()

    suspend fun refresh() = stateLock.withLock {
        replaceArchive(store.load())
        loaded = true
        publish()
    }

    suspend fun markRead(article: ReadNewsArticle) = stateLock.withLock {
        ensureLoaded()
        require(article.key.isNotBlank() && article.title.isNotBlank() && article.content.isNotBlank()) { "Readable article is required" }
        val identity = article.identity()
        store.updateRead(identity, article)
        replaceArchive(archive.copy(articles = archive.articles.filterNot { it.identity() == identity } + article))
        publish()
    }

    suspend fun markUnread(key: String) = stateLock.withLock {
        ensureLoaded()
        // Unmarking changes the explicit read list, not candidates or existing analysis.
        val identity = key.trim()
        store.updateRead(identity, null)
        replaceArchive(archive.copy(articles = archive.articles.filterNot { it.identity() == identity }))
        publish()
    }

    suspend fun saveFocus(text: String) = stateLock.withLock {
        ensureLoaded()
        require(text.length <= MAX_FOCUS_LENGTH) { "关注点不能超过 2,000 字" }
        saveState(archive.copy(focus = text.trim()))
        publish()
    }

    suspend fun clearError(taskId: Long?) = stateLock.withLock {
        ensureLoaded()
        saveState(archive.copy(lastError = null, dismissedErrorTaskId = taskId))
        publish()
    }

    suspend fun setSaved(id: String, saved: Boolean) = updateItem(id) {
        it.copy(saved = saved, savedAt = if (saved) it.savedAt ?: Clock.System.now().toEpochMilliseconds() else null)
    }

    suspend fun setIgnored(id: String, ignored: Boolean) = updateItem(id) { it.copy(ignored = ignored) }

    suspend fun linkReminder(id: String, reminderId: String?) = updateItem(id) {
        it.copy(reminderId = reminderId?.takeIf(String::isNotBlank))
    }

    suspend fun analyze(
        automatic: Boolean = false,
        onProgress: suspend (Int, Int, String) -> Unit = { _, _, _ -> },
    ): OpportunityAnalysisOutcome = analysisExecution.withLock {
        val initial = stateLock.withLock begin@{
            ensureLoaded()
            val snapshot = currentContext()
            if (automatic && shouldDeferAutomaticAnalysis(snapshot)) {
                publish(snapshot)
                return@begin null
            }
            val batch = AnalysisBatch(pendingArticles(snapshot))
            publishUpdating("正在查找适合你的文章", snapshot)
            snapshot to batch
        } ?: return@withLock OpportunityAnalysisOutcome()
        val (snapshot, batch) = initial
        try {
            check((candidateSource == null && batch.pending.isEmpty()) || snapshot.focus.isNotBlank() || !snapshot.thoughts.isNullOrBlank())
            val current = loadCandidates(snapshot)
            stateLock.withLock {
                checkAnalysisContext(current)
                batch.pending = pendingArticles(current)
                publishUpdating(batch.progress(), current)
            }
            val outcome = analyzePending(current, batch, onProgress)
            stateLock.withLock {
                checkAnalysisContext(current)
                _state.value = buildState(current)
            }
            outcome
        } catch (error: CancellationException) {
            throw error
        } catch (_: AiQueueWaitTimeout) {
            OpportunityAnalysisOutcome(batch.completed, batch.pending.size - batch.completed)
        } catch (error: Exception) {
            throw stateLock.withLock { recordFailure(error, batch) }
        } finally {
            withContext(NonCancellable) {
                stateLock.withLock {
                    if (_state.value.isUpdating) _state.value = buildState().copy(progress = batch.progress())
                }
            }
        }
    }

    private suspend fun analyzePending(
        snapshot: AnalysisContext,
        batch: AnalysisBatch,
        onProgress: suspend (Int, Int, String) -> Unit,
    ): OpportunityAnalysisOutcome {
        var quoteFailure: NewsOpportunityAnalysisException? = null
        var networkFailure: NewsOpportunityAnalysisException? = null
        var consecutiveNetworkFailures = 0
        var deferred = 0
        val total = batch.pending.size
        for (article in batch.pending) {
            onProgress(batch.completed, total, "正在分析第 ${batch.completed + 1}/$total 篇新闻")
            stateLock.withLock { checkAnalysisContext(snapshot) }
            try {
                val draft = analyzeArticle(article, snapshot)
                stateLock.withLock {
                    commitResult(article, snapshot, draft)
                    batch.completed++
                    publishUpdating(batch.progress(), snapshot)
                }
            } catch (_: AiQueueWaitTimeout) {
                // Congestion leaves the article pending; no request was sent.
                deferred++
                continue
            } catch (failure: NewsOpportunityAnalysisException) {
                when (failure.reason) {
                    OpportunityFailureReason.INVALID_QUOTE -> quoteFailure = quoteFailure ?: failure
                    OpportunityFailureReason.NETWORK_ERROR -> {
                        networkFailure = networkFailure ?: failure
                        consecutiveNetworkFailures++
                        if (consecutiveNetworkFailures >= MAX_CONSECUTIVE_NETWORK_FAILURES) throw failure
                    }
                    else -> throw failure
                }
                continue
            }
            consecutiveNetworkFailures = 0
            onProgress(batch.completed, total, batch.progress())
        }
        // Completed articles are durable, so a retry only analyzes remaining work.
        quoteFailure?.let { throw it }
        networkFailure?.let { throw it }
        return OpportunityAnalysisOutcome(batch.completed, deferred)
    }

    private suspend fun recordFailure(error: Exception, batch: AnalysisBatch): NewsOpportunityAnalysisException {
        currentCoroutineContext().ensureActive()
        val failure = error as? NewsOpportunityAnalysisException ?: NewsOpportunityAnalysisException(cause = error)
        DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_FAILED, DiagnosticSource.TASK,
            DiagnosticLevel.ERROR, error = failure)
        val message = failure.reason.message
        runCatching { saveState(archive.copy(lastError = message)) }
        _state.value = buildState().copy(progress = batch.progress(), error = message)
        return failure
    }

    private suspend fun analyzeArticle(article: ReadNewsArticle, context: AnalysisContext): OpportunityDraft? =
        try {
            withTimeout(analysisTimeoutMs) {
                // One absolute budget per article (prework included): congestion defers before this expires.
                withAiCallDeadline(analysisTimeoutMs) {
                    analyzer.analyze(OpportunityAnalysisInput(article, context.focus, context.thoughts))
                }
            }
        } catch (deferredByQueue: AiQueueWaitTimeout) {
            throw deferredByQueue
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw NewsOpportunityAnalysisException(OpportunityFailureReason.NETWORK_ERROR, timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            val networkFailure = generateSequence(error as Throwable) { it.cause }.take(8)
                .any { it is IOException || it is HttpRequestTimeoutException }
            if (networkFailure) throw NewsOpportunityAnalysisException(OpportunityFailureReason.NETWORK_ERROR, error)
            throw error
        }

    private fun shouldDeferAutomaticAnalysis(context: AnalysisContext): Boolean {
        if (archive.focus.isBlank() && context.thoughts.isNullOrBlank()) return true
        val elapsed = Clock.System.now().toEpochMilliseconds() - archive.lastAttemptAt
        return archive.lastAttemptContext == "$ANALYSIS_VERSION:${context.version}" && elapsed in 0 until AUTO_REFRESH_INTERVAL
    }

    private suspend fun loadCandidates(snapshot: AnalysisContext): AnalysisContext {
        val source = candidateSource
        stateLock.withLock {
            val cleared = archive.copy(lastError = null)
            if (source == null) {
                if (archive.lastError != null) commitAnalysisState(snapshot, cleared)
                else checkAnalysisContext(snapshot)
            } else {
                // Failed automatic attempts still count after a process restart.
                commitAnalysisState(snapshot, cleared.copy(lastAttemptAt = Clock.System.now().toEpochMilliseconds(),
                    lastAttemptContext = "$ANALYSIS_VERSION:${snapshot.version}"))
            }
        }
        val candidates = (source?.load() ?: return snapshot)
            .filter { it.key.isNotBlank() && it.title.isNotBlank() && it.content.isNotBlank() }
            .distinctBy { it.identity() }.take(MAX_CANDIDATES)
        return stateLock.withLock {
            currentCoroutineContext().ensureActive()
            store.replaceCandidates(candidates) { verifyAnalysisContext(snapshot) }
            replaceArchive(archive.copy(candidates = candidates))
            snapshot.copy(inputRevision = inputRevision)
        }
    }

    private suspend fun updateItem(id: String, transform: (NewsOpportunity) -> NewsOpportunity) = stateLock.withLock {
        ensureLoaded()
        val index = archive.items.indexOfFirst { it.id == id }
        require(index >= 0)
        val next = transform(archive.items[index])
        store.updateActions(next)
        replaceArchive(archive.copy(items = archive.items.toMutableList().apply { set(index, next) }))
        publish()
    }

    private suspend fun commitResult(article: ReadNewsArticle, snapshot: AnalysisContext, draft: OpportunityDraft?) {
        val identity = article.identity()
        val old = archive.items.firstOrNull { it.article.identity() == identity }
        val replacement = draft?.validated(article)?.toOpportunity(article, old)
            ?: old?.takeIf { it.saved || it.reminderId != null || it.ignored }
        val checkpoint = OpportunityCheckpoint(identity, article.fingerprint(snapshot.version))
        currentCoroutineContext().ensureActive()
        store.recordResult(replacement, checkpoint) { verifyAnalysisContext(snapshot) }
        replaceArchive(archive.copy(
            items = archive.items.filterNot { it.article.identity() == identity } + listOfNotNull(replacement),
            checkpoints = archive.checkpoints.filterNot { it.identity == identity } + checkpoint,
        ))
    }

    private fun OpportunityDraft.validated(article: ReadNewsArticle): OpportunityDraft {
        val originalQuote = resolveOpportunityQuote(article.content, quote)
            ?: throw NewsOpportunityAnalysisException(OpportunityFailureReason.INVALID_QUOTE)
        if (listOf(title, category, fact, relevance, action, caveat).any { it.isBlank() }) {
            throw NewsOpportunityAnalysisException(OpportunityFailureReason.INVALID_RESPONSE)
        }
        val compact = try { preview?.validated() } catch (error: IllegalArgumentException) {
            throw NewsOpportunityAnalysisException(OpportunityFailureReason.INVALID_RESPONSE, error)
        }
        return copy(quote = originalQuote, preview = compact)
    }

    private fun OpportunityDraft.toOpportunity(article: ReadNewsArticle, old: NewsOpportunity?) = NewsOpportunity(
        id = old?.id ?: sha256Hex("news-opportunity-v1:${article.identity()}"),
        article = article,
        title = title.trim(), category = category.trim(), fact = fact.trim(), relevance = relevance.trim(),
        action = action.trim(), caveat = caveat.trim(), quote = quote,
        createdAt = Clock.System.now().toEpochMilliseconds(),
        saved = old?.saved ?: false, ignored = old?.ignored ?: false, reminderId = old?.reminderId,
        savedAt = old?.savedAt,
        relevanceScore = relevanceScore.coerceIn(0, 100), actionabilityScore = actionabilityScore.coerceIn(0, 100),
        preview = preview,
    )

    private suspend fun checkAnalysisContext(expected: AnalysisContext) {
        currentCoroutineContext().ensureActive()
        verifyAnalysisContext(expected)
    }

    private fun verifyAnalysisContext(expected: AnalysisContext) {
        if (inputRevision != expected.inputRevision || readAnalysisContext() != expected) {
            throw NewsOpportunityAnalysisException(OpportunityFailureReason.CONTEXT_CHANGED)
        }
    }

    private fun currentContext(): AnalysisContext =
        runCatching { readAnalysisContext() }.getOrElse { analysisContext(thoughtsEnabled = false, thoughts = null) }

    private fun readAnalysisContext(): AnalysisContext {
        val enabled = context.enabled
        return analysisContext(enabled, if (enabled) context.verifiedContext()?.take(MAX_CONTEXT_LENGTH) else null)
    }

    private fun analysisContext(thoughtsEnabled: Boolean, thoughts: String?) = AnalysisContext(
        archive.focus, thoughts, thoughtsEnabled, sha256Hex("${archive.focus}\n${thoughts.orEmpty()}"), inputRevision,
    )

    private fun pendingArticles(context: AnalysisContext): List<ReadNewsArticle> {
        val checkpoints = archive.checkpoints.associate { it.identity to it.fingerprint }
        val pending = analysisArticles().filter { article ->
            article.content.isNotBlank() && checkpoints[article.identity()] != article.fingerprint(context.version)
        }
        val regularIds = (archive.candidates + archive.articles).map { it.identity() }.toSet()
        // Retained recommendations may no longer belong to the latest candidate window.
        // Backfill them in bounded batches without delaying or limiting regular analysis.
        val regular = pending.filter { it.identity() in regularIds }
        val backfill = pending.filterNot { it.identity() in regularIds }.take(MAX_CANDIDATES)
        return (regular + backfill).sortedBy { it.readAt }
    }

    private fun analysisArticles(): List<ReadNewsArticle> {
        val legacy = archive.items.filter { it.preview == null && !it.ignored && it.reminderId == null }.map { it.article }
        return (archive.candidates + archive.articles + legacy).distinctBy { it.identity() }
    }

    private fun buildState(context: AnalysisContext = currentContext()) = OpportunityState(
        items = archive.items.sortedByDescending { it.createdAt },
        focus = archive.focus,
        readCount = archive.articles.size,
        readArticleKeys = archive.articles.map { it.identity() }.toSet(),
        candidateCount = analysisArticles().size,
        error = archive.lastError?.takeUnless(::isOpportunityCancellationMessage),
        dismissedErrorTaskId = archive.dismissedErrorTaskId,
        pendingCount = pendingArticles(context).size,
        hasAnalysisContext = archive.focus.isNotBlank() || !context.thoughts.isNullOrBlank(),
    )

    private fun saveState(next: OpportunityArchive) {
        store.saveState(next)
        replaceArchive(next)
    }

    private suspend fun commitAnalysisState(snapshot: AnalysisContext, next: OpportunityArchive) {
        currentCoroutineContext().ensureActive()
        store.saveState(next) { verifyAnalysisContext(snapshot) }
        replaceArchive(next)
    }

    private fun replaceArchive(next: OpportunityArchive) {
        if (next.focus != archive.focus || next.candidates != archive.candidates || next.articles != archive.articles) {
            inputRevision++
        }
        archive = next
    }

    private fun ensureLoaded() {
        if (!loaded) { replaceArchive(store.load()); loaded = true }
    }

    private fun publish(context: AnalysisContext = currentContext()) {
        val previous = _state.value
        val next = buildState(context)
        _state.value = if (previous.isUpdating) next.copy(isUpdating = true, progress = previous.progress, error = null) else next
    }

    private fun publishUpdating(progress: String, context: AnalysisContext) {
        _state.value = buildState(context).copy(isUpdating = true, progress = progress, error = null)
    }

    private fun ReadNewsArticle.identity() = key.trim()

    private fun ReadNewsArticle.fingerprint(contextVersion: String) =
        sha256Hex("news-opportunity-analysis-$ANALYSIS_VERSION:${identity()}:${sha256Hex(content)}:$contextVersion")

    private class AnalysisBatch(var pending: List<ReadNewsArticle>, var completed: Int = 0) {
        fun progress() = if (pending.isEmpty()) "没有待分析的文章" else "已完成 $completed/${pending.size} 篇"
    }

    private data class AnalysisContext(
        val focus: String, val thoughts: String?, val thoughtsEnabled: Boolean, val version: String, val inputRevision: Long,
    )

    private companion object {
        const val ANALYSIS_VERSION = "v5"
        const val AUTO_REFRESH_INTERVAL = 30 * 60 * 1_000L
        const val MAX_CANDIDATES = 20
        /** A batch stops early only when the service looks unreachable, not on a single bad article. */
        const val MAX_CONSECUTIVE_NETWORK_FAILURES = 3
        const val MAX_FOCUS_LENGTH = 2_000
        const val MAX_CONTEXT_LENGTH = 4_000
    }
}
