package com.dailysatori.service.unifiednews

private val CitationRegex = Regex("\\[([RCDF]\\d+)]")
private val CitationLikeRegex = Regex("\\[([A-Z]+\\d*)]")

fun citationTokens(content: String): List<String> =
    CitationRegex.findAll(content).map { it.groupValues[1] }.toList()

fun invalidCitationTokens(content: String, sources: List<UnifiedNewsSourceItem>): List<String> {
    val valid = sources.map { it.refKey }.toSet()
    return CitationLikeRegex.findAll(content)
        .filterNot { it.isMarkdownInlineLinkLabel(content) || it.isMarkdownReferenceLinkLabel(content) || it.isMarkdownReferenceLabel(content) }
        .map { it.groupValues[1] }
        .filterNot { it in valid }
        .distinct()
        .toList()
}

private fun MatchResult.isMarkdownInlineLinkLabel(content: String): Boolean =
    content.getOrNull(range.last + 1) == '('

private fun MatchResult.isMarkdownReferenceLinkLabel(content: String): Boolean =
    content.getOrNull(range.last + 1) == '['

private fun MatchResult.isMarkdownReferenceLabel(content: String): Boolean {
    if (content.getOrNull(range.last + 1) != ':') return false
    val lineStart = content.lastIndexOf('\n', startIndex = range.first - 1).let { if (it == -1) 0 else it + 1 }
    return content.substring(lineStart, range.first).all { it == ' ' || it == '\t' }
}

fun buildUnifiedNewsPrompt(window: UnifiedNewsWindow, sources: List<UnifiedNewsSourceItem>): String {
    val sourceText = sources.joinToString("\n\n") { source ->
        """[${source.refKey}] ${source.title}
来源类型: ${source.sourceType.dbValue}
摘要: ${source.summary}
正文: ${source.content.take(8000)}""".trimIndent()
    }
    return """请基于以下来源，生成中文 Markdown 新闻汇总。

要求：
1. 只能使用给定来源，不要编造事实。
2. 每个事实判断都必须带引用，例如 [R1][F2]。
3. 引用必须完全匹配来源编号，不要创造不存在的编号。
4. 不要使用 `重点速览`、`值得关注` 这类泛泛分类。
5. 按今天新闻内容动态合并为 3-5 个大类，每个大类用二级标题 `## 分类名`。
6. 优先考虑这些方向，但不要机械照搬：AI、世界新闻、工具提效、体育、技术、商业、生活。
7. 每天的大类可以不同，尽量把相近主题合并，避免超过 5 个分类。不要输出总标题。
8. 每条新闻使用 Markdown 无序列表，格式为 `- 新闻标题或短句 [R1]`，不要输出无列表符号的长段落。
9. 对远程来源优先使用来源标题，保持短句，不要改写成长摘要。
10. 分类名必须简洁，通常 2-5 个汉字或短词；不要：全球安全威胁与基础设施事件，要：安全威胁；不要：AI前沿模型与能力突破，要：AI前沿。

日期: ${window.summaryDate}
窗口: ${window.key.value}

来源：
$sourceText""".trimIndent()
}
