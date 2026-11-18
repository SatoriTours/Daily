package com.dailysatori.ui.component.card

import com.dailysatori.service.parser.sanitizeArticleAiTitle
import com.dailysatori.shared.db.Article

fun articleDisplayTitle(article: Article): String = listOfNotNull(
    sanitizeArticleAiTitle(article.ai_title),
    sanitizeArticleAiTitle(article.title),
    articleSourceTitle(article.title),
    articleDisplayDomain(article.url),
).firstOrNull { it.isNotBlank() }.orEmpty()

/**
 * A source title comes from the feed or page itself, so it is read as written even when it is longer
 * than an AI-generated title may be; only the first line is taken and placeholder text is dropped.
 */
internal fun articleSourceTitle(value: String?): String? {
    val firstLine = value?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (firstLine.isBlank() || firstLine == "正在加载...") return null
    val cleaned = firstLine.trimStart('#').trim().removeSurrounding("“", "”").trim()
    if (cleaned.isBlank() || cleaned == "正在加载...") return null
    return cleaned
}

fun articleDisplayDomain(url: String?): String {
    if (url.isNullOrBlank()) return "文章详情"
    return url.removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .removePrefix("www.")
        .ifBlank { "文章详情" }
}
