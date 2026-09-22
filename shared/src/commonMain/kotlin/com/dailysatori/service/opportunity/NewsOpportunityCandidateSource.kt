package com.dailysatori.service.opportunity

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.service.remotenews.RemoteArticleSyncService
import com.dailysatori.service.remotenews.RemoteNewsConfigValues
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Remote_news_source
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Uses configured news sources, not reading history. Synced originals remain available in the article reader. */
class NewsOpportunityCandidateSource(
    private val articles: ArticleRepository,
    private val enabledSources: () -> List<Remote_news_source>,
    private val remoteNews: RemoteNewsService,
    private val sync: RemoteArticleSyncService,
    private val mappings: RemoteArticleSyncRepository,
) : OpportunityCandidateSource {
    override suspend fun load(): List<ReadNewsArticle> {
        val candidates = articles.getLatestSync(LIMIT).map { it.toOpportunityCandidate() }.toMutableList()
        val now = Clock.System.now()
        val date = now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        var failed = false
        for (source in enabledSources()) {
            val result = remoteNews.fetchTopArticlesToday(RemoteNewsConfigValues(source.base_url, source.api_token), limit = LIMIT)
            when (result) {
                is RemoteNewsResult.Success -> {
                    val saved = sync.syncSourceArticles(source.id, date, result.value.articles.take(LIMIT), now.toEpochMilliseconds())
                    if (saved.failures.isNotEmpty()) failed = true
                }
                is RemoteNewsResult.Failure -> failed = true
            }
            candidates += mappings.getArticlesBySource(source.id, LIMIT.toLong(), 0).map { it.toOpportunityCandidate(source.name) }
        }
        val readable = candidates.filter { it.title.isNotBlank() && it.content.isNotBlank() }
            .sortedByDescending { it.publishedAt }.distinctBy { it.key }.take(LIMIT)
        check(readable.isNotEmpty() || !failed) { "News sources unavailable" }
        return readable
    }

    private companion object { const val LIMIT = 20 }
}

fun newsArticleKey(url: String?, fallback: String): String = url?.trim()?.substringBefore('#')
    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { "url:$it" } ?: fallback

fun Article.toOpportunityCandidate(source: String = url.orEmpty().substringAfter("://").substringBefore('/')) = ReadNewsArticle(
    key = newsArticleKey(url, "local:$id"),
    title = title.orEmpty(),
    content = original_markdown_content.orEmpty(),
    url = url,
    source = source,
    publishedAt = (pub_date ?: created_at)?.let { Instant.fromEpochMilliseconds(it).toString() },
    readAt = 0, // A recommendation is not a reading event.
    localArticleId = id,
)
