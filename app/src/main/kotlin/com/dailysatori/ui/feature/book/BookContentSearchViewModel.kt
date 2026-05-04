package com.dailysatori.ui.feature.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.BookContentSearchResult
import com.dailysatori.data.repository.BookViewpointRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookContentSearchState(
    val query: String = "",
    val results: List<BookContentSearchResultItem> = emptyList(),
    val hasSearched: Boolean = false,
) {
    val visibleResults: List<BookContentSearchResultItem>
        get() = if (query.isBlank()) emptyList() else results
}

data class BookContentSearchResultItem(
    val viewpointId: Long,
    val bookId: Long,
    val bookTitle: String,
    val author: String,
    val title: String,
    val content: String,
    val example: String,
) {
    fun matches(keyword: String): Boolean {
        val query = keyword.trim().lowercase()
        if (query.isBlank()) return false
        return listOf(bookTitle, author, title, content, example).any { it.lowercase().contains(query) }
    }
}

class BookContentSearchViewModel(
    private val viewpointRepo: BookViewpointRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BookContentSearchState())
    val state: StateFlow<BookContentSearchState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var searchSequence = 0L

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query, results = emptyList(), hasSearched = false) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        val requestId = ++searchSequence
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val results = viewpointRepo.searchBookContent(query).map { it.toUiItem() }
            if (requestId != searchSequence || _state.value.query.trim() != query) return@launch
            _state.update { it.copy(results = results, hasSearched = true) }
        }
    }
}

private fun BookContentSearchResult.toUiItem(): BookContentSearchResultItem =
    BookContentSearchResultItem(
        viewpointId = viewpointId,
        bookId = bookId,
        bookTitle = bookTitle,
        author = author,
        title = title,
        content = content,
        example = example,
    )

fun bookContentSearchBookLine(bookTitle: String, author: String): String =
    if (author.isBlank()) "《$bookTitle》" else "《$bookTitle》 · $author"

fun bookContentSearchPreview(text: String, maxLength: Int = 48): String =
    if (text.length <= maxLength) text else text.take((maxLength - 1).coerceAtLeast(1)).trimEnd() + "..."
