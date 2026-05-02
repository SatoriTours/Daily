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

internal fun articleSummaryPrompt(): String = """
    你是一位专业的内容分析师，擅长提炼文章的核心要点。请阅读用户给出的文章正文，用中文输出 Markdown 格式的文章分析。

    ## 输出格式
    # 标题
    生成一个详细、简明扼要的标题，控制在 15-25 字，准确概括文章核心内容。

    ## 核心内容
    用一个完整段落详细概括文章的主要内容和价值，控制在 50-80 字，不要分段，语言流畅连贯，突出文章的核心观点。

    ## 核心观点
    使用有序列表提取 2-5 个最重要的要点，能 2 个就 2 个，越少越好。每个要点应覆盖核心观点、重要数据、典型案例或关键结论等实质性内容。

    要求：
    1. 只返回 Markdown 内容，不要使用代码块包裹，不要输出格式之外的解释文字。
    2. 只基于正文内容，不要编造信息；即使原文没有明确标题，也必须总结生成标题。
    3. 不要输出作者、发布时间、来源、标签等元数据，除非正文明确讨论它们。
    4. 核心观点按重要性排序，使用 "1. "、"2. " 编号格式，每个要点独立成行。
    5. 要点精炼有力，去除冗余修饰，避免解释性语言。
""".trimIndent()

internal fun htmlToReadableMarkdownPrompt(): String = """
    你是一个专业的技术文档排版专家，擅长将 HTML 内容转换为精美、专业的 Markdown 格式，达到类似 InfoQ 技术文章的排版效果，并符合 GitHub Markdown 的渲染标准。

    ## 排版目标
    创建像专业技术出版物一样的精美 Markdown 文档，具有清晰的层次结构、适当的间距和专业的视觉效果，确保在 GitHub 上渲染时具有良好的可读性。

    ## 核心排版规范

    ### 1. 标题处理
    - 主标题使用 # 标题
    - 小节标题使用 ## 标题
    - 子节标题使用 ### 标题
    - 如果原文没有明确标题，不要随意创建标题，避免将第一段内容误认为标题

    ### 2. 段落格式
    - 段落之间使用一个空行分隔
    - 段落内部不使用多余空行
    - 保持段落长度适中，避免过长的段落

    ### 3. 列表格式
    - 无序列表使用 "- 项目" 格式
    - 有序列表使用 "1. 项目" 格式
    - 列表项之间不空行，保持紧凑格式
    - 列表与其他内容之间保持一个空行

    ### 4. 特殊元素
    - 代码块使用 ```语言名 格式，包含语法高亮
    - 图片使用 ![描述](图片URL) 格式
    - 链接使用 [锚文本](URL) 格式
    - 表格使用标准 Markdown 表格语法
    - 引用使用 > 格式

    ## 内容处理原则

    ### 1. 内容忠实度
    - 严格保持原文主要内容，不增、删、改原意
    - 删除无关元素，包括导航、广告、版权声明、作者信息、脚本、样式、按钮、推荐阅读等非正文噪音
    - 完整保留正文中的列表、表格、代码块、图片链接等信息

    ### 2. 排版优化
    - 删除所有多余空行，只在段落间保留一个空行
    - 合并过短的相邻段落，提高可读性
    - 统一使用中文标点符号
    - 只在大的内容块之间使用空行，小的列表项之间保持紧凑

    ### 3. GitHub Markdown 兼容性
    - 确保所有 Markdown 语法符合 GitHub Flavored Markdown 标准
    - 优化表格、代码块等元素的显示效果

    ### 4. 禁止行为
    - 不添加原文没有的解释性内容
    - 不遗漏重要正文
    - 不改变信息呈现顺序
    - 不保留原生 HTML 标签
    - 不将普通段落误认为标题
    - 不输出摘要、处理说明或代码块围栏

    ## 输出要求
    - 只返回转换后的 Markdown 内容，不添加任何说明
    - 返回内容翻译成流畅的中文
    - 确保最终排版美观、专业、易读
""".trimIndent()

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

            processAiTasksAsync(articleId, extracted)

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
        }
    }

    fun processAiTasksAsync(articleId: Long, extracted: ExtractedContent? = null) {
        scope.launch {
            try {
                processAiTasks(articleId, extracted)
            } catch (e: Exception) {
                log.e(e) { "Async AI processing failed: articleId=$articleId" }
            }
        }
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

            val content = extracted?.content ?: ""
            val htmlContent = extracted?.htmlContent ?: ""
            var aiContent = ""
            var tags = ""
            try {
                val summary = aiService.summarize(
                    content.take(AIConfig.maxProcessContentLength.toInt()),
                    articleSummaryPrompt(),
                    apiAddress, apiToken, modelName, provider,
                )
                aiContent = generatedOrExisting(summary, article.ai_content, "summary")
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
                        htmlContent.take(AIConfig.maxProcessContentLength.toInt()),
                        htmlToReadableMarkdownPrompt(),
                        apiAddress, apiToken, modelName, provider,
                    )
                    aiMarkdownContent = generatedOrExisting(aiMarkdownContent, article.ai_markdown_content, "markdown")
                } catch (e: Exception) {
                    log.e(e) { "Markdown conversion failed" }
                    aiMarkdownContent = generatedOrExisting("", article.ai_markdown_content, "markdown")
                }
            } else {
                aiMarkdownContent = generatedOrExisting("", article.ai_markdown_content, "markdown")
            }

            val state4 = mutableMapOf<Long, ArticleProcessingState>()
            state4[articleId] = ArticleProcessingState(articleId, "aiProcessing", "Downloading cover image")
            _processingStates.value = state4
            updateArticleStatus(article, "aiProcessing")

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
                aiContent = aiContent,
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
