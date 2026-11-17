package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.component.card.articleDisplayDomain

internal data class DailyHeadlineStory(
    val title: String,
    val description: String?,
    val source: Unified_news_source?,
    val paragraphs: List<String> = emptyList(),
    val sources: List<Unified_news_source> = emptyList(),
    val citations: List<String> = emptyList(),
    val unmatchedCitations: List<String> = emptyList(),
)

internal data class DailyHeadlineDigest(
    val overview: List<String>,
    val stories: List<DailyHeadlineStory>,
    val remaining: String = "",
)

/** Reads the overview, only the 今日要点 stories, and the other generated sections kept separate. */
internal fun dailyHeadlineDigest(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    articleSummariesBySource: Map<Long, String> = emptyMap(),
    query: String = "",
): DailyHeadlineDigest {
    val parsed = parseDailyHeadlineSummary(summary.content)
    return DailyHeadlineDigest(
        overview = if (query.isBlank()) dailyHeadlineOverview(summary.content) else emptyList(),
        stories = dailyHeadlineStories(summary, sources, articleSummariesBySource, query),
        remaining = if (query.isBlank()) parsed.remaining else "",
    )
}

private data class HeadlinePoint(val text: String, val citations: List<String>)
private data class HeadlineSummary(val points: List<HeadlinePoint>, val remaining: String)
private data class SummarySection(val heading: String, val lines: List<String>)

private val SummaryHeadingRegex = Regex("""^\s*#{1,6}\s+(.+?)\s*$""")
private val PointCitationRegex = Regex("""\[([RCDFH]\d+)]""")
private val PointListPrefixRegex = Regex("""^\s*(?:[-*+]|\d+[.)、])\s+""")
private val PointWhitespaceRegex = Regex("""\s+""")

private fun parseDailyHeadlineSummary(content: String): HeadlineSummary {
    val sections = summarySections(content)
    val pointSections = sections.filter { it.heading.contains("今日要点") }.ifEmpty {
        sections.filter { isOtherSection(it.heading) && !isAnalysisHeading(it.heading) }
    }.ifEmpty {
        sections.filter { it.heading.isBlank() || it.heading.contains("今日统一新闻总结") }
    }
    val points = pointSections.flatMap { section ->
        val listOnly = section.heading.isBlank() || section.heading.contains("今日统一新闻总结")
        section.lines.mapNotNull { line -> headlinePoint(listOnly, line) }
    }.distinctBy { normalizeStoryTitle(it.text) }
    val remaining = sections.filter { section ->
        isOtherSection(section.heading) && pointSections.none { it == section }
    }.joinToString("\n\n") { section -> section.asMarkdown() }.trim()
    return HeadlineSummary(points, remaining)
}

private fun summarySections(content: String): List<SummarySection> {
    val sections = mutableListOf<SummarySection>()
    var heading = ""
    val lines = mutableListOf<String>()
    fun flush() {
        if (lines.any { it.isNotBlank() }) sections += SummarySection(heading, lines.toList())
        lines.clear()
    }
    content.lines().forEach { raw ->
        val match = SummaryHeadingRegex.matchEntire(raw)
        if (match != null) {
            flush()
            heading = match.groupValues[1].trim()
        } else {
            lines += raw
        }
    }
    flush()
    return sections
}

private fun SummarySection.asMarkdown(): String =
    if (heading.isBlank()) lines.joinToString("\n").trim() else "## $heading\n" + lines.joinToString("\n").trim()

private fun isOverviewHeading(heading: String): Boolean =
    heading.contains("今日要闻") || heading.contains("每日封面")

private fun isPointsHeading(heading: String): Boolean = heading.contains("今日要点")

private fun isOtherSection(heading: String): Boolean =
    heading.isNotBlank() && !isOverviewHeading(heading) && !isPointsHeading(heading) &&
        !heading.contains("今日统一新闻总结")

private fun isAnalysisHeading(heading: String): Boolean =
    heading.contains("重要变化") || heading.contains("值得关注")

private fun headlinePoint(requireList: Boolean, raw: String): HeadlinePoint? {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || trimmed.startsWith("#")) return null
    if (requireList && !PointListPrefixRegex.containsMatchIn(trimmed)) return null
    val citations = PointCitationRegex.findAll(trimmed).map { it.groupValues[1] }.distinct().toList()
    if (citations.isEmpty()) return null
    val text = visibleUnifiedNewsTextWithoutCitation(trimmed.replace(PointListPrefixRegex, ""))
        .replace(PointWhitespaceRegex, " ")
        .trim()
    return text.takeIf { it.isNotBlank() }?.let { HeadlinePoint(it, citations) }
}

