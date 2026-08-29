package com.dailysatori.service.diary

import com.dailysatori.service.parser.ExtractedContent
import com.dailysatori.service.parser.WebpageParserService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class DiaryLinkMaterial(
    val url: String,
    val title: String,
    val author: String? = null,
    val text: String,
    val extraction: DiaryAssistantExtraction,
    val warnings: List<String> = emptyList(),
)

fun interface DiaryLinkContentExtractor {
    suspend fun extract(url: String): DiaryLinkMaterial
}

class DiaryAssistantExtractionException(message: String) : IllegalStateException(message)

class DefaultDiaryLinkContentExtractor(
    private val fetch: suspend (String) -> ExtractedContent,
) : DiaryLinkContentExtractor {
    constructor(webpageParserService: WebpageParserService) : this(webpageParserService::extractContent)

    override suspend fun extract(url: String): DiaryLinkMaterial {
        val extracted = fetch(url)
        return if (diaryAssistantTarget(url) == DiaryAssistantTarget.DOUYIN) {
            parsePublicDouyinMaterial(url, extracted)
        } else {
            ordinaryPageMaterial(url, extracted)
        }
    }
}

fun parsePublicDouyinMaterial(url: String, content: ExtractedContent): DiaryLinkMaterial {
    val metadata = publicDouyinMetadata(content.htmlContent.orEmpty())
    val title = metadata.title ?: content.title.clean() ?: "抖音视频"
    val description = metadata.description ?: content.content.clean()
    val caption = metadata.caption
    val text = caption ?: description
        ?: throw DiaryAssistantExtractionException("未获取到公开抖音内容")
    return DiaryLinkMaterial(
        url = url,
        title = title,
        author = metadata.author,
        text = text.take(MAX_MATERIAL_LENGTH),
        extraction = if (caption != null) DiaryAssistantExtraction.FULL_TEXT else DiaryAssistantExtraction.NO_SUBTITLES,
        warnings = if (caption == null) listOf("未获取到视频字幕") else emptyList(),
    )
}

private fun ordinaryPageMaterial(url: String, content: ExtractedContent): DiaryLinkMaterial {
    val text = content.content.clean()?.take(MAX_MATERIAL_LENGTH)
        ?: throw DiaryAssistantExtractionException("未获取到可用网页正文")
    if (text.isBlockedPage()) throw DiaryAssistantExtractionException("网页内容不可访问")
    return DiaryLinkMaterial(
        url = url,
        title = content.title.clean() ?: "网页内容",
        text = text,
        extraction = DiaryAssistantExtraction.FULL_TEXT,
    )
}

private data class PublicDouyinMetadata(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val caption: String? = null,
)

private fun publicDouyinMetadata(html: String): PublicDouyinMetadata {
    val json = jsonLdMetadata(html)
    return PublicDouyinMetadata(
        title = json.title ?: metaContent(html, "og:title") ?: titleTag(html),
        author = json.author ?: metaContent(html, "article:author") ?: metaContent(html, "og:video:actor"),
        description = json.description ?: metaContent(html, "og:description") ?: metaContent(html, "description"),
        caption = json.caption ?: publicCaption(html),
    )
}

private fun jsonLdMetadata(html: String): PublicDouyinMetadata {
    val fields = JSON_LD_SCRIPT.findAll(html).flatMap { match ->
        runCatching { publicJson.parseToJsonElement(match.groupValues[1]) }.getOrNull()
            ?.let(::jsonObjects)
            .orEmpty()
            .asSequence()
    }.toList()
    return PublicDouyinMetadata(
        title = fields.firstValue("name", "headline"),
        author = fields.firstAuthor(),
        description = fields.firstValue("description"),
        caption = fields.firstValue("caption", "subtitle", "subtitles", "transcript"),
    )
}

private fun jsonObjects(element: JsonElement): List<JsonObject> = when (element) {
    is JsonObject -> listOf(element) + element.values.flatMap(::jsonObjects)
    is JsonArray -> element.flatMap(::jsonObjects)
    else -> emptyList()
}

private fun List<JsonObject>.firstValue(vararg keys: String): String? = firstNotNullOfOrNull { objectValue ->
    keys.firstNotNullOfOrNull { key -> objectValue[key]?.jsonPrimitive?.contentOrNull.clean() }
}

private fun List<JsonObject>.firstAuthor(): String? = firstNotNullOfOrNull { objectValue ->
    val author = objectValue["author"] ?: return@firstNotNullOfOrNull null
    when (author) {
        is JsonPrimitive -> author.contentOrNull.clean()
        is JsonObject -> author["name"]?.jsonPrimitive?.contentOrNull.clean()
        is JsonArray -> author.firstNotNullOfOrNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull.clean()
                is JsonObject -> item["name"]?.jsonPrimitive?.contentOrNull.clean()
                is JsonArray -> null
            }
        }
    }
}

private fun metaContent(html: String, name: String): String? = META_TAG.findAll(html)
    .firstNotNullOfOrNull { match ->
        val attributes = match.groupValues[1]
        if (META_NAME.find(attributes)?.groupValues?.get(2)?.equals(name, ignoreCase = true) == true) {
            META_CONTENT.find(attributes)?.groupValues?.get(2).clean()
        } else null
    }

private fun titleTag(html: String): String? = TITLE_TAG.find(html)?.groupValues?.get(1).clean()

private fun publicCaption(html: String): String? = CAPTION_FIELD.findAll(html)
    .firstNotNullOfOrNull { it.groupValues[1].clean() }

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotBlank() }

private fun String.isBlockedPage(): Boolean = BLOCKED_PAGE_MARKERS.any { marker ->
    contains(marker, ignoreCase = true)
}

private const val MAX_MATERIAL_LENGTH = 20_000
private val BLOCKED_PAGE_MARKERS = listOf("access denied", "captcha", "verify you are human", "请求过于频繁")
private val publicJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val JSON_LD_SCRIPT = Regex("""<script[^>]*type=[\"']application/ld\+json[\"'][^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
private val META_TAG = Regex("""<meta\s+([^>]+)>""", RegexOption.IGNORE_CASE)
private val META_NAME = Regex("""(?:property|name)\s*=\s*([\"'])(.*?)\1""", RegexOption.IGNORE_CASE)
private val META_CONTENT = Regex("""content\s*=\s*([\"'])(.*?)\1""", RegexOption.IGNORE_CASE)
private val TITLE_TAG = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
private val CAPTION_FIELD = Regex("""[\"'](?:caption|subtitle|subtitles|transcript)[\"']\s*:\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
