package com.dailysatori.ui.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.shared.db.Chat_conversation
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.decodeMcpSearchResults
import com.dailysatori.service.mcp.encodeMcpSearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatState(
    val messages: List<ChatMessageUi> = emptyList(),
    val isProcessing: Boolean = false,
    val currentStep: String = "",
    val sessionId: String = "chat_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
    val canLoadOlderMessages: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
)

data class ChatMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isError: Boolean = false,
    val searchResults: List<McpSearchResult> = emptyList(),
    val steps: List<String> = emptyList(),
    val isStreaming: Boolean = false,
)

enum class ChatMessageAction { Copy, Delete, ReAsk }

fun chatMessageActions(message: ChatMessageUi): List<ChatMessageAction> =
    listOf(ChatMessageAction.Copy, ChatMessageAction.Delete, ChatMessageAction.ReAsk)

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
    private val mcpAgentService: McpAgentService,
    private val chatConversationRepo: ChatConversationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiChatState())
    val state: StateFlow<AiChatState> = _state.asStateFlow()
    private var activeRequestJob: Job? = null

    init {
        loadLatestSession()
    }

    private fun loadLatestSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = chatConversationRepo.getSessions()
            if (sessions.isNotEmpty()) {
                val latestSession = sessions.first()
                val messages = chatConversationRepo.getLatestBySession(latestSession, aiChatHistoryPageSize().toLong())
                if (messages.isNotEmpty()) {
                    _state.update { it.copy(
                        sessionId = latestSession,
                        messages = messages.map(::toChatMessageUi),
                        canLoadOlderMessages = messages.size >= aiChatHistoryPageSize(),
                    ) }
                }
            }
        }
    }

    fun loadOlderMessages() {
        val snapshot = _state.value
        val firstMessage = snapshot.messages.firstOrNull() ?: return
        if (!snapshot.canLoadOlderMessages || snapshot.isLoadingOlderMessages) return

        _state.update { it.copy(isLoadingOlderMessages = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val olderMessages = chatConversationRepo.getBefore(
                sessionId = snapshot.sessionId,
                beforeCreatedAt = firstMessage.timestamp,
                limit = aiChatHistoryPageSize().toLong(),
            ).map(::toChatMessageUi)
            _state.update { current ->
                current.copy(
                    messages = olderMessages + current.messages,
                    canLoadOlderMessages = olderMessages.size >= aiChatHistoryPageSize(),
                    isLoadingOlderMessages = false,
                )
            }
        }
    }

    fun sendMessage(content: String) {
        if (_state.value.isProcessing) return
        val userMessage = ChatMessageUi(
            id = generateId(),
            role = "user",
            content = content,
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
        )
        _state.update { it.copy(
            messages = it.messages + userMessage,
            isProcessing = true,
            currentStep = "",
        ) }
        persistMessage(userMessage)

        activeRequestJob = viewModelScope.launch(Dispatchers.IO) {
            val assistantMessageId = generateId()
            try {
                val steps = mutableListOf<String>()
                val result = mcpAgentService.processQueryStreaming(
                    query = content,
                    onStep = { step, status ->
                        _state.update { it.copy(currentStep = step) }
                        if (status == "completed") steps.add(step)
                    },
                    onChunk = { chunk ->
                        _state.update { it.withStreamingAssistantChunk(assistantMessageId, chunk) }
                    },
                )
                val finalAssistantMessage = buildAssistantMessageOrNull(
                    answer = result.answer.ifBlank { aiChatBlankResponseMessage() },
                    searchResults = result.searchResults,
                    steps = steps,
                )?.copy(id = assistantMessageId)
                _state.update { current ->
                    if (current.messages.any { it.id == assistantMessageId }) {
                        current.finishedStreamingAssistant(
                            messageId = assistantMessageId,
                            finalContent = finalAssistantMessage?.content ?: aiChatBlankResponseMessage(),
                            searchResults = finalAssistantMessage?.searchResults.orEmpty(),
                            steps = finalAssistantMessage?.steps.orEmpty(),
                        )
                    } else {
                        current.withAssistantMessage(finalAssistantMessage)
                    }
                }
                _state.value.finalizedAssistantMessageForPersistence(assistantMessageId)?.let { persistMessage(it) }
            } catch (_: CancellationException) {
                _state.update { it.cancelledStreamingAssistant(assistantMessageId) }
            } finally {
                activeRequestJob = null
            }
        }
    }

    fun stopGeneration() {
        activeRequestJob?.cancel()
        activeRequestJob = null
        _state.update { it.stoppedGeneration() }
    }

    fun deleteMessage(message: ChatMessageUi) {
        _state.update { it.copy(messages = deleteChatMessage(it.messages, message.id)) }
        viewModelScope.launch(Dispatchers.IO) {
            chatConversationRepo.deleteMessage(
                sessionId = _state.value.sessionId,
                role = message.role,
                content = message.content,
                createdAt = message.timestamp,
            )
        }
    }

    fun reAsk(message: ChatMessageUi) {
        val content = reAskContentForMessage(_state.value.messages, message) ?: return
        sendMessage(content)
    }

    fun clearMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            chatConversationRepo.deleteBySession(_state.value.sessionId)
        }
        _state.update { it.copy(
            messages = emptyList(),
            isProcessing = false,
            currentStep = "",
            sessionId = "chat_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            canLoadOlderMessages = false,
            isLoadingOlderMessages = false,
        ) }
    }

    private fun persistMessage(message: ChatMessageUi) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatConversationRepo.insert(
                    sessionId = _state.value.sessionId,
                    role = message.role,
                    content = message.content,
                    searchResults = encodeMcpSearchResults(message.searchResults),
                    steps = encodeSteps(message.steps),
                    createdAt = message.timestamp,
                )
            } catch (_: Exception) { }
        }
    }

    private fun generateId(): String =
        generateChatMessageId(kotlinx.datetime.Clock.System.now().toEpochMilliseconds())

    private fun encodeSteps(steps: List<String>): String? = steps.takeIf { it.isNotEmpty() }?.joinToString("\n")

    private fun decodeSteps(value: String?): List<String> = value?.lines()?.filter { it.isNotBlank() }.orEmpty()

    private fun toChatMessageUi(msg: Chat_conversation): ChatMessageUi = ChatMessageUi(
        id = msg.id.toString(),
        role = msg.role,
        content = msg.content ?: "",
        timestamp = msg.created_at,
        searchResults = decodeMcpSearchResults(msg.search_results),
        steps = decodeSteps(msg.steps),
    )
}
