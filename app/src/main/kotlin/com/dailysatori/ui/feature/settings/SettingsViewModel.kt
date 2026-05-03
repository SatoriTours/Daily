package com.dailysatori.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.service.AppUpgradeService
import com.dailysatori.core.service.WebServerService
import com.dailysatori.data.repository.SettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import java.net.NetworkInterface

data class SettingsState(
    val isPageLoading: Boolean = false,
    val webServerRunning: Boolean = false,
    val isTogglingWebServer: Boolean = false,
    val webServerError: String? = null,
    val webServerAddress: String = "",
    val webServerToken: String = "",
    val isCheckingUpdate: Boolean = false,
    val updateVersion: String? = null,
    val currentVersion: String = "1.0.0",
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val error: String? = null,
)

class SettingsViewModel(
    private val webServerService: WebServerService,
    private val appUpgradeService: AppUpgradeService,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadWebServiceInfo()
    }

    private fun loadWebServiceInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val settingRepo = get<SettingRepository>(SettingRepository::class.java)
            val token = settingRepo.get("web_server_token") ?: ""
            val port = webServerService.getPort()
            val address = if (webServerService.isRunning() && port > 0) {
                getDeviceIp()?.let { "http://$it:$port" } ?: "http://localhost:$port"
            } else ""
            _state.update { it.copy(webServerToken = token, webServerAddress = address) }
        }
    }

    fun toggleWebServer() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTogglingWebServer = true, webServerError = null) }
            try {
                if (_state.value.webServerRunning) {
                    webServerService.stop()
                    _state.update { it.copy(webServerRunning = false, isTogglingWebServer = false, webServerAddress = "") }
                } else {
                    ensureToken()
                    val port = webServerService.start()
                    val address = getDeviceIp()?.let { "http://$it:$port" } ?: "http://localhost:$port"
                    _state.update { it.copy(webServerRunning = true, isTogglingWebServer = false, webServerAddress = address) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isTogglingWebServer = false, webServerError = e.message ?: "Unknown error") }
            }
        }
    }

    fun refreshToken() {
        viewModelScope.launch(Dispatchers.IO) {
            val newToken = generateToken()
            val settingRepo = get<SettingRepository>(SettingRepository::class.java)
            settingRepo.upsert("web_server_token", newToken)
            _state.update { it.copy(webServerToken = newToken) }
        }
    }

    private fun ensureToken() {
        val settingRepo = get<SettingRepository>(SettingRepository::class.java)
        if (com.dailysatori.BuildConfig.DEBUG) {
            settingRepo.upsert("web_server_token", "daily")
            _state.update { it.copy(webServerToken = "daily") }
            return
        }
        val existing = settingRepo.get("web_server_token")
        if (existing == null) {
            val newToken = generateToken()
            settingRepo.upsert("web_server_token", newToken)
            _state.update { it.copy(webServerToken = newToken) }
        }
    }

    private fun generateToken(): String {
        if (com.dailysatori.BuildConfig.DEBUG) return "daily"
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..10).map { chars.random() }.joinToString("")
    }

    private fun getDeviceIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
                ?.hostAddress
        } catch (_: Exception) { null }
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

    fun exportData() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExporting = true, exportProgress = 0.5f) }
            try {
                _state.update { it.copy(isExporting = false, exportProgress = 1f) }
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }
}
