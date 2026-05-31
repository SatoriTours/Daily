package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle

private val ChineseCharacterRegex = Regex("[\\u4e00-\\u9fff]")
private val EnglishWordRegex = Regex("\\b[A-Za-z][A-Za-z'-]{2,}\\b")
private const val LOCAL_REPROCESS_LANGUAGE_SAMPLE_LIMIT = 4_000
private const val LOCAL_REPROCESS_CHINESE_THRESHOLD = 8
private const val LOCAL_REPROCESS_ENGLISH_WORD_THRESHOLD = 12

internal fun cleanRemoteArticleText(value: String?): String? =
    value?.trim()?.takeIf { it.isNotBlank() }

internal fun remoteArticleViewpointMarkdown(viewpoints: List<String>): String? {
    val cleanViewpoints = viewpoints.mapNotNull(::cleanRemoteArticleText)
    return cleanViewpoints
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n") { "- $it" }
        ?.let { "## 关键观点\n\n$it" }
}

internal fun remoteArticleLanguageSample(article: RemoteArticle): String = listOfNotNull(
    article.title,
    article.summary,
    article.viewpoints.joinToString("\n"),
    article.content,
).joinToString("\n").take(LOCAL_REPROCESS_LANGUAGE_SAMPLE_LIMIT)

internal fun hasEnoughChineseForLocalArticle(article: RemoteArticle): Boolean =
    ChineseCharacterRegex.findAll(remoteArticleLanguageSample(article)).count() >= LOCAL_REPROCESS_CHINESE_THRESHOLD

internal fun hasEnoughEnglishForLocalArticle(article: RemoteArticle): Boolean =
    EnglishWordRegex.findAll(remoteArticleLanguageSample(article)).count() >= LOCAL_REPROCESS_ENGLISH_WORD_THRESHOLD
