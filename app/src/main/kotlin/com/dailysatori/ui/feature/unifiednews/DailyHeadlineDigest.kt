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

internal data class HeadlineOverviewLayout(
    val lead: String?,
    val paragraphs: List<String>,
)

private fun isOverviewHeading(heading: String): Boolean {
    val normalized = normalizeOverviewHeadingKey(heading)
    return normalized in HeadlineOverviewHeadingKeys ||
        heading.contains("今日要闻") ||
        heading.contains("每日封面")
}

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

/** Keeps generated overview paragraphs (including legacy list-style overviews); never synthesises copy. */
internal fun dailyHeadlineOverview(content: String): List<String> {
    val lines = content.lines()
    val headingIndex = lines.indexOfFirst { line ->
        SummaryHeadingRegex.matchEntire(line)?.groupValues?.get(1)?.let(::isExplicitOverviewHeading) == true
    }
    if (headingIndex >= 0) {
        val region = lines.drop(headingIndex + 1).takeWhile { !it.trimStart().startsWith("#") }
        return extractExplicitOverviewBlocks(region)
    }
    val region = lines
        .dropWhile { it.isBlank() || isWrapperOverviewTitleLine(it) }
        .takeWhile { !it.trimStart().startsWith("#") }
    val paragraphs = collectProseOverviewParagraphs(region)
    return paragraphs.takeIf { it.isNotEmpty() } ?: listOfNotNull(
        unifiedNewsBriefingContent(content).lead,
    )
}

private val HeadlineOverviewHeadingKeys = setOf("今日要闻", "每日封面", "综述", "今日总览")
private val OverviewMarkdownLinkRegex = Regex("""\[([^\]]+)]\([^)]+\)""")
private val OverviewItalicAsteriskRegex = Regex("""\*([^*\n]+)\*""")
private val OverviewItalicUnderscoreRegex = Regex("""(?<![\p{L}\p{N}])_([^_\n]+)_(?![\p{L}\p{N}])""")
private val OverviewAbbreviations = setOf(
    "e.g", "i.e", "vs", "dr", "mr", "mrs", "ms", "jr", "sr", "inc", "ltd", "no", "fig", "st", "prof", "etc", "u.s", "u.k",
)

private const val MaxLeadSentenceCodePoints = 90
private const val MinBalancedOverviewSplitCodePoints = 75
private const val MaxTwoParagraphOverviewCodePoints = 260
private const val MinOverviewChunkCodePoints = 25
private const val TargetLongOverviewParagraphCodePoints = 140
private const val MinLongOverviewFlushCodePoints = 60
private const val MinTrailingOverviewChunkCodePoints = 40

private fun normalizeOverviewHeadingKey(heading: String): String =
    stripLeadingHeadingDecorators(heading).trimEnd(':', '：', ' ')

private fun isExplicitOverviewHeading(heading: String): Boolean =
    normalizeOverviewHeadingKey(heading) in HeadlineOverviewHeadingKeys

private fun isWrapperOverviewTitleLine(line: String): Boolean {
    val match = SummaryHeadingRegex.matchEntire(line) ?: return false
    return normalizeOverviewHeadingKey(match.groupValues[1]).startsWith("今日统一新闻总结")
}

private fun extractExplicitOverviewBlocks(region: List<String>): List<String> {
    val blocks = mutableListOf<String>()
    val currentProse = mutableListOf<String>()
    fun flushProse() {
        if (currentProse.isEmpty()) return
        val text = joinWrappedOverviewLines(currentProse).withoutBriefingMarkdownForOverview()
        if (text.isNotBlank()) blocks += text
        currentProse.clear()
    }
    region.forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> flushProse()
            PointListPrefixRegex.containsMatchIn(line) -> {
                flushProse()
                val itemText = line.replace(PointListPrefixRegex, "").withoutBriefingMarkdownForOverview()
                if (itemText.isNotBlank()) blocks += itemText
            }
            else -> currentProse += line.removePrefix(">").trim()
        }
    }
    flushProse()
    return blocks
}

private fun collectProseOverviewParagraphs(region: List<String>): List<String> {
    val paragraphs = mutableListOf<String>()
    val current = mutableListOf<String>()
    fun flush() {
        if (current.isEmpty()) return
        val text = joinWrappedOverviewLines(current).withoutBriefingMarkdownForOverview()
        if (text.isNotBlank()) paragraphs += text
        current.clear()
    }
    region.forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            PointListPrefixRegex.containsMatchIn(line) -> flush()
            else -> current += line.removePrefix(">").trim()
        }
    }
    flush()
    return paragraphs
}

