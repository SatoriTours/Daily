package com.dailysatori.ui.feature.unifiednews

data class UnifiedNewsBriefingContent(
    val title: String,
    val lead: String?,
    val points: List<UnifiedNewsBriefingPoint>,
)

data class UnifiedNewsBriefingPoint(
    val text: String,
    val citation: String?,
)

private val BriefingCitationRegex = Regex("""\[([RCDF]\d+)]""")
private val BriefingListItemRegex = Regex("""^\s*[-*+]\s+(.+)""")
private val BriefingDailyCoverHeadingRegex = Regex("""^\s*#{1,6}\s*(?:🗞️\s*)?每日封面\s*$""")
private val BriefingHeadingRegex = Regex("""^\s*#{1,6}\s+.+""")

fun unifiedNewsBriefingContent(content: String): UnifiedNewsBriefingContent {
    val coverLead = briefingDailyCoverLeadFrom(content)
    val displayed = displayUnifiedNewsMarkdown(content).withoutDailyCoverSection()
    val points = displayed.lines().mapNotNull(::briefingPointFromLine)
    return UnifiedNewsBriefingContent(
        title = "今日封面",
        lead = coverLead ?: briefingLeadFrom(displayed),
        points = points,
    )
}

private fun briefingDailyCoverLeadFrom(content: String): String? {
    val lines = content.lines()
    val headingIndex = lines.indexOfFirst { BriefingDailyCoverHeadingRegex.matches(it.trim()) }
    if (headingIndex < 0) return null
    return lines.drop(headingIndex + 1)
        .map { it.trim() }
        .takeWhile { line -> !BriefingHeadingRegex.matches(line) }
        .firstOrNull { line -> line.isNotEmpty() && BriefingListItemRegex.find(line) == null }
        ?.withoutBriefingMarkdown()
}

private fun String.withoutDailyCoverSection(): String {
    val lines = lines()
    val headingIndex = lines.indexOfFirst { BriefingDailyCoverHeadingRegex.matches(it.trim()) }
    if (headingIndex < 0) return this
    val nextHeading = lines.drop(headingIndex + 1).indexOfFirst { BriefingHeadingRegex.matches(it.trim()) }
    val endIndex = if (nextHeading < 0) lines.size else headingIndex + 1 + nextHeading
    return (lines.take(headingIndex) + lines.drop(endIndex)).joinToString("\n")
}

private fun briefingPointFromLine(line: String): UnifiedNewsBriefingPoint? {
    val listText = BriefingListItemRegex.find(line)?.groupValues?.get(1) ?: return null
    val citation = BriefingCitationRegex.find(listText)?.groupValues?.get(1) ?: return null
    val text = listText.withoutBriefingMarkdown()

    return UnifiedNewsBriefingPoint(text = text, citation = citation)
}

private fun briefingLeadFrom(content: String): String? = content.lines()
    .map { it.trim() }
    .firstOrNull { line ->
        line.isNotEmpty() && !line.startsWith("#") && BriefingListItemRegex.find(line) == null
    }

private fun String.withoutBriefingMarkdown(): String = replace(BriefingCitationRegex, "")
    .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
    .replace(Regex("""__(.*?)__"""), "$1")
    .replace(Regex("""`([^`]*)`"""), "$1")
    .replace(Regex("""\*(.*?)\*"""), "$1")
    .replace(Regex("""_(.*?)_"""), "$1")
    .replace(Regex("""\s+([。！？；：，,.!?;:])"""), "$1")
    .trim()
