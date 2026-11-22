package com.dailysatori.service.parser

import com.dailysatori.config.AIConfig
import com.dailysatori.platform.WebViewPageContent
import com.dailysatori.service.externalfavorites.ExternalFavoriteItemDraft
import com.dailysatori.service.externalfavorites.isXArticleUrl
import com.dailysatori.shared.db.Article
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ExtractedContent(
    val title: String?,
    val content: String?,
    val htmlContent: String?,
    val coverImageUrl: String?,
    val readableHtmlContent: String? = null,
    val imageUrls: List<String> = emptyList(),
)

private val articleJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun normalizeArticleMarkdownImages(markdown: String, imageUrls: List<String>): String {
    val normalized = markdown.trim()
    val usableImageUrls = imageUrls
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    if (normalized.isBlank() || usableImageUrls.isEmpty()) return normalized

    val validImageRegex = Regex("""!\[[^]]*]\([^)]+\)""")
    val existingImageUrls = validImageRegex.findAll(normalized)
        .mapNotNull { match ->
            Regex("""!\[[^]]*]\(([^)]+)\)""").find(match.value)?.groupValues?.getOrNull(1)?.trim()
        }
        .filter { it.isNotBlank() }
        .toSet()

    val remainingImageUrls = usableImageUrls.filterNot { it in existingImageUrls }
    if (remainingImageUrls.isEmpty()) return normalized

    val imageLinkRegex = Regex("""[!！]\[([^]]*)]\([^)]+\)""")
    val malformedImageLinkMatch = imageLinkRegex.findAll(normalized)
        .firstOrNull { !validImageRegex.matches(it.value) }
    if (malformedImageLinkMatch != null) {
        val altText = malformedImageLinkMatch.groupValues.getOrNull(1)?.trim().orEmpty().ifBlank { "图片" }
        val fixed = normalized.replaceRange(malformedImageLinkMatch.range, "![$altText](${remainingImageUrls.first()})")
        return appendMissingArticleImages(fixed, remainingImageUrls.drop(1))
    }

    val bracketPlaceholderRegex = Regex("""[!！]\[([^]]+)](?!\()""")
    val bracketMatch = bracketPlaceholderRegex.find(normalized)
    if (bracketMatch != null) {
        val altText = bracketMatch.groupValues.getOrNull(1)?.trim().orEmpty().ifBlank { "图片" }
        val fixed = normalized.replaceRange(bracketMatch.range, "![$altText](${remainingImageUrls.first()})")
        return appendMissingArticleImages(fixed, remainingImageUrls.drop(1))
    }

    val textPlaceholderRegex = Regex("""[!！](图片|配图|插图|图像|image|photo|figure)""", RegexOption.IGNORE_CASE)
    val textMatch = textPlaceholderRegex.find(normalized)
    if (textMatch != null) {
        val altText = textMatch.groupValues.getOrNull(1)?.trim().orEmpty().ifBlank { "图片" }
        val fixed = normalized.replaceRange(textMatch.range, "![$altText](${remainingImageUrls.first()})")
        return appendMissingArticleImages(fixed, remainingImageUrls.drop(1))
    }

    return appendMissingArticleImages(normalized, remainingImageUrls)
}

private fun appendMissingArticleImages(markdown: String, imageUrls: List<String>): String {
    if (imageUrls.isEmpty()) return markdown
    return buildString {
        append(markdown.trim())
        imageUrls.forEachIndexed { index, imageUrl ->
            append("\n\n![图片 ").append(index + 1).append("](").append(imageUrl).append(')')
        }
    }
}

internal fun usableArticleContentOrThrow(content: String?, url: String): String {
    val text = content.orEmpty().trim()
    val normalized = text.lowercase()
    val errorMarkers = listOf(
        "net::err_",
        "web page not available",
        "网页无法打开",
        "just a moment",
        "checking your browser",
        "captcha",
        "access denied",
        "enable javascript",
    )
    if (errorMarkers.any { it in normalized }) {
        throw IllegalStateException("Extracted content is an error page")
    }
    val compact = text.replace(Regex("\\s+"), "")
    if (compact.length < MIN_USABLE_ARTICLE_CONTENT_CHARS && !isTwitterStatusUrl(url) && !isXArticleUrl(url)) {
        throw IllegalStateException("Extracted article content is too short")
    }
    val noiseLines = text.lines().map { it.trim().lowercase() }.filter { it.isNotBlank() }
    val noisy = noiseLines.count { it in contentNoiseLines }
    if (noiseLines.isNotEmpty() && noisy.toDouble() / noiseLines.size > 0.6 && !isTwitterStatusUrl(url) && !isXArticleUrl(url)) {
        throw IllegalStateException("Extracted content looks like navigation or login noise")
    }
    return text
}

