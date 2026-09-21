package com.dailysatori.service.opportunity

import com.dailysatori.service.externalfavorites.sha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class NewsOpportunityService(
    private val store: NewsOpportunityStore,
    private val analyzer: OpportunityAnalyzer,
    private val context: NewsOpportunityContext,
) {
    private val execution = Mutex()
    private var archive = OpportunityArchive()
    private var loaded = false
    private val _state = MutableStateFlow(OpportunityState())
    val state = _state.asStateFlow()

    suspend fun refresh() = execution.withLock {
        archive = store.load()
        loaded = true
        publish()
    }

    suspend fun markRead(article: ReadNewsArticle) = execution.withLock {
        ensureLoaded()
        require(article.key.isNotBlank() && article.title.isNotBlank() && article.content.isNotBlank()) { "Readable article is required" }
        val identity = article.identity()
        persistAndPublish(archive.copy(articles = archive.articles.filterNot { it.identity() == identity } + article))
    }

    suspend fun saveFocus(text: String) = execution.withLock {
        ensureLoaded()
        require(text.length <= MAX_FOCUS_LENGTH) { "关注点不能超过 2,000 字" }
        persistAndPublish(archive.copy(focus = text.trim()))
    }

    suspend fun setSaved(id: String, saved: Boolean) = updateItem(id) { it.copy(saved = saved) }

    suspend fun setIgnored(id: String, ignored: Boolean) = updateItem(id) { it.copy(ignored = ignored) }

    suspend fun linkReminder(id: String, reminderId: String?) = updateItem(id) {
        it.copy(reminderId = reminderId?.takeIf(String::isNotBlank))
    }

    suspend fun analyze(onProgress: suspend (Int, Int, String) -> Unit = { _, _, _ -> }) = execution.withLock {
        ensureLoaded()
        val analysisContext = currentContext()
        val pending = pendingArticles(analysisContext)
        var completed = 0
        _state.value = buildState(analysisContext).copy(isUpdating = true, progress = progress(0, pending.size), error = null)
        try {
            check(pending.isEmpty() || archive.focus.isNotBlank() || !analysisContext.thoughts.isNullOrBlank())
            pending.forEachIndexed { index, article ->
                check(currentContext().version == analysisContext.version) { "Context changed during analysis" }
                onProgress(index, pending.size, progress(index, pending.size))
                val draft = analyzer.analyze(OpportunityAnalysisInput(article, archive.focus, analysisContext.thoughts))
                val before = archive
                applyResult(article, analysisContext.version, draft)
                try { store.save(archive) } catch (error: Exception) { archive = before; throw error }
                completed = index + 1
                _state.value = buildState(analysisContext).copy(progress = progress(completed, pending.size), isUpdating = true)
                onProgress(index + 1, pending.size, progress(index + 1, pending.size))
            }
            publish(analysisContext)
        } catch (error: CancellationException) {
            _state.value = buildState(analysisContext).copy(progress = progress(completed, pending.size))
            throw error
        } catch (_: Exception) {
            _state.value = buildState(analysisContext).copy(progress = progress(completed, pending.size), error = SAFE_ERROR)
            throw NewsOpportunityAnalysisException()
        }
    }

    private suspend fun updateItem(id: String, transform: (NewsOpportunity) -> NewsOpportunity) = execution.withLock {
        ensureLoaded()
        require(archive.items.any { it.id == id })
        persistAndPublish(archive.copy(items = archive.items.map { if (it.id == id) transform(it) else it }))
    }

    private fun applyResult(article: ReadNewsArticle, contextVersion: String, draft: OpportunityDraft?) {
        val identity = article.identity()
        val old = archive.items.firstOrNull { it.article.identity() == identity }
        val retained = archive.items.filterNot { it.article.identity() == identity }
        val replacement = draft?.validated(article)?.toOpportunity(article, old)
        val checkpoint = OpportunityCheckpoint(identity, article.fingerprint(contextVersion))
        archive = archive.copy(
            items = if (replacement != null) retained + replacement else if (old != null && (old.saved || old.reminderId != null || old.ignored)) retained + old else retained,
            checkpoints = archive.checkpoints.filterNot { it.identity == identity } + checkpoint,
        )
    }

    private fun OpportunityDraft.validated(article: ReadNewsArticle): OpportunityDraft {
        require(quote.isNotBlank() && article.content.take(OPPORTUNITY_BODY_LIMIT).contains(quote)) { "Invalid article quote" }
        require(listOf(title, category, fact, relevance, action, caveat).all { it.isNotBlank() }) { "Incomplete analysis" }
        return this
    }

    private fun OpportunityDraft.toOpportunity(article: ReadNewsArticle, old: NewsOpportunity?) = NewsOpportunity(
        id = old?.id ?: sha256Hex("news-opportunity-v1:${article.identity()}"),
        article = article,
        title = title.trim(), category = category.trim(), fact = fact.trim(), relevance = relevance.trim(),
        action = action.trim(), caveat = caveat.trim(), quote = quote,
        createdAt = Clock.System.now().toEpochMilliseconds(),
        saved = old?.saved ?: false, ignored = old?.ignored ?: false, reminderId = old?.reminderId,
    )

    private fun currentContext(): AnalysisContext {
        val thoughts = runCatching { if (context.enabled) context.verifiedContext()?.take(MAX_CONTEXT_LENGTH) else null }.getOrNull()
        return AnalysisContext(thoughts, sha256Hex("${archive.focus}\n${thoughts.orEmpty()}"))
    }

    private fun pendingArticles(context: AnalysisContext): List<ReadNewsArticle> {
        val checkpoints = archive.checkpoints.associate { it.identity to it.fingerprint }
        return archive.articles.filter { article ->
            article.content.isNotBlank() && checkpoints[article.identity()] != article.fingerprint(context.version)
        }.sortedBy { it.readAt }
    }

    private fun buildState(context: AnalysisContext = currentContext()) = OpportunityState(
        items = archive.items.sortedByDescending { it.createdAt },
        focus = archive.focus,
        readCount = archive.articles.size,
        pendingCount = pendingArticles(context).size,
        hasAnalysisContext = archive.focus.isNotBlank() || !context.thoughts.isNullOrBlank(),
    )

    private fun persistAndPublish(next: OpportunityArchive) {
        store.save(next)
        archive = next
        _state.value = buildState()
    }

    private fun ensureLoaded() {
        if (!loaded) { archive = store.load(); loaded = true }
    }

    private fun publish(context: AnalysisContext = currentContext()) {
        _state.value = buildState(context)
    }

    private fun ReadNewsArticle.identity() = key.trim()

    private fun ReadNewsArticle.fingerprint(contextVersion: String) =
        sha256Hex("news-opportunity-analysis-v1:${identity()}:${sha256Hex(content)}:$contextVersion")

    private fun progress(done: Int, total: Int) = if (total == 0) "没有待分析的阅读" else "已完成 $done/$total 篇"

    private data class AnalysisContext(val thoughts: String?, val version: String)

    private companion object {
        const val MAX_FOCUS_LENGTH = 2_000
        const val MAX_CONTEXT_LENGTH = 4_000
        const val SAFE_ERROR = "分析失败，请稍后重试"
    }
}
