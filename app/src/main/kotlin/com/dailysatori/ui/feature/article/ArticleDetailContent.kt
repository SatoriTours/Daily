package com.dailysatori.ui.feature.article

import com.dailysatori.service.parser.normalizeArticleMarkdownImages
import com.dailysatori.data.repository.articleOriginalMarkdown

private val MarkdownHeadingRegex = Regex("""#{1,6}\s+.+""")
private val ExcessiveMarkdownNewlinesRegex = Regex("\n{3,}")
private val MalformedImagePlaceholderRegex = Regex(
    """[!！](?:\[[^]]*]|图片|配图|插图|图像|image|photo|figure)""",
    RegexOption.IGNORE_CASE,
)

internal fun articleDetailPageContent(
    page: Int,
    summary: String?,
    original: String?,
    storedOriginal: String? = null,
    originalImageUrls: List<String> = emptyList(),
    isRemoteSnapshot: Boolean = false,
): String = when (page) {
    // The reading area is the summary page: a synced article that has no AI summary yet still shows
    // the body it already stored (the list card previews the same body) instead of an empty state.
    0 -> summary.normalizedSummaryMarkdown()
        ?: articleOriginalMarkdown(storedOriginal, original, isRemoteSnapshot)
            .normalizedOriginalMarkdownOrFallback("暂无摘要内容", originalImageUrls)
    else -> articleOriginalMarkdown(storedOriginal, original, isRemoteSnapshot)
        .normalizedOriginalMarkdownOrFallback("暂无原文内容", originalImageUrls)
}

private fun String?.normalizedMarkdownOrFallback(fallback: String): String =
    this?.trim()?.takeIf { it.isNotBlank() } ?: fallback

private fun String?.normalizedSummaryMarkdown(): String? {
    val content = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val cleaned = content
        .lines()
        .dropWhile { line -> line.trim().isBlank() || line.trim().matches(MarkdownHeadingRegex) }
        .filterNot { line -> line.trim().matches(generatedSummaryGuideHeadingRegex) }
        .joinToString("\n")
        .replace(ExcessiveMarkdownNewlinesRegex, "\n\n")
        .trim()
    return cleaned.ifBlank { content }.replace(generatedArticleSourceFooterRegex, "").trim().ifBlank { null }
}

private val generatedArticleSourceFooterRegex = Regex(
    """(?:^|\n{2,})[ \t]*来源[ \t]*[:：][ \t]*\[原文]\((?:<[^>\n]+>|[^\n)]+)\)[ \t]*$""",
)

private fun String?.normalizedOriginalMarkdownOrFallback(fallback: String, imageUrls: List<String>): String {
    val content = normalizedMarkdownOrFallback(fallback)
    if (content == fallback || imageUrls.isEmpty() || !content.hasMalformedImagePlaceholder()) return content
    return normalizeArticleMarkdownImages(content, imageUrls)
}

private val generatedSummaryGuideHeadingRegex = Regex(
    """#{1,6}\s*(?:标题|核心内容|核心观点|核心观点[:：]?.*|核心内容[:：]?.*)\s*""",
)

private fun String.hasMalformedImagePlaceholder(): Boolean =
    MalformedImagePlaceholderRegex.containsMatchIn(this)

internal fun canManuallyRefreshArticle(isRefreshing: Boolean, articleStatus: String?): Boolean = true

/** Processing needs stored text; a preview is enough, an empty or unparsed article is not. */
internal fun canStartArticleAiProcess(isRefreshing: Boolean, hasProcessableText: Boolean): Boolean =
    !isRefreshing && hasProcessableText
