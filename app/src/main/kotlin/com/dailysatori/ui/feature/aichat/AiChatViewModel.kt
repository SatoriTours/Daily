package com.dailysatori.ui.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.decodeMcpSearchResults
import com.dailysatori.service.mcp.encodeMcpSearchResults
import kotlinx.coroutines.Dispatchers
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

class AiChatViewModel(
    private val mcpAgentService: McpAgentService,
    private val chatConversationRepo: ChatConversationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiChatState())
    val state: StateFlow<AiChatState> = _state.asStateFlow()

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

        viewModelScope.launch(Dispatchers.IO) {
            val steps = mutableListOf<String>()
            val result = mcpAgentService.processQuery(
                query = content,
                onStep = { step, status ->
                    _state.update { it.copy(currentStep = step) }
                    if (status == "completed") steps.add(step)
                },
            )

            val assistantMessage = ChatMessageUi(
                id = generateId(),
                role = "assistant",
                content = result.answer,
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                isError = result.answer.startsWith("\uD83D\uDE14 **出现问题**"),
                searchResults = result.searchResults,
                steps = steps,
            )
            _state.update { it.copy(
                messages = it.messages + assistantMessage,
                isProcessing = false,
                currentStep = "",
            ) }
            persistMessage(assistantMessage)
        }
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
                )
            } catch (_: Exception) { }
        }
    }

    private fun generateId(): String {
        val ts = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val r = (0..9999).random()
        return "${ts}_${r}"
    }

    private fun encodeSteps(steps: List<String>): String? = steps.takeIf { it.isNotEmpty() }?.joinToString("\n")

    private fun decodeSteps(value: String?): List<String> = value?.lines()?.filter { it.isNotBlank() }.orEmpty()
}