private const val MIN_USABLE_ARTICLE_CONTENT_CHARS = 30

private val contentNoiseLines = setOf(
    "登录",
    "注册",
    "首页",
    "login",
    "sign in",
    "sign up",
    "menu",
    "search",
    "subscribe",
)

internal fun isTwitterStatusUrl(url: String?): Boolean {
    val value = url?.trim().orEmpty()
    return Regex(
        pattern = "^https?://(?:www\\.)?(?:x\\.com|twitter\\.com)/(?:i/status|[^/?#]+/status)/[^/?#]+(?:[/?#].*)?$",
        option = RegexOption.IGNORE_CASE,
    ).matches(value)
}

internal fun twitterStatusMarkdown(url: String, extracted: ExtractedContent?): String {
    val cleanedText = cleanCollapsedTwitterArticleChrome(cleanTwitterStatusText(twitterStatusMarkdownText(extracted)))
    val text = if (shouldFormatTwitterMarkdownText(extracted, cleanedText)) {
        formatReadableMarkdownText(cleanedText)
    } else {
        cleanedText
    }
    val mediaUrls = twitterMediaUrls(extracted)
    val markdown = StringBuilder("# 推文内容\n\n")

    if (text.isNotBlank()) {
        markdown.append(text).append("\n\n")
    }
    markdown.append("原文链接：").append(url)

    if (mediaUrls.isNotEmpty()) {
        markdown.append("\n\n## 媒体\n\n")
        mediaUrls.forEachIndexed { index, mediaUrl ->
            if (index > 0) markdown.append('\n')
            markdown.append("![媒体 ").append(index + 1).append("](").append(mediaUrl).append(')')
        }
    }

    return markdown.toString()
}

internal fun twitterStatusMarkdownOrExisting(
    url: String,
    extracted: ExtractedContent?,
    existing: String?,
): String {
    if (!existing.isNullOrBlank() && !shouldReplaceMinimalTwitterMarkdown(existing, extracted)) return existing
    return twitterStatusMarkdown(url, extracted)
}

private fun shouldReplaceMinimalTwitterMarkdown(existing: String, extracted: ExtractedContent?): Boolean {
    val text = existing.trim()
    if (isWebViewNetworkErrorContent(text, null)) return true
    if (!hasSubstantialExtractedContent(extracted)) return false
    if (text.startsWith("原文链接：")) return true
    if (!text.startsWith("# 推文内容")) return false
    if (isAutoGeneratedTwitterMarkdown(text)) return true
    val compact = text.replace(Regex("\\s+"), "")
    return compact.length < 220 && "原文链接：" in text
}

private fun isAutoGeneratedTwitterMarkdown(text: String): Boolean =
    text.startsWith("# 推文内容") && "原文链接：" in text

private fun hasSubstantialExtractedContent(extracted: ExtractedContent?): Boolean {
    val textCompact = extracted?.content.orEmpty().replace(Regex("\\s+"), "")
    val readableCompact = extracted?.readableHtmlContent.orEmpty().htmlFragmentToText().replace(Regex("\\s+"), "")
    return textCompact.length >= 80 || readableCompact.length >= 80
}

private fun twitterStatusMarkdownText(extracted: ExtractedContent?): String {
    val text = extracted?.content.orEmpty()
    val textCompact = text.replace(Regex("\\s+"), "")
    if (textCompact.length >= 80) return text
    val readableText = extracted?.readableHtmlContent.orEmpty().htmlFragmentToText()
    val readableCompact = readableText.replace(Regex("\\s+"), "")
    return if (readableCompact.length >= 80) readableText else text
}

private fun usesReadableHtmlForTwitterMarkdown(extracted: ExtractedContent?): Boolean {
    val textCompact = extracted?.content.orEmpty().replace(Regex("\\s+"), "")
    val readableCompact = extracted?.readableHtmlContent.orEmpty().htmlFragmentToText().replace(Regex("\\s+"), "")
    return textCompact.length < 80 && readableCompact.length >= 80
}

