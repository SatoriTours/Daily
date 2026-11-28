package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.R
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class BriefingTitleSpec(
    val isToday: Boolean,
    val formattedMonthDay: String?,
)

internal data class BriefingKeyPoint(
    val markdown: String,
    val citations: List<String>,
)

internal sealed class BriefingSection {
    data class Overview(val markdown: String) : BriefingSection()
    data class Other(val title: String, val markdown: String) : BriefingSection()
    data class Untitled(val markdown: String) : BriefingSection()
}

internal sealed class BriefingDocument {
    data object Empty : BriefingDocument()
    data class Structured(
        val keyPoints: List<BriefingKeyPoint>,
        val sections: List<BriefingSection>,
    ) : BriefingDocument()
    data class Fallback(val rawMarkdown: String) : BriefingDocument()
}

private data class RawBriefingSection(
    val heading: String?,
    val lines: List<String>,
)

private val BriefingAtxHeadingRegex = Regex("""^\s*(#{1,6})\s+(.+?)\s*#*\s*$""")
private val BriefingFenceRegex = Regex("""^\s*(`{3,}|~{3,})""")
private val BriefingTopLevelListRegex = Regex("""^( ?(?:[-*+]\s+|\d+[.)]\s+|\d+、\s*))(.+)$""")
private val BriefingTableSeparatorRegex = Regex("""^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$""")
private val BriefingInlineCitationRegex = Regex("""\[([RCDFH]\d+)]""")
private val BriefingRefLinkDefinitionRegex = Regex("""^\s{0,3}\[([^\]\n]+)]:\s*\S+""")
private val BriefingWrapperTitleLineRegex = Regex("""^\s*#\s*今日统一新闻总结[^\n]*\n*""")

private data class PendingKeyPoint(
    val contentIndent: Int,
    val lines: MutableList<String>,
)

private val OverviewHeadingKeys = setOf("今日要闻", "每日封面", "综述", "今日总览")
private val KnownTopLevelHeadingKeys = OverviewHeadingKeys + setOf("今日要点", "重要变化", "值得关注")

internal fun briefingTitleSpec(
    summaryDate: String?,
    today: LocalDate = LocalDate.now(),
    locale: Locale = Locale.getDefault(),
): BriefingTitleSpec {
    val trimmed = summaryDate?.trim().orEmpty()
    if (trimmed.isEmpty()) return BriefingTitleSpec(isToday = false, formattedMonthDay = null)
    val parsed = runCatching { LocalDate.parse(trimmed) }.getOrNull()
    if (parsed == null) {
        return BriefingTitleSpec(isToday = trimmed == today.toString(), formattedMonthDay = null)
    }
    if (parsed == today) return BriefingTitleSpec(isToday = true, formattedMonthDay = null)
    val pattern = if (locale.language.equals("zh", ignoreCase = true)) "M月d日" else "MMM d"
    val formatted = runCatching { parsed.format(DateTimeFormatter.ofPattern(pattern, locale)) }.getOrNull()
    return BriefingTitleSpec(isToday = false, formattedMonthDay = formatted)
}

internal fun resolveBriefingTitle(
    summaryDate: String?,
    todayTitle: String,
    pastTitle: String,
    dateTitleTemplate: String,
    today: LocalDate = LocalDate.now(),
    locale: Locale = Locale.getDefault(),
): String {
    val spec = briefingTitleSpec(summaryDate, today, locale)
    if (spec.isToday) return todayTitle
    val monthDay = spec.formattedMonthDay ?: return pastTitle
    return runCatching { String.format(locale, dateTitleTemplate, monthDay) }.getOrDefault(pastTitle)
}

