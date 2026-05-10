package com.dailysatori.ui.feature.article

import com.dailysatori.service.parser.normalizeArticleMarkdownImages

internal fun articleDetailPageContent(
    page: Int,
    summary: String?,
    original: String?,
    originalImageUrls: List<String> = emptyList(),
): String = when (page) {
    0 -> summary.normalizedMarkdownOrFallback("暂无摘要内容")
    else -> original.normalizedOriginalMarkdownOrFallback("暂无原文内容", originalImageUrls)
}

private fun String?.normalizedMarkdownOrFallback(fallback: String): String =
    this?.trim()?.takeIf { it.isNotBlank() } ?: fallback

private fun String?.normalizedOriginalMarkdownOrFallback(fallback: String, imageUrls: List<String>): String {
    val content = normalizedMarkdownOrFallback(fallback)
    if (content == fallback || imageUrls.isEmpty() || !content.hasMalformedImagePlaceholder()) return content
    return normalizeArticleMarkdownImages(content, imageUrls)
}

private fun String.hasMalformedImagePlaceholder(): Boolean =
    Regex("""[!！](?:\[[^]]*]|图片|配图|插图|图像|image|photo|figure)""", RegexOption.IGNORE_CASE).containsMatchIn(this)

internal fun canManuallyRefreshArticle(isRefreshing: Boolean, articleStatus: String?): Boolean = true
