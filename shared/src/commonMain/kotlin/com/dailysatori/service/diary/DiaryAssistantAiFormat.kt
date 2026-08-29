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
private val diaryAssistantJson = Json { ignoreUnknownKeys = true; isLenient = true }

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
    }.getOrNull()
    return parsed?.let { it.copy(sources = it.sources.ifEmpty { compactDiaryAssistantSources(fallbackSources) }) }
        ?: DiaryAssistantParsedAi(cleaned, compactDiaryAssistantSources(fallbackSources))
}

fun renderDiaryAssistantMarkdown(content: String, sources: List<DiaryAssistantSource>): String {
    val body = content.trim()
    val compact = compactDiaryAssistantSources(sources)
    if (compact.isEmpty()) return body
    val links = compact.joinToString("、") {
        "[${it.title.ifBlank { it.url }.escapeDiaryAssistantMarkdownText()}](<${it.url}>)"
    }
    return if (body.isEmpty()) "来源：$links" else "$body\n\n来源：$links"
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
