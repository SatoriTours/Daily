package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.hotnews.HotNewsItem
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary

internal data class DailyHeadlineStory(
    val title: String,
    val description: String?,
    val source: Unified_news_source?,
)

internal fun dailyHeadlineStories(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    articleSummariesBySource: Map<Long, String> = emptyMap(),
    query: String = "",
): List<DailyHeadlineStory> {
    val ownSources = sources.filter { it.summary_id == summary.id }
    val stories = unifiedNewsBriefingContent(summary.content).points.map { point ->
        dailyHeadlineStory(point.text, ownSources.firstOrNull { it.ref_key == point.citation }, articleSummariesBySource)
    }
    val keyword = query.trim()
    if (keyword.isBlank()) return stories.take(3)
    val sourceResults = ownSources.map { dailyHeadlineStory(it.title, it, articleSummariesBySource) }
    return (stories + sourceResults).filter { story ->
        listOf(story.title, story.description, story.source?.title, story.source?.summary,
            story.source?.ref_key, story.source?.source_filename).any { it?.contains(keyword, ignoreCase = true) == true }
    }.distinctBy { it.source?.id?.toString() ?: it.title }
}

private fun dailyHeadlineStory(
    title: String,
    source: Unified_news_source?,
    articleSummariesBySource: Map<Long, String>,
): DailyHeadlineStory {
    val description = source?.let { unifiedNewsArticlePreview(it, "", emptyList(), articleSummariesBySource[it.id]) }
        ?.takeIf { it.isNotBlank() && it != title }
    return DailyHeadlineStory(title, description, source)
}

private val PreviewImageRegex = Regex("""!\[[^]]*]\([^)]*\)""")
private val PreviewLinkRegex = Regex("""\[([^]]+)]\([^)]*\)""")
private val PreviewCitationRegex = Regex("""\[([RCDFH]\d+)]""")
private val PreviewListRegex = Regex("""^\s*(?:[-*+]|\d+[.)、])\s+""")
private val PreviewWhitespaceRegex = Regex("""\s+""")
private val PreviewFactsLabelRegex = Regex("""\*\*(?:新闻事实|核心内容)：\*\*\s*""")
private val RepositoryNavigationMarkers = listOf("go to file", "open more actions menu", "latest commit", "folders and files")

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

private fun String.toNewsPreview(title: String, isRepository: Boolean): String = take(4_000)
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
    .takeUnless { preview ->
        isRepository && RepositoryNavigationMarkers.count { preview.contains(it, ignoreCase = true) } >= 2
    }.orEmpty()
