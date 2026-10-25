package com.dailysatori.ui.feature.aichat

import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.R
import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.assistant.AssistantActionView
import com.dailysatori.service.assistant.AssistantConversation
import com.dailysatori.service.assistant.AssistantMemoryView
import com.dailysatori.service.assistant.AssistantMessage
import com.dailysatori.service.assistant.AssistantReference
import com.dailysatori.service.assistant.AssistantSessionSummary
import kotlinx.coroutines.CancellationException
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.reminder.ReminderDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskDisplayState { None, Waiting, Processing, Failed, Cancelled, RequiresResume }

data class ChatMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isError: Boolean = false,
    val searchResults: List<McpSearchResult> = emptyList(),
    val reminderDrafts: List<ReminderDraft> = emptyList(),
    val steps: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val messageId: Long? = null,
    val turnId: Long? = null,
    val taskId: Long? = null,
    val taskDisplayState: TaskDisplayState = TaskDisplayState.None,
    val errorMessage: String = "",
    val isExcludedFromMemory: Boolean = false,
    val actions: List<AssistantActionView> = emptyList(),
    val references: List<AssistantReference> = emptyList(),
)

data class AssistantDraft(val value: TextFieldValue = TextFieldValue(), val revision: Long = 0)

private fun restoredAssistantDrafts(saved: List<String>?): Map<String, AssistantDraft> = saved.orEmpty().chunked(4)
    .filter { it.size == 4 }.associate { (id, text, start, end) ->
        id to AssistantDraft(TextFieldValue(text, TextRange(
            start.toIntOrNull()?.coerceIn(0, text.length) ?: text.length,
            end.toIntOrNull()?.coerceIn(0, text.length) ?: text.length,
        )))
    }

data class AiChatState(
    val messages: List<ChatMessageUi> = emptyList(),
    val isProcessing: Boolean = false,
    val currentStep: String = "",
    val sessionId: String = "",
    val availableSessions: List<String> = emptyList(),
    val sessionSummaries: List<AssistantSessionSummary> = emptyList(),
    val drafts: Map<String, AssistantDraft> = emptyMap(),
    val memories: List<AssistantMemoryView> = emptyList(),
    val cloudConsentAccepted: Boolean = false,
    val showConsentPrompt: Boolean = false,
    val canLoadOlderMessages: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val isLoadingMessages: Boolean = true,
    val showSessions: Boolean = false,
) {
    val draft: TextFieldValue get() = drafts[sessionId]?.value ?: TextFieldValue()
    val canEditDraft: Boolean get() = sessionId.isNotBlank()
}

enum class ChatMessageAction { Copy, Delete, ReAsk, ExcludeFromMemory }

fun chatMessageActions(message: ChatMessageUi): List<ChatMessageAction> =
    if (message.role == "user") {
        listOf(ChatMessageAction.Copy, ChatMessageAction.Delete, ChatMessageAction.ExcludeFromMemory)
    } else {
        listOf(ChatMessageAction.Copy, ChatMessageAction.Delete, ChatMessageAction.ReAsk)
    }

fun deleteChatMessage(messages: List<ChatMessageUi>, messageId: String): List<ChatMessageUi> =
    messages.filterNot { it.id == messageId }

fun reAskContentForMessage(messages: List<ChatMessageUi>, message: ChatMessageUi): String? {
    if (message.role == "user") return message.content
    val index = messages.indexOfFirst { it.id == message.id }
    if (index <= 0) return null
    return messages.take(index).lastOrNull { it.role == "user" }?.content
}

fun aiChatStoppedStatusText(): String = "已停止生成"

fun aiChatBlankResponseMessage(): String = "这次没有生成有效回复，请稍后重试。"

fun AiChatState.stoppedGeneration(): AiChatState = copy(
    isProcessing = false,
    currentStep = aiChatStoppedStatusText(),
)

fun aiChatShowsRefreshAction(): Boolean = false

