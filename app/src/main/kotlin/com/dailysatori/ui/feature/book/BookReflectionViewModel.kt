package com.dailysatori.ui.feature.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.BookViewpointAiRepository
import com.dailysatori.service.book.BookReflectionPromptMessage
import com.dailysatori.service.book.BookReflectionService
import com.dailysatori.shared.db.Book_viewpoint_ai_message
import com.dailysatori.shared.db.Book_viewpoint_ai_session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookReflectionMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String,
    val errorMessage: String,
    val isStreaming: Boolean = false,
)

data class BookReflectionSessionUi(
    val id: Long,
    val viewpointId: Long,
    val title: String,
    val summary: String,
    val summaryStatus: String,
    val summaryError: String,
    val updatedAt: Long,
    val summarizedAt: Long?,
)

data class BookReflectionState(
    val viewpointId: Long? = null,
    val bookTitle: String = "",
    val author: String = "",
    val viewpointTitle: String = "",
    val viewpointContent: String = "",
    val viewpointExample: String = "",
    val activeSession: BookReflectionSessionUi? = null,
    val sessions: List<BookReflectionSessionUi> = emptyList(),
    val messages: List<BookReflectionMessageUi> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val isSummarizing: Boolean = false,
    val showHistory: Boolean = false,
    val error: String? = null,
)

class BookReflectionViewModel(
    private val reflectionRepo: BookViewpointAiRepository,
    private val reflectionService: BookReflectionService,
) : ViewModel() {
    private val _state = MutableStateFlow(BookReflectionState())
    val state: StateFlow<BookReflectionState> = _state.asStateFlow()
    private var activeJob: Job? = null

    fun openViewpoint(
        viewpointId: Long,
        bookTitle: String,
        author: String,
        viewpointTitle: String,
        viewpointContent: String,
        viewpointExample: String,
    ) {
        stopGeneration()
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isLoading = true,
                    isProcessing = false,
                    isSummarizing = false,
                    error = null,
                )
            }
            val session = reflectionRepo.getLastOpenedSession(viewpointId)
                ?: reflectionRepo.getLatestUnsummarizedSession(viewpointId)
                ?: reflectionRepo.createSession(viewpointId).let { reflectionRepo.getSessionById(it)!! }
            reflectionRepo.markOpened(session.id)
            val sessions = reflectionRepo.getSessionsByViewpoint(viewpointId).map(::toSessionUi)
            val messages = reflectionRepo.getMessagesBySession(session.id).map(::toMessageUi)
            _state.update {
                it.copy(
                    viewpointId = viewpointId,
                    bookTitle = bookTitle,
                    author = author,
                    viewpointTitle = viewpointTitle,
                    viewpointContent = viewpointContent,
                    viewpointExample = viewpointExample,
                    activeSession = toSessionUi(session),
                    sessions = sessions,
                    messages = messages,
                    isLoading = false,
                )
            }
        }
    }

    fun sendMessage(content: String) {
        val question = content.trim()
        val snapshot = _state.value
        val session = snapshot.activeSession ?: return
        if (question.isBlank() || snapshot.isProcessing) return
        _state.update { it.copy(isProcessing = true) }
        activeJob = sendAssistantResponse(session.id, question, snapshot, insertUserMessage = true)
    }

    private fun sendAssistantResponse(
        sessionId: Long,
        question: String,
        snapshot: BookReflectionState,
        insertUserMessage: Boolean,
    ): Job = viewModelScope.launch(Dispatchers.IO) {
        if (insertUserMessage) {
            reflectionRepo.insertMessage(sessionId, "user", question)
            if (snapshot.messages.none { it.role == "user" }) {
                reflectionRepo.updateTitle(sessionId, bookReflectionTitleFromQuestion(question))
            }
            reloadActiveSession(sessionId)
        }
        val assistantId = reflectionRepo.insertMessage(sessionId, "assistant", "", status = "streaming")
        val streamed = StringBuilder()
        try {
            val result = reflectionService.answer(
                bookTitle = snapshot.bookTitle,
                author = snapshot.author,
                viewpointTitle = snapshot.viewpointTitle,
                viewpointContent = snapshot.viewpointContent,
                viewpointExample = snapshot.viewpointExample,
                existingSummaries = snapshot.sessions.mapNotNull { it.summary.takeIf(String::isNotBlank) },
                recentMessages = reflectionRepo.getMessagesBySession(sessionId).takeLast(12).map {
                    BookReflectionPromptMessage(it.role, it.content)
                },
                userQuestion = question,
                onChunk = { chunk ->
                    streamed.append(chunk)
                    reflectionRepo.updateMessage(assistantId, streamed.toString(), "streaming")
                    reloadMessages(sessionId)
                },
            )
            reflectionRepo.updateMessage(assistantId, result.content, "ready")
        } catch (error: CancellationException) {
            reflectionRepo.updateMessage(
                messageId = assistantId,
                content = "已停止生成",
                status = "ready",
                errorMessage = "",
            )
        } catch (error: Exception) {
            reflectionRepo.updateMessage(
                messageId = assistantId,
                content = "AI 回复失败，请稍后重试。",
                status = "failed",
                errorMessage = error.message.orEmpty(),
            )
        } finally {
            val finishedJob = currentCoroutineContext()[Job]
            reloadActiveSession(sessionId)
            if (activeJob == finishedJob) {
                _state.update { it.copy(isProcessing = false) }
                activeJob = null
            }
        }
    }

    fun createNewSegment() {
        val viewpointId = _state.value.viewpointId ?: return
        stopGeneration()
        viewModelScope.launch(Dispatchers.IO) {
            val sessionId = reflectionRepo.createSession(viewpointId)
            reloadActiveSession(sessionId, force = true)
        }
    }

    fun generateSummary() {
        val snapshot = _state.value
        val session = snapshot.activeSession ?: return
        val hasStreamingMessage = snapshot.messages.any { it.isStreaming || it.status == "streaming" }
        if (snapshot.isProcessing || snapshot.isSummarizing || snapshot.messages.isEmpty() || hasStreamingMessage) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSummarizing = true) }
            reflectionRepo.updateSummaryStatus(session.id, "generating")
            try {
                val messages = reflectionRepo.getMessagesBySession(session.id).map {
                    BookReflectionPromptMessage(it.role, it.content)
                }
                val summary = reflectionService.summarize(snapshot.bookTitle, snapshot.viewpointTitle, messages)
                reflectionRepo.updateSummary(session.id, bookReflectionTitleFromSummary(summary), summary)
            } catch (error: Exception) {
                reflectionRepo.updateSummaryStatus(session.id, "failed", error.message.orEmpty())
            } finally {
                reloadActiveSession(session.id)
                _state.update { it.copy(isSummarizing = false) }
            }
        }
    }

    fun retryLatest() {
        val snapshot = _state.value
        val session = snapshot.activeSession ?: return
        val messages = snapshot.messages
        if (!bookReflectionCanRetryLatest(messages) || snapshot.isProcessing) return
        val latestQuestion = messages.asReversed().firstOrNull { it.role == "user" }?.content ?: return
        _state.update { it.copy(isProcessing = true) }
        activeJob = sendAssistantResponse(session.id, latestQuestion, snapshot, insertUserMessage = false)
    }

    fun toggleHistory() {
        _state.update { it.copy(showHistory = !it.showHistory) }
    }

    fun selectSession(sessionId: Long) {
        stopGeneration()
        viewModelScope.launch(Dispatchers.IO) {
            reflectionRepo.markOpened(sessionId)
            reloadActiveSession(sessionId, force = true)
        }
    }

    fun stopGeneration() {
        activeJob?.cancel()
        activeJob = null
        _state.update { it.copy(isProcessing = false) }
    }

    private fun reloadActiveSession(sessionId: Long, force: Boolean = false) {
        val session = reflectionRepo.getSessionById(sessionId) ?: return
        val sessions = reflectionRepo.getSessionsByViewpoint(session.viewpoint_id).map(::toSessionUi)
        val messages = reflectionRepo.getMessagesBySession(sessionId).map(::toMessageUi)
        _state.update {
            if (!force && it.activeSession?.id != sessionId) return@update it
            it.copy(activeSession = toSessionUi(session), sessions = sessions, messages = messages, isLoading = false)
        }
    }

    private fun reloadMessages(sessionId: Long, force: Boolean = false) {
        val messages = reflectionRepo.getMessagesBySession(sessionId).map(::toMessageUi)
        _state.update {
            if (!force && it.activeSession?.id != sessionId) return@update it
            it.copy(messages = messages)
        }
    }
}

