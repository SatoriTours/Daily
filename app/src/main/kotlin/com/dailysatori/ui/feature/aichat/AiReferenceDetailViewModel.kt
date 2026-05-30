package com.dailysatori.ui.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.SearchResultOpenTarget
import com.dailysatori.service.mcp.searchResultOpenTarget
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Book_viewpoint
import com.dailysatori.shared.db.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiReferenceDetailState(
    val isLoading: Boolean = false,
    val article: Article? = null,
    val diary: Diary? = null,
    val book: Book? = null,
    val viewpoint: Book_viewpoint? = null,
    val error: String? = null,
)

class AiReferenceDetailViewModel(
    private val articleRepo: ArticleRepository,
    private val diaryRepo: DiaryRepository,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiReferenceDetailState())
    val state: StateFlow<AiReferenceDetailState> = _state.asStateFlow()

    fun load(result: McpSearchResult) {
        _state.value = AiReferenceDetailState(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = when (searchResultOpenTarget(result.type)) {
                SearchResultOpenTarget.Article -> loadArticle(result.id)
                SearchResultOpenTarget.Diary -> loadDiary(result.id)
                SearchResultOpenTarget.Book -> loadBook(result.id)
                SearchResultOpenTarget.BookViewpoint -> loadBookViewpoint(result.id)
                else -> AiReferenceDetailState(error = MISSING_CONTENT_MESSAGE)
            }
        }
    }

    fun clear() {
        _state.value = AiReferenceDetailState()
    }

    private fun loadDiary(id: Long): AiReferenceDetailState {
        val diary = diaryRepo.getById(id)
        return if (diary == null) {
            AiReferenceDetailState(error = MISSING_CONTENT_MESSAGE)
        } else {
            AiReferenceDetailState(diary = diary)
        }
    }

    private fun loadArticle(id: Long): AiReferenceDetailState {
        val article = articleRepo.getById(id)
        return if (article == null) {
            AiReferenceDetailState(error = MISSING_CONTENT_MESSAGE)
        } else {
            AiReferenceDetailState(article = article)
        }
    }

    private fun loadBook(id: Long): AiReferenceDetailState {
        val viewpoint = viewpointRepo.getById(id)
        if (viewpoint != null) {
            return AiReferenceDetailState(book = bookRepo.getById(viewpoint.book_id), viewpoint = viewpoint)
        }
        val book = bookRepo.getById(id)
        val firstViewpoint = viewpointRepo.getByBookSync(id).firstOrNull()
        return if (book == null && firstViewpoint == null) {
            AiReferenceDetailState(error = MISSING_CONTENT_MESSAGE)
        } else {
            AiReferenceDetailState(book = book, viewpoint = firstViewpoint)
        }
    }

    private fun loadBookViewpoint(id: Long): AiReferenceDetailState {
        val viewpoint = viewpointRepo.getById(id)
        return if (viewpoint == null) {
            AiReferenceDetailState(error = MISSING_CONTENT_MESSAGE)
        } else {
            AiReferenceDetailState(book = bookRepo.getById(viewpoint.book_id), viewpoint = viewpoint)
        }
    }

    private companion object {
        const val MISSING_CONTENT_MESSAGE = "内容不存在或已删除"
    }
}