internal fun parseBriefingDocument(content: String): BriefingDocument {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return BriefingDocument.Empty
    if (collectReferenceLinkLabels(trimmed).isNotEmpty()) return BriefingDocument.Fallback(trimmed)

    val scanned = scanRawBriefingSections(trimmed) ?: return BriefingDocument.Fallback(trimmed)
    if (scanned.none { it.heading != null }) return BriefingDocument.Fallback(trimmed)

    val keyPoints = mutableListOf<BriefingKeyPoint>()
    val hasExplicitOverview = scanned.any { it.heading != null && isBriefingOverviewHeading(it.heading) }
    val sections = mutableListOf<BriefingSection>()

    for (raw in scanned) {
        val heading = raw.heading
        if (heading != null && isBriefingKeyPointsHeading(heading)) {
            val parsedPoints = parseBriefingKeyPoints(raw.lines) ?: return BriefingDocument.Fallback(trimmed)
            keyPoints += parsedPoints
            continue
        }
        val body = raw.lines.joinToString("\n").trim()
        if (body.isEmpty()) {
            if (heading != null && !isKnownBriefingHeading(heading)) {
                sections += BriefingSection.Other(stripLeadingHeadingDecorators(heading), "")
            }
            continue
        }
        sections += when {
            heading == null && !hasExplicitOverview -> BriefingSection.Overview(body)
            heading == null -> BriefingSection.Untitled(body)
            isBriefingOverviewHeading(heading) -> BriefingSection.Overview(body)
            else -> BriefingSection.Other(stripLeadingHeadingDecorators(heading), body)
        }
    }

    val hasBodyContent = keyPoints.isNotEmpty() || sections.any { it.hasBodyContent() }
    if (!hasBodyContent) return BriefingDocument.Fallback(trimmed)
    return BriefingDocument.Structured(keyPoints = keyPoints, sections = sections)
}

private fun BriefingSection.hasBodyContent(): Boolean = when (this) {
    is BriefingSection.Overview -> markdown.isNotBlank()
    is BriefingSection.Other -> markdown.isNotBlank()
    is BriefingSection.Untitled -> markdown.isNotBlank()
}

private fun scanRawBriefingSections(content: String): List<RawBriefingSection>? {
    val sections = mutableListOf<RawBriefingSection>()
    var currentHeading: String? = null
    val currentLines = mutableListOf<String>()
    var fenceChar: Char? = null
    var fenceLength = 0

    fun flush() {
        if (currentHeading != null || currentLines.any { it.isNotBlank() }) {
            sections += RawBriefingSection(currentHeading, currentLines.toList())
        }
        currentLines.clear()
    }

    for (line in content.lines()) {
        val fenceMatch = BriefingFenceRegex.find(line)
        if (fenceMatch != null) {
            val marker = fenceMatch.groupValues[1]
            if (fenceChar == null) {
                fenceChar = marker.first()
                fenceLength = marker.length
            } else if (marker.first() == fenceChar && marker.length >= fenceLength && line.trim() == marker) {
                fenceChar = null
                fenceLength = 0
            }
            currentLines += line
            continue
        }
        if (fenceChar != null) {
            currentLines += line
            continue
        }
        val headingMatch = BriefingAtxHeadingRegex.matchEntire(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val rawTitle = headingMatch.groupValues[2].trim()
            if (level == 1 && isWrapperBriefingTitle(rawTitle)) continue
            if (isSectionBoundaryHeading(level, rawTitle)) {
                flush()
                currentHeading = rawTitle
                continue
            }
        }
        currentLines += line
    }
    if (fenceChar != null) return null
    flush()
    return sections
}

private fun parseBriefingKeyPoints(lines: List<String>): List<BriefingKeyPoint>? {
    val items = mutableListOf<PendingKeyPoint>()
    var sawBlankSinceLastLine = false

    for (line in lines) {
        if (line.isBlank()) {
            sawBlankSinceLastLine = true
            continue
        }
        if (BriefingFenceRegex.containsMatchIn(line) ||
            BriefingAtxHeadingRegex.matches(line) ||
            BriefingTableSeparatorRegex.matches(line)
        ) {
            return null
        }
        val listMatch = BriefingTopLevelListRegex.matchEntire(line)
        if (listMatch != null) {
            val prefix = listMatch.groupValues[1]
            val body = listMatch.groupValues[2].trimEnd()
            items += PendingKeyPoint(contentIndent = prefix.length, lines = mutableListOf(body))
            sawBlankSinceLastLine = false
            continue
        }
        val current = items.lastOrNull() ?: return null
        if (line.startsWith('\t')) return null
        val leadingSpaces = line.takeWhile { it == ' ' }.length
        if (leadingSpaces >= current.contentIndent + 4) return null
        if (sawBlankSinceLastLine) {
            if (leadingSpaces < 2) return null
            current.lines += ""
        }
        val strip = minOf(leadingSpaces, current.contentIndent)
        current.lines += line.substring(strip).trimEnd()
        sawBlankSinceLastLine = false
    }

    return items.mapNotNull { pending ->
        val markdown = pending.lines.joinToString("\n").trim()
        if (markdown.isEmpty()) return@mapNotNull null
        val citations = BriefingInlineCitationRegex.findAll(markdown).map { it.groupValues[1] }.distinct().toList()
        BriefingKeyPoint(markdown = markdown, citations = citations)
    }
}

internal fun stripLeadingWrapperTitle(content: String): String =
    content.trim().replace(BriefingWrapperTitleLineRegex, "").trim().ifBlank { content.trim() }

