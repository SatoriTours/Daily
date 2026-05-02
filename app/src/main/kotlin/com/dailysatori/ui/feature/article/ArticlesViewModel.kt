package com.dailysatori.ui.feature.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.service.parser.WebpageParserService
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
)

class ArticlesViewModel(
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
    private val webpageParserService: WebpageParserService,
) : ViewModel() {
    private val _state = MutableStateFlow(ArticlesState())
    val state: StateFlow<ArticlesState> = _state.asStateFlow()

    private var loadJob: Job? = null

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
            try {
                webpageParserService.saveWebpage(url = url, comment = null, title = null, tags = null)
            } catch (_: Exception) {
            }
            _state.update { it.copy(isAddingArticle = false) }
        }
    }
}
