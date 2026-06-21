package com.dailysatori.ui.feature.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArticleDetailState(
    val article: Article? = null,
    val tags: List<Tag> = emptyList(),
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
    private val memoryExtractService: MemoryExtractService,
    private val webpageParserService: WebpageParserService,
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
                    if (shouldReloadArticleAfterProcessingState(processing.status)) {
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
            val processing = webpageParserService.processingStates.value[articleId]
            val stage = processing?.status ?: article?.status.orEmpty()
            val progress = processing?.progress.orEmpty()
            _state.update {
                it.copy(
                    article = article,
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
                val text = article.ai_markdown_content ?: ""
                val title = article.ai_title ?: article.title ?: "未命名"
                memoryExtractService.extractAndSave(
                    sourceType = "article",
                    sourceId = articleId,
                    title = title,
                    content = text,
                )
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
            webpageParserService.refreshArticle(articleId)
        }
    }

    fun refreshArticleWithXApi() {
        refreshArticleWithMessage("正在通过 X API 获取内容...") {
            webpageParserService.refreshArticleWithXApi(articleId)
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
