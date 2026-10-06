package com.dailysatori.ui.feature.settings.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.diary.SpeechConfig
import com.dailysatori.service.diary.SpeechSettingsService
import com.dailysatori.service.diary.SpeechTranscriptionApi
import com.dailysatori.service.diary.SpeechTranscriptionException
import com.dailysatori.service.diary.TranscriptionErrorCode
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.diary.speechSettingsProviders
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

data class SpeechSettingsState(
    val config: SpeechConfig = speechSettingsProviders.first().newConfig(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val savedConfig: SpeechConfig? = null,
) {
    val editable: Boolean get() = loaded && !saving && !testing
    val hasChanges: Boolean get() = loaded && config != (savedConfig ?: speechSettingsProviders.first().newConfig())
    val canSave: Boolean get() = editable && hasChanges && config.validationError() == null
    val canTest: Boolean get() = editable && config.validationError() == null
}

class SpeechSettingsViewModel(
    private val service: SpeechSettingsService,
    private val api: SpeechTranscriptionApi,
    private val i18n: I18nService,
) : ViewModel() {
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
                _state.update {
                    val loadedConfig = if (provider != null && config != null) config.copy(
                        apiAddress = provider.apiAddress, apiKey = config.apiKey.takeIf { sameOrigin }.orEmpty(),
                    ) else it.config
                    it.copy(
                        config = loadedConfig,
                        savedConfig = loadedConfig,
                        loaded = true,
                        message = if (config != null && !sameOrigin) "请填写所选提供商的官方 API Key" else null,
                    )
                }
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

    fun discardChanges() {
        if (!_state.value.editable) return
        drafts.clear()
        _state.update { it.copy(config = it.savedConfig ?: speechSettingsProviders.first().newConfig(), message = null, isError = false) }
    }

    fun testConfiguration(loadAudio: () -> Pair<ByteArray, String>) {
        val snapshot = _state.value
        if (!snapshot.canTest) return
        _state.update { it.copy(testing = true, message = null, isError = false) }
        viewModelScope.launch {
            try {
                val text = withTimeout(30_000) {
                    withContext(Dispatchers.IO) {
                        val (bytes, name) = loadAudio()
                        require(bytes.isNotEmpty() && bytes.size <= SPEECH_TEST_MAX_BYTES)
                        api.transcribe(snapshot.config, bytes, name)
                    }
                }
                _state.update { it.copy(message = i18n.t("speech_test.success") + "\n" + text.take(300), isError = false) }
            } catch (_: TimeoutCancellationException) {
                testFailed("timeout")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SpeechTranscriptionException) {
                val category = when (error.code) {
                    TranscriptionErrorCode.AUTH_FAILED -> "auth"
                    TranscriptionErrorCode.MODEL_UNSUPPORTED -> "model"
                    TranscriptionErrorCode.SERVICE_UNAVAILABLE -> "unavailable"
                    TranscriptionErrorCode.AUDIO_EMPTY, TranscriptionErrorCode.AUDIO_TOO_LARGE -> "audio"
                    else -> "rejected"
                }
                testFailed(category)
            } catch (_: HttpRequestTimeoutException) {
                testFailed("timeout")
            } catch (_: IOException) {
                testFailed("network")
            } catch (_: IllegalArgumentException) {
                testFailed("audio")
            } catch (_: Exception) {
                testFailed("rejected")
            } finally {
                _state.update { it.copy(testing = false) }
            }
        }
    }

    private fun testFailed(category: String) {
        // Never display or log exception messages: providers can echo the submitted Key.
        _state.update { it.copy(message = i18n.t("speech_test.$category"), isError = true) }
    }

    fun save(onSaved: () -> Unit = {}) {
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
                _state.update { it.copy(saving = false, savedConfig = snapshot.config, message = "已保存，语音日记将使用此配置转写") }
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, message = "保存失败，请重试", isError = true) }
            }
        }
    }
}

internal const val SPEECH_TEST_MAX_BYTES = 1024 * 1024
