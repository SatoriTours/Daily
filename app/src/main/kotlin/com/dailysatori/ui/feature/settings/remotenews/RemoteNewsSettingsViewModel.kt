package com.dailysatori.ui.feature.settings.remotenews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.remotenews.RemoteNewsConfigValues
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteNewsSettingsState(
    val baseUrl: String = "",
    val token: String = "",
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
)

class RemoteNewsSettingsViewModel(
    private val settingRepo: SettingRepository,
    private val remoteNewsService: RemoteNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteNewsSettingsState())
    val state: StateFlow<RemoteNewsSettingsState> = _state.asStateFlow()

    init { load() }

    fun updateBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, message = null) }

    fun updateToken(value: String) = _state.update { it.copy(token = value, message = null) }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    baseUrl = settingRepo.get(SettingKeys.remoteNewsBaseUrl).orEmpty(),
                    token = settingRepo.get(SettingKeys.remoteNewsApiToken).orEmpty(),
                )
            }
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            settingRepo.upsert(SettingKeys.remoteNewsBaseUrl, state.value.baseUrl.trim())
            settingRepo.upsert(SettingKeys.remoteNewsApiToken, state.value.token.trim())
            _state.update { it.copy(isSaving = false, message = "远程新闻设置已保存") }
        }
    }

    fun testConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, message = null) }
            val config = remoteNewsService.configOrFailure(state.value.baseUrl, state.value.token)
            val message = when (config) {
                is RemoteNewsResult.Failure -> config.message
                is RemoteNewsResult.Success<RemoteNewsConfigValues> -> when (val result = remoteNewsService.fetchDigests(config.value, 1, 1)) {
                    is RemoteNewsResult.Success -> "连接成功"
                    is RemoteNewsResult.Failure -> result.message
                }
            }
            _state.update { it.copy(isTesting = false, message = message) }
        }
    }
}
