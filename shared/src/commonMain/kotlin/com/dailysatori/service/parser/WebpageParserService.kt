package com.dailysatori.service.parser

import com.dailysatori.service.articleai.analyzeArticleBody

import com.dailysatori.data.repository.reusableOriginalBody

import co.touchlab.kermit.Logger
import com.dailysatori.service.diagnostics.*
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ImageRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.REMOTE_PROCESSING_STALE
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.service.mapConcurrently
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.shared.db.Article
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
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
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

data class ArticleProcessingState(
    val articleId: Long,
    val status: String,
    val progress: String = "",
    val contentRevision: Long = 0,
)

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

internal fun shouldPersistArticleProcessingError(error: Throwable): Boolean =
    error !is CancellationException || error is TimeoutCancellationException

class WebpageParserService(
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
    private val imageRepo: ImageRepository,
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    webViewLoader: WebViewLoader,
    private val fileManager: FileManager,
    private val httpClient: HttpClient,
    externalFavoriteSourceRepo: ExternalFavoriteSourceRepository,
    xBookmarksConnector: XBookmarksConnector,
    private val remoteArticleSyncRepo: RemoteArticleSyncRepository? = null,
    private val settingRepo: SettingRepository,
    private val coverScheduler: ArticleCoverScheduler? = null,
    externalFavoriteItemRepo: ExternalFavoriteItemRepository? = null,
) {
    private val log = Logger.withTag("WebpageParser")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queueLock = Any()
    private val pendingProcessingIds = ArrayDeque<Long>()
    private val queuedAt = mutableMapOf<Long, Long>()
    private val activeProcessingIds = mutableSetOf<Long>()
    private val refreshIntents = mutableSetOf<Long>()
    private val slotChanges = MutableStateFlow(0L)
    private val contentFetcher = ArticleContentFetcher(
        webViewLoader, externalFavoriteSourceRepo, xBookmarksConnector, externalFavoriteItemRepo,
    )
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

    /** One pending refresh per article: repeated clicks merge and the intent survives until it runs. */
    private fun requestRefreshAfterProcessing(articleId: Long) {
        articleRepo.updateStatus(articleId, "pending")
        synchronized(queueLock) { refreshIntents += articleId }
        enqueueArticleProcessing(articleId)
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
        val articleIds = mutableListOf<Triple<Long, Long, Boolean>>()
        synchronized(queueLock) {
            while (activeProcessingIds.size < MAX_CONCURRENT_PROCESSING && pendingProcessingIds.isNotEmpty()) {
                val articleId = pendingProcessingIds.removeFirst()
                val waitMs = DiagnosticLog.elapsed() - (queuedAt.remove(articleId) ?: DiagnosticLog.elapsed())
                if (activeProcessingIds.add(articleId)) {
                    articleIds.add(Triple(articleId, waitMs.coerceAtLeast(0), refreshIntents.remove(articleId) == true))
                }
            }
        }
        articleIds.forEach { (articleId, waitMs, refresh) ->
            scope.launch {
                DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.PARSER,
                    fields = mapOf("articleStage" to "queued", "durationMs" to waitMs.toString()))
                try {
                    val article = articleRepo.getById(articleId)
                    if (article != null && (refresh || isRecoverableArticleForProcessing(article.status, article.ai_content, article.ai_markdown_content))) {
                        setProcessingState(articleId, "pending")
                        val extracted = (if (refresh) null else existingArticleOriginalExtractedContent(article))
                            ?: article.url?.let { extractContent(it) }
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
                        processAiTasks(articleId, extracted, forceAi = refresh)
                    }
                } catch (e: Exception) {
                    log.e(e) { "Queued article processing failed: articleId=$articleId" }
                    if (refresh && shouldPersistArticleProcessingError(e)) {
                        recordProcessingFailure(articleId, e, retryOnFailure = false)
                    }
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
            // A refresh requested while this article was running is served once after it releases the slot.
            if (articleId in refreshIntents && articleId !in pendingProcessingIds) {
                pendingProcessingIds.addLast(articleId)
                queuedAt[articleId] = DiagnosticLog.elapsed()
            }
        }
        slotChanges.update { it + 1 }
        drainProcessingQueue()
    }

    suspend fun processAiTasks(
        articleId: Long,
        extracted: ExtractedContent? = null,
        retryOnFailure: Boolean = false,
        forceAi: Boolean = false,
        retranslate: Boolean = false,
    ) = DiagnosticLog.diagnostics.operation(DiagnosticSource.PARSER) {
        processAiTasksRecorded(articleId, extracted, retryOnFailure, forceAi, retranslate)
    }

    private suspend fun processAiTasksRecorded(
        articleId: Long,
        extracted: ExtractedContent?,
        retryOnFailure: Boolean,
        forceAi: Boolean,
        retranslate: Boolean,
    ) {
        val article = articleRepo.getById(articleId) ?: return
        try {
            val sourceSnapshot = persistOriginal(article, extracted)
            val original = com.dailysatori.data.repository.articleOriginalMarkdown(sourceSnapshot, null,
                isRemoteSnapshot = remoteArticleSyncRepo?.findByArticleId(articleId) != null).orEmpty()
            setProcessingState(articleId, "webContentFetched", "Original saved")
            articleRepo.updateStatus(articleId, "aiProcessing")
            scheduleCover(articleId)
            val body = analyzeArticleBody(original)
            val sameOriginal = sourceSnapshot == article.original_markdown_content
            val usableSummary = !forceAi && sameOriginal &&
                existingSummaryOrNull(article.ai_content)?.let { !articleNeedsTranslation(it) } == true
            val plan = body.automatic(usableSummary)
            val needsSummary = !retranslate && plan?.summary == true
            val needsTranslation = retranslate || (plan?.translation == true &&
                (forceAi || !sameOriginal || hasMissingEnglishArticleTranslation(original, article.ai_content, article.ai_markdown_content)))
            if (!retranslate && plan?.translation != true) {
                articleRepo.updateMarkdownIfOriginalMatches(articleId, sourceSnapshot, original)
            }
            if (needsSummary || needsTranslation) runArticleAi(article, extracted, sourceSnapshot, original,
                needsSummary, needsTranslation, forceAi || retranslate)
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

    private suspend fun runArticleAi(
        article: Article, extracted: ExtractedContent?, sourceSnapshot: String, original: String,
        needsSummary: Boolean, needsTranslation: Boolean, forceAi: Boolean,
    ) {
        val articleId = article.id
        val config = aiConfigService.getConfig(AiPurpose.EXTERNAL_CONTENT)
        if (config == null || config.api_address.isBlank() || config.api_token.isBlank()) {
            throw ArticleAiConfigurationMissingException()
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
            force = forceAi,
            keepOverview = !needsSummary,
            keepMarkdown = !needsTranslation,
            translate = needsTranslation,
        )
    }

    private fun persistOriginal(article: Article, extracted: ExtractedContent?): String {
        if (extracted?.readableHtmlContent.isNullOrBlank() && !article.original_markdown_content.isNullOrBlank() &&
            extracted?.content == article.original_markdown_content) return article.original_markdown_content
        if (remoteArticleSyncRepo?.findByArticleId(article.id) != null && !article.original_markdown_content.isNullOrBlank()) {
            return article.original_markdown_content
        }
        val original = extracted?.let { articleOriginalMarkdown(it, article.url.orEmpty()) }
            ?.takeIf { it.isNotBlank() } ?: article.original_markdown_content.orEmpty()
        if (original.isBlank()) throw ArticleBodyMissingException()
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

    /** A failed refresh keeps the saved article instead of leaving the loading placeholder behind. */
    private fun restoreArticleAfterFailedRefresh(articleId: Long, before: Article) {
        val current = articleRepo.getById(articleId) ?: return
        if (current.title != "正在加载...") return
        articleRepo.update(
            id = articleId,
            title = before.title,
            aiTitle = current.ai_title,
            aiContent = current.ai_content,
            aiMarkdownContent = current.ai_markdown_content,
            url = current.url,
            isFavorite = current.is_favorite ?: 0L,
            comment = current.comment,
            status = current.status.orEmpty().ifBlank { "error" },
            coverImage = current.cover_image,
            coverImageUrl = current.cover_image_url,
            pubDate = current.pub_date,
        )
    }


    suspend fun extractContent(url: String): ExtractedContent =
        measureArticleProcessingStage("extraction") {
            DiagnosticLog.diagnostics.operation(DiagnosticSource.PARSER) { contentFetcher.extractContent(url) }
        }

    /** Loads a public page without applying article-length gates; cancellation stops the WebView load. */
    suspend fun fetchPublicPage(url: String): ExtractedContent = contentFetcher.fetchPublicPage(url)

    suspend fun refreshArticle(articleId: Long) {
        val article = articleRepo.getById(articleId)
            ?: throw Exception("Article not found: $articleId")
        val url = article.url ?: throw Exception("Article has no URL")
        val ownsProcessing = markArticleActive(articleId)
        if (!ownsProcessing) {
            requestRefreshAfterProcessing(articleId)
            return
        }
        // This run already services any refresh that was owed for the article.
        synchronized(queueLock) { refreshIntents.remove(articleId) }

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

            // A manual refresh must actually open the page; a stored body cannot stand in for it.
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

            setProcessingState(articleId, "webContentFetched")

            processAiTasks(articleId, extracted, forceAi = true)

            log.i { "refreshArticle completed: articleId=$articleId" }
        } catch (e: Exception) {
            if (!shouldPersistArticleProcessingError(e)) throw e
            log.e(e) { "refreshArticle failed: articleId=$articleId" }
            restoreArticleAfterFailedRefresh(articleId, article)
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

            val extracted = contentFetcher.fetchXPostExtractedContent(url)

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

    suspend fun reprocessArticle(articleId: Long, retranslate: Boolean = false) {
        awaitProcessingOwnership(articleId)
        try {
            val article = articleRepo.getById(articleId) ?: throw CancellationException("文章已删除")
            val body = article.reusableOriginalBody()
                ?: throw ArticleBodyMissingException()
            val extracted = existingArticleOriginalExtractedContent(article)?.copy(content = body)
                ?: throw ArticleBodyMissingException()
            val usableSummary = existingSummaryOrNull(article.ai_content)?.let { !articleNeedsTranslation(it) } == true
            val missingTranslation = hasMissingEnglishArticleTranslation(body, article.ai_content, article.ai_markdown_content)
            if (retranslate && (missingTranslation || articleTranslationMarkdown(body, article.ai_markdown_content) == null)) return
            val plan = analyzeArticleBody(body).automatic(usableSummary)
            if (!retranslate && article.status == "completed" && plan?.summary != true && !missingTranslation) return
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
            processAiTasks(articleId, extracted, retranslate = retranslate)

            val processed = articleRepo.getById(articleId) ?: throw IllegalStateException("Processed article disappeared")
            if ((plan?.summary == true && articleNeedsTranslation(processed.ai_content.orEmpty())) ||
                ((retranslate || plan?.translation == true) &&
                    hasMissingEnglishArticleTranslation(body, processed.ai_content, processed.ai_markdown_content))) {
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
    }
}

private const val REMOTE_PROCESSING_VERSION = "article-general-v4"
