package com.dailysatori.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.AppUpgradeService
import com.dailysatori.service.WebServerService
import com.dailysatori.service.setting.SettingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val isPageLoading: Boolean = false,
    val webServerRunning: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val updateVersion: String? = null,
    val currentVersion: String = "1.0.0",
    val googleBooksApiKey: String = "",
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val error: String? = null,
)

class SettingsViewModel(
    private val settingService: SettingService,
    private val webServerService: WebServerService,
    private val appUpgradeService: AppUpgradeService,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val apiKey = settingService.getString("google_books_api_key", "")
        _state.update { it.copy(googleBooksApiKey = apiKey) }
    }

    fun toggleWebServer() {
        if (_state.value.webServerRunning) {
            webServerService.stop()
            _state.update { it.copy(webServerRunning = false) }
        } else {
            webServerService.start()
            _state.update { it.copy(webServerRunning = true) }
        }
    }

    fun checkUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCheckingUpdate = true, error = null) }
            try {
                val latest = appUpgradeService.checkForUpdate(_state.value.currentVersion)
                _state.update { it.copy(isCheckingUpdate = false, updateVersion = latest) }
            } catch (e: Exception) {
                _state.update { it.copy(isCheckingUpdate = false, error = e.message) }
            }
        }
    }

    fun saveGoogleBooksApiKey(key: String) {
        settingService.set("google_books_api_key", key)
        _state.update { it.copy(googleBooksApiKey = key) }
    }

    fun exportData() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExporting = true, exportProgress = 0.5f) }
            try {
                // Export logic placeholder
                _state.update { it.copy(isExporting = false, exportProgress = 1f) }
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }
}
