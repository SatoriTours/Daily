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
    val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }
    return LocalFavoriteArticleFields(
        title = cleanTitle,
        aiTitle = cleanTitle,
        aiContent = remoteArticleSummaryForLocalFavorite(summary, viewpoints),
        aiMarkdownContent = content?.trim()?.takeIf { it.isNotBlank() },
        url = url?.trim()?.takeIf { it.isNotBlank() },
        coverImageUrl = coverUrl?.trim()?.takeIf { it.isNotBlank() },
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
    val text = listOfNotNull(title, summary, viewpoints.joinToString("\n"), content)
        .joinToString("\n")
        .take(LOCAL_REPROCESS_LANGUAGE_SAMPLE_LIMIT)
    val chineseCount = Regex("[\\u4e00-\\u9fff]").findAll(text).count()
    if (chineseCount >= LOCAL_REPROCESS_CHINESE_THRESHOLD) return false
    val englishWordCount = Regex("\\b[A-Za-z][A-Za-z'-]{2,}\\b").findAll(text).count()
    return englishWordCount >= LOCAL_REPROCESS_ENGLISH_WORD_THRESHOLD
}

internal fun remoteArticleSummaryForLocalFavorite(summary: String?, viewpoints: List<String>): String? {
    val cleanSummary = summary?.trim()?.takeIf { it.isNotBlank() }
    val cleanViewpoints = viewpoints.map { it.trim() }.filter { it.isNotBlank() }
    val viewpointMarkdown = cleanViewpoints
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n") { "- $it" }
        ?.let { "## 关键观点\n\n$it" }
    return listOfNotNull(cleanSummary, viewpointMarkdown)
        .joinToString("\n\n")
        .takeIf { it.isNotBlank() }
}

internal fun remoteArticleTimeMillis(value: String?): Long? = try {
    value?.trim()?.takeIf { it.isNotBlank() }?.let { Instant.parse(it).toEpochMilliseconds() }
} catch (_: Exception) {
    null
}

private const val LOCAL_REPROCESS_LANGUAGE_SAMPLE_LIMIT = 4_000
private const val LOCAL_REPROCESS_CHINESE_THRESHOLD = 8
private const val LOCAL_REPROCESS_ENGLISH_WORD_THRESHOLD = 12
