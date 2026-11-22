package com.dailysatori.service.parser

import com.dailysatori.config.AIConfig
import com.dailysatori.shared.db.Article
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class ParsedArticleSummary(
    val summary: String,
    val coverImageUrl: String?,
)

internal data class ArticleAnalysisResult(
    val title: String?,
    val summary: String,
    val markdown: String,
)

internal data class NormalizedAiConfigValues(
    val apiAddress: String,
    val apiToken: String,
    val modelName: String,
    val provider: String,
)

private val articleJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun normalizeAiConfigValues(
    apiAddress: String,
    apiToken: String,
    modelName: String,
    provider: String,
): NormalizedAiConfigValues = NormalizedAiConfigValues(
    apiAddress = apiAddress.trim().trimEnd('/'),
    apiToken = apiToken.trim(),
    modelName = modelName.trim(),
    provider = provider.trim(),
)

internal fun generatedOrExisting(generated: String, existing: String?, fieldName: String): String {
    if (generated.isNotBlank()) return generated
    if (!existing.isNullOrBlank()) return existing
    throw IllegalStateException("AI $fieldName generation returned empty result")
}

internal fun generatedMarkdownOrFallback(generated: String, existing: String?, extractedContent: String?): String {
    if (generated.isNotBlank()) return generated
    if (!existing.isNullOrBlank()) return existing
    if (!extractedContent.isNullOrBlank()) return extractedContent.trim()
    return ""
}

internal fun generatedSummaryOrFallback(
    generated: String,
    existing: String?,
    extractedContent: String?,
    existingMarkdownContent: String? = null,
): String {
    if (generated.isNotBlank()) return generated
    existingSummaryOrNull(existing)?.let { return it }
    if (!extractedContent.isNullOrBlank()) return extractedContent.trim().take(AIConfig.maxSummaryLength)
    if (!existingMarkdownContent.isNullOrBlank()) return existingMarkdownContent.trim().take(AIConfig.maxSummaryLength)
    throw IllegalStateException("AI summary generation returned empty result")
}

internal fun existingSummaryOrNull(existing: String?): String? {
    val summary = existing?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (isLegacyProcessingErrorSummary(summary)) return null
    return summary
}

private fun isLegacyProcessingErrorSummary(summary: String): Boolean =
    summary == "Job was cancelled" ||
        summary == "StandaloneCoroutine was cancelled" ||
        summary == "AI summary generation returned empty result"

internal fun selectedArticleAiTitle(
    generatedTitle: String?,
    summaryTitle: String?,
    extractedTitle: String?,
    originalTitle: String?,
): String = listOf(generatedTitle, summaryTitle, extractedTitle, originalTitle)
    .mapNotNull { sanitizeArticleAiTitle(it) }
    .firstOrNull()
    .orEmpty()