fun aiChatShowsMemorySearchAction(): Boolean = false

fun aiChatHistoryPageSize(): Int = 12

private fun aiChatAssistantContentIsError(content: String): Boolean =
    content.startsWith("😔 **出现问题**") || content == aiChatBlankResponseMessage()

fun buildAssistantMessageOrNull(
    answer: String,
    searchResults: List<McpSearchResult>,
    steps: List<String>,
    now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): ChatMessageUi? {
    val content = answer.trim()
    if (content.isBlank()) return null
    return ChatMessageUi(
        id = generateChatMessageId(now),
        role = "assistant",
        content = content,
        timestamp = now,
        isError = aiChatAssistantContentIsError(content),
        searchResults = searchResults,
        steps = steps,
    )
}

fun AiChatState.withStreamingAssistantChunk(
    messageId: String,
    chunk: String,
    now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): AiChatState {
    if (chunk.isEmpty()) return this
    val index = messages.indexOfFirst { it.id == messageId }
    val updatedMessages = if (index >= 0) {
        messages.mapIndexed { i, message ->
            if (i == index) message.copy(content = message.content + chunk, isStreaming = true) else message
        }
    } else {
        messages + ChatMessageUi(
            id = messageId,
            role = "assistant",
            content = chunk,
            timestamp = now,
            isStreaming = true,
        )
    }
    return copy(messages = updatedMessages, isProcessing = true, currentStep = "")
}

fun AiChatState.finishedStreamingAssistant(
    messageId: String,
    finalContent: String,
    searchResults: List<McpSearchResult>,
    steps: List<String>,
    reminderDrafts: List<ReminderDraft> = emptyList(),
    now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): AiChatState {
    var foundMessage = false
    val updatedMessages = messages.map { message ->
        if (message.id == messageId) {
            foundMessage = true
            val displayedContent = finalContent.ifBlank { message.content }
            message.copy(
                content = displayedContent,
                isStreaming = false,
                searchResults = searchResults,
                reminderDrafts = reminderDrafts,
                steps = steps,
                isError = aiChatAssistantContentIsError(displayedContent),
            )
        } else {
            message
        }
    }
    val finalMessages = if (!foundMessage && finalContent.isNotBlank()) {
        updatedMessages + ChatMessageUi(
            id = messageId,
            role = "assistant",
            content = finalContent,
            timestamp = now,
            isError = aiChatAssistantContentIsError(finalContent),
            searchResults = searchResults,
            reminderDrafts = reminderDrafts,
            steps = steps,
            isStreaming = false,
        )
    } else {
        updatedMessages
    }
    return copy(messages = finalMessages, isProcessing = false, currentStep = "")
}

fun AiChatState.withAssistantMessage(message: ChatMessageUi?): AiChatState = copy(
    messages = message?.let { messages + it } ?: messages,
    isProcessing = false,
    currentStep = "",
)

fun persistedChatMessage(role: String, content: String, timestamp: Long): ChatMessageUi = ChatMessageUi(
    id = timestamp.toString(), role = role, content = content, timestamp = timestamp,
)

fun AiChatState.finalizedAssistantMessageForPersistence(messageId: String): ChatMessageUi? =
    messages.firstOrNull { it.id == messageId && it.role == "assistant" && !it.isStreaming }

fun AiChatState.cancelledStreamingAssistant(messageId: String): AiChatState = copy(
    messages = messages.filterNot { it.id == messageId && it.isStreaming },
    isProcessing = false,
    currentStep = aiChatStoppedStatusText(),
)

fun generateChatMessageId(now: Long): String {
    val r = (0..9999).random()
    return "${now}_${r}"
}

