package com.dailysatori.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.mcp.McpAgentResult
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.mcp.McpSearchResult
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
) : ViewModel() {
    private val _state = MutableStateFlow(AiChatState())
    val state: StateFlow<AiChatState> = _state.asStateFlow()

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

        viewModelScope.launch(Dispatchers.IO) {
            val steps = mutableListOf<String>()
            val result = mcpAgentService.processQuery(
                query = content,
                onStep = { step, status ->
                    _state.update { it.copy(currentStep = step) }
                    if (status == "completed") {
                        steps.add(step)
                    }
                },
            )

            val assistantMessage = ChatMessageUi(
                id = generateId(),
                role = "assistant",
                content = result.answer,
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                isError = result.answer.startsWith("😔 **出现问题**"),
                searchResults = result.searchResults,
                steps = steps,
            )

            _state.update { it.copy(
                messages = it.messages + assistantMessage,
                isProcessing = false,
                currentStep = "",
            ) }
        }
    }

    fun retryMessage(message: ChatMessageUi) {
        val index = _state.value.messages.indexOf(message)
        if (index < 1) return

        val userMessage = _state.value.messages[index - 1]
        if (userMessage.role != "user") return

        _state.update { it.copy(
            messages = it.messages.filter { m -> m.id != message.id },
        ) }

        sendMessage(userMessage.content)
    }

    fun clearMessages() {
        _state.update { it.copy(
            messages = emptyList(),
            isProcessing = false,
            currentStep = "",
        ) }
    }

    private fun generateId(): String =
        "${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}"
}