/** Keeps up to two generated overview paragraphs; never synthesises copy in the UI. */
internal fun dailyHeadlineOverview(content: String): List<String> {
    val lines = content.lines()
    val headingIndex = lines.indexOfFirst { DailyOverviewHeadingRegex.matches(it) }
    val region = if (headingIndex >= 0) {
        lines.drop(headingIndex + 1).takeWhile { !it.trimStart().startsWith("#") }
    } else {
        lines.takeWhile { !it.trimStart().startsWith("#") }
    }
    val paragraphs = mutableListOf<String>()
    val current = mutableListOf<String>()
    fun flush() {
        if (current.isEmpty()) return
        val text = current.joinToString("").withoutBriefingMarkdownForOverview()
        if (text.isNotBlank()) paragraphs += text
        current.clear()
    }
    region.forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            PointListPrefixRegex.containsMatchIn(line) -> flush()
            else -> current += line
        }
    }
    flush()
    return (paragraphs.takeIf { it.isNotEmpty() } ?: listOfNotNull(
        unifiedNewsBriefingContent(content).lead,
    )).take(2)
}

private val DailyOverviewHeadingRegex = Regex("""^\s*#{1,6}\s*(?:🗞️\s*)?(?:今日要闻|每日封面)\s*$""")

private fun String.withoutBriefingMarkdownForOverview(): String = visibleUnifiedNewsTextWithoutCitation(this)
    .replace(Regex("""\*(.*?)\*"""), "$1")
    .replace(Regex("""_(.*?)_"""), "$1")

internal fun dailyHeadlineStories(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    articleSummariesBySource: Map<Long, String> = emptyMap(),
    query: String = "",
): List<DailyHeadlineStory> {
    val ownSources = sources.filter { it.summary_id == summary.id }
    val stories = parseDailyHeadlineSummary(summary.content).points.mapNotNull { point ->
        headlinePointStory(point, ownSources, articleSummariesBySource)
    }.distinctBy { normalizeStoryTitle(it.title) }
    val keyword = query.trim()
    if (keyword.isBlank()) return stories
    val matchingStories = stories.filter { storyMatches(it, keyword) }
    val representedSourceIds = matchingStories.flatMap { it.sources }.map { it.id }.toSet()
    val sourceStories = ownSources.filterNot { it.id in representedSourceIds }
        .mapNotNull { sourceStory(it, articleSummariesBySource) }
        .filter { storyMatches(it, keyword) }
    return (matchingStories + sourceStories).distinctBy { normalizeStoryTitle(it.title) }
}

private fun headlinePointStory(
    point: HeadlinePoint,
    ownSources: List<Unified_news_source>,
    articleSummariesBySource: Map<Long, String>,
): DailyHeadlineStory? {
    val (title, body) = splitHeadlinePoint(point.text)
    val matched = point.citations.mapNotNull { citation -> ownSources.firstOrNull { it.ref_key == citation } }
    val unmatched = point.citations.filter { citation -> ownSources.none { it.ref_key == citation } }
    val sourceParagraphs = matched.flatMap { source ->
        unifiedNewsReadingParagraphs(source, articleSummariesBySource[source.id])
    }
    val paragraphs = mergeStoryParagraphs(title, body, sourceParagraphs)
    if (title.isBlank() || paragraphs.isEmpty()) return null
    return DailyHeadlineStory(title, paragraphs.first(), matched.firstOrNull(), paragraphs, matched, point.citations, unmatched)
}

private fun storyMatches(story: DailyHeadlineStory, keyword: String): Boolean =
    listOf(story.title, story.description, story.paragraphs.joinToString(" ")).any {
        it?.contains(keyword, ignoreCase = true) == true
    } || story.sources.any { source ->
        listOf(source.title, source.summary, source.ref_key, source.source_filename)
            .any { it?.contains(keyword, ignoreCase = true) == true }
    }

