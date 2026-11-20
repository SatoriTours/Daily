package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.service.articleai.*
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Article_ai_job
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ArticleAiRepository(private val db: DailySatoriDatabase, private val settings: SettingRepository) {
    private val q get() = db.articleAiQueries
    fun <T> transaction(block: () -> T): T = q.transactionWithResult { block() }

    fun record(article: Article, remote: RemoteArticle) {
        val old = q.getArticleAiSource(article.id).executeAsOneOrNull()
        val candidate = analyzeArticleBody(remote.content, remote.bodyKind, analyzeIncomplete = true)
        // A recurring feed preview or error does not invalidate a previously verified full version.
        if (old?.body_kind == ArticleBodyKind.BODY.name && candidate.kind != ArticleBodyKind.BODY &&
            analyzeArticleBody(article.reusableOriginalBody()).hash == old.body_hash) return
        val body = analyzeArticleBody(article.reusableOriginalBody(), remote.bodyKind, analyzeIncomplete = true)
        val summary = remote.summary?.trim()?.takeIf {
            remote.summaryLanguage?.lowercase() in setOf("zh", "zh-cn", "zh-hans") &&
                !it.equals(body.markdown, ignoreCase = true) &&
                analyzeArticleBody(it).language == ArticleLanguage.CHINESE
        }
        q.putArticleAiSource(article.id, body.hash, body.kind.name, summary ?: upstream(article.id, body.hash),
            cacheCount(body.hash, article.id))
    }

    fun keepVerifiedOriginal(articleId: Long): Boolean =
        q.getArticleAiSource(articleId).executeAsOneOrNull()?.body_kind == ArticleBodyKind.BODY.name

    fun input(article: Article): ArticleBody {
        val source = q.getArticleAiSource(article.id).executeAsOneOrNull()
        val kind = source?.body_kind?.let(ArticleBodyKind::valueOf) ?: legacyKind(article)
        return analyzeArticleBody(article.reusableOriginalBody(), kind, analyzeIncomplete = true)
    }

    fun originalVersion(article: Article): String = article.reusableOriginalBody().orEmpty()

    private fun legacyKind(article: Article): ArticleBodyKind {
        val original = article.original_markdown_content.orEmpty()
        if (article.reusableOriginalBody().isNullOrBlank())
            return if (original.startsWith("## 摘要")) ArticleBodyKind.SOURCE_SUMMARY else ArticleBodyKind.MISSING
        // Older RSS stored description-only snippets as original. Only a separately identified body
        // and description in that snapshot establishes trusted provenance for historical processing.
        return if (original.startsWith("## 摘要") && "\n## 原文\n" in original)
            ArticleBodyKind.BODY else ArticleBodyKind.UNVERIFIED
    }

    private fun upstream(id: Long, hash: String): String? {
        val source = q.getArticleAiSource(id).executeAsOneOrNull() ?: return null
        // Preview snapshots stored before fingerprinting kept an empty body hash; do not drop their
        // publisher summary just because the fingerprint is now derived from the preview text.
        val usable = source.body_hash == hash || (source.body_hash.isEmpty() && source.body_kind != ArticleBodyKind.BODY.name)
        return source.takeIf { usable }?.upstream_summary
    }

    fun view(article: Article): ArticleAiView {
        val body = input(article)
        val summary = result(body.hash, SUMMARY)
        val translation = result(body.hash, TRANSLATION)
        val upstream = upstream(article.id, body.hash)
        val job = jobs(body.hash).firstOrNull { it.state in active } ?: jobs(body.hash).firstOrNull()
        return ArticleAiView(body.kind, body.language, body.units, summary?.markdown ?: upstream,
            translation?.markdown, summary?.partial == 1L, translation?.partial == 1L, summary == null && upstream != null,
            job?.state, job?.mode?.let(ArticleAiMode::valueOf), job?.code, settings.get(BLOCKED).orEmpty(),
            article.reusableOriginalBody())
    }

    fun observe(id: Long, article: () -> Article?): Flow<ArticleAiView?> =
        q.articleAiObserve(id).asFlow().mapToOneOrNull(Dispatchers.IO).map { article()?.let(::view) }

    fun plan(article: Article): ArticleAiMode? {
        val body = input(article)
        val summary = hasSummary(article.id, body.hash)
        val translation = result(body.hash, TRANSLATION) != null
        return when (body.automatic(summary)) {
            ArticleAiMode.BOTH -> if (translation) ArticleAiMode.SUMMARY else ArticleAiMode.BOTH
            ArticleAiMode.TRANSLATION -> ArticleAiMode.TRANSLATION.takeUnless { translation }
            else -> body.automatic(summary)
        }
    }

    fun enqueue(article: Article, requested: ArticleAiMode?, priority: Long, redo: Boolean = false): Long? =
        transaction {
            val body = input(article)
            // A preview or unverified snapshot is only processed when the user explicitly asks for it.
            if (body.kind != ArticleBodyKind.BODY && (priority != MANUAL || !body.manualProcessable()))
                return@transaction null
            val previous = jobs(body.hash)
            val queued = previous.firstOrNull { it.state == "queued" }
            val running = previous.firstOrNull { it.state == "running" }
            if (requested == null || priority != MANUAL) {
                if (running != null || queued != null) {
                    if (priority != MANUAL || queued == null || queued.priority == MANUAL) return@transaction null
                    q.raiseArticleAiPriority(queued.id)
                    return@transaction queued.id
                }
            }
            if (priority != MANUAL && previous.any { it.state == "failed" }) return@transaction null
            val mode = requested ?: plan(article)
                ?: forcedMode(article, body).takeIf { priority == MANUAL }
                ?: return@transaction null
            val retrying = requested != null && priority == MANUAL && previous.firstOrNull()?.state == "failed"
            val needed = if (redo || retrying) mode else missing(article.id, body.hash, mode) ?: return@transaction null
            // A live request already produces a fresh result for the fields it owns. Save at most
            // one follow-up for uncovered fields; clicks for the same running mode cost nothing.
            val uncovered = running?.mode?.let(ArticleAiMode::valueOf)?.let { needed.without(it) }
            val intent = if (running == null) needed else uncovered ?: return@transaction null
            val storedSource = q.getArticleAiSource(article.id).executeAsOneOrNull()
            if (storedSource == null || storedSource.body_hash != body.hash)
                q.putArticleAiSource(article.id, body.hash, body.kind.name, storedSource?.upstream_summary,
                    cacheCount(body.hash, article.id))
            if (queued != null) {
                if (queued.mode == intent.name && queued.priority == MANUAL) return@transaction null
                if (queued.requests == 0L) {
                    q.replaceQueuedArticleAiIntent(article.id, intent.name, queued.id)
                    return@transaction queued.id
                }
                finish(queued, "obsolete", "manual_replaced")
            }
            q.addArticleAiJob(article.id, body.hash, intent.name, VERSION, TARGET, priority)
            q.articleAiLastId().executeAsOne()
        }

    /** Requests both fields for a processed-on-demand snapshot, reusing any verified upstream summary. */
    private fun forcedMode(article: Article, body: ArticleBody): ArticleAiMode? {
        if (!body.manualProcessable()) return null
        if (body.language != ArticleLanguage.FOREIGN) return ArticleAiMode.SUMMARY
        return if (hasSummary(article.id, body.hash)) ArticleAiMode.TRANSLATION else ArticleAiMode.BOTH
    }

    fun historyCandidates(): List<Article> = q.articleAiHistory().executeAsList().asSequence()
        .mapNotNull { article ->
            val body = input(article)
            if (plan(article) == null || jobs(body.hash).isNotEmpty()) null else body.hash to article
        }.distinctBy { it.first }.map { it.second }.toList()

    fun historyEnabled() = settings.get(HISTORY) == "1"
    fun historyWaiting() = q.articleAiHistoryWaiting().executeAsOne().toInt()
    fun setHistoryEnabled(enabled: Boolean) = settings.upsert(HISTORY, if (enabled) "1" else "0")
    fun blocked() = settings.get(BLOCKED).orEmpty()
    fun block(code: String) = settings.upsert(BLOCKED, code)
    fun recover() = q.interruptArticleAiJobs()
    fun pick(now: Long) = q.pickArticleAiJob(if (historyEnabled()) 1 else 0, now).executeAsOneOrNull()
    fun nextTime() = q.articleAiNextTime(if (historyEnabled()) 1 else 0).executeAsOne().MIN
    fun candidates(job: Article_ai_job) = (listOf(job.article_id) +
        q.articleAiCandidateIds(job.body_hash).executeAsList()).distinct()

    fun begin(job: Article_ai_job, now: Long): Long = transaction {
        q.beginArticleAiAttempt(job.id)
        q.addArticleAiAttempt(job.id, now)
        q.articleAiLastId().executeAsOne()
    }

    fun finish(job: Article_ai_job, state: String, code: String = "", next: Long = 0, cacheHits: Long = 0) =
        q.setArticleAiJob(state, code, next, cacheHits, job.id)

    fun audit(id: Long, outcome: String, usage: ArticleAiUsage = ArticleAiUsage()) =
        q.finishArticleAiAttempt(outcome, usage.input, usage.output, usage.cached, id)

    fun save(job: Article_ai_job, output: ArticleAiOutput, partial: Boolean) {
        output.summary?.let { q.putArticleAiResult(job.body_hash, SUMMARY, VERSION, TARGET, it, if (partial) 1 else 0) }
        output.translation?.let { q.putArticleAiResult(job.body_hash, TRANSLATION, VERSION, TARGET, it, if (partial) 1 else 0) }
        q.markArticleAiSharedCache(cacheCount(job.body_hash, -1), job.body_hash, job.article_id)
    }

    private fun cacheCount(hash: String, articleId: Long): Long =
        if (jobs(hash).any { it.article_id == articleId && it.state == "succeeded" }) 0
        else listOf(SUMMARY, TRANSLATION).count { result(hash, it) != null }.toLong()

    private fun hasSummary(id: Long, hash: String) = upstream(id, hash) != null || result(hash, SUMMARY) != null
    private fun result(hash: String, kind: String) =
        q.articleAiResult(hash, kind, VERSION, TARGET).executeAsOneOrNull()
    private fun jobs(hash: String) = q.articleAiJobs(hash, VERSION, TARGET).executeAsList()

    private fun missing(id: Long, hash: String, mode: ArticleAiMode): ArticleAiMode? {
        val summary = mode.summary && !hasSummary(id, hash)
        val translation = mode.translation && result(hash, TRANSLATION) == null
        return ArticleAiMode.entries.firstOrNull { it.summary == summary && it.translation == translation }
    }

    private fun ArticleAiMode.without(other: ArticleAiMode): ArticleAiMode? =
        ArticleAiMode.entries.firstOrNull { it.summary == (summary && !other.summary) &&
            it.translation == (translation && !other.translation) }

    companion object {
        const val HISTORY_PRIORITY = 1L
        const val NEW = 2L
        const val MANUAL = 3L
        private const val SUMMARY = "summary"
        private const val TRANSLATION = "translation"
        private const val VERSION = 1L
        private const val TARGET = "zh-Hans"
        private const val HISTORY = "article_ai_history_enabled"
        private const val BLOCKED = "article_ai_blocked"
        private val active = setOf("queued", "running")
    }
}

data class ArticleAiView(
    val kind: ArticleBodyKind,
    val language: ArticleLanguage,
    val units: Int,
    val summary: String?,
    val translation: String?,
    val summaryPartial: Boolean,
    val translationPartial: Boolean,
    val sourceSummary: Boolean,
    val jobState: String?,
    val jobMode: ArticleAiMode?,
    val error: String?,
    val blocked: String,
    val original: String? = null,
)
