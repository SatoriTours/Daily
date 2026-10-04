package com.dailysatori.ui.feature.aiconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.AiModel
import com.dailysatori.config.AiProvider
import com.dailysatori.config.findProvider
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiModelCatalogService
import com.dailysatori.service.ai.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class AiConfigDraft(
    val providerId: String? = null,
    val modelId: String? = null,
    val apiToken: String = "",
    val isDefault: Boolean = false,
)

data class AiConfigEditState(
    val selectedProvider: AiProvider? = null,
    val selectedModel: AiModel? = null,
    val apiToken: String = "",
    val customModelName: String = "",
    val availableModels: List<AiModel> = emptyList(),
    val isDefault: Boolean = false,
    val wasDefault: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val isRefreshingModels: Boolean = false,
    val modelRefreshMessage: String? = null,
    val testResult: String? = null,
    val testSuccess: Boolean? = null,
    val savedDraft: AiConfigDraft = AiConfigDraft(),
    val isLoading: Boolean = false,
    val saveError: String? = null,
) {
    val draft: AiConfigDraft get() = AiConfigDraft(selectedProvider?.id,
        currentModelId(customModelName, selectedModel), apiToken, isDefault)
    val hasChanges: Boolean get() = draft != savedDraft
    val editable: Boolean get() = !isLoading && !isSaving && !isTesting
}