private fun joinWrappedOverviewLines(lines: List<String>): String = buildString {
    lines.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        if (isNotEmpty() && needsSpaceBetweenLines(last(), trimmed.first())) append(' ')
        append(trimmed)
    }
}

private fun needsSpaceBetweenLines(previous: Char, next: Char): Boolean =
    previous.code < 128 && next.code < 128 &&
        (previous.isLetterOrDigit() || previous in ",.;:!?)") &&
        (next.isLetterOrDigit() || next == '(')

private fun String.withoutBriefingMarkdownForOverview(): String = replace(OverviewMarkdownLinkRegex, "$1")
    .let(::visibleUnifiedNewsTextWithoutCitation)
    .replace(OverviewItalicAsteriskRegex, "$1")
    .replace(OverviewItalicUnderscoreRegex, "$1")
    .trim()

/** Structures overview text into a subtle lead sentence plus natural paragraphs without fabricating or clipping copy. */
internal fun headlineOverviewLayout(overview: List<String>): HeadlineOverviewLayout? {
    val blocks = overview.map { it.trim() }.filter { it.isNotEmpty() }
    if (blocks.isEmpty()) return null
    val firstBlock = blocks.first()
    val extraBlocks = blocks.drop(1)
    val firstRanges = naturalSentenceRanges(firstBlock)
    val firstSentence = firstBlock.sliceRange(firstRanges.first())
    val leadFits = firstSentence.codePointCount(0, firstSentence.length) <= MaxLeadSentenceCodePoints
    if (extraBlocks.isEmpty()) {
        if (firstRanges.size == 1) {
            return if (leadFits) HeadlineOverviewLayout(lead = firstSentence, paragraphs = emptyList())
            else HeadlineOverviewLayout(lead = null, paragraphs = listOf(firstBlock))
        }
        return if (leadFits) {
            HeadlineOverviewLayout(
                lead = firstSentence,
                paragraphs = groupSentenceRangesIntoParagraphs(firstBlock, firstRanges.drop(1)),
            )
        } else {
            HeadlineOverviewLayout(
                lead = null,
                paragraphs = groupSentenceRangesIntoParagraphs(firstBlock, firstRanges),
            )
        }
    }
    if (!leadFits) {
        return HeadlineOverviewLayout(lead = null, paragraphs = blocks.flatMap(::splitOversizedOverviewBlock))
    }
    val remaining = buildList {
        if (firstRanges.size > 1) {
            val tail = firstBlock.sliceRange(firstRanges[1].first..firstRanges.last().last)
            if (tail.isNotEmpty()) addAll(splitOversizedOverviewBlock(tail))
        }
        extraBlocks.forEach { addAll(splitOversizedOverviewBlock(it)) }
    }
    return HeadlineOverviewLayout(lead = firstSentence, paragraphs = remaining)
}

private fun splitOversizedOverviewBlock(block: String): List<String> {
    if (block.codePointCount(0, block.length) <= MaxTwoParagraphOverviewCodePoints) return listOf(block)
    val ranges = naturalSentenceRanges(block)
    return if (ranges.size >= 2) groupSentenceRangesIntoParagraphs(block, ranges) else listOf(block)
}

private fun groupSentenceRangesIntoParagraphs(source: String, ranges: List<IntRange>): List<String> {
    if (ranges.isEmpty()) return emptyList()
    if (ranges.size == 1) return listOf(source.sliceRange(ranges.first()))
    val full = source.sliceRange(ranges.first().first..ranges.last().last)
    val totalCp = full.codePointCount(0, full.length)
    if (totalCp < MinBalancedOverviewSplitCodePoints) return listOf(full)
    if (totalCp <= MaxTwoParagraphOverviewCodePoints) {
        return splitIntoTwoBalancedParagraphs(source, ranges) ?: listOf(full)
    }
    return groupLongOverviewSentences(source, ranges)
}

