package com.dailysatori.ui.component.card

import com.dailysatori.service.parser.sanitizeArticleAiTitle
import com.dailysatori.shared.db.Article

fun articleDisplayTitle(article: Article): String = listOfNotNull(
    sanitizeArticleAiTitle(article.ai_title),
    sanitizeArticleAiTitle(article.title),
    articleDisplayDomain(article.url),
).firstOrNull { it.isNotBlank() }.orEmpty()

fun articleDisplayDomain(url: String?): String {
    if (url.isNullOrBlank()) return "文章详情"
    return url.removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .removePrefix("www.")
        .ifBlank { "文章详情" }
}
