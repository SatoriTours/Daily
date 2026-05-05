package com.dailysatori.ui.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ChatConversationRepository
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
)

data class ChatMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isError: Boolean = false,
    val searchResults: List<McpSearchResult> = emptyList(),
    val steps: List<String> = emptyList(),
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

fun aiChatShowsMemorySearchAction(): Boolean = true

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
        isError = content.startsWith("😔 **出现问题**") || content == aiChatBlankResponseMessage(),
        searchResults = searchResults,
        steps = steps,
    )
}

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
                val messages = chatConversationRepo.getBySession(latestSession)
                if (messages.isNotEmpty()) {
                    _state.update { it.copy(
                        sessionId = latestSession,
                        messages = messages.map { msg ->
                            ChatMessageUi(
                                id = msg.id.toString(),
                                role = msg.role,
                                content = msg.content ?: "",
                                timestamp = msg.created_at,
                                searchResults = decodeMcpSearchResults(msg.search_results),
                                steps = decodeSteps(msg.steps),
                            )
                        },
                    ) }
                }
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
            try {
                val steps = mutableListOf<String>()
                val result = mcpAgentService.processQuery(
                    query = content,
                    onStep = { step, status ->
                        _state.update { it.copy(currentStep = step) }
                        if (status == "completed") steps.add(step)
                    },
                )
                val assistantMessage = buildAssistantMessageOrNull(
                    answer = result.answer.ifBlank { aiChatBlankResponseMessage() },
                    searchResults = result.searchResults,
                    steps = steps,
                )
                _state.update { current ->
                    current.copy(
                        messages = assistantMessage?.let { current.messages + it } ?: current.messages,
                        isProcessing = false,
                        currentStep = "",
                    )
                }
                assistantMessage?.let { persistMessage(it) }
            } catch (_: CancellationException) {
                _state.update { it.stoppedGeneration() }
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
}