private fun splitIntoTwoBalancedParagraphs(source: String, ranges: List<IntRange>): List<String>? {
    var bestSplit = -1
    var bestDiff = Int.MAX_VALUE
    for (split in 1 until ranges.size) {
        val left = source.sliceRange(ranges.first().first..ranges[split - 1].last)
        val right = source.sliceRange(ranges[split].first..ranges.last().last)
        val leftCp = left.codePointCount(0, left.length)
        val rightCp = right.codePointCount(0, right.length)
        if (leftCp < MinOverviewChunkCodePoints || rightCp < MinOverviewChunkCodePoints) continue
        val diff = kotlin.math.abs(leftCp - rightCp)
        if (diff < bestDiff) {
            bestDiff = diff
            bestSplit = split
        }
    }
    if (bestSplit < 0) return null
    return listOf(
        source.sliceRange(ranges.first().first..ranges[bestSplit - 1].last),
        source.sliceRange(ranges[bestSplit].first..ranges.last().last),
    )
}

private fun groupLongOverviewSentences(source: String, ranges: List<IntRange>): List<String> {
    val chunks = mutableListOf<Pair<Int, Int>>()
    var chunkStart = ranges.first().first
    var chunkEnd = ranges.first().last
    for (index in 1 until ranges.size) {
        val nextRange = ranges[index]
        val currentText = source.sliceRange(chunkStart..chunkEnd)
        val currentCp = currentText.codePointCount(0, currentText.length)
        val candidateText = source.sliceRange(chunkStart..nextRange.last)
        val candidateCp = candidateText.codePointCount(0, candidateText.length)
        if (currentCp >= MinLongOverviewFlushCodePoints && candidateCp > TargetLongOverviewParagraphCodePoints) {
            chunks += chunkStart to chunkEnd
            chunkStart = nextRange.first
            chunkEnd = nextRange.last
        } else {
            chunkEnd = nextRange.last
        }
    }
    chunks += chunkStart to chunkEnd
    if (chunks.size >= 2) {
        val last = chunks.last()
        val lastText = source.sliceRange(last.first..last.second)
        if (lastText.codePointCount(0, lastText.length) < MinTrailingOverviewChunkCodePoints) {
            val prev = chunks[chunks.lastIndex - 1]
            chunks[chunks.lastIndex - 1] = prev.first to last.second
            chunks.removeAt(chunks.lastIndex)
        }
    }
    return chunks.map { (start, end) -> source.sliceRange(start..end) }
}

private fun naturalSentenceRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var start = 0
    while (start < text.length && text[start].isWhitespace()) start++
    var index = start
    while (index < text.length) {
        val ch = text[index]
        val isBoundary = ch in "。！？" || (ch in ".!?" && isAsciiSentenceBoundary(text, index))
        if (!isBoundary) {
            index++
            continue
        }
        var end = index
        while (end + 1 < text.length && text[end + 1] in "。！？!?") end++
        while (end + 1 < text.length && text[end + 1] in "”’」』）】》\"')]") end++
        if (start <= end) ranges += start..end
        index = end + 1
        while (index < text.length && text[index].isWhitespace()) index++
        start = index
    }
    if (start < text.length) {
        var end = text.lastIndex
        while (end >= start && text[end].isWhitespace()) end--
        if (start <= end) ranges += start..end
    }
    return ranges.ifEmpty { listOf(0..text.lastIndex) }
}

private fun isAsciiSentenceBoundary(text: String, index: Int): Boolean {
    val ch = text[index]
    val next = text.getOrNull(index + 1)
    if (ch == '.') {
        if (text.getOrNull(index - 1) == '.' || next == '.') return false
        val prev = text.getOrNull(index - 1)
        if (prev != null && prev.isDigit() && next != null && next.isDigit()) return false
        if (next != null && (next.isLetterOrDigit() || next == '/' || next == '_')) return false
        if (isAbbreviationPeriod(text, index)) return false
    }
    var after = index + 1
    while (after < text.length && text[after] in "”’」』）】》\"')]") after++
    val follower = text.getOrNull(after) ?: return true
    return follower.isWhitespace() || follower.code >= 128
}

private fun isAbbreviationPeriod(text: String, dotIndex: Int): Boolean {
    var start = dotIndex - 1
    while (start >= 0 && (text[start].isLetter() || text[start] == '.')) start--
    val token = text.substring(start + 1, dotIndex)
    if (token.isEmpty()) return false
    if (token.length == 1 && token[0].isUpperCase()) return true
    return token.lowercase() in OverviewAbbreviations
}

private fun String.sliceRange(range: IntRange): String = substring(range.first, range.last + 1).trim()

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
