package com.dailysatori.ui.feature.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.countNewLeadingArticles
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.shouldShowNewArticlesIndicator
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArticlesState(
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedTagId: Long? = null,
    val showFavoritesOnly: Boolean = false,
    val isSearchVisible: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val dailyCounts: Map<Long, Long> = emptyMap(),
    val isAddingArticle: Boolean = false,
    val isRefreshing: Boolean = false,
    val scrollToTopRequest: Long = 0,
    val newArticlesAboveCount: Int = 0,
)

class ArticlesViewModel(
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
    private val articleProcessingScheduler: ArticleProcessingScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(ArticlesState())
    val state: StateFlow<ArticlesState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var rememberedTopArticleId: Long? = null
    private var rememberedWasAtTop = true

    init {
        android.util.Log.d("ArticlesVM", "ViewModel initializing, loading articles...")
        loadArticles()
        viewModelScope.launch(Dispatchers.IO) {
            tagRepo.getAll().collect { tags ->
                _state.update { it.copy(tags = tags) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            articleRepo.getDailyCounts().collect { counts ->
                _state.update { it.copy(dailyCounts = counts) }
            }
        }
    }

    fun loadArticles() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val currentState = _state.value
            val flow = when {
                currentState.searchQuery.isNotBlank() -> articleRepo.search(currentState.searchQuery)
                currentState.selectedTagId != null -> articleRepo.getByTag(currentState.selectedTagId!!)
                currentState.showFavoritesOnly -> articleRepo.getFavorites()
                else -> articleRepo.getAll()
            }
            android.util.Log.d("ArticlesVM", "Loading articles with flow")
            flow.collect { articles ->
                android.util.Log.d("ArticlesVM", "Got ${articles.size} articles")
                _state.update { it.copy(articles = articles, isLoading = false) }
            }
        }
    }

    fun refreshArticles() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isRefreshing = true) }
            val currentState = _state.value
            val flow = when {
                currentState.searchQuery.isNotBlank() -> articleRepo.search(currentState.searchQuery)
                currentState.selectedTagId != null -> articleRepo.getByTag(currentState.selectedTagId!!)
                currentState.showFavoritesOnly -> articleRepo.getFavorites()
                else -> articleRepo.getAll()
            }
            flow.collect { articles ->
                _state.update { it.copy(articles = articles, isRefreshing = false, isLoading = false) }
            }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadArticles()
    }

    fun filterByTag(tagId: Long?) {
        _state.update { it.copy(selectedTagId = tagId) }
        loadArticles()
    }

    fun toggleFavoritesOnly() {
        _state.update { it.copy(showFavoritesOnly = !_state.value.showFavoritesOnly) }
        loadArticles()
    }

    fun setFavoritesOnly(enabled: Boolean) {
        if (_state.value.showFavoritesOnly == enabled) return
        _state.update { it.copy(showFavoritesOnly = enabled) }
        loadArticles()
    }

    fun toggleSearch() {
        _state.update { it.copy(isSearchVisible = !_state.value.isSearchVisible) }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            articleRepo.toggleFavorite(id)
        }
    }

    fun addArticle(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isAddingArticle = true) }
            articleProcessingScheduler.enqueueSave(url)
            _state.update { it.copy(isAddingArticle = false, scrollToTopRequest = it.scrollToTopRequest + 1) }
        }
    }

    fun rememberVisibleTopArticle(articleId: Long?, isAtTop: Boolean) {
        rememberedTopArticleId = articleId
        rememberedWasAtTop = isAtTop
    }

    fun checkNewArticlesAbove(firstVisibleArticleId: Long?, isAtTop: Boolean) {
        val referenceArticleId = rememberedTopArticleId
        val newCount = countNewLeadingArticles(_state.value.articles.map { it.id }, referenceArticleId)
        val wasOrIsAtTop = rememberedWasAtTop || isAtTop
        _state.update {
            it.copy(newArticlesAboveCount = if (shouldShowNewArticlesIndicator(newCount, wasOrIsAtTop)) newCount else 0)
        }
    }

    fun clearNewArticlesIndicator() {
        _state.update { it.copy(newArticlesAboveCount = 0) }
        rememberedTopArticleId = _state.value.articles.firstOrNull()?.id
        rememberedWasAtTop = true
    }
}
