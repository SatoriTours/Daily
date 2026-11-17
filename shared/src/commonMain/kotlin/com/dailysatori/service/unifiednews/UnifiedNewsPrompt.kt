package com.dailysatori.service.unifiednews

private val CitationRegex = Regex("\\[([RCDFH]\\d+)]")
private val CitationLikeRegex = Regex("\\[([A-Z]+\\d*)]")

fun citationTokens(content: String): List<String> =
    CitationRegex.findAll(content)
        .filterNot { it.isMarkdownLabel(content) }
        .map { it.groupValues[1] }
        .toList()

fun invalidCitationTokens(content: String, sources: List<UnifiedNewsSourceItem>): List<String> {
    val valid = sources.map { it.refKey }.toSet()
    return CitationLikeRegex.findAll(content)
        .filterNot { it.isMarkdownLabel(content) }
        .map { it.groupValues[1] }
        .filterNot { it in valid }
        .distinct()
        .toList()
}

fun hasValidCitationTokens(content: String, sources: List<UnifiedNewsSourceItem>): Boolean {
    val valid = sources.map { it.refKey }.toSet()
    return citationTokens(content).any { it in valid }
}

fun removeInvalidCitationTokens(content: String, sources: List<UnifiedNewsSourceItem>): String {
    val valid = sources.map { it.refKey }.toSet()
    return CitationLikeRegex.replace(content) { match ->
        val token = match.groupValues[1]
        when {
            match.isMarkdownLabel(content) -> match.value
            token in valid -> match.value
            else -> ""
        }
    }
}

fun sanitizeGeneratedUnifiedNewsContent(content: String, sources: List<UnifiedNewsSourceItem>): String =
    content.lines()
        .mapNotNull { line -> sanitizeGeneratedUnifiedNewsLine(line, sources) }
        .joinToString("\n")

private fun sanitizeGeneratedUnifiedNewsLine(line: String, sources: List<UnifiedNewsSourceItem>): String? {
    if (line.isBlank()) return line
    val sanitized = removeInvalidCitationTokens(line, sources)
    if (line.trimStart().startsWith("#")) return sanitized
    return sanitized.takeIf { hasValidCitationTokens(it, sources) }
}

private fun MatchResult.isMarkdownLabel(content: String): Boolean =
    isMarkdownInlineLinkLabel(content) ||
        isMarkdownReferenceLinkLabel(content) ||
        isMarkdownReferenceTargetLabel(content) ||
        isMarkdownReferenceDestinationLabel(content)

private fun MatchResult.isMarkdownInlineLinkLabel(content: String): Boolean =
    content.getOrNull(range.last + 1) == '('

private fun MatchResult.isMarkdownReferenceLinkLabel(content: String): Boolean {
    if (content.getOrNull(range.last + 1) != '[') return false
    val nextClose = content.indexOf(']', startIndex = range.last + 2)
    if (nextClose == -1) return false
    val nextLabel = content.substring(range.last + 2, nextClose)
    return !CitationLikeRegex.matches("[$nextLabel]")
}

private fun MatchResult.isMarkdownReferenceTargetLabel(content: String): Boolean {
    if (content.getOrNull(range.last + 1) != ':') return false
    val lineStart = content.lastIndexOf('\n', startIndex = range.first - 1).let { if (it == -1) 0 else it + 1 }
    return content.substring(lineStart, range.first).all { it == ' ' || it == '\t' }
}

private fun MatchResult.isMarkdownReferenceDestinationLabel(content: String): Boolean {
    if (content.getOrNull(range.first - 1) != ']') return false
    val previousOpen = content.lastIndexOf('[', startIndex = range.first - 2)
    if (previousOpen == -1) return false
    val previousLabel = content.substring(previousOpen + 1, range.first - 1)
    return previousLabel.isNotEmpty() && !CitationLikeRegex.matches("[$previousLabel]")
}

fun buildUnifiedNewsPrompt(window: UnifiedNewsWindow, sources: List<UnifiedNewsSourceItem>): String {
    val sourceText = sources.joinToString("\n\n") { source ->
        """[${source.refKey}] ${source.title}
来源类型: ${source.sourceType.dbValue}
摘要: ${source.summary}
正文: ${source.content.take(8000)}""".trimIndent()
    }
    return """请基于以下来源，生成中文 Markdown 每日新闻简报。

要求：
1. 只能使用给定来源，不要编造事实。
2. 每个关键判断都必须带引用，例如 [R1][F2][H3]。H 表示平台热点，排名表示关注度，不代表事实可信度。
3. 引用必须完全匹配来源编号，不要创造不存在的编号。
4. 输出结构使用这四个二级标题：`## 今日要闻`、`## 今日要点`、`## 重要变化`、`## 值得关注`。
5. `## 今日要闻` 下输出 1-2 段、每段 150-250 字的连贯总览，第一段概括主要事件与变化，第二段可补充来源明确支持的影响或不确定性；每个关键判断带引用。不分点、不重复标题、不为凑字数补充事实，来源较少时可以缩短。
6. `## 今日要点` 按重要性列出真正值得关注的新闻事件，数量按实际内容决定，不固定五点、不凑数；每条格式为 `- 标题：1-3 句完整总结，说明新闻事实、变化或影响 [R1][F2]`。正文必须由来源支持，不能只写短标题或空话；同一事件跨来源合并并保留全部相关引用，独立事件即使来自同一来源也分别列出。其他二级标题下面使用 Markdown 无序列表，每条带引用。
7. 优先做跨来源综合，不要机械逐条复述来源。
8. 远程来源的标题可保留原题，但要点正文仍需完整说明来源支持的事实与影响，不要只复述标题。
9. 如果来源不足以支持可靠判断，请明确说明无法可靠生成，不要猜测。
10. 不要输出总标题。
11. 新闻与热点统一去重。同一事件跨平台合并引用，兼顾不同来源，不让单个平台包揽要点。所有标题和说明使用简体中文，保留必要的产品名和仓库标识。
12. 如果来源中存在 H 编号的平台热点，简报必须包含至少一条由该热点正文支持的内容，并在相应句子中附上有效 H 引用；不要仅列出引用而省略内容。没有热点来源时，不编造热点或 H 引用。

日期: ${window.summaryDate}
窗口: ${window.key.value}

来源：
$sourceText""".trimIndent()
}
