package com.dailysatori.ui.feature.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.service.book.BookIntelligenceService
import com.dailysatori.service.book.BookSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookSearchState(
    val query: String = "",
    val results: List<BookSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisStep: String = "",
    val analysisMessage: String? = null,
    val addedBookTitle: String? = null,
    val addedBookId: Long? = null,
    val error: String? = null,
)

class BookSearchViewModel(
    private val bookIntelligenceService: BookIntelligenceService,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BookSearchState())
    val state: StateFlow<BookSearchState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var searchSequence = 0L

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        val requestId = ++searchSequence

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null, addedBookTitle = null) }
            try {
                val results = bookIntelligenceService.searchBooks(query)
                if (requestId != searchSequence) return@launch
                if (_state.value.query.trim() != query) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                if (results.isEmpty()) {
                    _state.update { it.copy(isLoading = false, error = "未找到可靠书籍资料，请换个关键词再试") }
                } else {
                    _state.update { it.copy(results = results, isLoading = false) }
                }
            } catch (error: CancellationException) {
                if (requestId == searchSequence) _state.update { it.copy(isLoading = false) }
                throw error
            } catch (e: Exception) {
                if (requestId != searchSequence) return@launch
                _state.update { it.copy(isLoading = false, error = e.message ?: "搜索失败") }
            }
        }
    }

    fun addAndAnalyzeBook(result: BookSearchResult) {
        if (_state.value.isAnalyzing) return
        _state.update {
            it.copy(
                isAnalyzing = true,
                analysisStep = "正在搜索书籍资料",
                analysisMessage = null,
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            var insertedBookId: Long? = null
            try {
                val bookId = bookRepo.insertAndReturnId(
                    title = result.title,
                    author = result.author,
                    category = result.category,
                    coverImage = result.coverUrl,
                    introduction = result.introduction,
                )
                insertedBookId = bookId
                _state.update { it.copy(analysisStep = "正在提炼核心观点") }
                val viewpoints = bookIntelligenceService.generateViewpoints(result)
                _state.update { it.copy(analysisStep = "正在生成观点卡片") }
                viewpoints.forEach { draft -> viewpointRepo.insert(bookId, draft.title, draft.content, draft.example) }
                val message = bookAnalysisResultMessage(viewpoints.size)
                _state.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisStep = "",
                        analysisMessage = message,
                        addedBookTitle = result.title,
                        addedBookId = bookId,
                    )
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(isAnalyzing = false, analysisStep = "") }
                throw error
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisStep = "",
                        analysisMessage = if (insertedBookId != null) bookAnalysisFailureMessage() else null,
                        addedBookTitle = if (insertedBookId != null) result.title else null,
                        addedBookId = insertedBookId,
                        error = if (insertedBookId == null) "添加失败" else null,
                    )
                }
            }
        }
    }

    fun addBook(result: BookSearchResult) {
        addAndAnalyzeBook(result)
    }

    fun clearAdded() {
        _state.update { it.copy(analysisMessage = null, addedBookTitle = null, addedBookId = null) }
    }
}

fun bookAnalysisPartialMessage(count: Int): String = "已生成 $count 个观点，可稍后重试补全"

fun bookAnalysisFailureMessage(): String = "分析失败，可重新生成观点"

fun bookAnalysisSuccessMessage(): String = "已生成 10 个观点"

private fun bookAnalysisResultMessage(count: Int): String? = when (count) {
    0 -> bookAnalysisFailureMessage()
    in 1..9 -> bookAnalysisPartialMessage(count)
    else -> bookAnalysisSuccessMessage()
}
