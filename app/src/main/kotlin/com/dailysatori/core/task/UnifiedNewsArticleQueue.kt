package com.dailysatori.core.task

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.hotnews.HotNewsService
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Unified_news_source

class UnifiedNewsArticleQueue(
    private val summaries: UnifiedNewsSummaryRepository,
    private val articles: ArticleRepository,
    private val tasks: AsyncTaskRepository,
    private val hotNews: HotNewsService,
    private val remoteArticles: RemoteArticleSyncRepository,
    private val schedule: (Long) -> Unit,
) {
    fun enqueueLatest() {
        val summary = summaries.getLatestSuccessful() ?: return
        enqueue(summaries.getSources(summary.id))
    }

    fun enqueue(sources: List<Unified_news_source>): Map<Long, Long> {
        val pending = mutableSetOf<Long>()
        val ids = sources.mapNotNull { source ->
            val url = source.source_url?.trim()?.substringBefore('#')?.trimEnd('/')
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            val existing = findArticle(source, url)
            if (existing == null && url == null) return@mapNotNull null
            val articleId = enqueueArticle(source, url, existing, pending) ?: return@mapNotNull null
            source.id to articleId
        }.toMap()
        pending.forEach(schedule)
        return ids
    }

    private fun findArticle(source: Unified_news_source, url: String?): Article? {
        if (source.source_type == "local_favorite") return source.source_id?.let(articles::getById)
        val remoteSourceId = source.source_filename?.removePrefix("remote_news_source:")
            ?.takeIf { source.source_filename?.startsWith("remote_news_source:") == true }?.toLongOrNull()
        val remoteArticleId = source.source_id
        val mapped = if (source.source_type == "remote_article" && remoteSourceId != null && remoteArticleId != null) {
            remoteArticles.findByRemoteIdentity(remoteSourceId, remoteArticleId)?.article_id
        } else null
        return mapped?.let(articles::getById) ?: url?.let { articles.findIntakeArticle(it)?.id }?.let(articles::getById)
    }

    private fun enqueueArticle(source: Unified_news_source, url: String?, existing: Article?, pending: MutableSet<Long>): Long? {
        if (!existing?.ai_content.isNullOrBlank() && !existing?.ai_markdown_content.isNullOrBlank() && existing?.status == "completed") {
            return existing.id
        }
        val keys = listOfNotNull(url?.let { "save_article:$it" }, existing?.let { "remote_article_reprocess:${it.id}" })
        val previous = keys.mapNotNull(tasks::getLatestByUniqueKey)
        if (previous.isNotEmpty()) {
            val active = previous.filter { it.status in setOf("queued", "running", "retrying") }
            active.forEach { pending.add(it.id) }
            return existing?.id ?: if (active.isNotEmpty() && url != null) articles.createPendingFromUrl(url) else null
        }
        val cachedBody = hotNews.find(source.source_filename.orEmpty())
            ?.takeIf { it.url.substringBefore('#').trimEnd('/') == url }
            ?.originalContent?.takeIf { it.length >= 40 }
        val canReprocess = existing != null &&
            (!existing.original_markdown_content.isNullOrBlank() || !existing.ai_markdown_content.isNullOrBlank() || cachedBody != null)
        if (!canReprocess && url == null) return existing?.id
        val type = if (canReprocess) "remote_article_reprocess" else "save_article"
        val taskId = tasks.enqueue(type = type,
            payloadJson = if (canReprocess) articlePostProcessingPayloadJson(existing!!.id) else saveArticleTaskPayloadJson(url!!),
            uniqueKey = if (canReprocess) "$type:${existing!!.id}" else "$type:$url")
        // Persist task ownership before publishing an article for startup recovery.
        val id = existing?.id ?: articles.createPendingFromUrl(url!!)
        if (cachedBody != null && articles.getById(id)?.original_markdown_content.isNullOrBlank()) {
            articles.updateOriginalMarkdownContent(id, cachedBody)
        }
        if (!canReprocess && existing?.status == "completed") articles.updateStatus(id, "pending")
        pending.add(taskId)
        return id
    }
}
