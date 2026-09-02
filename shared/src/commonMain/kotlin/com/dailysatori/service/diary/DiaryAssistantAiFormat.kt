package com.dailysatori.service.diary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull

/** The deliberately small, transport-neutral result returned by the AI formatter. */
data class DiaryAssistantParsedAi(
    val content: String,
    val sources: List<DiaryAssistantSource>,
)

private const val DIARY_ASSISTANT_CONTEXT_LIMIT = 240
private const val DIARY_ASSISTANT_SOURCE_LIMIT = 3
private val diaryAssistantJson = Json { ignoreUnknownKeys = true }
private val diaryAssistantInlineAutoLink = Regex(
    "<(?:[A-Za-z][A-Za-z0-9+.-]*:[^>\\s]*|[^<>\\s@]+@[^<>\\s@]+)>",
)
private val diaryAssistantInlineAddress = Regex(
    "(?:(?:https?|ftp|file)://|www\\.)[^\\s<>\"'\\]\\[{}]+|" +
        "mailto:[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|" +
        "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
    RegexOption.IGNORE_CASE,
)
private val diaryAssistantTrailingPunctuation = setOf(
    '.', ',', '!', '?', ';', ':', '。', '，', '！', '？', '；', '：', '、',
    ')', ']', '}', '）', '】', '》', '」', '』',
)

fun buildDiaryKnowledgePrompt(
    selectedText: String,
    contextBefore: String = "",
    contextAfter: String = "",
    allowModelKnowledgeFallback: Boolean = false,
): String = buildString {
    appendLine("请解释或补充下面选中的内容，使用简洁、准确的中文。")
    appendLine("选中内容：${selectedText.trim()}")
    appendLine("选中内容前文：${contextBefore.takeLast(DIARY_ASSISTANT_CONTEXT_LIMIT)}")
    appendLine("选中内容后文：${contextAfter.take(DIARY_ASSISTANT_CONTEXT_LIMIT)}")
    appendLine("只输出 JSON：{\"content\":\"简洁补充正文\",\"sources\":[{\"title\":\"来源标题\",\"url\":\"https://example.com\"}]}")
    if (!allowModelKnowledgeFallback) append("若无法可靠核实，请不要编造来源或事实。")
}

fun buildDiaryKnowledgePrompt(request: DiaryAssistantRequest): String =
    buildDiaryKnowledgePrompt(request.selectedText, request.contextBefore, request.contextAfter, request.allowModelKnowledgeFallback)

fun parseDiaryAssistantAiResponse(
    response: String,
    fallbackSources: List<DiaryAssistantSource>,
): DiaryAssistantParsedAi {
    if (response.length > 12_000) throw DiaryAssistantInvalidResponseException("AI 返回内容过长")
    val cleaned = response.trim().removeCodeFence().trim()
    val parsed = runCatching {
        val objectValue = diaryAssistantJson.parseToJsonElement(cleaned).jsonObject
        val content = (objectValue["content"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        val sources = (objectValue["sources"] as? JsonArray).orEmpty().mapNotNull { item ->
            val source = item as? JsonObject ?: return@mapNotNull null
            val title = (source["title"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            val parsedUrl = (source["url"] as? JsonPrimitive)?.contentOrNull
                ?.let(::parseDiaryAssistantHttpUrl)
                ?: return@mapNotNull null
            DiaryAssistantSource(title.ifBlank { parsedUrl.value }, parsedUrl.value)
        }
        DiaryAssistantParsedAi(content, compactDiaryAssistantSources(sources))
    }.getOrElse { throw DiaryAssistantInvalidResponseException("AI 返回格式异常") }
    return parsed.copy(sources = parsed.sources.ifEmpty { compactDiaryAssistantSources(fallbackSources) })
}

fun renderDiaryAssistantMarkdown(content: String, sources: List<DiaryAssistantSource>): String {
    val body = content.removeDiaryAssistantInlineUrls().trim()
    val compact = compactDiaryAssistantSources(sources)
    if (compact.isEmpty()) return body
    val links = compact.joinToString("、") {
        "[${it.title.ifBlank { it.url }.escapeDiaryAssistantMarkdownText()}](<${it.url}>)"
    }
    return if (body.isEmpty()) "来源：$links" else "$body\n\n来源：$links"
}

private fun String.removeDiaryAssistantInlineUrls(): String =
    removeDiaryAssistantReferenceDefinitions()
        .removeDiaryAssistantInlineMarkdownDestinations()
        .replace(diaryAssistantInlineAutoLink, "")
        .replace(diaryAssistantInlineAddress) { match ->
            match.value.takeLastWhile { it in diaryAssistantTrailingPunctuation }
        }

private fun String.removeDiaryAssistantInlineMarkdownDestinations(): String {
    val output = StringBuilder(length)
    var cursor = 0
    while (cursor < length) {
        val labelStart = indexOf('[', cursor)
        if (labelStart < 0) {
            output.append(substring(cursor))
            break
        }
        val labelEnd = matchingDiaryAssistantDelimiter(labelStart, '[', ']')
        val destinationOpening = getOrNull(labelEnd + 1)
        val destinationClosing = when (destinationOpening) {
            '(' -> ')'
            '[' -> ']'
            else -> null
        }
        if (labelEnd < 0 || destinationClosing == null) {
            output.append(substring(cursor, labelStart + 1))
            cursor = labelStart + 1
            continue
        }
        val destinationEnd = matchingDiaryAssistantDelimiter(labelEnd + 1, destinationOpening!!, destinationClosing)
        if (destinationEnd < 0) {
            output.append(substring(cursor, labelStart + 1))
            cursor = labelStart + 1
            continue
        }
        val replacementStart = if (labelStart > cursor && this[labelStart - 1] == '!') labelStart - 1 else labelStart
        output.append(substring(cursor, replacementStart))
        output.append(substring(labelStart + 1, labelEnd))
        cursor = destinationEnd + 1
    }
    return output.toString()
}

private fun String.removeDiaryAssistantReferenceDefinitions(): String = lineSequence()
    .filterNot { line ->
        val candidate = line.trimStart()
        val labelEnd = candidate.matchingDiaryAssistantDelimiter(0, '[', ']')
        labelEnd >= 0 && candidate.getOrNull(labelEnd + 1) == ':'
    }
    .joinToString("\n")

private fun String.matchingDiaryAssistantDelimiter(start: Int, opening: Char, closing: Char): Int {
    if (start !in indices || this[start] != opening) return -1
    var depth = 1
    var index = start + 1
    var quote: Char? = null
    while (index < length) {
        if (this[index] == '\\') {
            index += 2
            continue
        }
        if (opening == '(' && (this[index] == '\'' || this[index] == '"')) {
            quote = if (quote == this[index]) null else quote ?: this[index]
            index++
            continue
        }
        if (quote != null) {
            index++
            continue
        }
        when (this[index]) {
            opening -> depth++
            closing -> if (--depth == 0) return index
        }
        index++
    }
    return -1
}

private fun String.removeCodeFence(): String =
    replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*```$"), "")

private fun String.escapeDiaryAssistantMarkdownText(): String =
    replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")

private fun compactDiaryAssistantSources(sources: List<DiaryAssistantSource>): List<DiaryAssistantSource> {
    val seen = mutableSetOf<String>()
    return sources.asSequence().mapNotNull { source ->
        val url = parseDiaryAssistantHttpUrl(source.url) ?: return@mapNotNull null
        if (!seen.add(url.value)) null
        else DiaryAssistantSource(source.title.trim().ifBlank { url.value }, url.value)
    }.take(DIARY_ASSISTANT_SOURCE_LIMIT).toList()
}