private fun shouldFormatTwitterMarkdownText(extracted: ExtractedContent?, text: String): Boolean {
    if (usesReadableHtmlForTwitterMarkdown(extracted)) return true
    val compact = text.replace(Regex("\\s+"), "")
    return compact.length >= 260 || Regex("第[一二三四五六七八九十]+步").containsMatchIn(text)
}

private fun cleanCollapsedTwitterArticleChrome(text: String): String {
    var cleaned = text.trim()
    val firstYearTitle = Regex("""20\d{2}年""").find(cleaned)
    if (cleaned.startsWith("对话") && firstYearTitle != null) {
        val prefix = cleaned.substring(0, firstYearTitle.range.first)
        if ("@" in prefix && prefix.length <= 80) {
            cleaned = cleaned.substring(firstYearTitle.range.first)
        }
    }
    cleaned = cleaned.replace(Regex("""(?<=[）)])\d[\d.万千kKmM]*(?=上一篇|很多朋友|坦率|这篇|第一步)"""), "")
    return cleaned
}

internal fun formatReadableMarkdownText(text: String): String {
    val normalized = text.trim()
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
    if (normalized.isBlank()) return ""

    return normalized.split(Regex("\\n\\s*\\n"))
        .flatMap { paragraph -> splitReadableParagraph(paragraph.trim()) }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

private fun splitReadableParagraph(paragraph: String): List<String> {
    if (paragraph.lines().size > 1) {
        return paragraph.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { splitReadableParagraph(it) }
    }

    val sentences = Regex("""[^。！？!?]+[。！？!?]+(?:["'”’）)]*)?""")
        .findAll(paragraph)
        .map { it.value.trim() }
        .toMutableList()
    val remainder = paragraph.removePrefix(sentences.joinToString("")).trim()
    if (remainder.isNotBlank()) sentences += remainder
    if (sentences.size <= 1) return listOf(paragraph)
    if (paragraph.length <= 140 && !paragraph.contains(Regex("第[一二三四五六七八九十]+步"))) return listOf(paragraph)
    if (paragraph.contains(Regex("第[一二三四五六七八九十]+步"))) return sentences

    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    sentences.forEach { sentence ->
        if (current.isNotEmpty() && current.length + sentence.length > 110) {
            chunks += current.toString().trim()
            current.clear()
        }
        current.append(sentence)
    }
    if (current.isNotBlank()) chunks += current.toString().trim()
    return chunks
}

internal fun isWebViewNetworkErrorContent(text: String?, html: String?): Boolean {
    val content = "${text.orEmpty()}\n${html.orEmpty()}".lowercase()
    return "net::err_" in content ||
        "web page not available" in content ||
        "webview loaded a network error page" in content ||
        "网页无法打开" in content
}

internal fun failedExtractionFallback(url: String): ExtractedContent {
    throw IllegalStateException("Unable to extract article content")
}

private fun cleanTwitterStatusText(content: String?): String = content.orEmpty()
    .lines()
    .map { it.trim() }
    .filter { it.isNotBlank() && !isTwitterUiNoiseLine(it) }
    .joinToString("\n")

private fun isTwitterUiNoiseLine(line: String): Boolean {
    val normalized = line.lowercase()
    return normalized in twitterUiNoiseLines ||
        Regex("^\\d{1,2}:\\d{2}\\s*(am|pm)?\\s*·.*$", RegexOption.IGNORE_CASE).matches(line) ||
        Regex("^[\\d,.]+[kmb]?$").matches(normalized)
}

private val twitterUiNoiseLines = setOf(
    "x",
    "post",
    "see new posts",
    "translate post",
    "reply",
    "repost",
    "like",
    "share",
    "quote",
    "bookmark",
    "views",
    "show more",
)

private fun twitterMediaUrls(extracted: ExtractedContent?): List<String> =
    listOfNotNull(extracted?.coverImageUrl)
        .plus(extracted?.imageUrls.orEmpty())
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

internal fun xPostLookupDraftExtractedContent(draft: ExternalFavoriteItemDraft): ExtractedContent {
    val metadata = runCatching { articleJson.parseToJsonElement(draft.normalizedJson).jsonObject }.getOrNull()
    val urlTitle = metadata?.string("url_title")
    val urlDescription = metadata?.string("url_description")
    val imageUrls = metadata.xPostLookupImageUrls()
    val completeArticle = metadata?.string("article_content_complete") == "true"
    val content = if (completeArticle) draft.text else listOf(draft.text, urlTitle ?: draft.title, urlDescription)
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .joinToString("\n\n")

    return ExtractedContent(
        title = draft.title.takeIf { it.isNotBlank() } ?: urlTitle,
        content = content,
        htmlContent = null,
        coverImageUrl = imageUrls.firstOrNull(),
        readableHtmlContent = null,
        imageUrls = imageUrls,
    )
}

private fun JsonObject?.xPostLookupImageUrls(): List<String> {
    if (this == null) return emptyList()
    val urlImages = get("url_images")
        ?.jsonArrayOrNull()
        ?.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull }
        .orEmpty()
    val mediaImages = get("media")
        ?.jsonArrayOrNull()
        ?.mapNotNull { media ->
            val obj = media.jsonObjectOrNull() ?: return@mapNotNull null
            obj.string("url") ?: obj.string("preview_image_url")
        }
        .orEmpty()
    return (urlImages + mediaImages)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitiveOrNull()?.contentOrNull

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.jsonArrayOrNull(): JsonArray? =
    runCatching { jsonArray }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull() =
    runCatching { jsonPrimitive }.getOrNull()

internal fun WebViewPageContent.summaryTextOrHtmlFallback(): String {
    val readable = readableContent.orEmpty().trim()
    if (readable.isNotBlank()) return readable.htmlFragmentToText()
    val textContent = text.trim()
    if (textContent.isNotBlank()) return textContent
    return html
}

private fun String.htmlFragmentToText(): String = this
    .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</(p|div|li|h[1-6]|tr|blockquote)>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

internal fun validatedCoverImageUrl(candidate: String?, imageUrls: List<String>): String? {
    if (candidate.isNullOrBlank()) return null
    val normalizedCandidate = candidate.normalizeTwitterImageUrl()
    return imageUrls.firstOrNull { it == normalizedCandidate }
}

internal fun existingArticleOriginalExtractedContent(article: Article): ExtractedContent? =
    existingArticleOriginalExtractedContent(
        title = article.title,
        aiTitle = article.ai_title,
        aiContent = article.ai_content,
        aiMarkdownContent = article.ai_markdown_content,
        coverImageUrl = article.cover_image_url,
        originalMarkdownContent = article.original_markdown_content,
    )

internal fun existingArticleOriginalExtractedContent(
    title: String?,
    aiTitle: String?,
    aiContent: String?,
    aiMarkdownContent: String?,
    coverImageUrl: String?,
    originalMarkdownContent: String? = null,
): ExtractedContent? {
    val original = listOf(originalMarkdownContent, aiMarkdownContent)
        .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotBlank) }
        ?: return null
    val displayTitle = listOf(title, aiTitle)
        .firstNotNullOfOrNull { it?.trim()?.takeIf { value -> value.isNotBlank() } }
    return ExtractedContent(
        title = displayTitle,
        content = original,
        htmlContent = null,
        coverImageUrl = coverImageUrl?.trim()?.takeIf { it.isNotBlank() },
        readableHtmlContent = null,
        imageUrls = emptyList(),
    )
}

internal fun splitArticleText(text: String, maxChunkLength: Int = AIConfig.maxContentLength.toInt()): List<String> {
    require(maxChunkLength > 0)
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        current.toString().trim().takeIf(String::isNotBlank)?.let(chunks::add)
        current.clear()
    }
    text.trim().split(Regex("\\n{2,}")).forEach { paragraph ->
        if (paragraph.length > maxChunkLength) {
            flush()
            paragraph.chunked(maxChunkLength).map(String::trim).filter(String::isNotBlank).forEach(chunks::add)
        } else if (current.isEmpty()) {
            current.append(paragraph)
        } else if (current.length + 2 + paragraph.length <= maxChunkLength) {
            current.append("\n\n").append(paragraph)
        } else {
            flush()
            current.append(paragraph)
        }
    }
    flush()
    return chunks
}
