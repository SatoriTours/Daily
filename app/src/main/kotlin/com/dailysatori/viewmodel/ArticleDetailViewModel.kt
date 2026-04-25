package com.dailysatori.viewmodel

import androidx.lifecycle.ViewModel
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ArticleDetailState(
    val article: com.dailysatori.shared.db.Article? = null,
    val tags: List<com.dailysatori.shared.db.Tag> = emptyList(),
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

    fun loadArticle() {
        _state.value = _state.value.copy(isLoading = true)
        val article = articleRepo.getById(articleId)
        _state.value = _state.value.copy(article = article, isLoading = false)
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTabIndex = index)
    }

    fun toggleFavorite() {
        articleRepo.toggleFavorite(articleId)
        loadArticle()
    }
}
