package com.dailysatori.service.parser

private val paragraphBreak = Regex("""\n(?:[ \t]*\n)+""")
private val topHeading = Regex("""^# ([^\n#<>\x60\[\]|]+)$""")
private val markdownStructure = Regex(
    """[<>\x60\[\]|#\\]|https?://|(?m)^(?:[ \t]+|[-+*][ \t]|[0-9]+[.)][ \t]|[-=_*~]{3,}[ \t]*$)""",
)
private val fenceLine = Regex("""^ {0,3}(\x60{3,}|~{3,})(.*)$""")

/** Cleans published translations without editing the saved source or changing real Markdown structure. */
fun tidyArticleTranslation(markdown: String, articleTitle: String? = null): String {
    // Unknown HTML block boundaries and noncanonical line endings are safer to leave intact.
    if ('\r' in markdown || '<' in markdown) return markdown
    val blocks = translationBlocks(markdown)
    val first = blocks.firstOrNull() ?: return markdown
    val heading = topHeading.matchEntire(first.text)?.groupValues?.get(1)?.trim()
    val plain = blocks.map { block ->
        if (block.fenced) null else block.text.withoutInlineTitle(heading).takeUnless {
            it.isBlank() || markdownStructure.containsMatchIn(it)
        }
    }

    val output = StringBuilder(markdown.length)
    var cursor = 0
    var index = 0
    var dropHeading = heading != null && blocks.size > 1 && titleKey(heading) == titleKey(articleTitle.orEmpty())
    while (index < blocks.size) {
        val cycle = duplicateCycle(plain, index)
        if (cycle == 0) {
            index++
            continue
        }
        var end = index + cycle * 2
        while (end + cycle <= plain.size && plain.subList(index, index + cycle) == plain.subList(end, end + cycle)) end += cycle
        val attachedTitle = (index until end).any { blocks[it].text != plain[it] }
        if (heading != null && index == 1 && attachedTitle) dropHeading = true
        val start = blocks[index].start
        output.append(markdown.substring(cursor, start))
        output.append(plain.subList(index, index + cycle).joinToString("\n\n"))
        cursor = blocks[end - 1].end
        index = end
    }
    output.append(markdown.substring(cursor))
    return if (dropHeading) output.substring(blocks[1].start) else output.toString()
}

private data class TranslationBlock(val start: Int, val end: Int, val text: String, val fenced: Boolean)

private fun translationBlocks(markdown: String): List<TranslationBlock> = buildList {
    var fence: String? = null
    var start = 0
    fun addBlock(end: Int) {
        val block = markdown.substring(start, end)
        var fenced = fence != null
        for (line in block.lineSequence()) {
            val marker = fenceLine.matchEntire(line) ?: continue
            val token = marker.groupValues[1]
            val open = fence
            fenced = true
            if (open == null) fence = token
            else if (token.first() == open.first() && token.length >= open.length && marker.groupValues[2].isBlank()) fence = null
        }
        add(TranslationBlock(start, end, block, fenced))
    }
    for (separator in paragraphBreak.findAll(markdown)) {
        addBlock(separator.range.first)
        start = separator.range.last + 1
    }
    addBlock(markdown.length)
}

private fun duplicateCycle(blocks: List<String?>, start: Int): Int {
    // Only collapse adjacent, exactly matching multi-paragraph runs. Bound the lookahead.
    for (length in 2..minOf(8, (blocks.size - start) / 2)) {
        val first = blocks.subList(start, start + length)
        if (first.none { it == null } && first == blocks.subList(start + length, start + length * 2)) return length
    }
    return 0
}

private fun String.withoutInlineTitle(heading: String?): String {
    if (heading.isNullOrBlank() || !endsWith("# $heading")) return this
    val preceding = removeSuffix("# $heading").trimEnd()
    return if (preceding.lastOrNull() in setOf('。', '！', '？', '.', '!', '?')) preceding else this
}

private fun titleKey(title: String): String = title.filterNot(Char::isWhitespace).lowercase()

private val factsLabel = Regex("""(?m)^\*\*(?:新闻事实|核心内容)[：:]\*\*[ \t]*""")
private val legacyAnalysisLabel = Regex("""(?m)^\*\*(?:为什么重要（分析）|工作／技术决策（分析）)[：:]\*\*""")
private val verifiedSourceFooter = Regex("""(?m)^来源[：:][ \t]*\[原文]\(<https?://[^>\n]+>\)[ \t]*(?:\n[ \t]*)*\z""")
private val oldEventTimeLine = Regex("""^[0-9][0-9年月日时分秒:：/ .+TZ–-]*(?:UTC)?$""")

/** Hide only the app's known article brief wrapper; leave publisher and unknown summaries alone. */
fun articleReaderSummary(markdown: String): String {
    val facts = factsLabel.find(markdown) ?: return markdown
    val prefix = markdown.substring(0, facts.range.first).trim()
    if (prefix.isNotEmpty() && !oldEventTimeLine.matches(prefix)) return markdown
    val content = markdown.substring(facts.range.last + 1)
    val tail = listOfNotNull(legacyAnalysisLabel.find(content), verifiedSourceFooter.find(content))
        .minOfOrNull { it.range.first } ?: content.length
    return content.substring(0, tail).trim().ifBlank { markdown }
}
