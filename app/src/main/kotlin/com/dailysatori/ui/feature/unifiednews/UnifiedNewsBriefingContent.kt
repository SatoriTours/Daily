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

fun unifiedNewsBriefingContent(content: String): UnifiedNewsBriefingContent {
    val displayed = displayUnifiedNewsMarkdown(content)
    val points = displayed.lines().mapNotNull(::briefingPointFromLine)
    return UnifiedNewsBriefingContent(
        title = "今日封面",
        lead = briefingLeadFrom(displayed),
        points = points,
    )
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
