package com.dailysatori.ui.feature.aiconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.shared.db.Ai_config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiPurposeUiState(
    val configs: List<Ai_config> = emptyList(),
    val assignments: Map<AiPurpose, Long> = emptyMap(),
    val defaultConfig: Ai_config? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class AiPurposeViewModel(
    private val repo: AIConfigRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiPurposeUiState(isLoading = true))
    val state: StateFlow<AiPurposeUiState> = _state.asStateFlow()

    init {
        observeConfigs()
    }

    private fun observeConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.getAll().collectLatest { configs ->
                val defaultConfig = configs.firstOrNull { it.is_default == 1L }
                val assignments = runCatching { repo.getPurposeAssignments() }.getOrDefault(emptyMap())
                _state.update {
                    it.copy(
                        configs = configs,
                        defaultConfig = defaultConfig,
                        assignments = assignments,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun selectModelForPurpose(purpose: AiPurpose, configId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.setPurposeConfig(purpose, configId)
                val updatedAssignments = repo.getPurposeAssignments()
                _state.update {
                    it.copy(
                        assignments = updatedAssignments,
                        errorMessage = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(errorMessage = "ai_purpose.save_failed")
                }
            }
        }
    }

    fun clearErrorMessage() {
        _state.update { it.copy(errorMessage = null) }
    }
}
