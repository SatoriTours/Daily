package com.dailysatori.service.parser

import co.touchlab.kermit.Logger
import com.dailysatori.service.diagnostics.*
import com.dailysatori.config.AIConfig
import com.dailysatori.config.WebViewConfig
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ImageRepository
import com.dailysatori.data.repository.needsChineseReprocessing
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.REMOTE_PROCESSING_STALE
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.WebViewPageContent
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.service.mapConcurrently
import com.dailysatori.service.retryTransientFailure
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.externalfavorites.ExternalFavoriteItemDraft
import com.dailysatori.service.externalfavorites.ExternalFavoriteProvider
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.service.externalfavorites.xPostIdFromStatusUrl
import com.dailysatori.service.externalfavorites.isXArticleUrl
import com.dailysatori.service.externalfavorites.canonicalizeXArticleUrl
import com.dailysatori.service.externalfavorites.xArticleOwningPostId
import com.dailysatori.service.externalfavorites.xIsArticle
import com.dailysatori.service.externalfavorites.favoriteMetadata
import com.dailysatori.service.externalfavorites.favoriteHasArticleCache
import com.dailysatori.service.externalfavorites.withXContentFetchSession
import com.dailysatori.service.externalfavorites.xOfficialContentRequestLimit
import com.dailysatori.shared.db.Article
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
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

internal data class ParsedArticleSummary(
    val summary: String,
    val coverImageUrl: String?,
)

internal data class ArticleAnalysisResult(
    val title: String?,
    val summary: String,
    val markdown: String,
)

