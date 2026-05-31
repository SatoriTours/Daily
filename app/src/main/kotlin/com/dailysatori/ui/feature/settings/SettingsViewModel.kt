package com.dailysatori.ui.feature.settings

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.BuildConfig
import com.dailysatori.core.service.AppRelease
import com.dailysatori.core.service.AppUpgradeService
import com.dailysatori.core.service.WebServerService
import com.dailysatori.data.repository.SettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.security.SecureRandom

data class SettingsState(
    val isPageLoading: Boolean = false,
    val webServerRunning: Boolean = false,
    val isTogglingWebServer: Boolean = false,
    val webServerError: String? = null,
    val webServerAddress: String = "",
    val webServerToken: String = "",
    val isCheckingUpdate: Boolean = false,
    val availableRelease: AppRelease? = null,
    val showUpdateDialog: Boolean = false,
    val updateMessage: String? = null,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float? = null,
    val updateDownloadProgressText: String = "",
    val downloadId: Long? = null,
    val pendingInstallFilePath: String? = null,
    val installReadyFilePath: String? = null,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val error: String? = null,
)

class SettingsViewModel(
    private val webServerService: WebServerService,
    private val appUpgradeService: AppUpgradeService,
    private val settingRepo: SettingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadWebServiceInfo()
    }

    private fun loadWebServiceInfo() {
        viewModelScope.launch(Dispatchers.IO) {
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
            settingRepo.upsert("web_server_token", newToken)
            _state.update { it.copy(webServerToken = newToken) }
        }
    }

    private fun ensureToken() {
        val existing = settingRepo.get("web_server_token")
        if (existing == null) {
            val newToken = generateToken()
            settingRepo.upsert("web_server_token", newToken)
            _state.update { it.copy(webServerToken = newToken) }
        }
    }

    private fun generateToken(): String {
        return generateWebServerToken()
    }

    companion object {
        fun generateWebServerToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
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
                val release = appUpgradeService.checkForUpdate(
                    currentVersion = _state.value.currentVersion,
                    suppressErrors = false,
                )
                _state.update { current ->
                    current.copy(
                        isCheckingUpdate = false,
                        availableRelease = release,
                        showUpdateDialog = release != null,
                        updateMessage = if (release == null) "已经是最新版" else null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isCheckingUpdate = false, updateMessage = e.message ?: "检查更新失败") }
            }
        }
    }

    fun checkUpdateAutomatically() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_state.value.isCheckingUpdate || _state.value.availableRelease != null) return@launch
            val release = appUpgradeService.checkForUpdate(_state.value.currentVersion)
            if (release != null) {
                _state.update { it.copy(availableRelease = release, showUpdateDialog = true) }
            }
        }
    }

    fun startUpdateDownload(context: Context) {
        val release = _state.value.availableRelease ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.update {
                    it.copy(
                        showUpdateDialog = true,
                        updateMessage = null,
                        isDownloadingUpdate = true,
                        updateDownloadProgress = null,
                        updateDownloadProgressText = updateDownloadProgressText(null),
                    )
                }
                val download = appUpgradeService.downloadApk(context.applicationContext, release) { downloaded, total ->
                    val progress = updateDownloadProgress(downloaded, total)
                    _state.update {
                        it.copy(
                            updateDownloadProgress = progress,
                            updateDownloadProgressText = updateDownloadProgressText(progress),
                        )
                    }
                }
                _state.update {
                    it.copy(
                        showUpdateDialog = false,
                        isDownloadingUpdate = false,
                        updateDownloadProgress = 1f,
                        updateDownloadProgressText = "下载完成，正在安装...",
                        downloadId = download.id,
                        pendingInstallFilePath = download.filePath,
                        installReadyFilePath = installReadyFilePathAfterDownload(download.filePath),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        showUpdateDialog = false,
                        isDownloadingUpdate = false,
                        updateMessage = e.message ?: "下载更新失败",
                    )
                }
            }
        }
    }

    fun createInstallIntentForDownload(context: Context, completedId: Long): Intent? {
        if (!appUpgradeService.hasPendingDownload(completedId)) return null
        return appUpgradeService.createInstallIntentForDownload(context, completedId)
            ?: run {
                _state.update { it.copy(updateMessage = "下载失败，请重试", downloadId = null, pendingInstallFilePath = null) }
                null
            }
    }

    fun createPendingInstallIntent(context: Context): Intent? {
        return appUpgradeService.createPendingInstallIntent(context)
    }

    fun createInstallIntentForFilePath(context: Context, filePath: String): Intent? {
        return appUpgradeService.createInstallIntentForFilePathIfExists(context, filePath)
    }

    fun dismissUpdateDialog() {
        if (_state.value.isDownloadingUpdate) return
        _state.update { it.copy(showUpdateDialog = false) }
    }

    fun clearUpdateMessage() {
        _state.update { it.copy(updateMessage = null) }
    }

    fun clearInstallReadyFilePath() {
        _state.update { it.copy(installReadyFilePath = null) }
    }

    fun notifyInstallFileUnavailable() {
        appUpgradeService.clearPendingDownload()
        _state.update {
            it.copy(
                updateMessage = "安装包不可用，请重新下载",
                downloadId = null,
                pendingInstallFilePath = null,
                installReadyFilePath = null,
            )
        }
    }

    fun markInstallLaunched() {
        appUpgradeService.clearPendingDownload()
        _state.update {
            it.copy(
                showUpdateDialog = false,
                isDownloadingUpdate = false,
                updateDownloadProgress = null,
                updateDownloadProgressText = "",
                downloadId = null,
                pendingInstallFilePath = null,
                installReadyFilePath = null,
            )
        }
    }

    fun notifyInstallPermissionRequired() {
        _state.update { it.copy(updateMessage = "请允许安装未知来源应用，返回后将继续安装") }
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

internal fun updateDownloadProgress(downloadedBytes: Long, totalBytes: Long): Float? {
    if (totalBytes <= 0L) return null
    return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

internal fun updateDownloadProgressText(progress: Float?): String {
    return progress?.let { "下载中 ${(it * 100).toInt()}%" } ?: "正在准备下载..."
}

internal fun installReadyFilePathAfterDownload(filePath: String): String? =
    filePath.trim().takeIf { it.isNotBlank() }
