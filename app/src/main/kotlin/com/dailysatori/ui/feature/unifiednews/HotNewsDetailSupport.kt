package com.dailysatori.ui.feature.unifiednews

enum class HotNewsContentLabel {
    AI_SUMMARY,
    CHINESE_TRANSLATION,
    CHINESE_ORIGINAL,
    ORIGINAL,
}

enum class HotNewsMetaStatus {
    PROCESSING,
    FAILED,
    READY,
}

private val protectedArticleSyntax = Regex("""(?ms)^(`{3,}|~{3,})[^\n]*\n.*?^\1[ \t]*$|`[^`\n]+`|(?<=\]\()[^\n)]+(?=\))|^[ \t]{0,3}\[[^\]\n]+]:[ \t]*\S+[^\n]*$|<https?://[^\s<>]+>|https?://[^\s<>]+""")

private val bareOrTargetUrlRegex = Regex("""https?://[^\s<>)]+|<https?://[^\s<>]+>""")

internal fun isChineseOriginalContent(body: String?): Boolean {
    if (body.isNullOrBlank()) return false
    val prose = protectedArticleSyntax.replace(body, "")
    val chinese = prose.count { it in '\u4e00'..'\u9fff' }
    val english = prose.count { it in 'a'..'z' || it in 'A'..'Z' }
    // A genuine Chinese article must have substantial Chinese and not require translation
    return chinese >= 2 && !(english >= 4 && Regex("\\b[A-Za-z]{2,}\\b").containsMatchIn(prose) && chinese * 2 < english)
}

internal fun calculateHotNewsReadingTimeMinutes(rawMarkdown: String?): Int? {
    if (rawMarkdown.isNullOrBlank()) return null
    // Strip markdown link targets: [text](url) -> text
    val textWithoutLinkTargets = rawMarkdown.replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
    // Strip image targets: ![alt](url) -> alt
    val textWithoutImages = textWithoutLinkTargets.replace(Regex("""!\[([^\]]*)\]\([^)]+\)"""), "$1")
    // Strip bare URLs so long HTTP targets do not artificially inflate reading length
    val textWithoutUrls = bareOrTargetUrlRegex.replace(textWithoutImages, "")
    // Strip code fence markers ```lang or ~~~lang and closing fences ``` / ~~~
    val textWithoutFenceLines = textWithoutUrls.replace(Regex("""(?m)^[ \t]*(`{3,}|~{3,})[^\n]*$"""), "")
    // Strip pure markdown markup punctuation (headings, bold, italics, blockquotes, bullets, table dividers)
    val stripped = textWithoutFenceLines
        .replace(Regex("""[*_`~|>#-]+"""), "")
        .replace(Regex("""\s+"""), "")
    val charCount = stripped.length
    if (charCount == 0) return null
    return maxOf(1, (charCount + 349) / 350)
}

internal fun resolveHotNewsContentLabel(
    isBodySelected: Boolean,
    hasTranslation: Boolean,
    isOriginalChinese: Boolean,
): HotNewsContentLabel {
    if (!isBodySelected) return HotNewsContentLabel.AI_SUMMARY
    if (hasTranslation) return HotNewsContentLabel.CHINESE_TRANSLATION
    return if (isOriginalChinese) HotNewsContentLabel.CHINESE_ORIGINAL else HotNewsContentLabel.ORIGINAL
}

internal fun resolveHotNewsMetaStatus(
    isLoading: Boolean,
    isFailed: Boolean,
): HotNewsMetaStatus {
    if (isLoading) return HotNewsMetaStatus.PROCESSING
    if (isFailed) return HotNewsMetaStatus.FAILED
    return HotNewsMetaStatus.READY
}

internal fun shouldShowHotNewsDiscussion(
    itemUrl: String?,
    discussionUrl: String?,
): Boolean {
    if (discussionUrl.isNullOrBlank()) return false
    return discussionUrl.trim() != itemUrl?.trim()
}

internal fun formatHotNewsSummaryMarkdown(raw: String): String {
    if (raw.isBlank()) return raw
    val lines = raw.lines()
    val result = StringBuilder()
    var inBacktickFence = false
    var inTildeFence = false

    for (i in lines.indices) {
        val line = lines[i]
        val trimmedStart = line.trimStart()

        if (trimmedStart.startsWith("```")) {
            inBacktickFence = !inBacktickFence
            result.append(line)
            if (i < lines.size - 1) result.append("\n")
            continue
        }
        if (trimmedStart.startsWith("~~~")) {
            inTildeFence = !inTildeFence
            result.append(line)
            if (i < lines.size - 1) result.append("\n")
            continue
        }

        if (inBacktickFence || inTildeFence) {
            result.append(line)
            if (i < lines.size - 1) result.append("\n")
            continue
        }

        // Indented code block (4 spaces or tab) should not be converted
        if (line.startsWith("    ") || line.startsWith("\t")) {
            result.append(line)
            if (i < lines.size - 1) result.append("\n")
            continue
        }

        // Only format top-level summary headers: **核心内容：** / **为什么重要（分析）：** / **项目状态：**
        val sectionRegex = Regex("""^\s{0,3}\*\*([^*：:\n]+)[：:]\*\*\s*(.*)$""")
        val match = sectionRegex.find(line)
        if (match != null) {
            val sectionName = match.groupValues[1].trim()
            val content = match.groupValues[2].trim()
            result.append("### ").append(sectionName).append("\n\n")
            if (content.isNotEmpty()) {
                result.append(content)
            }
        } else {
            result.append(line)
        }
        if (i < lines.size - 1) result.append("\n")
    }
    return result.toString()
}
