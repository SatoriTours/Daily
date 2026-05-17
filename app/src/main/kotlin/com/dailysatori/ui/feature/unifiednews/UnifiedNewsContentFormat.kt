package com.dailysatori.ui.feature.unifiednews

private val LeadingUnifiedNewsTitleRegex = Regex("""^\s*#\s*今日统一新闻总结[^\n]*\n+""")
private val CitationTokenRegex = Regex("""\[([RCDF]\d+)](?!\()""")
private val UnifiedNewsCitationRegex = Regex("""\[([RCDF]\d+)]""")
private val BlankLineBetweenListItemsRegex = Regex("""(?m)^(\s*[-*+]\s+.+)\n\s*\n(?=\s*[-*+]\s+)""")
private val OrderedListItemRegex = Regex("""^\s*\d+[.)]\s+(.+)""")
private val UnorderedListItemRegex = Regex("""^\s*[-*+]\s+.+""")

fun displayUnifiedNewsMarkdown(content: String): String = content
    .replace(LeadingUnifiedNewsTitleRegex, "")
    .normalizeUnifiedNewsListLines()
    .replace(BlankLineBetweenListItemsRegex, "$1\n")
    .trim()

private fun String.normalizeUnifiedNewsListLines(): String = lines()
    .joinToString("\n") { line ->
        when {
            line.isBlank() -> line
            line.trimStart().startsWith("## ") -> line.withUnifiedNewsHeadingIcon()
            line.trimStart().startsWith("#") -> line
            UnorderedListItemRegex.matches(line) -> line
            UnifiedNewsCitationRegex.containsMatchIn(line) -> "- ${OrderedListItemRegex.find(line)?.groupValues?.get(1) ?: line.trim()}"
            else -> line
        }
    }

private fun String.withUnifiedNewsHeadingIcon(): String {
    val heading = trimStart().removePrefix("## ").trim()
    if (heading.firstOrNull()?.isSurrogate() == true) return this
    val icon = when {
        heading.contains("AI", ignoreCase = true) || heading.contains("人工智能") -> "🤖"
        heading.contains("体育") || heading.contains("赛事") -> "🏅"
        heading.contains("世界") || heading.contains("国际") -> "🌍"
        heading.contains("工具") || heading.contains("效率") || heading.contains("提效") -> "🛠️"
        heading.contains("技术") || heading.contains("科技") -> "💻"
        heading.contains("商业") || heading.contains("财经") -> "💼"
        heading.contains("生活") -> "🌿"
        else -> "🗞️"
    }
    return replaceFirst("## ", "## $icon ")
}

fun unifiedNewsMarkdownWithCitationLinks(content: String): String = content
    .lines()
    .joinToString("\n") { line ->
        val citation = primaryCitationInUnifiedNewsLine(line) ?: return@joinToString line
        val visible = visibleUnifiedNewsTextWithoutCitation(line)
        val listPrefix = Regex("""^(\s*[-*+]\s+)(.*)$""").find(visible)
        if (listPrefix != null) {
            val prefix = listPrefix.groupValues[1]
            val text = listPrefix.groupValues[2]
            "$prefix[$text](${unifiedNewsCitationUrl(citation)})"
        } else {
            "[$visible](${unifiedNewsCitationUrl(citation)})"
        }
    }

fun visibleUnifiedNewsTextWithoutCitation(text: String): String = text
    .replace(UnifiedNewsCitationRegex, "")
    .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
    .replace(Regex("""__(.*?)__"""), "$1")
    .replace(Regex("""`([^`]*)`"""), "$1")
    .replace(Regex("""\s+([。！？；：，,.!?;:])"""), "$1")
    .trimEnd()

fun unifiedNewsCitationUrl(citation: String): String = "daily-satori-citation://$citation"

fun primaryCitationInUnifiedNewsLine(line: String): String? = UnifiedNewsCitationRegex
    .find(line)
    ?.groupValues
    ?.get(1)

fun citationFromUnifiedNewsUrl(uri: String): String? = uri
    .removePrefix("daily-satori-citation://")
    .takeIf { it != uri && it.isNotBlank() }

fun unifiedNewsSummaryTimeLabel(summaryDate: String, windowKey: String): String = when (windowKey) {
    "daily" -> summaryDate
    "0800" -> "$summaryDate 08:00"
    "1330" -> "$summaryDate 13:30"
    "1800" -> "$summaryDate 18:00"
    "2100" -> "$summaryDate 21:00"
    "final" -> "$summaryDate 全天"
    else -> "$summaryDate $windowKey"
}

fun unifiedNewsSummaryTitle(summaryDate: String): String {
    val parts = summaryDate.split("-")
    if (parts.size != 3) return "${summaryDate}总结"
    val year = parts[0]
    val month = parts[1].trimStart('0').ifBlank { parts[1] }
    val day = parts[2].trimStart('0').ifBlank { parts[2] }
    return "${year}年${month}月${day}日总结"
}

fun unifiedNewsSourceTypeLabel(sourceType: String): String = when (sourceType) {
    "remote_article" -> "远程新闻"
    "local_favorite" -> "本地收藏"
    else -> "来源"
}
