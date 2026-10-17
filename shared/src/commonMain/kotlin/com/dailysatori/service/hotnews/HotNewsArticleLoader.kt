package com.dailysatori.service.hotnews

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.articleOriginalMarkdown
import com.dailysatori.service.parser.articleNeedsTranslation
import com.dailysatori.service.parser.articleTranslationMarkdown
import com.dailysatori.shared.db.Article
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class HotNewsArticleLoader(
    private val articles: ArticleRepository,
    private val hotNews: HotNewsService,
    private val process: suspend (Long) -> Unit,
) {
    suspend fun load(item: HotNewsItem, onArticle: (Article) -> Unit = {}): Article = coroutineScope {
        val existing = articles.getByUrl(item.url)
        existing?.let(onArticle)
        if (existing != null && isReady(existing)) return@coroutineScope existing
        val article = prepare(item, existing)
        onArticle(article)
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            articles.observeById(article.id).filterNotNull().collect { onArticle(it) }
        }
        try { process(article.id) }
        finally {
            observer.cancel()
            articles.getById(article.id)?.let(onArticle)
        }
        val latest = articles.getById(article.id) ?: error("文章不可用")
        require(isReady(latest)) { "文章中文处理尚未完成" }
        latest
    }

    private suspend fun prepare(item: HotNewsItem, existing: Article?): Article {
        val savedOriginal = existing?.let {
            articleOriginalMarkdown(it.original_markdown_content, it.ai_markdown_content, it.source_type == "remote_news")
        }
        val original = savedOriginal?.takeIf { it.isNotBlank() } ?: hotNews.ensureContent(item).originalContent
        val id = existing?.id ?: articles.insert(title = item.originalTitle, aiTitle = item.displayTitle,
            aiContent = item.displaySummary, url = item.url)
        if (existing?.original_markdown_content.isNullOrBlank()) articles.updateOriginalMarkdownContent(id, original)
        return articles.getById(id) ?: error("文章不可用")
    }

    private fun isReady(article: Article): Boolean {
        if (article.status != "completed" || !isChineseHotNewsText(article.ai_title.orEmpty()) ||
            article.ai_content.isNullOrBlank() || article.ai_markdown_content.isNullOrBlank()) return false
        val original = articleOriginalMarkdown(article.original_markdown_content, article.ai_markdown_content,
            article.source_type == "remote_news").orEmpty()
        return !articleNeedsTranslation(original) || articleTranslationMarkdown(original, article.ai_markdown_content) != null
    }
}
