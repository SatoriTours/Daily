package com.dailysatori.ui.feature.aiconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.AIConfigRepository
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

    fun deleteConfig(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
        }
    }
}
