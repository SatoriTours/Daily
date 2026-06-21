package com.dailysatori.ui.feature.article

import com.dailysatori.service.parser.normalizeArticleMarkdownImages

internal fun articleDetailPageContent(
    page: Int,
    summary: String?,
    original: String?,
    originalImageUrls: List<String> = emptyList(),
): String = when (page) {
    0 -> summary.normalizedSummaryMarkdownOrFallback("暂无摘要内容")
    else -> original.normalizedOriginalMarkdownOrFallback("暂无原文内容", originalImageUrls)
}

private fun String?.normalizedMarkdownOrFallback(fallback: String): String =
    this?.trim()?.takeIf { it.isNotBlank() } ?: fallback

private fun String?.normalizedSummaryMarkdownOrFallback(fallback: String): String {
    val content = normalizedMarkdownOrFallback(fallback)
    if (content == fallback) return content
    val cleaned = content
        .lines()
        .dropWhile { line -> line.trim().isBlank() || line.trim().matches(Regex("""#{1,6}\s+.+""")) }
        .filterNot { line -> line.trim().matches(generatedSummaryGuideHeadingRegex) }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    return cleaned.ifBlank { content }
}

private fun String?.normalizedOriginalMarkdownOrFallback(fallback: String, imageUrls: List<String>): String {
    val content = normalizedMarkdownOrFallback(fallback)
    if (content == fallback || imageUrls.isEmpty() || !content.hasMalformedImagePlaceholder()) return content
    return normalizeArticleMarkdownImages(content, imageUrls)
}

private val generatedSummaryGuideHeadingRegex = Regex(
    """#{1,6}\s*(?:标题|核心内容|核心观点|核心观点[:：]?.*|核心内容[:：]?.*)\s*""",
)

private fun String.hasMalformedImagePlaceholder(): Boolean =
    Regex("""[!！](?:\[[^]]*]|图片|配图|插图|图像|image|photo|figure)""", RegexOption.IGNORE_CASE).containsMatchIn(this)

internal fun canManuallyRefreshArticle(isRefreshing: Boolean, articleStatus: String?): Boolean = true
