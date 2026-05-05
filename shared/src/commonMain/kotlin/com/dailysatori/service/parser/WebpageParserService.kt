package com.dailysatori.service.parser

import co.touchlab.kermit.Logger
import com.dailysatori.config.AIConfig
import com.dailysatori.config.WebViewConfig
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ImageRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.Article
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

data class ExtractedContent(
    val title: String?,
    val content: String?,
    val htmlContent: String?,
    val coverImageUrl: String?,
    val imageUrls: List<String> = emptyList(),
)

internal data class ParsedArticleSummary(
    val summary: String,
    val coverImageUrl: String?,
)

data class ArticleProcessingState(
    val articleId: Long,
    val status: String,
    val progress: String = "",
)

internal data class NormalizedAiConfigValues(
    val apiAddress: String,
    val apiToken: String,
    val modelName: String,
    val provider: String,
)

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

internal fun isRecoverableArticleStatus(status: String?): Boolean = when (status) {
    "pending", "webContentFetched", "aiProcessing" -> true
    else -> false
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
) {
    private val log = Logger.withTag("WebpageParser")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queueLock = Any()
    private val pendingProcessingIds = ArrayDeque<Long>()
    private val activeProcessingIds = mutableSetOf<Long>()

    private val _processingStates = MutableStateFlow<Map<Long, ArticleProcessingState>>(emptyMap())
    val processingStates: StateFlow<Map<Long, ArticleProcessingState>> = _processingStates

    suspend fun resumeInterruptedProcessing() {
        withContext(Dispatchers.IO) {
            articleRepo.getRecoverableForProcessingSync()
                .filter { isRecoverableArticleStatus(it.status) }
                .forEach { article ->
                    if (!markArticleActive(article.id)) return@forEach
                    try {
                        val extracted = article.url?.let { extractContent(it) }
                        if (extracted != null) {
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
                        processAiTasks(article.id, extracted)
                    } catch (e: Exception) {
                        log.e(e) { "Interrupted article processing failed: articleId=${article.id}" }
                    } finally {
                        finishQueuedArticle(article.id)
                    }
                }
        }
    }

    suspend fun saveWebpage(
        url: String,
        comment: String?,
        title: String?,
        tags: List<String>?,
    ): Long {
        log.i { "saveWebpage: url=$url" }

        val articleId = articleRepo.insert(
            title = title ?: "正在加载...",
            comment = comment,
            url = url,
            status = "pending",
            pubDate = Clock.System.now().toEpochMilliseconds(),
        )

        val article = articleRepo.getById(articleId) ?: throw Exception("Failed to create article")
        val ownsProcessing = markArticleActive(articleId)
        if (!ownsProcessing) {
            log.i { "saveWebpage skipped active article processing: articleId=$articleId" }
            return articleId
        }
        val state = mutableMapOf<Long, ArticleProcessingState>()
        state[articleId] = ArticleProcessingState(articleId, "pending")
        _processingStates.value = state

        try {
            val extracted = extractContent(url)

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

            val updatedState = mutableMapOf<Long, ArticleProcessingState>()
            updatedState[articleId] = ArticleProcessingState(articleId, "webContentFetched")
            _processingStates.value = updatedState

            tags?.let { tagRepo.setTagsForArticle(articleId, it) }

            processAiTasks(articleId, extracted)

            log.i { "saveWebpage completed: articleId=$articleId" }
            return articleId
        } catch (e: Exception) {
            log.e(e) { "saveWebpage failed: articleId=$articleId" }
            articleRepo.update(
                id = articleId,
                title = article.title,
                aiTitle = article.ai_title,
                aiContent = e.message ?: "Extraction failed",
                aiMarkdownContent = article.ai_markdown_content,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "error",
                coverImage = article.cover_image,
                coverImageUrl = article.cover_image_url,
                pubDate = article.pub_date,
            )
            val errorState = mutableMapOf<Long, ArticleProcessingState>()
            errorState[articleId] = ArticleProcessingState(articleId, "error", e.message ?: "")
            _processingStates.value = errorState
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
                true
            }
        }
        if (shouldDrain) drainProcessingQueue()
    }

    private fun drainProcessingQueue() {
        val articleIds = mutableListOf<Long>()
        synchronized(queueLock) {
            while (activeProcessingIds.size < MAX_CONCURRENT_PROCESSING && pendingProcessingIds.isNotEmpty()) {
                val articleId = pendingProcessingIds.removeFirst()
                if (activeProcessingIds.add(articleId)) articleIds.add(articleId)
            }
        }
        articleIds.forEach { articleId ->
            scope.launch {
                try {
                    val article = articleRepo.getById(articleId)
                    if (article != null && isRecoverableArticleStatus(article.status)) {
                        val extracted = article.url?.let { extractContent(it) }
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

    private fun markArticleActive(articleId: Long): Boolean = synchronized(queueLock) {
        if (activeProcessingIds.size >= MAX_CONCURRENT_PROCESSING || activeProcessingIds.contains(articleId)) {
            false
        } else {
            pendingProcessingIds.remove(articleId)
            activeProcessingIds.add(articleId)
            true
        }
    }

    private fun finishQueuedArticle(articleId: Long) {
        synchronized(queueLock) {
            activeProcessingIds.remove(articleId)
        }
        drainProcessingQueue()
    }

    suspend fun processAiTasks(articleId: Long, extracted: ExtractedContent? = null) {
        val article = articleRepo.getById(articleId) ?: return
        log.i { "processAiTasks: articleId=$articleId" }

        val state = mutableMapOf<Long, ArticleProcessingState>()
        state[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Starting AI tasks")
        _processingStates.value = state
        updateArticleStatus(article, "aiProcessing")

        try {
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
            val apiAddress = normalizedConfig.apiAddress
            val apiToken = normalizedConfig.apiToken
            val modelName = normalizedConfig.modelName
            val provider = normalizedConfig.provider

            val originalTitle = article.title ?: ""
            var aiTitle = ""
            if (originalTitle.isNotBlank() && originalTitle != "正在加载...") {
                val titleState = mutableMapOf<Long, ArticleProcessingState>()
                titleState[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Generating title")
                _processingStates.value = titleState
                updateArticleStatus(article, "aiProcessing")
                try {
                    aiTitle = if (!containsChinese(originalTitle)) {
                        val translated = aiService.translate(
                            originalTitle.trim(),
                            "Translate the following text to Chinese. Only return the translation, nothing else.",
                            apiAddress, apiToken, modelName, provider,
                        )
                        if (translated.length >= AIConfig.longTitleThreshold) {
                            aiService.summarize(translated, "Summarize the following text in one short line in Chinese.", apiAddress, apiToken, modelName, provider)
                        } else translated
                    } else if (originalTitle.length >= AIConfig.longTitleThreshold) {
                        aiService.summarize(originalTitle.trim(), "Summarize the following text in one short line in Chinese.", apiAddress, apiToken, modelName, provider)
                    } else originalTitle.trim()
                } catch (e: Exception) {
                    log.e(e) { "Title generation failed" }
                    aiTitle = originalTitle.trim()
                }
            }

            val state2 = mutableMapOf<Long, ArticleProcessingState>()
            state2[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Generating summary")
            _processingStates.value = state2
            updateArticleStatus(article, "aiProcessing")

            val htmlContent = extracted?.htmlContent ?: ""
            var aiContent = ""
            var tags = ""
            var aiCoverImageUrl: String? = null
            try {
                val summary = aiService.summarize(
                    htmlForAiModel(htmlContent, modelName),
                    articleSummaryPrompt(),
                    apiAddress, apiToken, modelName, provider,
                )
                val parsedSummary = parseArticleSummaryOutput(summary)
                aiCoverImageUrl = validatedCoverImageUrl(parsedSummary.coverImageUrl, extracted?.imageUrls.orEmpty())
                aiContent = generatedOrExisting(parsedSummary.summary, article.ai_content, "summary")
            } catch (e: Exception) {
                log.e(e) { "Summary generation failed" }
                aiContent = generatedOrExisting("", article.ai_content, "summary")
            }

            val state3 = mutableMapOf<Long, ArticleProcessingState>()
            state3[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Converting to Markdown")
            _processingStates.value = state3
            updateArticleStatus(article, "aiProcessing")

            var aiMarkdownContent = ""
            if (htmlContent.isNotBlank()) {
                try {
                    aiMarkdownContent = aiService.htmlToMarkdown(
                        htmlForAiModel(htmlContent, modelName),
                        htmlToReadableMarkdownPrompt(),
                        apiAddress, apiToken, modelName, provider,
                    )
                    aiMarkdownContent = generatedMarkdownOrFallback(aiMarkdownContent, article.ai_markdown_content, extracted?.content)
                } catch (e: Exception) {
                    log.e(e) { "Markdown conversion failed" }
                    aiMarkdownContent = generatedMarkdownOrFallback("", article.ai_markdown_content, extracted?.content)
                }
            } else {
                aiMarkdownContent = generatedMarkdownOrFallback("", article.ai_markdown_content, extracted?.content)
            }

            val state4 = mutableMapOf<Long, ArticleProcessingState>()
            state4[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Downloading cover image")
            _processingStates.value = state4
            updateArticleStatus(article, "aiProcessing")

            var coverImage: String? = article.cover_image
            val coverImageUrl = aiCoverImageUrl ?: extracted?.coverImageUrl ?: article.cover_image_url
            if (!coverImageUrl.isNullOrBlank() && (coverImage == null || coverImage.isBlank())) {
                try {
                    coverImage = downloadCoverImage(articleId, coverImageUrl)
                } catch (e: Exception) {
                    log.e(e) { "Cover image download failed" }
                }
            }

            articleRepo.update(
                id = articleId,
                title = article.title,
                aiTitle = aiTitle,
                aiContent = aiContent,
                aiMarkdownContent = aiMarkdownContent,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "completed",
                coverImage = coverImage,
                coverImageUrl = coverImageUrl,
                pubDate = article.pub_date,
            )

            val completedState = mutableMapOf<Long, ArticleProcessingState>()
            completedState[articleId] = ArticleProcessingState(articleId, "completed")
            _processingStates.value = completedState
            log.i { "AI processing completed: articleId=$articleId" }
        } catch (e: Exception) {
            log.e(e) { "AI processing failed: articleId=$articleId" }
            articleRepo.update(
                id = articleId,
                title = article.title,
                aiTitle = article.ai_title,
                aiContent = article.ai_content,
                aiMarkdownContent = article.ai_markdown_content,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "error",
                coverImage = article.cover_image,
                coverImageUrl = article.cover_image_url,
                pubDate = article.pub_date,
            )
            val errorState = mutableMapOf<Long, ArticleProcessingState>()
            errorState[articleId] = ArticleProcessingState(articleId, "error", e.message ?: "")
            _processingStates.value = errorState
            throw e
        }
    }

    suspend fun extractContent(url: String): ExtractedContent {
        return withContext(Dispatchers.Default) {
            extractTwitterContent(url)?.let { return@withContext it }
            try {
                val html = suspendCoroutine<String> { cont ->
                    webViewLoader.loadContent(url, WebViewConfig.timeoutMs) { result ->
                        result.fold(
                            onSuccess = { cont.resume(it) },
                            onFailure = { cont.resumeWithException(it) }
                        )
                    }
                }

                val title = extractTitle(html)
                val coverImageUrl = extractCoverImageUrl(html, url)
                val imageUrls = extractContentImageUrls(html, url)
                val textContent = extractTextContent(html)

                ExtractedContent(
                    title = title,
                    content = textContent.take(AIConfig.maxContentLength.toInt()),
                    htmlContent = html,
                    coverImageUrl = coverImageUrl,
                    imageUrls = imageUrls,
                )
            } catch (e: Exception) {
                log.e(e) { "Content extraction failed for $url" }
                try {
                    val response = httpClient.get(url)
                    val html = response.bodyAsText()
                    val title = extractTitle(html)
                    val coverImageUrl = extractCoverImageUrl(html, url)
                    val imageUrls = extractContentImageUrls(html, url)
                    val textContent = extractTextContent(html)
                    ExtractedContent(
                        title = title,
                        content = textContent.take(AIConfig.maxContentLength.toInt()),
                        htmlContent = html,
                        coverImageUrl = coverImageUrl,
                        imageUrls = imageUrls,
                    )
                } catch (e2: Exception) {
                    log.e(e2) { "Fallback extraction also failed" }
                    ExtractedContent(null, null, null, null)
                }
            }
        }
    }

    private suspend fun extractTwitterContent(url: String): ExtractedContent? {
        val statusId = twitterStatusId(url) ?: return null
        return runCatching {
            val json = httpClient.get("https://api.fxtwitter.com/status/$statusId").bodyAsText()
            parseFxTwitterTweetPayload(json, url)
        }.getOrNull()
    }

    suspend fun refreshArticle(articleId: Long) {
        val article = articleRepo.getById(articleId)
            ?: throw Exception("Article not found: $articleId")
        val url = article.url ?: throw Exception("Article has no URL")

        log.i { "refreshArticle: articleId=$articleId, url=$url" }

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

        val state = mutableMapOf<Long, ArticleProcessingState>()
        state[articleId] = ArticleProcessingState(articleId, "pending")
        _processingStates.value = state

        try {
            val extracted = extractContent(url)

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

            val updatedState = mutableMapOf<Long, ArticleProcessingState>()
            updatedState[articleId] = ArticleProcessingState(articleId, "webContentFetched")
            _processingStates.value = updatedState

            processAiTasks(articleId, extracted)

            log.i { "refreshArticle completed: articleId=$articleId" }
        } catch (e: Exception) {
            log.e(e) { "refreshArticle failed: articleId=$articleId" }
            articleRepo.update(
                id = articleId,
                title = article.title,
                aiTitle = article.ai_title,
                aiContent = article.ai_content,
                aiMarkdownContent = article.ai_markdown_content,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "error",
                coverImage = article.cover_image,
                coverImageUrl = article.cover_image_url,
                pubDate = article.pub_date,
            )
            val errorState = mutableMapOf<Long, ArticleProcessingState>()
            errorState[articleId] = ArticleProcessingState(articleId, "error", e.message ?: "")
            _processingStates.value = errorState
            throw e
        }
    }

    suspend fun reprocessArticle(articleId: Long) {
        val article = articleRepo.getById(articleId) ?: throw Exception("Article not found: $articleId")
        articleRepo.update(
            id = articleId, title = article.title, aiTitle = "",
            aiContent = "", aiMarkdownContent = "",
            url = article.url, isFavorite = article.is_favorite ?: 0L, comment = article.comment,
            status = "webContentFetched", coverImage = article.cover_image,
            coverImageUrl = article.cover_image_url, pubDate = article.pub_date,
        )
        val state = mutableMapOf<Long, ArticleProcessingState>()
        state[articleId] = ArticleProcessingState(articleId, "webContentFetched")
        _processingStates.value = state
        processAiTasksAsync(articleId)
    }

    private suspend fun updateArticleStatus(article: Article, status: String) {
        articleRepo.update(
            id = article.id,
            title = article.title,
            aiTitle = article.ai_title,
            aiContent = article.ai_content,
            aiMarkdownContent = article.ai_markdown_content,
            url = article.url,
            isFavorite = article.is_favorite ?: 0L,
            comment = article.comment,
            status = status,
            coverImage = article.cover_image,
            coverImageUrl = article.cover_image_url,
            pubDate = article.pub_date,
        )
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
                log.e(e) { "Failed to download cover image" }
                null
            }
        }
    }

    private companion object {
        const val MAX_CONCURRENT_PROCESSING = 5
    }
}

internal fun parseArticleSummaryOutput(output: String): ParsedArticleSummary {
    val coverRegex = Regex("""(?im)^\s*COVER_IMAGE_URL\s*:\s*(\S*)\s*$""")
    val coverImageUrl = coverRegex.find(output)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    val summary = output.replace(coverRegex, "").trim()
    return ParsedArticleSummary(summary = summary, coverImageUrl = coverImageUrl)
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
