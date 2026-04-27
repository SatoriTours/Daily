package com.dailysatori.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
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
)

class ArticleDetailViewModel(
    private val articleId: Long,
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
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
    }

    fun loadArticle() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val article = articleRepo.getById(articleId)
            _state.update { it.copy(article = article, isLoading = false) }
        }
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(selectedTabIndex = index) }
    }

    fun toggleFavorite() {
        viewModelScope.launch(Dispatchers.IO) {
            articleRepo.toggleFavorite(articleId)
            loadArticle()
        }
    }

    fun deleteArticle() {
        viewModelScope.launch(Dispatchers.IO) {
            articleRepo.delete(articleId)
        }
    }
}
