package com.dailysatori.ui.feature.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.service.book.BookIntelligenceService
import com.dailysatori.service.book.BookSearchResult
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

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
) {
    val visibleResults: List<BookSearchResult>
        get() = if (query.isBlank()) emptyList() else results
}

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
        searchJob?.cancel()
        searchSequence++
        _state.update { it.copy(query = query, results = emptyList(), isLoading = false, error = null) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        val requestId = ++searchSequence

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null, addedBookTitle = null) }
            try {
                val results = withTimeout(bookSearchTimeoutMs()) { bookIntelligenceService.searchBooks(query) }
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
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                if (requestId != searchSequence) return@launch
                _state.update { it.copy(isLoading = false, error = bookSearchTimeoutMessage()) }
            } catch (error: CancellationException) {
                if (requestId == searchSequence) _state.update { it.copy(isLoading = false) }
                throw error
            } catch (e: Exception) {
                if (requestId != searchSequence) return@launch
                _state.update { it.copy(isLoading = false, error = bookSearchFailureMessage(e)) }
            }
        }
    }

    fun addAndAnalyzeBook(result: BookSearchResult) {
        if (_state.value.isAnalyzing) return
        _state.update {
            it.copy(
                isAnalyzing = true,
                analysisStep = bookAnalysisStartStep(result.title),
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
                _state.update { it.copy(analysisStep = bookAnalysisGeneratingStep()) }
                viewpoints.forEach { draft -> viewpointRepo.insert(bookId, draft.title, draft.content, draft.example) }
                val message = bookAnalysisCompletionNotice(result.title, viewpoints.size)
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

fun bookAnalysisStartStep(title: String): String = "正在添加《$title》"

fun bookAnalysisGeneratingStep(): String = "正在生成观点卡片"

fun bookAnalysisCompletionNotice(title: String, count: Int): String = "《$title》已添加，$count 个观点已生成"

fun bookAnalysisStatusVisible(isAnalyzing: Boolean, analysisMessage: String?): Boolean =
    isAnalyzing || analysisMessage != null

fun bookAddSearchShowsTrailingSearchButton(): Boolean = false

fun compactBookAddActionText(isAnalyzing: Boolean): String = if (isAnalyzing) "分析中" else "添加并分析"

fun bookSearchTimeoutMs(): Long = 30_000L

fun bookSearchTimeoutMessage(): String = "搜索超时，请换个关键词再试"

fun bookSearchFailureMessage(error: Throwable): String =
    if (error is kotlinx.coroutines.TimeoutCancellationException) bookSearchTimeoutMessage() else error.message ?: "搜索失败"

fun doubanBookSearchUrl(result: BookSearchResult): String {
    if (result.sourceUrl.isNotBlank()) return result.sourceUrl
    val query = listOf(result.title, result.author).filter { it.isNotBlank() }.joinToString(" ")
    return "https://www.douban.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
}

fun buildChineseBookSearchInstruction(query: String): String =
    if (query.any { it.code > 127 }) "搜索：$query。优先返回中文书籍、中文作者名和中文资料摘要。" else "搜索：$query"

private fun bookAnalysisResultMessage(count: Int): String? = when (count) {
    0 -> bookAnalysisFailureMessage()
    in 1..9 -> bookAnalysisPartialMessage(count)
    else -> bookAnalysisSuccessMessage()
}
