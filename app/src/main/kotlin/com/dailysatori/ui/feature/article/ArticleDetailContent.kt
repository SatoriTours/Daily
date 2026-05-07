package com.dailysatori.ui.feature.article

internal fun articleDetailPageContent(
    page: Int,
    summary: String?,
    original: String?,
): String = when (page) {
    0 -> summary.normalizedMarkdownOrFallback("暂无摘要内容")
    else -> original.normalizedMarkdownOrFallback("暂无原文内容")
}

private fun String?.normalizedMarkdownOrFallback(fallback: String): String =
    this?.trim()?.takeIf { it.isNotBlank() } ?: fallback

internal fun canManuallyRefreshArticle(isRefreshing: Boolean, articleStatus: String?): Boolean = true
