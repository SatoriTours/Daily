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
import com.dailysatori.service.ai.AIFunctionType
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.Article
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
)

data class ArticleProcessingState(
    val articleId: Long,
    val status: String,
    val progress: String = "",
)

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

    private val _processingStates = MutableStateFlow<Map<Long, ArticleProcessingState>>(emptyMap())
    val processingStates: StateFlow<Map<Long, ArticleProcessingState>> = _processingStates

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
        val state = mutableMapOf<Long, ArticleProcessingState>()
        state[articleId] = ArticleProcessingState(articleId, "pending")
        _processingStates.value = state

        try {
            val extracted = extractContent(url)

            articleRepo.update(
                id = articleId,
                title = extracted.title ?: article.title,
                aiTitle = article.ai_title,
                content = extracted.content,
                aiContent = article.ai_content,
                htmlContent = extracted.htmlContent,
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

            processAiTasksAsync(articleId)

            log.i { "saveWebpage completed: articleId=$articleId" }
            return articleId
        } catch (e: Exception) {
            log.e(e) { "saveWebpage failed: articleId=$articleId" }
            articleRepo.update(
                id = articleId,
                title = article.title,
                aiTitle = article.ai_title,
                content = article.content,
                aiContent = e.message ?: "Extraction failed",
                htmlContent = article.html_content,
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

    fun processAiTasksAsync(articleId: Long) {
        scope.launch {
            try {
                processAiTasks(articleId)
            } catch (e: Exception) {
                log.e(e) { "Async AI processing failed: articleId=$articleId" }
            }
        }
    }

    suspend fun processAiTasks(articleId: Long) {
        val article = articleRepo.getById(articleId) ?: return
        log.i { "processAiTasks: articleId=$articleId" }

        val state = mutableMapOf<Long, ArticleProcessingState>()
        state[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Starting AI tasks")
        _processingStates.value = state

        try {
            val apiAddress = aiConfigService.getApiAddress(AIFunctionType.ARTICLE)
            val apiToken = aiConfigService.getApiToken(AIFunctionType.ARTICLE)
            val modelName = aiConfigService.getModelName(AIFunctionType.ARTICLE)

            if (apiAddress.isEmpty() || apiToken.isEmpty()) {
                log.w { "AI config not set, skipping AI processing" }
                val completedState = mutableMapOf<Long, ArticleProcessingState>()
                completedState[articleId] = ArticleProcessingState(articleId, "completed")
                _processingStates.value = completedState
                return
            }

            val originalTitle = article.title ?: ""
            var aiTitle = ""
            if (originalTitle.isNotBlank() && originalTitle != "正在加载...") {
                val titleState = mutableMapOf<Long, ArticleProcessingState>()
                titleState[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Generating title")
                _processingStates.value = titleState
                try {
                    aiTitle = if (!containsChinese(originalTitle)) {
                        val translated = aiService.translate(
                            originalTitle.trim(),
                            "Translate the following text to Chinese. Only return the translation, nothing else.",
                            apiAddress, apiToken, modelName,
                        )
                        if (translated.length >= AIConfig.longTitleThreshold) {
                            aiService.summarize(translated, "Summarize the following text in one short line in Chinese.", apiAddress, apiToken, modelName)
                        } else translated
                    } else if (originalTitle.length >= AIConfig.longTitleThreshold) {
                        aiService.summarize(originalTitle.trim(), "Summarize the following text in one short line in Chinese.", apiAddress, apiToken, modelName)
                    } else originalTitle.trim()
                } catch (e: Exception) {
                    log.e(e) { "Title generation failed" }
                    aiTitle = originalTitle.trim()
                }
            }

            val state2 = mutableMapOf<Long, ArticleProcessingState>()
            state2[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Generating summary")
            _processingStates.value = state2

            val content = (article.content ?: article.html_content) ?: ""
            var aiContent = ""
            var tags = ""
            try {
                val summary = aiService.summarize(
                    content.take(AIConfig.maxProcessContentLength.toInt()),
                    "请对以下文章进行摘要总结，用中文输出 Markdown 格式：\n\n" +
                    "格式要求：\n" +
                    "- 先写一段总体概述\n" +
                    "- 然后一个 ### 核心观点 小节\n" +
                    "- 核心观点下使用有序列表，每项格式为：N. **标签：** 一句话说明\n" +
                    "- 总字数不超过500字",
                    apiAddress, apiToken, modelName,
                )
                aiContent = summary
            } catch (e: Exception) {
                log.e(e) { "Summary generation failed" }
                aiContent = article.content ?: ""
            }

            val state3 = mutableMapOf<Long, ArticleProcessingState>()
            state3[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Converting to Markdown")
            _processingStates.value = state3

            var aiMarkdownContent = ""
            val htmlContent = article.html_content ?: ""
            if (htmlContent.isNotBlank()) {
                try {
                    aiMarkdownContent = aiService.htmlToMarkdown(
                        htmlContent.take(AIConfig.maxProcessContentLength.toInt()),
                        "",
                        apiAddress, apiToken, modelName,
                    )
                } catch (e: Exception) {
                    log.e(e) { "Markdown conversion failed" }
                    aiMarkdownContent = htmlContent
                }
            }

            val state4 = mutableMapOf<Long, ArticleProcessingState>()
            state4[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Downloading cover image")
            _processingStates.value = state4

            var coverImage: String? = article.cover_image
            val coverImageUrl = article.cover_image_url
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
                content = article.content,
                aiContent = aiContent,
                htmlContent = article.html_content,
                aiMarkdownContent = aiMarkdownContent,
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "completed",
                coverImage = coverImage,
                coverImageUrl = article.cover_image_url,
                pubDate = article.pub_date,
            )

            val completedState = mutableMapOf<Long, ArticleProcessingState>()
            completedState[articleId] = ArticleProcessingState(articleId, "completed")
            _processingStates.value = completedState
            log.i { "AI processing completed: articleId=$articleId" }
        } catch (e: Exception) {
            log.e(e) { "AI processing failed: articleId=$articleId" }
            val errorState = mutableMapOf<Long, ArticleProcessingState>()
            errorState[articleId] = ArticleProcessingState(articleId, "error", e.message ?: "")
            _processingStates.value = errorState
        }
    }

    suspend fun extractContent(url: String): ExtractedContent {
        return withContext(Dispatchers.Default) {
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
                val coverImageUrl = extractCoverImageUrl(html)
                val textContent = extractTextContent(html)

                ExtractedContent(
                    title = title,
                    content = textContent.take(AIConfig.maxContentLength.toInt()),
                    htmlContent = html.take(AIConfig.maxProcessContentLength.toInt()),
                    coverImageUrl = coverImageUrl,
                )
            } catch (e: Exception) {
                log.e(e) { "Content extraction failed for $url" }
                try {
                    val response = httpClient.get(url)
                    val html = response.bodyAsText()
                    val title = extractTitle(html)
                    val coverImageUrl = extractCoverImageUrl(html)
                    val textContent = extractTextContent(html)
                    ExtractedContent(
                        title = title,
                        content = textContent.take(AIConfig.maxContentLength.toInt()),
                        htmlContent = html.take(AIConfig.maxProcessContentLength.toInt()),
                        coverImageUrl = coverImageUrl,
                    )
                } catch (e2: Exception) {
                    log.e(e2) { "Fallback extraction also failed" }
                    ExtractedContent(null, null, null, null)
                }
            }
        }
    }

    suspend fun reprocessArticle(articleId: Long) {
        val article = articleRepo.getById(articleId) ?: throw Exception("Article not found: $articleId")
        articleRepo.update(
            id = articleId, title = article.title, aiTitle = "",
            content = article.content, aiContent = "",
            htmlContent = article.html_content, aiMarkdownContent = "",
            url = article.url, isFavorite = article.is_favorite ?: 0L, comment = article.comment,
            status = "webContentFetched", coverImage = article.cover_image,
            coverImageUrl = article.cover_image_url, pubDate = article.pub_date,
        )
        val state = mutableMapOf<Long, ArticleProcessingState>()
        state[articleId] = ArticleProcessingState(articleId, "webContentFetched")
        _processingStates.value = state
        processAiTasksAsync(articleId)
    }

    private fun extractTitle(html: String): String? {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", options)
        return titleRegex.find(html)?.groupValues?.get(1)?.trim()?.let { title ->
            htmlDecode(title).replace(Regex("\\s+"), " ")
        }
    }

    private fun extractCoverImageUrl(html: String): String? {
        val opts = setOf(RegexOption.IGNORE_CASE)
        val ogImageRegex = Regex("""<meta[^>]*property\s*=\s*["']og:image["'][^>]*content\s*=\s*["']([^"']*)["']""", opts)
        val ogImage2Regex = Regex("""<meta[^>]*content\s*=\s*["']([^"']*)["'][^>]*property\s*=\s*["']og:image["']""", opts)
        return ogImageRegex.find(html)?.groupValues?.get(1)
            ?: ogImage2Regex.find(html)?.groupValues?.get(1)
            ?: extractFirstImgSrc(html)
    }

    private fun extractFirstImgSrc(html: String): String? {
        val imgRegex = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE))
        return imgRegex.find(html)?.groupValues?.get(1)
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
}

