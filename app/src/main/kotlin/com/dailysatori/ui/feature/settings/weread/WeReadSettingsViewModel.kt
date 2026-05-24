package com.dailysatori.ui.feature.settings.weread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.book.resolveStoredWeReadApiKey
import com.dailysatori.service.security.SecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeReadSettingsState(
    val apiKey: String = "",
    val savedApiKey: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
)

class WeReadSettingsViewModel(
    private val settingRepository: SettingRepository,
    private val secretCipher: SecretCipher,
) : ViewModel() {
    private val _state = MutableStateFlow(WeReadSettingsState())
    val state: StateFlow<WeReadSettingsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = settingRepository.get(SettingKeys.weReadApiKey).orEmpty()
            val key = resolveStoredWeReadApiKey(
                stored = stored,
                isEncrypted = secretCipher::isEncrypted,
                decrypt = secretCipher::decrypt,
                onPlaintext = { plain -> settingRepository.upsert(SettingKeys.weReadApiKey, secretCipher.encrypt(plain)) },
            )
            _state.update { it.copy(apiKey = key, savedApiKey = key, message = null) }
        }
    }

    fun updateApiKey(value: String) {
        _state.update { it.copy(apiKey = value, message = null) }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            runCatching {
                val key = _state.value.apiKey.trim()
                settingRepository.upsert(SettingKeys.weReadApiKey, secretCipher.encrypt(key))
                _state.update {
                    it.copy(apiKey = key, savedApiKey = key, message = weReadSavedMessage())
                }
            }
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            runCatching {
                settingRepository.delete(SettingKeys.weReadApiKey)
                _state.update { it.copy(apiKey = "", savedApiKey = "", message = weReadClearedMessage()) }
            }
            _state.update { it.copy(isSaving = false) }
        }
    }

}

fun weReadSettingsTitle(): String = "微信读书"

fun weReadApiKeyStatus(apiKey: String): String {
    val trimmed = apiKey.trim()
    if (trimmed.isBlank()) return "未配置"
    if (trimmed.length < 8) return "已配置"
    val suffix = trimmed.takeLast(4)
    return "已配置：wrk-****$suffix"
}

fun weReadSaveButtonText(isSaving: Boolean): String = if (isSaving) "保存中..." else "保存"

fun weReadClearButtonText(): String = "清空"

fun weReadSavedMessage(): String = "微信读书 API Key 已保存"

fun weReadClearedMessage(): String = "微信读书 API Key 已清空"
