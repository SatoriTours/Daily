package com.dailysatori.viewmodel

import androidx.lifecycle.ViewModel
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BooksState(
    val books: List<com.dailysatori.shared.db.Book> = emptyList(),
    val viewpoints: List<com.dailysatori.shared.db.BookViewpoint> = emptyList(),
    val currentBookId: Long? = null,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
)

class BooksViewModel(
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BooksState())
    val state: StateFlow<BooksState> = _state

    fun loadBooks() {
        _state.value = _state.value.copy(isLoading = true)
        _state.value = _state.value.copy(isLoading = false)
    }

    fun selectBook(bookId: Long?) {
        _state.value = _state.value.copy(currentBookId = bookId, currentPage = 0)
    }

    fun setPage(page: Int) {
        _state.value = _state.value.copy(currentPage = page)
    }
}