fun sanitizeArticleAiTitle(value: String?): String? {
    val firstLine = value
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    if (firstLine.isBlank() || firstLine == "正在加载...") return null
    if (firstLine.startsWith("##")) return null
    val cleaned = firstLine
        .removePrefix("#")
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("“", "”")
        .removeSurrounding("'")
        .replace(Regex("""^\s*(?:[-*]|\d+[.)、]|[一二三四五六七八九十]+[、.])\s*"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
    if (cleaned.isBlank() || cleaned == "正在加载...") return null
    if (cleaned.length > 30) return null
    if (cleaned.contains("##") || cleaned.startsWith("- ") || cleaned.startsWith("* ")) return null
    val punctuationCount = cleaned.count { it in listOf('，', ',', '。', '.', '；', ';', '：', ':') }
    if (cleaned.length > 24 && punctuationCount > 1) return null
    return cleaned
}

internal fun parseArticleSummaryOutput(output: String): ParsedArticleSummary {
    val coverRegex = Regex("""(?im)^\s*COVER_IMAGE_URL\s*:\s*(\S*)\s*$""")
    val coverImageUrl = coverRegex.find(output)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    val summary = sanitizeArticleSummaryMarkdown(output.replace(coverRegex, ""))
    return ParsedArticleSummary(summary = summary, coverImageUrl = coverImageUrl)
}

internal fun parseArticleAnalysisOutput(output: String): ArticleAnalysisResult {
    val jsonText = extractJsonObject(output)
        ?: throw IllegalStateException("AI analysis output is not JSON")
    val obj = articleJson.parseToJsonElement(jsonText).jsonObject
    val title = obj.stringValue("title") ?: obj.stringValue("chinese_title")
    val summary = sanitizeArticleSummaryMarkdown(obj.stringValue("summary").orEmpty())
    val markdown = obj.stringValue("markdown").orEmpty().trim()
    if (summary.isBlank()) throw IllegalStateException("AI analysis summary is blank")
    if (markdown.isBlank()) throw IllegalStateException("AI analysis markdown is blank")
    return ArticleAnalysisResult(
        title = title?.trim()?.takeIf { it.isNotBlank() },
        summary = summary,
        markdown = markdown,
    )
}

internal fun sanitizeArticleSummaryMarkdown(markdown: String): String {
    val lines = markdown.trim().lines()
    val cleaned = lines
        .dropWhile { line -> line.trim().isBlank() || line.trim().matches(Regex("""#{1,6}\s+.+""")) }
        .filterNot { line -> line.trim().matches(generatedSummaryGuideHeadingRegex) }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    return stripThirdPersonArticleIntro(cleaned.ifBlank { markdown.trim() })
}

private val generatedSummaryGuideHeadingRegex = Regex(
    """#{1,6}\s*(?:标题|核心内容|核心观点|核心观点[:：]?.*|核心内容[:：]?.*)\s*""",
)

private fun stripThirdPersonArticleIntro(summary: String): String {
    val normalized = summary.trim()
    if (normalized.isBlank()) return normalized
    val stripped = thirdPersonArticleIntroRegexes.fold(normalized) { current, regex ->
        regex.replace(current, "").trimStart()
    }
    return stripped.ifBlank { normalized }
}

private val thirdPersonArticleIntroRegexes = listOf(
    Regex("""^这是一篇[^，,。；;：:]{0,80}(?:文章|名文|作品|内容|报道|帖子|推文)[，,。；;：:]?\s*"""),
    Regex("""^这是(?:一篇|一个|一则|一段)[^，,。；;：:]{0,80}(?:文章|名文|作品|内容|报道|帖子|推文)[，,。；;：:]?\s*"""),
    Regex("""^(?:本文|这篇文章|这篇名文|这篇内容)(?:主要)?(?:介绍了?|讲述了?|探讨了?|阐述了?|说明了?|表达了?|指出|认为|强调)[，,：:]?\s*"""),
)

private fun JsonObject.stringValue(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun extractJsonObject(value: String): String? {
    val cleaned = value.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    if (cleaned.startsWith("{") && cleaned.endsWith("}")) return cleaned

    val start = cleaned.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until cleaned.length) {
        val ch = cleaned[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                inString = false
            }
            continue
        }
        when (ch) {
            '"' -> inString = true
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return cleaned.substring(start, index + 1)
            }
        }
    }
    return null
}

internal fun htmlForAiModel(html: String, modelName: String, fallbackLimit: Int = 180_000): String {
    val limit = aiHtmlCharLimit(modelName, fallbackLimit)
    return if (html.length <= limit) html else html.take(limit)
}

internal fun articleSummaryInput(extracted: ExtractedContent?, modelName: String): String {
    val textContent = extracted?.content?.trim().orEmpty()
    if (textContent.isNotBlank()) return textContent
    return htmlForAiModel(extracted?.htmlContent.orEmpty(), modelName)
}

internal fun articleTitleInput(extracted: ExtractedContent?, modelName: String): String = articleSummaryInput(extracted, modelName)

internal fun articleAnalysisInput(article: Article, extracted: ExtractedContent?, modelName: String): String = buildString {
    append("标题：")
    append(extracted?.title?.takeIf { it.isNotBlank() } ?: article.title.orEmpty())
    append("\n\n")
    append(articleMarkdownInput(extracted, modelName))
}

internal fun articleMarkdownInput(extracted: ExtractedContent?, modelName: String): String {
    val textContent = extracted?.content?.trim().orEmpty()
    val readableHtml = extracted?.readableHtmlContent?.trim().orEmpty()
    val images = extracted?.imageUrls.orEmpty().joinToString("\n")
    if (readableHtml.isNotBlank()) {
        return buildString {
            append("正文 HTML：\n")
            append(htmlForAiModel(readableHtml, modelName))
            if (images.isNotBlank()) {
                append("\n\n正文图片：\n")
                append(images)
            }
        }
    }
    if (textContent.isNotBlank()) {
        return buildString {
            append("正文文本：\n")
            append(textContent)
            if (images.isNotBlank()) {
                append("\n\n正文图片：\n")
                append(images)
            }
        }
    }
    return htmlForAiModel(extracted?.htmlContent.orEmpty(), modelName)
}

internal fun extractMarkdownHeadingTitle(markdown: String): String? = markdown
    .lineSequence()
    .firstOrNull { it.trimStart().startsWith("# ") }
    ?.trim()
    ?.removePrefix("#")
    ?.trim()
    ?.takeIf { it.isNotBlank() }

private fun aiHtmlCharLimit(modelName: String, fallbackLimit: Int): Int {
    val normalized = modelName.lowercase()
    return when {
        "gemini-3" in normalized || "gemini-2.5" in normalized || "gemini-1.5" in normalized -> 900_000
        "gpt-4.1" in normalized || "gpt-5" in normalized -> 900_000
        "claude" in normalized -> 180_000
        "deepseek" in normalized || "qwen" in normalized || "kimi" in normalized -> 120_000
        else -> fallbackLimit
    }
}
