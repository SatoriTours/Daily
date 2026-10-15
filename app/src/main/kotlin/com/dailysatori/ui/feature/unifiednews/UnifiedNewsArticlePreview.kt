package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.hotnews.HotNewsItem
import com.dailysatori.shared.db.Unified_news_source

private val PreviewImageRegex = Regex("""!\[[^]]*]\([^)]*\)""")
private val PreviewLinkRegex = Regex("""\[([^]]+)]\([^)]*\)""")
private val PreviewCitationRegex = Regex("""\[([RCDFH]\d+)]""")
private val PreviewListRegex = Regex("""^\s*(?:[-*+]|\d+[.)、])\s+""")
private val PreviewWhitespaceRegex = Regex("""\s+""")
private val PreviewFactsLabelRegex = Regex("""\*\*(?:新闻事实|核心内容)：\*\*\s*""")

internal fun unifiedNewsArticlePreview(
    source: Unified_news_source,
    briefing: String,
    appearances: List<HotNewsItem>,
    articleSummary: String? = null,
): String {
    val processedPreview = articleSummary.orEmpty().substringBefore("**为什么重要（分析）：**")
        .replace(PreviewFactsLabelRegex, "").toNewsPreview(source.title)
    if (processedPreview.isNotBlank()) return processedPreview.take(600)
    val citedText = briefing.lineSequence().filter { line ->
        PreviewCitationRegex.findAll(line).any { it.groupValues[1] == source.ref_key }
    }.joinToString(" ")
    val candidates = listOf(source.summary) + appearances.map { it.displaySummary } +
        appearances.map { it.originalContent } + citedText
    val previews = candidates.map { it.toNewsPreview(source.title) }.filter { it.isNotBlank() }
    return (previews.firstOrNull { it.length >= 80 } ?: previews.maxByOrNull { it.length }).orEmpty().take(600)
}

private fun String.toNewsPreview(title: String): String = take(4_000)
    .replace(PreviewImageRegex, "")
    .replace(PreviewLinkRegex, "$1")
    .replace(PreviewCitationRegex, "")
    .withoutUnifiedNewsBasicMarkdown()
    .lineSequence()
    .map { it.trim().removePrefix(">").trim().replace(PreviewListRegex, "") }
    .filter { line ->
        line.isNotBlank() && line != title.trim() && !line.startsWith("#") &&
            !line.startsWith("```") && !line.all { it in ".…-* _" }
    }
    .joinToString(" ")
    .replace(PreviewWhitespaceRegex, " ")
    .normalizeUnifiedNewsPunctuationSpacing()
    .trim()
