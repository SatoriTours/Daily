package com.dailysatori.ui.feature.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.task.ArticlePostProcessingScheduler
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Tag
import kotlinx.coroutines.Dispatchers
import com.dailysatori.service.articleai.manualProcessable
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

class ArticleDetailViewModel(
    private val articleId: Long,
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
    private val postProcessingScheduler: ArticlePostProcessingScheduler,
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
        refreshArticleWithMessage("正在打开网页...") {
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

    /** Runs AI on what is already stored; a preview is forced, a full body only fills missing results. */
    fun processWithExistingBody() {
        refreshArticleWithMessage("正在用已保存的正文进行 AI 处理...") {
            val article = articleRepo.getById(articleId) ?: error("文章已删除")
            if (article.source_type == "remote_news") processStoredBody("文章没有可处理的正文或来源预览")
            else webpageParserService.reprocessArticle(articleId)
        }
    }

    private fun refreshArticleWithMessage(
        initialStatus: String,
        refresh: suspend () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isRefreshing = true,
                    processingStatus = initialStatus,
    private suspend fun processStoredBody(noTextMessage: String) {
        val article = articleRepo.getById(articleId) ?: error("文章已删除")
        if (!articleAi.input(article).manualProcessable())
            throw com.dailysatori.service.parser.ArticleBodyMissingException(noTextMessage)
        articleAiQueue.manual(articleId)
    }

                    processingStage = "pending",
                    processingProgress = "",
                    refreshError = null,
                )
            }
            try {
                refresh()
            } catch (e: Exception) {
                val article = articleRepo.getById(articleId)
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        article = article,
                        refreshError = e.message ?: "刷新失败",
                    )
                }
                return@launch
            }
            val article = articleRepo.getById(articleId)
            if (article != null && article.status == "error") {
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        article = article,
                        refreshError = article.ai_content ?: "处理失败",
                    )
                }
            } else {
                _state.update { it.copy(isRefreshing = false, article = article, refreshError = null) }
            }
        }
    }
}