internal fun stripLeadingHeadingDecorators(rawHeading: String): String {
    val trimmed = rawHeading.trim()
    var index = 0
    while (index < trimmed.length) {
        val cp = trimmed.codePointAt(index)
        if (!isHeadingDecoratorCodePoint(cp)) break
        index += Character.charCount(cp)
    }
    return trimmed.substring(index).trim().ifBlank { trimmed }
}

private fun isHeadingDecoratorCodePoint(cp: Int): Boolean =
    Character.isWhitespace(cp) ||
        cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || cp == 0x20E3 ||
        Character.getType(cp).toByte() == Character.OTHER_SYMBOL

private fun normalizeBriefingHeadingKey(rawHeading: String): String =
    stripLeadingHeadingDecorators(rawHeading).trimEnd(':', '：', ' ')

private fun isWrapperBriefingTitle(rawHeading: String): Boolean =
    normalizeBriefingHeadingKey(rawHeading).startsWith("今日统一新闻总结")

private fun isSectionBoundaryHeading(level: Int, rawHeading: String): Boolean =
    level <= 2 || (level == 3 && normalizeBriefingHeadingKey(rawHeading) in KnownTopLevelHeadingKeys)

private fun isBriefingOverviewHeading(rawHeading: String): Boolean =
    normalizeBriefingHeadingKey(rawHeading) in OverviewHeadingKeys

private fun isBriefingKeyPointsHeading(rawHeading: String): Boolean =
    normalizeBriefingHeadingKey(rawHeading) == "今日要点"

private fun isKnownBriefingHeading(rawHeading: String): Boolean =
    normalizeBriefingHeadingKey(rawHeading) in KnownTopLevelHeadingKeys

private fun collectReferenceLinkLabels(markdown: String): Set<String> {
    val labels = mutableSetOf<String>()
    var fenceChar: Char? = null
    var fenceLength = 0
    for (line in markdown.lines()) {
        val fenceMatch = BriefingFenceRegex.find(line)
        if (fenceMatch != null) {
            val marker = fenceMatch.groupValues[1]
            if (fenceChar == null) {
                fenceChar = marker.first()
                fenceLength = marker.length
            } else if (marker.first() == fenceChar && marker.length >= fenceLength && line.trim() == marker) {
                fenceChar = null
                fenceLength = 0
            }
            continue
        }
        if (fenceChar != null) continue
        val defMatch = BriefingRefLinkDefinitionRegex.find(line)
        if (defMatch != null) {
            labels += defMatch.groupValues[1].trim().lowercase(Locale.ROOT)
        }
    }
    return labels
}

internal fun linkifyBriefingCitations(markdown: String, availableRefKeys: Set<String>): String {
    if (availableRefKeys.isEmpty() || !BriefingInlineCitationRegex.containsMatchIn(markdown)) return markdown
    val definedRefLabels = collectReferenceLinkLabels(markdown)
    var fenceChar: Char? = null
    var fenceLength = 0
    return markdown.lines().joinToString("\n") { line ->
        val fenceMatch = BriefingFenceRegex.find(line)
        if (fenceMatch != null) {
            val marker = fenceMatch.groupValues[1]
            if (fenceChar == null) {
                fenceChar = marker.first()
                fenceLength = marker.length
            } else if (marker.first() == fenceChar && marker.length >= fenceLength && line.trim() == marker) {
                fenceChar = null
                fenceLength = 0
            }
            line
        } else if (fenceChar != null || BriefingRefLinkDefinitionRegex.containsMatchIn(line)) {
            line
        } else {
            linkifyBriefingCitationsInLine(line, availableRefKeys, definedRefLabels)
        }
    }
}

private fun linkifyBriefingCitationsInLine(
    line: String,
    availableRefKeys: Set<String>,
    definedRefLabels: Set<String>,
): String {
    val out = StringBuilder(line.length + 32)
    var index = 0
    while (index < line.length) {
        when (line[index]) {
            '`' -> index = appendInlineCodeSpan(line, index, out)
            '[' -> index = appendBracketToken(line, index, availableRefKeys, definedRefLabels, out)
            else -> {
                out.append(line[index])
                index++
            }
        }
    }
    return out.toString()
}

private fun appendInlineCodeSpan(line: String, start: Int, out: StringBuilder): Int {
    var tickEnd = start
    while (tickEnd < line.length && line[tickEnd] == '`') tickEnd++
    val delimiter = line.substring(start, tickEnd)
    val closeIndex = line.indexOf(delimiter, tickEnd)
    val end = if (closeIndex >= 0) closeIndex + delimiter.length else tickEnd
    out.append(line, start, end)
    return end
}

