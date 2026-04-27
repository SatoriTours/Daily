package com.dailysatori.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AIFunctionType
import com.dailysatori.shared.db.Ai_config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiConfigState(
    val configs: List<Ai_config> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AiConfigViewModel(
    private val repo: AIConfigRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiConfigState())
    val state: StateFlow<AiConfigState> = _state.asStateFlow()

    init {
        loadConfigs()
        viewModelScope.launch(Dispatchers.IO) {
            repo.getAll().collect { configs ->
                _state.update { it.copy(configs = configs, isLoading = false) }
            }
        }
    }

    fun loadConfigs() {
        _state.update { it.copy(isLoading = true) }
    }

    fun getFunctionTypeIcon(type: Long): String = when (AIFunctionType.entries.find { it.value == type }) {
        AIFunctionType.GENERAL -> "⚙️"
        AIFunctionType.ARTICLE -> "📄"
        AIFunctionType.BOOK -> "📖"
        AIFunctionType.DIARY -> "📝"
        null -> "⚙️"
    }

    fun getFunctionTypeColor(type: Long): Pair<Long, Long> = when (AIFunctionType.entries.find { it.value == type }) {
        AIFunctionType.GENERAL -> 0xFF5E8BFF to 0xFF3A5CAA
        AIFunctionType.ARTICLE -> 0xFF2196F3 to 0xFF1565C0
        AIFunctionType.BOOK -> 0xFF4CAF50 to 0xFF2E7D32
        AIFunctionType.DIARY -> 0xFFFF9800 to 0xFFE65100
        null -> 0xFF757575 to 0xFF616161
    }
}
