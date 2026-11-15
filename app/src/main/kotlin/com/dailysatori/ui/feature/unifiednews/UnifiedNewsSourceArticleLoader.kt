package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.data.repository.articleOriginalMarkdown
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.shared.db.Article

/** One page of a remote subscription exactly as it is stored locally. */
data class UnifiedNewsSourceArticlePage(
    val articles: List<RemoteArticle>,
    val hasMore: Boolean,
    val missingSource: Boolean,
)

/**
 * Reads one page of a remote subscription from the local database only.
 *
 * It deliberately has no network or sync dependency: pull-to-refresh and list paging are read-only
 * views of what the background task stored, so no gesture can start a download or write a row. A local
 * source with no stored article simply yields an empty page and the existing empty state.
 */
class UnifiedNewsSourceArticleLoader(
    private val sourceRepo: RemoteNewsSourceRepository,
    private val syncRepo: RemoteArticleSyncRepository,
) {
    suspend fun load(sourceId: Long, offset: Long, pageSize: Long): UnifiedNewsSourceArticlePage {
        if (sourceRepo.getById(sourceId) == null) {
            return UnifiedNewsSourceArticlePage(articles = emptyList(), hasMore = false, missingSource = true)
        }
        val stored = syncRepo.getArticlesBySource(remoteSourceId = sourceId, limit = pageSize, offset = offset)
        return UnifiedNewsSourceArticlePage(
            articles = stored.map { it.toRemoteArticleForDisplay() },
            hasMore = stored.size.toLong() == pageSize,
            missingSource = false,
        )
    }
}

internal fun Article.toRemoteArticleForDisplay(): RemoteArticle = RemoteArticle(
    id = id,
    title = ai_title ?: title,
    url = url,
    summary = ai_content,
    content = articleOriginalMarkdown(original_markdown_content, ai_markdown_content, source_type == "remote_news"),
    coverUrl = cover_image_url,
    publishedAt = pub_date?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it).toString() },
    createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(created_at).toString(),
)