private fun sourceStory(source: Unified_news_source, articleSummariesBySource: Map<Long, String>): DailyHeadlineStory? {
    val paragraphs = unifiedNewsReadingParagraphs(source, articleSummariesBySource[source.id])
    if (paragraphs.isEmpty()) return null
    return DailyHeadlineStory(source.title.trim(), paragraphs.first(), source, paragraphs, listOf(source), listOf(source.ref_key))
}

private fun splitHeadlinePoint(text: String): Pair<String, String> {
    val colon = text.indexOfFirst { it == '：' || it == ':' }
    if (colon in 2..60) {
        return text.take(colon).trim() to text.drop(colon + 1).trim()
    }
    val sentenceEnd = text.indexOfFirst { it in "。！？!?" }
    return if (sentenceEnd in 12 until text.lastIndex) {
        text.take(sentenceEnd + 1).trim() to text.drop(sentenceEnd + 1).trim()
    } else {
        text.trim() to ""
    }
}

private fun mergeStoryParagraphs(title: String, body: String, sourceParagraphs: List<String>): List<String> {
    val merged = mutableListOf<String>()
    (listOf(body) + sourceParagraphs).forEach { raw ->
        val paragraph = raw.trim()
        if (paragraph.isBlank() || paragraph == title.trim()) return@forEach
        if (merged.any { it.contains(paragraph) }) return@forEach
        merged.removeAll { paragraph.contains(it) }
        merged += paragraph
    }
    return merged
}

private fun normalizeStoryTitle(title: String): String = title
    .replace(PointWhitespaceRegex, "")
    .replace(Regex("""[，。！？、；：,.!?;:]"""), "")

/** Full generated reading text for a story; no Chinese-only filter and no silent character cut. */
internal fun unifiedNewsReadingParagraphs(
    source: Unified_news_source,
    articleSummary: String?,
): List<String> {
    val raw = articleSummary?.takeIf { it.isNotBlank() } ?: source.summary
    if (raw.isBlank()) return emptyList()
    val isRepository = source.source_filename?.startsWith("github:") == true ||
        runCatching { java.net.URI(source.source_url.orEmpty()).host }.getOrNull() in setOf("github.com", "www.github.com")
    return raw.toReadingParagraphs(source.title, isRepository)
}

private val ReadingAnalysisLabelRegex = Regex("""\*\*(?:新闻事实|核心内容|为什么重要（分析）)\s*[：:]\*\*""")
private val ReadingParagraphSplitRegex = Regex("""\n\s*\n""")
private val ReadingHtmlTagRegex = Regex("""<[^>]+>""")
private val ReadingWhitespaceRegex = Regex("""\s+""")

private fun String.toReadingParagraphs(title: String, isRepository: Boolean): List<String> {
    val cleaned = replace(ReadingHtmlTagRegex, " ")
        .replace(PreviewImageRegex, "")
        .replace(PreviewLinkRegex, "$1")
        .replace(PreviewCitationRegex, "")
        .replace(ReadingAnalysisLabelRegex, "\n\n")
    val paragraphs = cleaned.split(ReadingParagraphSplitRegex).mapNotNull { block ->
        block.lineSequence()
            .map { it.trim().removePrefix(">").trim().replace(PointListPrefixRegex, "").trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("```") && !it.all { ch -> ch in ".…-* _" } }
            .joinToString(" ")
            .withoutUnifiedNewsBasicMarkdown()
            .replace(ReadingWhitespaceRegex, " ")
            .normalizeUnifiedNewsPunctuationSpacing()
            .trim()
            .takeIf { it.isNotBlank() && it != title.trim() && !it.isRepositoryNavNoise(isRepository) }
    }
    return paragraphs
}

private fun String.isRepositoryNavNoise(isRepository: Boolean): Boolean =
    isRepository && RepositoryNavigationMarkers.count { contains(it, ignoreCase = true) } >= 2

internal fun headlineSourceMeta(source: Unified_news_source): String = listOfNotNull(
    source.source_url?.takeIf { it.isNotBlank() }?.let(::articleDisplayDomain),
    unifiedNewsSourceTypeLabel(source.source_type),
).joinToString(" · ")

internal fun headlineStorySourceMeta(story: DailyHeadlineStory): String = buildList {
    story.sources.map(::headlineSourceMeta).distinct().takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" · ")) }
    if (story.unmatchedCitations.isNotEmpty()) add("未匹配引用 " + story.unmatchedCitations.joinToString(" "))
}.joinToString(" · ")