class AiConfigEditViewModel(
    private val repo: AIConfigRepository,
    private val aiService: AiService,
    private val modelCatalogService: AiModelCatalogService,
) : ViewModel() {
    private val _state = MutableStateFlow(AiConfigEditState())
    private var loadJob: Job? = null
    private val loadRequestToken = AtomicLong(0L)
    val state: StateFlow<AiConfigEditState> = _state.asStateFlow()

    fun load(configId: Long?) {
        loadJob?.cancel()
        val token = loadRequestToken.incrementAndGet()
        _state.value = AiConfigEditState(isLoading = configId != null)
        if (configId == null) return
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = repo.getById(configId) ?: error("配置不存在")
                val provider = findProvider(config.provider)
                val model = provider?.models?.find { it.id == config.model_name }
                val availableModels = provider?.let { modelsForProvider(it) }.orEmpty()
                _state.update {
                    if (token != loadRequestToken.get()) return@update it
                    it.copy(
                        apiToken = config.api_token,
                        isDefault = config.is_default == 1L,
                        wasDefault = config.is_default == 1L,
                        selectedProvider = provider,
                        selectedModel = model,
                        customModelName = config.model_name,
                        availableModels = availableModels,
                        savedDraft = AiConfigDraft(provider?.id, currentModelId(config.model_name, model), config.api_token, config.is_default == 1L),
                        isLoading = false,
                    )
                }
                autoRefreshModelsIfNeeded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { if (token == loadRequestToken.get()) it.copy(saveError = "读取配置失败，请返回后重试") else it }
            } finally {
                _state.update { if (token == loadRequestToken.get()) it.copy(isLoading = false) else it }
            }
        }
    }

    fun selectProvider(provider: AiProvider) {
        if (!_state.value.editable) return
        _state.update {
            it.copy(
                selectedProvider = provider,
                selectedModel = null,
                customModelName = "",
                availableModels = modelsForProvider(provider),
                modelRefreshMessage = null,
                testResult = null,
                testSuccess = null,
            )
        }
        autoRefreshModelsIfNeeded()
    }

    fun selectModel(model: AiModel) {
        if (!_state.value.editable) return
        _state.update { it.copy(selectedModel = model, customModelName = model.id, testResult = null, testSuccess = null, saveError = null) }
    }

    fun updateApiToken(value: String) {
        if (!_state.value.editable) return
        _state.update { it.copy(apiToken = value, testResult = null, testSuccess = null, saveError = null) }
        autoRefreshModelsIfNeeded()
    }

    fun updateCustomModelName(value: String) {
        if (!_state.value.editable) return
        _state.update { it.copy(customModelName = value, testResult = null, testSuccess = null, saveError = null) }
    }

    fun updateIsDefault(value: Boolean) {
        if (!_state.value.editable) return
        _state.update { it.copy(isDefault = value) }
    }

    fun testConnection() {
        val snapshot = _state.value
        if (!snapshot.editable) return
        val provider = snapshot.selectedProvider ?: return
        val modelId = currentModelId(snapshot.customModelName, snapshot.selectedModel) ?: return
        val token = snapshot.apiToken
        _state.update { it.copy(isTesting = true, testResult = null, testSuccess = null) }
        viewModelScope.launch {
            try {
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
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(testResult = "连接失败，请重试", testSuccess = false) }
            } finally {
                _state.update { it.copy(isTesting = false) }
            }
        }
    }

    fun save(configId: Long?, onSaved: () -> Unit) {
        val snapshot = _state.value
        if (!snapshot.editable || !snapshot.hasChanges) return
        val provider = snapshot.selectedProvider ?: return
        val modelId = currentModelId(snapshot.customModelName, snapshot.selectedModel) ?: return
        val token = snapshot.apiToken
        val defaultValue = snapshot.isDefault
        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (configId != null) {
                        repo.update(configId, provider.id, provider.apiHost, token, modelId, if (defaultValue) 1L else 0L)
                    } else {
                        repo.insert(provider.id, provider.apiHost, token, modelId, if (defaultValue) 1L else 0L)
                    }
                }
                _state.update { it.copy(savedDraft = snapshot.draft) }
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(saveError = "保存失败，请重试") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun refreshModels() {
        refreshModels(auto = false)
    }

    private fun autoRefreshModelsIfNeeded() {
        val snapshot = _state.value
        val provider = snapshot.selectedProvider ?: return
        if (snapshot.apiToken.isBlank()) return
        if (snapshot.isRefreshingModels) return
        if (!modelCatalogService.shouldAutoRefresh(provider.id)) return
        refreshModels(auto = true)
    }

    private fun refreshModels(auto: Boolean) {
        val snapshot = _state.value
        val provider = snapshot.selectedProvider ?: return
        val apiToken = snapshot.apiToken.trim()
        if (apiToken.isBlank()) {
            if (!auto) _state.update { it.copy(modelRefreshMessage = "请先填写 API Token") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isRefreshingModels = true,
                    modelRefreshMessage = if (auto) null else "正在刷新模型列表...",
                )
            }
            val result = withContext(Dispatchers.IO) {
                modelCatalogService.refreshModels(provider, apiToken)
            }
            _state.update { current ->
                if (current.selectedProvider?.id != provider.id) {
                    current.copy(isRefreshingModels = false)
                } else {
                    val refreshedModels = result.getOrNull().orEmpty()
                    val models = mergeModels(refreshedModels, provider.models)
                    current.copy(
                        availableModels = models,
                        selectedModel = current.selectedModel?.takeIf { selected -> models.any { it.id == selected.id } },
                        isRefreshingModels = false,
                        modelRefreshMessage = result.fold(
                            onSuccess = {
                                if (it.isEmpty()) "没有获取到新模型，可继续使用内置列表或手动输入" else "模型列表已更新"
                            },
                            onFailure = { error -> error.message ?: "模型列表刷新失败，已保留内置列表" },
                        ).takeIf { !auto },
                    )
                }
            }
        }
    }

    private fun modelsForProvider(provider: AiProvider): List<AiModel> =
        mergeModels(modelCatalogService.cachedModels(provider.id), provider.models)
}

private fun currentModelId(
    customModelName: String,
    selectedModel: AiModel?,
): String? {
    return customModelName.ifBlank { selectedModel?.id.orEmpty() }.trim().takeIf { it.isNotBlank() }
}

private fun mergeModels(primary: List<AiModel>, fallback: List<AiModel>): List<AiModel> =
    (primary + fallback).distinctBy { it.id }
