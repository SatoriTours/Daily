package com.dailysatori.ui.feature.settings.crayfishnews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.crayfishnews.CrayfishNewsConfigValues
import com.dailysatori.service.crayfishnews.CrayfishNewsResult
import com.dailysatori.service.crayfishnews.CrayfishNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CrayfishNewsSettingsState(
    val baseUrl: String = "",
    val token: String = "",
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
)

class CrayfishNewsSettingsViewModel(
    private val settingRepo: SettingRepository,
    private val crayfishNewsService: CrayfishNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(CrayfishNewsSettingsState())
    val state: StateFlow<CrayfishNewsSettingsState> = _state.asStateFlow()

    init { load() }

    fun updateBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, message = null) }

    fun updateToken(value: String) = _state.update { it.copy(token = value, message = null) }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    baseUrl = settingRepo.get(SettingKeys.crayfishNewsBaseUrl).orEmpty(),
                    token = settingRepo.get(SettingKeys.crayfishNewsApiToken).orEmpty(),
                )
            }
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            settingRepo.upsert(SettingKeys.crayfishNewsBaseUrl, state.value.baseUrl.trim())
            settingRepo.upsert(SettingKeys.crayfishNewsApiToken, state.value.token.trim())
            _state.update { it.copy(isSaving = false, message = "小龙虾新闻设置已保存") }
        }
    }

    fun testConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, message = null) }
            val config = crayfishNewsService.configOrFailure(state.value.baseUrl, state.value.token)
            val message = when (config) {
                is CrayfishNewsResult.Failure -> config.message
                is CrayfishNewsResult.Success<CrayfishNewsConfigValues> -> when (val result = crayfishNewsService.healthCheck(config.value)) {
                    is CrayfishNewsResult.Success -> "连接成功 (用户: ${result.value.user})"
                    is CrayfishNewsResult.Failure -> result.message
                }
            }
            _state.update { it.copy(isTesting = false, message = message) }
        }
    }
}
