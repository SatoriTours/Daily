package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.hotnews.HotNewsItem
import com.dailysatori.shared.db.Unified_news_source

internal val PreviewImageRegex = Regex("""!\[[^]]*]\([^)]*\)""")
internal val PreviewLinkRegex = Regex("""\[([^]]+)]\([^)]*\)""")
internal val PreviewCitationRegex = Regex("""\[([RCDFH]\d+)]""")
private val PreviewListRegex = Regex("""^\s*(?:[-*+]|\d+[.)、])\s+""")
private val PreviewWhitespaceRegex = Regex("""\s+""")
private val PreviewFactsLabelRegex = Regex("""\*\*(?:新闻事实|核心内容)：\*\*\s*""")
internal val RepositoryNavigationMarkers = listOf("go to file", "open more actions menu", "latest commit", "folders and files")

internal fun unifiedNewsArticlePreview(
    source: Unified_news_source,
    briefing: String,
    appearances: List<HotNewsItem>,
    articleSummary: String? = null,
): String {
    val isRepository = source.source_filename?.startsWith("github:") == true ||
        runCatching { java.net.URI(source.source_url.orEmpty()).host }.getOrNull() in setOf("github.com", "www.github.com")
    val processedPreview = articleSummary.orEmpty().substringBefore("**为什么重要（分析）：**")
        .replace(PreviewFactsLabelRegex, "").toNewsPreview(source.title, isRepository)
    if (processedPreview.isNotBlank()) return processedPreview.take(600)
    val citedText = briefing.lineSequence().filter { line ->
        PreviewCitationRegex.findAll(line).any { it.groupValues[1] == source.ref_key }
    }.joinToString(" ")
    val candidates = appearances.map { it.displaySummary } + source.summary + citedText +
        appearances.map { it.originalContent }
    return candidates.asSequence().map { it.toNewsPreview(source.title, isRepository) }
        .firstOrNull { it.isNotBlank() }.orEmpty().take(600)
}

private fun String.toNewsPreview(title: String, isRepository: Boolean): String =
    takeIf(::isChineseOriginalContent).orEmpty().take(4_000)
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
    .takeIf(::isChineseOriginalContent)
    .orEmpty()
    .takeUnless { preview ->
        isRepository && RepositoryNavigationMarkers.count { preview.contains(it, ignoreCase = true) } >= 2
    }.orEmpty()