data class ArticleProcessingState(
    val articleId: Long,
    val status: String,
    val progress: String = "",
    val contentRevision: Long = 0,
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

internal fun isRecoverableArticleStatus(status: String?): Boolean = when (status) {
    "pending", "webContentFetched", "aiProcessing", "retrying" -> true
    else -> false
}

internal fun isRecoverableArticleForProcessing(
    status: String?,
    aiContent: String?,
    aiMarkdownContent: String?,
): Boolean {
    if (isRecoverableArticleStatus(status)) return true
    if (status != "error") return false
    return isLegacyRecoverableProcessingError(aiContent, aiMarkdownContent)
}

internal fun isLegacyRecoverableProcessingError(message: String?, aiMarkdownContent: String?): Boolean {
    val error = message?.trim().orEmpty()
    return error == "Job was cancelled" ||
        error == "StandaloneCoroutine was cancelled" ||
        (error == "AI summary generation returned empty result" && !aiMarkdownContent.isNullOrBlank())
}

internal fun articleProcessingErrorMessage(error: Exception): String =
    error.message?.takeIf { it.isNotBlank() } ?: "AI processing failed"

internal fun summaryAfterProcessingError(
    existingSummary: String?,
    existingMarkdownContent: String?,
    errorMessage: String,
): String = existingSummaryOrNull(existingSummary)
    ?: existingMarkdownContent?.trim()?.takeIf { it.isNotBlank() }
    ?: errorMessage

internal fun finalArticleStatus(aiContent: String?, aiMarkdownContent: String?): String =
    if (existingSummaryOrNull(aiContent) != null || !aiMarkdownContent.isNullOrBlank()) "completed" else "error"

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

internal fun shouldPersistArticleProcessingError(error: Throwable): Boolean =
    error !is CancellationException || error is TimeoutCancellationException

internal fun isRetryableArticleExtractionFailure(error: Throwable): Boolean {
    val status = Regex("""WebView HTTP error:\s*(\d{3})""", RegexOption.IGNORE_CASE)
        .find(error.message.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    return status == null || status == 408 || status == 425 || status == 429 || status >= 500
}

internal fun webpageExtractionFailureLogMessage(url: String, error: Throwable): String {
    val parsed = runCatching { URLBuilder().takeFrom(url).build() }.getOrNull()
    val origin = parsed?.takeIf { it.protocol.name in setOf("http", "https") && it.host.isNotBlank() }
        ?.let { "${it.protocol.name}://${it.host}" }
        ?: "[redacted URL]"
    return "Content extraction failed for $origin (${error::class.simpleName ?: "Exception"})"
}

class WebpageParserService(
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
    private val imageRepo: ImageRepository,
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val webViewLoader: WebViewLoader,
    private val fileManager: FileManager,
    private val httpClient: HttpClient,
    private val externalFavoriteSourceRepo: ExternalFavoriteSourceRepository,
    private val xBookmarksConnector: XBookmarksConnector,
    private val remoteArticleSyncRepo: RemoteArticleSyncRepository? = null,
    private val settingRepo: SettingRepository,
    private val coverScheduler: ArticleCoverScheduler? = null,
    private val externalFavoriteItemRepo: ExternalFavoriteItemRepository? = null,
) {
    private val log = Logger.withTag("WebpageParser")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queueLock = Any()
    private val pendingProcessingIds = ArrayDeque<Long>()
    private val queuedAt = mutableMapOf<Long, Long>()
    private val activeProcessingIds = mutableSetOf<Long>()
    private val slotChanges = MutableStateFlow(0L)
    private val aiProcessor = ArticleAiProcessor(aiService, settingRepo)

    private val _processingStates = MutableStateFlow<Map<Long, ArticleProcessingState>>(emptyMap())
    val processingStates: StateFlow<Map<Long, ArticleProcessingState>> = _processingStates

    suspend fun resumeInterruptedProcessing() {
        withContext(Dispatchers.IO) {
            val failures = articleRepo.getRecoverableForProcessingSync()
                .filter { isRecoverableArticleForProcessing(it.status, it.ai_content, it.ai_markdown_content) }
                .mapConcurrently(MAX_CONCURRENT_PROCESSING, ::resumeArticleProcessing)
                .filterNotNull()
            if (failures.isNotEmpty()) {
                throw IllegalStateException("${failures.size} article(s) remain incomplete", failures.first())
            }
        }
    }

    private suspend fun resumeArticleProcessing(article: Article): Throwable? {
        if (!markArticleActive(article.id)) {
            enqueueArticleProcessing(article.id)
            return null
        }
        return try {
            val extracted = existingArticleOriginalExtractedContent(article) ?: article.url?.let { extractContent(it) }
            if (extracted != null) updateArticleAfterExtraction(article, extracted)
            processAiTasks(article.id, extracted)
            null
        } catch (error: Exception) {
            if (!shouldPersistArticleProcessingError(error)) throw error
            log.e(error) { "Interrupted article processing failed: articleId=${article.id}" }
            error
        } finally {
            finishQueuedArticle(article.id)
        }
    }

    private fun updateArticleAfterExtraction(article: Article, extracted: ExtractedContent) {
        articleRepo.update(
            id = article.id,
            title = extracted.title ?: article.title,
            aiTitle = article.ai_title,
            aiContent = article.ai_content,
            aiMarkdownContent = article.ai_markdown_content,
            url = article.url,
            isFavorite = article.is_favorite ?: 0L,
            comment = article.comment,
            status = "webContentFetched",
            coverImage = article.cover_image,
            coverImageUrl = extracted.coverImageUrl ?: article.cover_image_url,
            pubDate = article.pub_date,
        )
    }

    suspend fun saveWebpage(
        url: String,
        comment: String?,
        title: String?,
        tags: List<String>?,
        retryOnFailure: Boolean = false,
    ): Long {
        val existing = findExistingArticleByUrl(url)
        if (existing != null && !shouldRetryExistingArticleSave(existing.status)) return existing.id

        val articleId = existing?.id ?: try {
            articleRepo.insert(
                title = title ?: "正在加载...",
                comment = comment,
                url = url,
                status = "pending",
                pubDate = Clock.System.now().toEpochMilliseconds(),
            )
        } catch (e: Exception) {
            val existing = findExistingArticleByUrl(url) ?: throw e
            if (!shouldRetryExistingArticleSave(existing.status)) return existing.id
            existing.id
        }

        val ownsProcessing = measureArticleProcessingStage("queued") { awaitProcessingOwnership(articleId, reuseCompleted = true) }
        if (!ownsProcessing) return articleId
        val article = articleRepo.getById(articleId) ?: run {
            finishQueuedArticle(articleId)
            throw CancellationException("文章已删除")
        }
        articleRepo.updateStatus(articleId, "pending")
        setProcessingState(articleId, "pending")

        try {
            val extracted = existingArticleOriginalExtractedContent(article) ?: extractContent(url)

            articleRepo.update(
                id = articleId,
                title = extracted.title ?: article.title,
                aiTitle = article.ai_title,
                aiContent = article.ai_content,
                aiMarkdownContent = article.ai_markdown_content,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "webContentFetched",
                coverImage = article.cover_image,
                coverImageUrl = extracted.coverImageUrl ?: article.cover_image_url,
                pubDate = article.pub_date,
            )

            setProcessingState(articleId, "webContentFetched")

            tags?.let { tagRepo.setTagsForArticle(articleId, it) }

            processAiTasks(articleId, extracted, retryOnFailure)

            log.i { "saveWebpage completed: articleId=$articleId" }
            return articleId
        } catch (e: Exception) {
            if (!shouldPersistArticleProcessingError(e)) throw e
            log.e(e) { "saveWebpage failed: articleId=$articleId" }
            val alreadyRecorded = articleRepo.getById(articleId)?.status in setOf("error", "retrying")
            if (!alreadyRecorded && recordProcessingFailure(articleId, e, retryOnFailure)) return articleId
            throw e
        } finally {
            if (ownsProcessing) finishQueuedArticle(articleId)
        }
    }

    fun processAiTasksAsync(articleId: Long, extracted: ExtractedContent? = null) {
        if (extracted == null) {
            enqueueArticleProcessing(articleId)
            return
        }

        if (!markArticleActive(articleId)) {
            enqueueArticleProcessing(articleId)
            return
        }

        scope.launch {
            try {
                processAiTasks(articleId, extracted)
            } catch (e: Exception) {
                log.e(e) { "Async AI processing failed: articleId=$articleId" }
            } finally {
                finishQueuedArticle(articleId)
            }
        }
    }

    private fun enqueueArticleProcessing(articleId: Long) {
        val shouldDrain = synchronized(queueLock) {
            if (activeProcessingIds.contains(articleId) || pendingProcessingIds.contains(articleId)) {
                false
            } else {
                pendingProcessingIds.addLast(articleId)
                queuedAt[articleId] = DiagnosticLog.elapsed()
                true
            }
        }
        if (shouldDrain) drainProcessingQueue()
    }

    private fun drainProcessingQueue() {
        val articleIds = mutableListOf<Pair<Long, Long>>()
        synchronized(queueLock) {
            while (activeProcessingIds.size < MAX_CONCURRENT_PROCESSING && pendingProcessingIds.isNotEmpty()) {
                val articleId = pendingProcessingIds.removeFirst()
                val waitMs = DiagnosticLog.elapsed() - (queuedAt.remove(articleId) ?: DiagnosticLog.elapsed())
                if (activeProcessingIds.add(articleId)) articleIds.add(articleId to waitMs.coerceAtLeast(0))
            }
        }
        articleIds.forEach { (articleId, waitMs) ->
            scope.launch {
                DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.PARSER,
                    fields = mapOf("articleStage" to "queued", "durationMs" to waitMs.toString()))
                try {
                    val article = articleRepo.getById(articleId)
                    if (article != null && isRecoverableArticleForProcessing(article.status, article.ai_content, article.ai_markdown_content)) {
                        setProcessingState(articleId, "pending")
                        val extracted = existingArticleOriginalExtractedContent(article) ?: article.url?.let { extractContent(it) }
                        if (extracted != null) {
                            articleRepo.update(
                                id = articleId,
                                title = extracted.title ?: article.title,
                                aiTitle = article.ai_title,
                                aiContent = article.ai_content,
                                aiMarkdownContent = article.ai_markdown_content,
                                url = article.url,
                                isFavorite = article.is_favorite ?: 0L,
                                comment = article.comment,
                                status = "webContentFetched",
                                coverImage = article.cover_image,
                                coverImageUrl = extracted.coverImageUrl ?: article.cover_image_url,
                                pubDate = article.pub_date,
                            )
                            setProcessingState(articleId, "webContentFetched")
                        }
                        processAiTasks(articleId, extracted)
                    }
                } catch (e: Exception) {
                    log.e(e) { "Queued article processing failed: articleId=$articleId" }
                } finally {
                    finishQueuedArticle(articleId)
                }
            }
        }
    }

    private fun setProcessingState(articleId: Long, status: String, progress: String = "") {
        _processingStates.update { it + (articleId to ArticleProcessingState(articleId, status, progress,
            it[articleId]?.contentRevision ?: 0)) }
    }

    private suspend fun awaitProcessingOwnership(articleId: Long, reuseCompleted: Boolean = false): Boolean {
        while (true) {
            val revision = slotChanges.value
            if (markArticleActive(articleId)) return true
            slotChanges.first { it != revision }
            if (reuseCompleted && articleRepo.getById(articleId)?.status == "completed") return false
        }
    }

    private fun markArticleActive(articleId: Long): Boolean = synchronized(queueLock) {
        if (activeProcessingIds.size >= MAX_CONCURRENT_PROCESSING || activeProcessingIds.contains(articleId)) {
            false
        } else {
            pendingProcessingIds.remove(articleId)
            queuedAt.remove(articleId)
            activeProcessingIds.add(articleId)
            true
        }
    }

    private fun finishQueuedArticle(articleId: Long) {
        synchronized(queueLock) {
            activeProcessingIds.remove(articleId)
        }
        slotChanges.update { it + 1 }
        drainProcessingQueue()
    }

    suspend fun processAiTasks(articleId: Long, extracted: ExtractedContent? = null, retryOnFailure: Boolean = false) =
        DiagnosticLog.diagnostics.operation(DiagnosticSource.PARSER) { processAiTasksRecorded(articleId, extracted, retryOnFailure) }

    private suspend fun processAiTasksRecorded(articleId: Long, extracted: ExtractedContent?, retryOnFailure: Boolean) {
        val article = articleRepo.getById(articleId) ?: return
        try {
            val sourceSnapshot = persistOriginal(article, extracted)
            val original = com.dailysatori.data.repository.articleOriginalMarkdown(sourceSnapshot, null,
                isRemoteSnapshot = remoteArticleSyncRepo?.findByArticleId(articleId) != null).orEmpty()
            setProcessingState(articleId, "webContentFetched", "Original saved")
            articleRepo.updateStatus(articleId, "aiProcessing")
            scheduleCover(articleId)
            val config = aiConfigService.getDefaultConfig()
            if (config == null || config.api_address.isBlank() || config.api_token.isBlank()) {
                throw IllegalStateException("AI config not set")
            }
            val normalizedConfig = normalizeAiConfigValues(
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
            )
            aiProcessor.process(articleId, original, extracted?.title ?: article.title.orEmpty(), normalizedConfig,
                onOverview = { overview ->
                    ensureSourceCurrent(articleId, sourceSnapshot)
                    articleRepo.updateOverviewIfOriginalMatches(articleId, sourceSnapshot, overview.title, overview.summary)
                    ensureSourceCurrent(articleId, sourceSnapshot)
                    setProcessingState(articleId, "aiProcessing", "Summary ready")
                },
                onMarkdown = { markdown ->
                    ensureSourceCurrent(articleId, sourceSnapshot)
                    articleRepo.updateMarkdownIfOriginalMatches(articleId, sourceSnapshot, markdown)
                    ensureSourceCurrent(articleId, sourceSnapshot)
                    setProcessingState(articleId, "aiProcessing", "Content ready")
                },
                onProgress = { setProcessingState(articleId, "aiProcessing", it) },
                ensureCurrent = { ensureSourceCurrent(articleId, sourceSnapshot) },
                sourceUrl = article.url.orEmpty(),
            )
            ensureSourceCurrent(articleId, sourceSnapshot)
            articleRepo.updateStatusIfOriginalMatches(articleId, sourceSnapshot, "completed")
            ensureSourceCurrent(articleId, sourceSnapshot)
            setProcessingState(articleId, "completed")
        } catch (e: Exception) {
            if (!shouldPersistArticleProcessingError(e)) throw e
            log.e(e) { "AI processing failed: articleId=$articleId" }
            if (recordProcessingFailure(articleId, e, retryOnFailure)) return
            throw e
        }
    }

    private fun persistOriginal(article: Article, extracted: ExtractedContent?): String {
        if (extracted?.readableHtmlContent.isNullOrBlank() && !article.original_markdown_content.isNullOrBlank() &&
            extracted?.content == article.original_markdown_content) return article.original_markdown_content
        if (remoteArticleSyncRepo?.findByArticleId(article.id) != null && !article.original_markdown_content.isNullOrBlank()) {
            return article.original_markdown_content
        }
        val original = extracted?.let { articleOriginalMarkdown(it, article.url.orEmpty()) }
            ?.takeIf { it.isNotBlank() } ?: article.original_markdown_content.orEmpty()
        require(original.isNotBlank()) { "文章没有可处理的正文" }
        articleRepo.updateOriginalMarkdownContent(article.id, original)
        if (article.ai_markdown_content.isNullOrBlank()) articleRepo.updateAiMarkdownContent(article.id, original)
        return original
    }

    private fun ensureSourceCurrent(articleId: Long, snapshot: String) {
        if (articleRepo.getById(articleId)?.original_markdown_content != snapshot) {
            throw CancellationException("处理期间原文已更新，将使用新版本重试")
        }
    }

    private fun scheduleCover(articleId: Long) {
        val article = articleRepo.getById(articleId) ?: return
        if (article.cover_image_url.isNullOrBlank() || !article.cover_image.isNullOrBlank()) return
        try {
            if (coverScheduler != null) coverScheduler.enqueue(articleId)
            else scope.launch { runCatching { downloadArticleCover(articleId) } }
        } catch (_: Exception) {
            log.w { "Cover task enqueue failed: articleId=$articleId" }
        }
    }

    suspend fun downloadArticleCover(articleId: Long) = measureArticleProcessingStage("cover") {
        downloadArticleCoverRecorded(articleId)
    }

    private suspend fun downloadArticleCoverRecorded(articleId: Long) {
        val article = articleRepo.getById(articleId) ?: return
        val url = article.cover_image_url?.takeIf { it.isNotBlank() } ?: return
        if (!article.cover_image.isNullOrBlank()) return
        val cover = downloadCoverImage(articleId, url) ?: throw IllegalStateException("封面下载失败")
        val latest = articleRepo.getById(articleId) ?: return
        if (latest.cover_image_url != url) return
        articleRepo.updateCoverIfUrlMatches(articleId, url, cover)
        _processingStates.update { states ->
            val state = states[articleId] ?: ArticleProcessingState(articleId, latest.status.orEmpty())
            states + (articleId to state.copy(contentRevision = state.contentRevision + 1))
        }
    }

    private fun recordProcessingFailure(articleId: Long, error: Exception, retryOnFailure: Boolean): Boolean {
        val article = articleRepo.getById(articleId) ?: return false
        val errorMessage = articleProcessingErrorMessage(error)
        val status = if (retryOnFailure) "retrying" else "error"
        articleRepo.updateStatus(articleId, status)
        setProcessingState(articleId, status, errorMessage)
        return false
    }


    suspend fun extractContent(url: String): ExtractedContent =
        measureArticleProcessingStage("extraction") {
            DiagnosticLog.diagnostics.operation(DiagnosticSource.PARSER) { extractContentRecorded(url) }
        }

    private suspend fun extractContentRecorded(url: String): ExtractedContent {
        return withContext(Dispatchers.Default) {
            try {
                retryTransientFailure(
                    maxAttempts = ARTICLE_EXTRACTION_MAX_ATTEMPTS,
                    initialDelayMs = ARTICLE_EXTRACTION_RETRY_DELAY_MS,
                    shouldRetry = ::isRetryableArticleExtractionFailure,
                ) {
                    extractContentOnce(url)
                }
            } catch (e: Exception) {
                if (!shouldPersistArticleProcessingError(e)) throw e
                log.e { webpageExtractionFailureLogMessage(url, e) }
                if (isXArticleUrl(url)) throw e
                failedExtractionFallback(url)
            }
        }
    }

    /** Loads a public page without applying article-length gates; cancellation stops the WebView load. */
    suspend fun fetchPublicPage(url: String): ExtractedContent = withContext(Dispatchers.Default) {
        retryTransientFailure(
            maxAttempts = ARTICLE_EXTRACTION_MAX_ATTEMPTS,
            initialDelayMs = ARTICLE_EXTRACTION_RETRY_DELAY_MS,
            shouldRetry = ::isRetryableArticleExtractionFailure,
        ) {
            fetchPublicPageOnce(url)
        }
    }

    private suspend fun extractContentOnce(url: String): ExtractedContent {
        val extracted = if (isTwitterStatusUrl(url) || isXArticleUrl(url)) fetchXPostExtractedContent(url) else fetchPublicPageOnce(url)
        return extracted.copy(
            content = usableArticleContentOrThrow(extracted.content, url),
        )
    }

    private suspend fun fetchPublicPageOnce(url: String): ExtractedContent {
        val page = suspendCancellableCoroutine<WebViewPageContent> { cont ->
            val handle = webViewLoader.loadContent(url, WebViewConfig.timeoutMs) { result ->
                if (!cont.isActive) return@loadContent
                result.fold(
                    onSuccess = { cont.resume(it) },
                    onFailure = { cont.resumeWithException(it) },
                )
            }
            cont.invokeOnCancellation { handle.cancel() }
        }
        val html = page.html
        if (isWebViewNetworkErrorContent(page.text, html)) throw IllegalStateException("WebView loaded a network error page")
        val title = page.readableTitle?.trim()?.takeIf { it.isNotBlank() } ?: extractTitle(html)
        val coverImageUrl = extractCoverImageUrl(html, url)
        val imageUrls = extractContentImageUrls(html, url)
        val textContent = page.summaryTextOrHtmlFallback().let { if (it == html) extractTextContent(html) else it }
        return ExtractedContent(
            title = title,
            content = textContent,
            htmlContent = html,
            coverImageUrl = coverImageUrl,
            readableHtmlContent = page.readableContent,
            imageUrls = imageUrls,
        )
    }

    private suspend fun fetchXPostExtractedContent(url: String): ExtractedContent {
        val articleUrl = canonicalizeXArticleUrl(url)
        val reference = if (articleUrl != null) externalFavoriteItemRepo?.findXArticleReference(articleUrl)
            ?: throw IllegalStateException("未找到 X 文章对应的父推文，请先同步收藏后重试")
        else xPostIdFromStatusUrl(url)?.let { externalFavoriteItemRepo?.findXPostReference(it) }
        val expectedArticle = articleUrl != null || reference?.let {
            xIsArticle(it.canonical_url, favoriteMetadata(it.normalized_json)) || favoriteHasArticleCache(it)
        } == true
        val postId = if (expectedArticle && reference != null) xArticleOwningPostId(reference) else xPostIdFromStatusUrl(url)
        if (postId == null) throw IllegalStateException("未找到有效的父推文，请先同步收藏后重试")
        val config = reference?.source_id?.let { externalFavoriteSourceRepo.getById(it)?.config_json }.orEmpty()
        return withXContentFetchSession(xOfficialContentRequestLimit(config)) {
            val draft = xBookmarksConnector.fetchPostById(null, postId, expectedArticle = expectedArticle) {
                resolveXSource(reference)
            } ?: throw IllegalStateException("未能获取 X 完整正文，请稍后重试")
            if (articleUrl != null && canonicalizeXArticleUrl(draft.canonicalUrl.orEmpty()) != articleUrl) {
                throw IllegalStateException("文章与父推文映射不一致，请重新同步收藏")
            }
            xPostLookupDraftExtractedContent(draft)
        }
    }

    private suspend fun resolveXSource(reference: com.dailysatori.shared.db.External_favorite_item?): com.dailysatori.shared.db.External_favorite_source {
        val source = if (reference != null) externalFavoriteSourceRepo.getById(reference.source_id)
            ?.takeIf { it.provider == ExternalFavoriteProvider.X.id && it.enabled == 1L }
            ?: throw IllegalStateException("该收藏所属 X 来源已删除或停用")
        else externalFavoriteSourceRepo.getEnabled().filter { it.provider == ExternalFavoriteProvider.X.id }.singleOrNull()
            ?: throw IllegalStateException("无法确定 X 授权来源，请先同步该链接的收藏")
        return xBookmarksConnector.refreshAuth(source).also { refreshed ->
            if (refreshed.auth_json != source.auth_json) externalFavoriteSourceRepo.updateAuthJson(source.id, refreshed.auth_json)
        }
    }

    suspend fun refreshArticle(articleId: Long) {
        val article = articleRepo.getById(articleId)
            ?: throw Exception("Article not found: $articleId")
        val url = article.url ?: throw Exception("Article has no URL")
        val ownsProcessing = markArticleActive(articleId)
        if (!ownsProcessing) {
            articleRepo.updateStatus(articleId, "pending")
            enqueueArticleProcessing(articleId)
            return
        }

        log.i { "refreshArticle: articleId=$articleId, url=$url" }

        try {
            articleRepo.update(
                id = articleId,
                title = "正在加载...",
                aiTitle = article.ai_title,
                aiContent = article.ai_content,
                aiMarkdownContent = article.ai_markdown_content,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "pending",
                coverImage = article.cover_image,
                coverImageUrl = article.cover_image_url,
                pubDate = article.pub_date,
            )

            setProcessingState(articleId, "pending")

            val extracted = existingArticleOriginalExtractedContent(article) ?: extractContent(url)

            articleRepo.update(
                id = articleId,
                title = extracted.title ?: article.title,
                aiTitle = article.ai_title,
                aiContent = article.ai_content,
                aiMarkdownContent = article.ai_markdown_content,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "webContentFetched",
                coverImage = article.cover_image,
                coverImageUrl = extracted.coverImageUrl ?: article.cover_image_url,
                pubDate = article.pub_date,
            )

            setProcessingState(articleId, "webContentFetched")

            processAiTasks(articleId, extracted)

            log.i { "refreshArticle completed: articleId=$articleId" }
        } catch (e: Exception) {
            if (!shouldPersistArticleProcessingError(e)) throw e
            log.e(e) { "refreshArticle failed: articleId=$articleId" }
            recordProcessingFailure(articleId, e, retryOnFailure = false)
            throw e
        } finally {
            finishQueuedArticle(articleId)
        }
    }

    suspend fun refreshArticleWithXApi(articleId: Long) {
        val article = articleRepo.getById(articleId)
            ?: throw Exception("Article not found: $articleId")
        val url = article.url ?: throw Exception("Article has no URL")
        val ownsProcessing = markArticleActive(articleId)
        if (!ownsProcessing) throw IllegalStateException("Article is already processing")

        log.i { "refreshArticleWithXApi: articleId=$articleId, url=$url" }

        try {
            articleRepo.update(
                id = articleId,
                title = "正在加载...",
                aiTitle = article.ai_title,
                aiContent = article.ai_content,
                aiMarkdownContent = article.ai_markdown_content,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "pending",
                coverImage = article.cover_image,
                coverImageUrl = article.cover_image_url,
                pubDate = article.pub_date,
            )

            setProcessingState(articleId, "pending", "Fetching X post")

            val extracted = fetchXPostExtractedContent(url)

            articleRepo.update(
                id = articleId,
                title = extracted.title ?: article.title,
                aiTitle = article.ai_title,
                aiContent = article.ai_content,
                aiMarkdownContent = article.ai_markdown_content,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "webContentFetched",
                coverImage = article.cover_image,
                coverImageUrl = extracted.coverImageUrl ?: article.cover_image_url,
                pubDate = article.pub_date,
            )

            setProcessingState(articleId, "webContentFetched")
            processAiTasks(articleId, extracted)
        } catch (e: Exception) {
            if (!shouldPersistArticleProcessingError(e)) throw e
            log.e(e) { "refreshArticleWithXApi failed: articleId=$articleId" }
            recordProcessingFailure(articleId, e, retryOnFailure = false)
            throw e
        } finally {
            finishQueuedArticle(articleId)
        }
    }

    suspend fun reprocessArticle(articleId: Long) {
        awaitProcessingOwnership(articleId)
        try {
            val article = articleRepo.getById(articleId) ?: throw CancellationException("文章已删除")
            val extracted = existingArticleOriginalExtractedContent(article)
                ?: throw IllegalStateException("Article has no reusable original content")
            val expectedSourceHash = remoteArticleSyncRepo?.findByArticleId(articleId)?.source_content_hash.orEmpty()
            remoteArticleSyncRepo?.markProcessing(articleId)
            articleRepo.update(
                id = articleId, title = article.title, aiTitle = article.ai_title,
                aiContent = article.ai_content, aiMarkdownContent = article.ai_markdown_content,
                url = article.url, isFavorite = article.is_favorite ?: 0L, comment = article.comment,
                status = "webContentFetched", coverImage = article.cover_image,
                coverImageUrl = article.cover_image_url, pubDate = article.pub_date,
            )
            setProcessingState(articleId, "webContentFetched")
            processAiTasks(articleId, extracted)

            val processed = articleRepo.getById(articleId) ?: throw IllegalStateException("Processed article disappeared")
            val output = listOf(processed.ai_title, processed.ai_content, processed.ai_markdown_content)
                .filterNotNull()
                .joinToString("\n")
            if (needsChineseReprocessing(output)) {
                articleRepo.updateStatus(articleId, "error")
                setProcessingState(articleId, "error", "AI did not produce Chinese content")
                throw IllegalStateException("文章整理未生成有效中文内容")
            }
            if (expectedSourceHash.isNotBlank() &&
                remoteArticleSyncRepo?.markProcessingSuccess(articleId, expectedSourceHash, REMOTE_PROCESSING_VERSION) == false
            ) {
                throw IllegalStateException("处理期间原文已更新，将使用新版本重试")
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (remoteArticleSyncRepo?.findByArticleId(articleId)?.processing_state != REMOTE_PROCESSING_STALE) {
                remoteArticleSyncRepo?.markProcessingFailure(articleId, error.message.orEmpty().ifBlank { "文章整理失败" })
            }
            throw error
        } finally {
            finishQueuedArticle(articleId)
        }
    }


    private fun findExistingArticleByUrl(url: String): Article? {
        return articleRepo.findIntakeArticle(url)?.id?.let(articleRepo::getById)
    }

    private fun shouldRetryExistingArticleSave(status: String?): Boolean = when (status) {
        "pending", "webContentFetched", "aiProcessing", "retrying", "error" -> true
        else -> false
    }

    private fun extractTitle(html: String): String? {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", options)
        return titleRegex.find(html)?.groupValues?.get(1)?.trim()?.let { title ->
            htmlDecode(title).replace(Regex("\\s+"), " ")
        }
    }

    private fun extractTextContent(html: String): String {
        val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val body = html.replace(Regex("<head[^>]*>.*?</head>", opts), "")
        val withoutScripts = body.replace(Regex("<script[^>]*>.*?</script>", opts), "")
        val withoutStyles = withoutScripts.replace(Regex("<style[^>]*>.*?</style>", opts), "")
        val withoutTags = withoutStyles.replace(Regex("<[^>]+>"), " ")
        val cleaned = htmlDecode(withoutTags).replace(Regex("\\s+"), " ").trim()
        return cleaned
    }

    private fun htmlDecode(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: it.value }
    }

    private fun containsChinese(text: String): Boolean {
        return Regex("[\\u4e00-\\u9fff]").containsMatchIn(text)
    }

    private suspend fun downloadCoverImage(articleId: Long, imageUrl: String): String? {
        return withContext(Dispatchers.Default) {
            try {
                val response = httpClient.get(imageUrl)
                val bytes = response.bodyAsBytes()
                val ext = imageUrl.substringAfterLast('.').substringBefore('?').let {
                    if (it.length in 2..5) it else "jpg"
                }
                val fileName = "cover_${articleId}_${Clock.System.now().toEpochMilliseconds()}.$ext"
                val imagesDir = fileManager.getImagesDir()
                val filePath = "$imagesDir/$fileName"
                fileManager.writeFile(filePath, bytes)
                "images/$fileName"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.w { "Cover image request failed: articleId=$articleId" }
                null
            }
        }
    }

    private companion object {
        const val MAX_CONCURRENT_PROCESSING = 2
        const val ARTICLE_EXTRACTION_MAX_ATTEMPTS = 2
        const val ARTICLE_EXTRACTION_RETRY_DELAY_MS = 500L
    }
}

private const val REMOTE_PROCESSING_VERSION = "article-general-v4"

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

internal fun validatedCoverImageUrl(candidate: String?, imageUrls: List<String>): String? {
    if (candidate.isNullOrBlank()) return null
    val normalizedCandidate = candidate.normalizeTwitterImageUrl()
    return imageUrls.firstOrNull { it == normalizedCandidate }
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
