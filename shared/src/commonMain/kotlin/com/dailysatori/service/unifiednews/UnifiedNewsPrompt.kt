package com.dailysatori.service.unifiednews

private val CitationRegex = Regex("\\[([RCDF]\\d+)]")
private val CitationLikeRegex = Regex("\\[([A-Z]+\\d*)]")

fun citationTokens(content: String): List<String> =
    CitationRegex.findAll(content).map { it.groupValues[1] }.toList()

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
    if (line.isBlank() || line.trimStart().startsWith("#")) return line
    val hasCitationLikeToken = CitationLikeRegex.findAll(line).any { !it.isMarkdownLabel(line) }
    if (!hasCitationLikeToken) return line
    val sanitized = removeInvalidCitationTokens(line, sources)
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
2. 每个关键判断都必须带引用，例如 [R1][F2]。
3. 引用必须完全匹配来源编号，不要创造不存在的编号。
4. 输出结构使用这三个二级标题：`## 今日要点`、`## 重要变化`、`## 值得关注`。
5. 每个二级标题下面使用 Markdown 无序列表，格式为 `- 新闻标题或短句 [R1]`，不要输出无列表符号的长段落。
6. 优先做跨来源综合，不要机械逐条复述来源。
7. 对远程来源优先使用来源标题，保持短句，不要改写成长摘要。
8. 如果来源不足以支持可靠判断，请明确说明无法可靠生成，不要猜测。
9. 不要输出总标题。

日期: ${window.summaryDate}
窗口: ${window.key.value}

来源：
$sourceText""".trimIndent()
}