private fun appendBracketToken(
    line: String,
    start: Int,
    availableRefKeys: Set<String>,
    definedRefLabels: Set<String>,
    out: StringBuilder,
): Int {
    val closeBracket = line.indexOf(']', start + 1)
    if (closeBracket < 0) {
        out.append(line[start])
        return start + 1
    }
    if (line.getOrNull(closeBracket + 1) == '(') {
        val closeParen = line.indexOf(')', closeBracket + 2)
        if (closeParen > closeBracket + 1) {
            out.append(line, start, closeParen + 1)
            return closeParen + 1
        }
    }
    val candidate = line.substring(start, closeBracket + 1)
    val citationMatch = BriefingInlineCitationRegex.matchEntire(candidate)
    if (line.getOrNull(closeBracket + 1) == '[') {
        val secondClose = line.indexOf(']', closeBracket + 2)
        if (secondClose > closeBracket + 1) {
            val secondCandidate = line.substring(closeBracket + 1, secondClose + 1)
            val isCollapsedRef = secondCandidate == "[]"
            val isSecondCitation = BriefingInlineCitationRegex.matches(secondCandidate)
            if (citationMatch == null || isCollapsedRef || !isSecondCitation) {
                out.append(line, start, secondClose + 1)
                return secondClose + 1
            }
        }
    }
    if (citationMatch != null) {
        val refKey = citationMatch.groupValues[1]
        if (refKey in availableRefKeys && refKey.lowercase(Locale.ROOT) !in definedRefLabels) {
            out.append('[').append(refKey).append("](").append(unifiedNewsCitationUrl(refKey)).append(')')
            val nextIndex = closeBracket + 1
            if (BriefingInlineCitationRegex.find(line, nextIndex)?.range?.first == nextIndex) {
                out.append(' ')
            }
            return nextIndex
        }
        out.append(candidate)
        return closeBracket + 1
    }
    out.append(line[start])
    return start + 1
}

@Composable
internal fun BriefingKeyPointRow(
    index: Int,
    point: BriefingKeyPoint,
    availableRefKeys: Set<String>,
    showBottomDivider: Boolean,
    onCitationClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs, bottom = Spacing.s + Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                modifier = Modifier
                    .widthIn(min = IconSize.m)
                    .padding(top = Spacing.s + Spacing.xxs),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            BriefingMarkdown(
                content = point.markdown,
                availableRefKeys = availableRefKeys,
                onCitationClick = onCitationClick,
                modifier = Modifier.weight(1f),
            )
        }
        if (showBottomDivider) {
            HorizontalDivider(thickness = BorderWidth.xs, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
internal fun BriefingSectionBlock(
    section: BriefingSection,
    availableRefKeys: Set<String>,
    showTopDivider: Boolean,
    onCitationClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (showTopDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(bottom = Spacing.xs),
                thickness = BorderWidth.xs,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        when (section) {
            is BriefingSection.Overview -> {
                Text(
                    text = stringResource(R.string.news_focus_overview_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BriefingMarkdown(
                    content = section.markdown,
                    availableRefKeys = availableRefKeys,
                    onCitationClick = onCitationClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is BriefingSection.Other -> {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (section.markdown.isNotBlank()) {
                    BriefingMarkdown(
                        content = section.markdown,
                        availableRefKeys = availableRefKeys,
                        onCitationClick = onCitationClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            is BriefingSection.Untitled -> {
                BriefingMarkdown(
                    content = section.markdown,
                    availableRefKeys = availableRefKeys,
                    onCitationClick = onCitationClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun BriefingMarkdown(
    content: String,
    availableRefKeys: Set<String>,
    onCitationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rendered = remember(content, availableRefKeys) {
        linkifyBriefingCitations(content, availableRefKeys)
    }
    val parentUriHandler = LocalUriHandler.current
    val uriHandler = remember(parentUriHandler, onCitationClick) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val citation = citationFromUnifiedNewsUrl(uri)
                if (citation != null) onCitationClick(citation) else parentUriHandler.openUri(uri)
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        SelectionContainer(modifier = modifier) {
            Markdown(
                modifier = Modifier.fillMaxWidth(),
                content = rendered,
                colors = markdownColor(linkText = MaterialTheme.colorScheme.primary),
                typography = MarkdownStyles.summaryTypography(),
                padding = MarkdownStyles.summaryPadding(),
            )
        }
    }
}
