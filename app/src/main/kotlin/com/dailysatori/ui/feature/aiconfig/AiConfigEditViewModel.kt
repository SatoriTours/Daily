package com.dailysatori.ui.feature.aiconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.AiModel
import com.dailysatori.config.AiProvider
import com.dailysatori.config.findProvider
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class AiConfigEditState(
    val selectedProvider: AiProvider? = null,
    val selectedModel: AiModel? = null,
    val apiToken: String = "",
    val customModelName: String = "",
    val isDefault: Boolean = false,
    val wasDefault: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean? = null,
)

class AiConfigEditViewModel(
    private val repo: AIConfigRepository,
    private val aiService: AiService,
) : ViewModel() {
    private val _state = MutableStateFlow(AiConfigEditState())
    private var loadJob: Job? = null
    private val loadRequestToken = AtomicLong(0L)
    val state: StateFlow<AiConfigEditState> = _state.asStateFlow()

    fun load(configId: Long?) {
        loadJob?.cancel()
        val token = loadRequestToken.incrementAndGet()
        _state.value = AiConfigEditState()
        if (configId == null) return
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val config = repo.getById(configId) ?: return@launch
            val provider = findProvider(config.provider)
            val model = provider?.models?.find { it.id == config.model_name }
            _state.update {
                if (token != loadRequestToken.get()) return@update it
                it.copy(
                    apiToken = config.api_token,
                    isDefault = config.is_default == 1L,
                    wasDefault = config.is_default == 1L,
                    selectedProvider = provider,
                    selectedModel = model,
                    customModelName = if (model == null) config.model_name else "",
                )
            }
        }
    }

    fun selectProvider(provider: AiProvider) {
        _state.update {
            it.copy(
                selectedProvider = provider,
                selectedModel = null,
                customModelName = "",
                testResult = null,
                testSuccess = null,
            )
        }
    }

    fun selectModel(model: AiModel) {
        _state.update { it.copy(selectedModel = model, customModelName = "") }
    }

    fun updateApiToken(value: String) {
        _state.update { it.copy(apiToken = value) }
    }

    fun updateCustomModelName(value: String) {
        _state.update { it.copy(customModelName = value) }
    }

    fun updateIsDefault(value: Boolean) {
        _state.update { it.copy(isDefault = value) }
    }

    fun testConnection() {
        val snapshot = _state.value
        val provider = snapshot.selectedProvider ?: return
        val modelId = currentModelId(snapshot.selectedProvider.models.isEmpty(), snapshot.customModelName, snapshot.selectedModel) ?: return
        val token = snapshot.apiToken
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, testResult = null, testSuccess = null) }
            val result = withContext(Dispatchers.IO) {
                aiService.testConnection(
                    apiAddress = provider.apiHost,
                    apiToken = token,
                    modelName = modelId,
                    provider = provider.id,
                )
            }
            _state.update {
                it.copy(
                    testSuccess = result.isSuccess,
                    testResult = result.fold(
                        onSuccess = { message -> "连接成功：${message.take(80)}" },
                        onFailure = { error -> error.message ?: "连接失败" },
                    ),
                    isTesting = false,
                )
            }
        }
    }

    fun save(configId: Long?, onSaved: () -> Unit) {
        val snapshot = _state.value
        val provider = snapshot.selectedProvider ?: return
        val modelId = currentModelId(provider.models.isEmpty(), snapshot.customModelName, snapshot.selectedModel) ?: return
        val token = snapshot.apiToken
        val defaultValue = snapshot.isDefault
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                withContext(Dispatchers.IO) {
                    if (configId != null) {
                        repo.update(configId, provider.id, provider.apiHost, token, modelId, if (defaultValue) 1L else 0L)
                    } else {
                        repo.insert(provider.id, provider.apiHost, token, modelId, if (defaultValue) 1L else 0L)
                    }
                }
                onSaved()
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}

private fun currentModelId(
    isCustomModel: Boolean,
    customModelName: String,
    selectedModel: AiModel?,
): String? {
    return when {
        isCustomModel && customModelName.isNotBlank() -> customModelName.trim()
        selectedModel != null -> selectedModel.id
        else -> null
    }
}
