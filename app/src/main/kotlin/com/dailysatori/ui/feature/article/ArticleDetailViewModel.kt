package com.dailysatori.ui.feature.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.task.ArticlePostProcessingScheduler
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.service.articleai.manualProcessable
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Tag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import com.dailysatori.service.ai.AiHttpFailure
import com.dailysatori.service.ai.AiQueueWaitTimeout
import com.dailysatori.service.parser.ArticleAiConfigurationMissingException
import com.dailysatori.service.parser.ArticleAiProcessingException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArticleDetailState(
    val article: Article? = null,
    val tags: List<Tag> = emptyList(),
    val sourceNames: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTabIndex: Int = 0,
    val isRefreshing: Boolean = false,
    val processingStatus: String = "",
    val processingStage: String = "",
    val processingProgress: String = "",
    val refreshError: String? = null,
)

/** A refresh only counts when the page produced a complete body; a preview is reported, not silently reused. */
internal fun refreshedRemoteArticleBody(content: String?): com.dailysatori.service.articleai.ArticleBody {
    val body = com.dailysatori.service.articleai.analyzeArticleBody(content)
    if (body.kind != com.dailysatori.service.articleai.ArticleBodyKind.BODY) {
        throw com.dailysatori.service.parser.ArticleBodyMissingException(
            "重新抓取未取得完整正文（只拿到来源预览或错误页）；已保留现有内容，可用「AI 处理」处理已保存的正文或预览",
        )
    }
    return body
}