class AiChatViewModel(
    private val conversation: AssistantConversation,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(AiChatState(
        sessionId = savedStateHandle["assistant.ui.session"] ?: "",
        drafts = restoredAssistantDrafts(savedStateHandle.get<ArrayList<String>>("assistant.ui.drafts")),
    ))
    val state: StateFlow<AiChatState> = _state.asStateFlow()

    private var messagesJob: Job? = null
    private val draftClientUuids = mutableMapOf<Pair<String, String>, String>()

    init {
        state.map { it.sessionId to it.drafts }.distinctUntilChanged().onEach { (session, drafts) ->
            savedStateHandle["assistant.ui.session"] = session
            // Save text and selection, not an IME composition owned by an input connection that may no longer exist.
            savedStateHandle["assistant.ui.drafts"] = ArrayList(drafts.flatMap { (id, draft) ->
                listOf(id, draft.value.text, draft.value.selection.start.toString(), draft.value.selection.end.toString())
            })
        }.launchIn(viewModelScope)
        val consented = conversation.cloudConsent()
        _state.update {
            it.copy(
                cloudConsentAccepted = consented,
                showConsentPrompt = !consented,
            )
        }

        conversation.sessionSummaries().onEach { summaries ->
            val sessions = summaries.map { it.id }
            val removed = _state.value.availableSessions.toSet() - sessions.toSet()
            draftClientUuids.keys.removeAll { it.first in removed }
            var activeSessionChanged = false
            _state.update { current ->
                val isCurrentlyKnown = current.sessionId in current.availableSessions
                val isDeletedFromPersisted = isCurrentlyKnown && current.sessionId !in sessions
                val currentSession = when {
                    // 1. Session is currently active and still present in persisted sessions
                    current.sessionId.isNotBlank() && current.sessionId in sessions -> current.sessionId
                    // 2. Session was known/persisted and has now been deleted from persistence -> fallback
                    isDeletedFromPersisted -> createNewSessionId()
                    // 3. Session is brand-new / unpersisted (not in availableSessions or sessions yet) -> keep it!
                    current.sessionId.isNotBlank() && !isCurrentlyKnown -> current.sessionId
                    // 4. Initial launch or blank sessionId
                    else -> sessions.firstOrNull() ?: createNewSessionId()
                }
                activeSessionChanged = (currentSession != current.sessionId)
                current.copy(
                    availableSessions = sessions, sessionSummaries = summaries, sessionId = currentSession,
                    drafts = current.drafts - removed,
                    showSessions = if (isDeletedFromPersisted) false else current.showSessions,
                    messages = if (activeSessionChanged) emptyList() else current.messages,
                    isProcessing = if (activeSessionChanged) false else current.isProcessing,
                    isLoadingMessages = activeSessionChanged || current.isLoadingMessages,
                    currentStep = if (activeSessionChanged) "" else current.currentStep,
                )
            }
            if (activeSessionChanged || messagesJob == null) {
                observeMessagesForSession(_state.value.sessionId)
            }
        }.launchIn(viewModelScope)

        conversation.memories().onEach { memories ->
            _state.update { it.copy(memories = memories) }
        }.launchIn(viewModelScope)
    }

    private fun createNewSessionId(): String = "chat_${java.util.UUID.randomUUID()}"

    fun setSessionsVisible(visible: Boolean) { _state.update { it.copy(showSessions = visible) } }

    fun switchSession(sessionId: String) {
        if (_state.value.sessionId == sessionId) return
        _state.update { it.copy(sessionId = sessionId, messages = emptyList(), isProcessing = false,
            isLoadingMessages = true, currentStep = "") }
        observeMessagesForSession(sessionId)
    }

    fun startNewSession(): String {
        val current = _state.value
        if (current.sessionId.isNotBlank() && current.sessionId !in current.availableSessions) return current.sessionId
        val newSessionId = current.drafts.entries.lastOrNull {
            it.key !in current.availableSessions && it.value.value.text.isNotBlank()
        }?.key ?: createNewSessionId()
        switchSession(newSessionId)
        return newSessionId
    }

    private fun observeMessagesForSession(sessionId: String) {
        messagesJob?.cancel()
        if (sessionId.isBlank()) {
            _state.update { it.copy(messages = emptyList(), isProcessing = false) }
            return
        }
        messagesJob = conversation.messages(sessionId).onEach { rawMessages ->
            if (_state.value.sessionId != sessionId) return@onEach
            val mapped = rawMessages.map(::toChatMessageUi)
            val anyRunning = mapped.any { it.isStreaming || it.taskDisplayState == TaskDisplayState.Waiting || it.taskDisplayState == TaskDisplayState.Processing }
            _state.update { current ->
                current.copy(
                    messages = mapped,
                    isLoadingMessages = false,
                    isProcessing = anyRunning,
                    currentStep = if (anyRunning) "正在处理…" else "",
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun toChatMessageUi(msg: AssistantMessage): ChatMessageUi {
        val taskState = when (msg.status) {
            "queued", "pending" -> TaskDisplayState.Waiting
            "running", "retrying" -> TaskDisplayState.Processing
            "failed" -> TaskDisplayState.Failed
            "cancelled" -> TaskDisplayState.Cancelled
            "requires_resume" -> TaskDisplayState.RequiresResume
            "succeeded", "complete" -> TaskDisplayState.None
            else -> TaskDisplayState.None
        }
        val isError = taskState == TaskDisplayState.Failed || msg.error.isNotBlank()
        return ChatMessageUi(
            id = msg.id.toString(),
            messageId = msg.id.takeIf { it > 0 },
            role = msg.role,
            isStreaming = msg.status == "streaming",
            content = msg.content,
            timestamp = msg.createdAt,
            turnId = msg.turnId,
            taskId = msg.taskId,
            taskDisplayState = taskState,
            errorMessage = msg.error,
            isExcludedFromMemory = msg.excluded,
            isError = isError,
            actions = msg.actions,
            references = msg.references,
        )
    }

    fun updateDraft(value: TextFieldValue, sessionId: String = _state.value.sessionId) {
        if (sessionId.isBlank()) return
        _state.update { current ->
            // A finishing IME callback belongs to the field that produced it, never the newly selected session.
            if (sessionId != current.sessionId && sessionId !in current.availableSessions && sessionId !in current.drafts) return@update current
            val previous = current.drafts[sessionId] ?: AssistantDraft()
            val revision = previous.revision + if (value.text != previous.value.text) 1 else 0
            current.copy(drafts = current.drafts + (sessionId to AssistantDraft(value, revision)))
        }
    }

    fun prepareInput(input: String): String {
        return draftClientUuids.getOrPut(_state.value.sessionId to input) {
            "msg_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}_${(1000..9999).random()}"
        }
    }

    fun sendMessage(
        content: String,
        clientMessageId: String? = null,
        explicitContext: com.dailysatori.service.diary.DiaryThoughtChatContext? = null,
        includeThoughts: Boolean = true,
        onSuccess: (() -> Unit)? = null,
    ) {
        if (content.isBlank()) return
        val session = _state.value.sessionId.ifBlank { startNewSession() }
        val uuid = clientMessageId ?: prepareInput(content)
        val acceptedDraft = _state.value.drafts[session]?.takeIf { it.value.text == content }
        viewModelScope.launch {
            try {
                conversation.sendWithContext(
                    sessionId = session,
                    clientMessageId = uuid,
                    content = content,
                    explicitContext = explicitContext,
                    includeThoughts = includeThoughts,
                )
                draftClientUuids.remove(session to content)
                if (acceptedDraft != null) _state.update { current ->
                    val latest = current.drafts[session]
                    if (latest?.revision == acceptedDraft.revision && latest.value.text == content) {
                        current.copy(drafts = current.drafts - session)
                    } else current
                }
                onSuccess?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiErrors.value = UiError("发送失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun cancelTurn(turnId: Long) {
        viewModelScope.launch { conversation.cancel(turnId) }
    }

    fun retryTurn(turnId: Long) {
        viewModelScope.launch { conversation.retry(turnId) }
    }

    fun excludeMessage(messageId: Long) {
        viewModelScope.launch { conversation.excludeMessage(messageId) }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { conversation.deleteMessage(messageId) }
    }

    fun deleteMessage(message: ChatMessageUi) {
        if (message.isStreaming) return
        val id = message.messageId ?: message.id.toLongOrNull()
        if (id != null) {
            deleteMessage(id)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                conversation.deleteSession(sessionId)
                val wasCurrent = _state.value.sessionId == sessionId
                val next = if (wasCurrent) createNewSessionId() else _state.value.sessionId
                _state.update { current -> current.copy(
                    sessionId = next,
                    showSessions = if (wasCurrent) false else current.showSessions,
                    messages = if (wasCurrent) emptyList() else current.messages,
                    isProcessing = if (wasCurrent) false else current.isProcessing,
                    isLoadingMessages = wasCurrent || current.isLoadingMessages,
                    drafts = current.drafts - sessionId,
                    availableSessions = current.availableSessions.filterNot { it == sessionId },
                    sessionSummaries = current.sessionSummaries.filterNot { it.id == sessionId },
                ) }
                draftClientUuids.keys.removeAll { it.first == sessionId }
                if (wasCurrent) observeMessagesForSession(next)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiErrors.value = UiError("", resourceId = R.string.assistant_delete_failed)
            }
        }
    }

    data class UiError(val message: String, val timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
        val resourceId: Int? = null)
    private val _uiErrors = MutableStateFlow<UiError?>(null)
    val uiErrors: StateFlow<UiError?> = _uiErrors.asStateFlow()

    fun clearUiError() {
        _uiErrors.value = null
    }

    fun forgetMemory(memoryId: Long) {
        viewModelScope.launch {
            try {
                conversation.forgetMemory(memoryId)
            } catch (e: Exception) {
                _uiErrors.value = UiError("遗忘记忆失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun correctMemory(memoryId: Long, corrected: String) {
        viewModelScope.launch {
            try {
                conversation.correctMemory(memoryId, corrected)
            } catch (e: Exception) {
                _uiErrors.value = UiError("纠正记忆失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun acceptCloudConsent() {
        conversation.setCloudConsent(true)
        _state.update { it.copy(cloudConsentAccepted = true, showConsentPrompt = false) }
        val resumeCandidate = _state.value.messages.lastOrNull {
            it.taskDisplayState == TaskDisplayState.RequiresResume ||
                (it.taskDisplayState == TaskDisplayState.Failed && it.errorMessage.contains("cloud_consent_required"))
        }?.turnId
        if (resumeCandidate != null) {
            retryTurn(resumeCandidate)
        }
    }

    fun declineCloudConsent() {
        conversation.setCloudConsent(false)
        _state.update { it.copy(cloudConsentAccepted = false, showConsentPrompt = false) }
    }

    fun promptCloudConsent() {
        _state.update { it.copy(showConsentPrompt = true) }
    }

    fun dismissCloudConsentPrompt() {
        _state.update { it.copy(showConsentPrompt = false) }
    }

    fun stopGeneration() {
        val activeTurn = _state.value.messages.lastOrNull {
            it.taskDisplayState == TaskDisplayState.Waiting || it.taskDisplayState == TaskDisplayState.Processing
        }?.turnId
        if (activeTurn != null) {
            cancelTurn(activeTurn)
        }
    }

    fun reAsk(message: ChatMessageUi, explicitContext: com.dailysatori.service.diary.DiaryThoughtChatContext? = null, includeThoughts: Boolean = true) {
        val content = reAskContentForMessage(_state.value.messages, message) ?: return
        sendMessage(content)
    }

    fun loadOlderMessages() {}
}