private fun toSessionUi(session: Book_viewpoint_ai_session): BookReflectionSessionUi =
    BookReflectionSessionUi(
        id = session.id,
        viewpointId = session.viewpoint_id,
        title = session.title,
        summary = session.summary,
        summaryStatus = session.summary_status,
        summaryError = session.summary_error,
        updatedAt = session.updated_at,
        summarizedAt = session.summarized_at,
    )

private fun toMessageUi(message: Book_viewpoint_ai_message): BookReflectionMessageUi =
    BookReflectionMessageUi(
        id = message.id.toString(),
        role = message.role,
        content = message.content,
        createdAt = message.created_at,
        status = message.status,
        errorMessage = message.error_message,
        isStreaming = message.status == "streaming",
    )

fun bookReflectionSummaryActionText(summary: String): String =
    if (summary.isBlank()) "沉淀这一段" else "更新沉淀"

fun bookReflectionStartingPrompts(): List<String> = listOf(
    "这个观点我可能漏掉了哪些角度？",
    "帮我用更具体的例子解释一下",
    "你反问我几个问题，帮我想清楚",
)

fun bookReflectionTitleFromQuestion(question: String): String {
    val firstLine = question.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        .trimEnd('？', '?', '。', '.', '！', '!')
    return firstLine.take(20).ifBlank { "新的思考" }
}

fun bookReflectionTitleFromSummary(summary: String): String {
    val core = summary.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("我理解到的核心：") }
        ?.removePrefix("我理解到的核心：")
        ?.trim()
        ?.trimEnd('。', '.', '？', '?', '！', '!')
        .orEmpty()
    return core.take(20).ifBlank { "新的思考" }
}

fun bookReflectionCanRetryLatest(messages: List<BookReflectionMessageUi>): Boolean {
    val last = messages.lastOrNull() ?: return false
    return last.role == "user" || (last.role == "assistant" && last.status == "failed")
}
