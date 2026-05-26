package com.dailysatori.ui.feature.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.service.book.BookAiFallbackGenerator
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Book_viewpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BooksState(
    val books: List<Book> = emptyList(),
    val viewpoints: List<Book_viewpoint> = emptyList(),
    val currentBookId: Long? = null,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class BooksViewModel(
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
    private val bookAiFallbackGenerator: BookAiFallbackGenerator,
) : ViewModel() {
    private val _state = MutableStateFlow(BooksState())
    val state: StateFlow<BooksState> = _state.asStateFlow()

    init {
        loadBooks()
    }

    fun loadBooks() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            bookRepo.getAll().collect { books ->
                _state.update { it.copy(books = books, isLoading = false) }
                if (books.isNotEmpty() && _state.value.currentBookId == null) {
                    selectBook(books.first().id)
                }
            }
        }
    }

    fun selectBook(bookId: Long?) {
        _state.update { it.copy(currentBookId = bookId, currentPage = 0) }
        if (bookId != null) {
            loadViewpoints(bookId)
        }
    }

    private fun loadViewpoints(bookId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            viewpointRepo.getByBook(bookId).collect { viewpoints ->
                _state.update { it.copy(viewpoints = viewpoints) }
            }
        }
    }

    fun shuffle() {
        val books = _state.value.books
        if (books.isNotEmpty()) {
            val randomBook = books.random()
            selectBook(randomBook.id)
        }
    }

    fun refresh() {
        loadBooks()
    }

    fun setPage(page: Int) {
        _state.update { it.copy(currentPage = page) }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            viewpointRepo.deleteByBook(bookId)
            bookRepo.delete(bookId)
            _state.update { it.copy(currentBookId = null, viewpoints = emptyList()) }
        }
    }

    fun regenerateViewpoint(viewpointId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val viewpoint = viewpointRepo.getById(viewpointId) ?: return@launch
            val book = bookRepo.getById(viewpoint.book_id) ?: return@launch
            viewpointRepo.updateStatusContext(
                id = viewpoint.id,
                title = viewpoint.title,
                content = viewpoint.content,
                example = viewpoint.example,
                status = "generating",
                errorMessage = "",
                outlineJson = viewpoint.outline_json,
                sourceNotes = viewpoint.source_notes,
            )
            val draft = runCatching { bookAiFallbackGenerator.regenerate(book, viewpoint) }.getOrElse { error ->
                com.dailysatori.service.book.BookViewpointDraft(
                    title = viewpoint.title,
                    content = viewpoint.content,
                    example = viewpoint.example,
                    status = "failed",
                    errorMessage = error.message ?: "AI 观点生成失败，请稍后重试",
                    outlineJson = viewpoint.outline_json,
                    sourceNotes = viewpoint.source_notes,
                )
            }
            viewpointRepo.updateStatusContext(
                id = viewpoint.id,
                title = draft.title,
                content = draft.content,
                example = draft.example,
                status = draft.status,
                errorMessage = draft.errorMessage,
                outlineJson = draft.outlineJson.ifBlank { viewpoint.outline_json },
                sourceNotes = draft.sourceNotes,
            )
        }
    }
}