class ArticleDetailViewModel(
    private val articleId: Long,
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
    private val postProcessingScheduler: ArticlePostProcessingScheduler,
    private val webpageParserService: WebpageParserService,
    private val articleAi: com.dailysatori.data.repository.ArticleAiRepository,
    private val articleAiQueue: com.dailysatori.service.articleai.ArticleAiQueue,
) : ViewModel() {
    private val _state = MutableStateFlow(ArticleDetailState())
    val state: StateFlow<ArticleDetailState> = _state.asStateFlow()

    init {
        loadArticle()
        viewModelScope.launch(Dispatchers.IO) {
            tagRepo.getByArticle(articleId).collect { tags ->
                _state.update { it.copy(tags = tags) }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            var contentRevision = 0L
            webpageParserService.processingStates.collect { states ->
                states[articleId]?.let { processing ->
                    val statusText = articleProcessingMessage(processing.status, processing.progress).orEmpty()
                    _state.update {
                        it.copy(
                            isRefreshing = isArticleProcessing(processing.status),
                            processingStatus = statusText,
                            processingStage = processing.status,
                            processingProgress = processing.progress,
                        )
                    }
                    if (shouldReloadArticleAfterProcessingState(processing.status, processing.progress) ||
                        contentRevision != processing.contentRevision) {
                        contentRevision = processing.contentRevision
                        loadArticle()
                    }
                }
            }
        }
    }

    fun loadArticle() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val article = articleRepo.getById(articleId)
            val sourceNames = articleRepo.getSourceNames(articleId)
            val processing = webpageParserService.processingStates.value[articleId]
            val stage = processing?.status ?: article?.status.orEmpty()
            val progress = processing?.progress.orEmpty()
            _state.update {
                it.copy(
                    article = article,
                    sourceNames = sourceNames,
                    isLoading = false,
                    isRefreshing = isArticleProcessing(stage),
                    processingStatus = articleProcessingMessage(stage, progress).orEmpty(),
                    processingStage = stage,
                    processingProgress = progress,
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(selectedTabIndex = index) }
    }

    fun toggleFavorite() {
        viewModelScope.launch(Dispatchers.IO) {
            articleRepo.toggleFavorite(articleId)
            loadArticle()
            val article = articleRepo.getById(articleId)
            if (article != null && article.is_favorite == 1L) {
                postProcessingScheduler.enqueueMemoryExtraction(articleId)
            }
        }
    }

    fun deleteArticle() {
        viewModelScope.launch(Dispatchers.IO) {
            articleRepo.delete(articleId)
        }
    }

    fun refreshArticle() {
        refreshArticleWithMessage("正在打开网页...", failureKey = "fetch_failed") {
            val article = articleRepo.getById(articleId)
            if (article?.source_type == "remote_news") refreshRemoteOriginal(article)
            else webpageParserService.refreshArticle(articleId)
        }
    }

    private suspend fun refreshRemoteOriginal(article: Article) {
        val url = article.url ?: error("文章没有来源链接")
        val fetched = webpageParserService.extractContent(url)
        val body = refreshedRemoteArticleBody(fetched.content)
        val updated = articleAi.transaction {
            val current = articleRepo.getById(articleId) ?: return@transaction null
            if (articleAi.originalVersion(current) != articleAi.originalVersion(article)) return@transaction null
            articleRepo.updateOriginalMarkdownContent(articleId, "## 原文\n\n${body.markdown}")
            articleRepo.getById(articleId)?.also {
                articleAi.record(it, com.dailysatori.service.remotenews.RemoteArticle(articleId, content = body.markdown))
            }
        } ?: throw IllegalStateException("处理期间原文已更新，请重新刷新")
        if (!articleAi.input(updated).manualProcessable())
            throw com.dailysatori.service.parser.ArticleBodyMissingException("重新抓取未取得可用正文；已保留现有内容")
        // The source was fetched again, so rerun AI even when the body text matches the last version.
        articleAiQueue.manual(articleId, redo = true)
    }

    private var manualProcessing: Job? = null

    fun processWithExistingBody(retranslate: Boolean = false) {
        if (manualProcessing?.isActive == true || state.value.isRefreshing) return
        manualProcessing = refreshArticleWithMessage("正在用已保存的正文进行 AI 处理...",
            failureKey = if (retranslate) "retranslation_failed" else "retry_saved_reading") {
            val article = articleRepo.getById(articleId) ?: error("文章已删除")
            if (article.source_type == "remote_news") processStoredBody("文章没有可处理的正文或来源预览")
            else webpageParserService.reprocessArticle(articleId, retranslate)
        }
    }

    private suspend fun processStoredBody(noTextMessage: String) {
        val article = articleRepo.getById(articleId) ?: error("文章已删除")
        if (!articleAi.input(article).manualProcessable())
            throw com.dailysatori.service.parser.ArticleBodyMissingException(noTextMessage)
        articleAiQueue.manual(articleId)
    }

    private fun refreshArticleWithMessage(
        initialStatus: String,
        failureKey: String,
        refresh: suspend () -> Unit,
    ): Job = viewModelScope.launch(Dispatchers.IO) {
        _state.update {
            it.copy(
                isRefreshing = true,
                processingStatus = initialStatus,
                processingStage = "pending",
                processingProgress = "",
                refreshError = null,
            )
        }
        var errorKey: String? = null
        try {
            refresh()
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            errorKey = articleDetailFailureKey(error, failureKey)
        }
        val article = articleRepo.getById(articleId)
        _state.update {
            it.copy(isRefreshing = false, article = article,
                refreshError = errorKey ?: failureKey.takeIf { article?.status == "error" })
        }
    }
}

internal fun articleDetailFailureKey(error: Exception, fallback: String): String = when (error) {
    is ArticleAiConfigurationMissingException -> "paused_configuration"
    is AiQueueWaitTimeout -> "reading_busy"
    is AiHttpFailure -> when (error.status) {
        401, 403 -> "paused_auth"
        402 -> "paused_billing"
        429 -> "paused_rate"
        else -> if (fallback == "fetch_failed") "retry_saved_reading" else fallback
    }
    is ArticleAiProcessingException -> when {
        fallback == "retranslation_failed" -> fallback
        error.summaryFailed && !error.translationFailed -> "retry_saved_summary"
        error.translationFailed && !error.summaryFailed -> "retry_saved_translation"
        else -> "retry_saved_reading"
    }
    else -> fallback
}
