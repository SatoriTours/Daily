package com.dailysatori.ui.feature.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.service.book.BookSearchResult
import com.dailysatori.service.mcp.McpAgentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookSearchState(
    val query: String = "",
    val results: List<BookSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val addedBookTitle: String? = null,
    val error: String? = null,
)

class BookSearchViewModel(
    private val mcpAgentService: McpAgentService,
    private val bookRepo: BookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BookSearchState())
    val state: StateFlow<BookSearchState> = _state.asStateFlow()

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null, addedBookTitle = null) }
            try {
                val results = mcpAgentService.searchBookOnline(query)
                if (results.isEmpty()) {
                    _state.update { it.copy(isLoading = false, error = "未找到相关书籍") }
                } else {
                    _state.update { it.copy(results = results, isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "搜索失败") }
            }
        }
    }

    fun addBook(result: BookSearchResult) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bookRepo.insert(
                    title = result.title,
                    author = result.author,
                    category = result.category,
                    coverImage = result.coverUrl,
                    introduction = result.introduction,
                )
                _state.update { it.copy(addedBookTitle = result.title) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "添加失败") }
            }
        }
    }

    fun clearAdded() {
        _state.update { it.copy(addedBookTitle = null) }
    }
}
