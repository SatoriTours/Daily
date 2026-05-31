package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle
import kotlinx.datetime.Instant

data class LocalFavoriteArticleFields(
    val title: String?,
    val aiTitle: String?,
    val aiContent: String?,
    val aiMarkdownContent: String?,
    val url: String?,
    val isFavorite: Long = 1L,
    val comment: String? = null,
    val status: String = "completed",
    val coverImage: String? = null,
    val coverImageUrl: String?,
    val pubDate: Long?,
)

fun RemoteArticle.toLocalFavoriteArticleFields(): LocalFavoriteArticleFields {
    val cleanTitle = cleanRemoteArticleText(title)
    return LocalFavoriteArticleFields(
        title = cleanTitle,
        aiTitle = cleanTitle,
        aiContent = remoteArticleSummaryForLocalFavorite(summary, viewpoints),
        aiMarkdownContent = cleanRemoteArticleText(content),
        url = cleanRemoteArticleText(url),
        coverImageUrl = cleanRemoteArticleText(coverUrl),
        pubDate = remoteArticleTimeMillis(publishedAt) ?: remoteArticleTimeMillis(processedAt) ?: remoteArticleTimeMillis(createdAt),
    )
}

fun RemoteArticle.toLocalCachedArticleFields(sourceTime: Long? = null): LocalFavoriteArticleFields {
    val favoriteFields = toLocalFavoriteArticleFields()
    return favoriteFields.copy(
        isFavorite = 0,
        aiMarkdownContent = favoriteFields.aiMarkdownContent
            ?: favoriteFields.aiContent
            ?: title?.trim()?.takeIf { it.isNotBlank() },
        pubDate = favoriteFields.pubDate ?: sourceTime,
    )
}

fun RemoteArticle.needsLocalAiReprocessingForChineseOutput(): Boolean {
    if (url.isNullOrBlank()) return false
    if (hasEnoughChineseForLocalArticle(this)) return false
    return hasEnoughEnglishForLocalArticle(this)
}

internal fun remoteArticleSummaryForLocalFavorite(summary: String?, viewpoints: List<String>): String? = listOfNotNull(
    cleanRemoteArticleText(summary),
    remoteArticleViewpointMarkdown(viewpoints),
).joinToString("\n\n").takeIf { it.isNotBlank() }

internal fun remoteArticleTimeMillis(value: String?): Long? = try {
    value?.trim()?.takeIf { it.isNotBlank() }?.let { Instant.parse(it).toEpochMilliseconds() }
} catch (_: Exception) {
    null
}
