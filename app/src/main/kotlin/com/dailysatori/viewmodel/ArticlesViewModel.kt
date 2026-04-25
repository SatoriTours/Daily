package com.dailysatori.viewmodel

import androidx.lifecycle.ViewModel
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ArticlesState(
    val articles: List<com.dailysatori.shared.db.Article> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedTagId: Long? = null,
    val showFavoritesOnly: Boolean = false,
    val isSearchVisible: Boolean = false,
)

class ArticlesViewModel(
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ArticlesState())
    val state: StateFlow<ArticlesState> = _state.asStateFlow()

    fun loadArticles() {
        _state.value = _state.value.copy(isLoading = true)
        // Will be connected to actual repository flows later
        _state.value = _state.value.copy(isLoading = false)
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun filterByTag(tagId: Long?) {
        _state.value = _state.value.copy(selectedTagId = tagId)
    }

    fun toggleFavoritesOnly() {
        _state.value = _state.value.copy(showFavoritesOnly = !_state.value.showFavoritesOnly)
    }

    fun toggleSearch() {
        _state.value = _state.value.copy(isSearchVisible = !_state.value.isSearchVisible)
    }
}
