package com.dailysatori.ui.feature.settings.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.diary.SpeechConfig
import com.dailysatori.service.diary.SpeechSettingsService
import com.dailysatori.service.diary.speechSettingsProviders
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpeechSettingsState(
    val config: SpeechConfig = speechSettingsProviders.first().newConfig(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val editable: Boolean get() = loaded && !saving
    val canSave: Boolean get() = editable && config.validationError() == null
}

class SpeechSettingsViewModel(private val service: SpeechSettingsService) : ViewModel() {
    private val _state = MutableStateFlow(SpeechSettingsState())
    val state = _state.asStateFlow()
    private val drafts = mutableMapOf<String, SpeechConfig>()

    init {
        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) { service.load() }
                val provider = speechSettingsProviders.firstOrNull { it.id == config?.provider }
                val officialOrigin = provider?.apiAddress?.let { Url(it).host }
                val previousOrigin = config?.apiAddress?.let { runCatching { Url(it).host }.getOrNull() }
                val sameOrigin = officialOrigin != null && officialOrigin == previousOrigin
                _state.update { it.copy(
                    config = if (provider != null && config != null) config.copy(
                        apiAddress = provider.apiAddress, apiKey = config.apiKey.takeIf { sameOrigin }.orEmpty(),
                    ) else it.config,
                    loaded = true,
                    message = if (config != null && !sameOrigin) "请填写所选提供商的官方 API Key" else null,
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(loaded = true, message = "读取语音配置失败，请重新填写", isError = true) }
            }
        }
    }

    fun selectProvider(id: String) {
        if (!_state.value.editable) return
        val provider = speechSettingsProviders.firstOrNull { it.id == id } ?: return
        val current = _state.value.config
        drafts[current.provider] = current
        updateConfig(drafts[id] ?: provider.newConfig())
    }

    fun setModel(value: String) {
        val provider = speechSettingsProviders.firstOrNull { it.id == _state.value.config.provider } ?: return
        if (value in provider.models) updateConfig(_state.value.config.copy(model = value))
    }
    fun setApiKey(value: String) = updateConfig(_state.value.config.copy(apiKey = value))

    private fun updateConfig(config: SpeechConfig) {
        if (_state.value.editable) _state.update { it.copy(config = config, message = null, isError = false) }
    }

    fun save() {
        val snapshot = _state.value
        if (!snapshot.editable) return
        snapshot.config.validationError()?.let { error ->
            _state.update { it.copy(message = error, isError = true) }
            return
        }
        _state.update { it.copy(saving = true, message = null, isError = false) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { service.save(snapshot.config) }
                _state.update { it.copy(saving = false, message = "已保存，语音日记将使用此配置转写") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, message = "保存失败，请重试", isError = true) }
            }
        }
    }
}
