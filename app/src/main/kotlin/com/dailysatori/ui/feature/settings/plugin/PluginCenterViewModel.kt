package com.dailysatori.ui.feature.settings.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.plugin.PluginService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PluginInfo(
    val fileName: String,
    val content: String,
    val lastUpdated: Long = 0,
)

data class PluginCenterState(
    val plugins: List<PluginInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val updatingPluginId: String = "",
    val serverUrl: String = "",
    val testMessage: String? = null,
    val testSucceeded: Boolean? = null,
    val error: String? = null,
)

class PluginCenterViewModel(
    private val pluginService: PluginService,
    private val settingRepo: SettingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PluginCenterState())
    val state: StateFlow<PluginCenterState> = _state.asStateFlow()
    private var testJob: Job? = null
    private var testRequestId = 0L

    init {
        loadPlugins()
    }

    fun loadPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                loadPluginState()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun loadPluginState() {
        val serverUrl = settingRepo.get("plugin_server_url") ?: ""
        val plugins = settingRepo.getAllKeys()
            .filter { it.startsWith("plugin_content_") }
            .map { key ->
                val fileName = key.removePrefix("plugin_content_")
                val content = settingRepo.get(key) ?: ""
                PluginInfo(fileName = fileName, content = content)
            }
        _state.update { it.copy(plugins = plugins, serverUrl = serverUrl, isLoading = false) }
    }

    fun updatePlugin(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(updatingPluginId = fileName, error = null) }
            try {
                if (pluginService.forceUpdate(fileName)) {
                    loadPluginState()
                } else {
                    _state.update { it.copy(error = "插件更新失败：$fileName") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(updatingPluginId = "") }
            }
        }
    }

    fun updateAllPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                var failedPlugin = ""
                _state.value.plugins.forEach { plugin ->
                    _state.update { it.copy(updatingPluginId = plugin.fileName) }
                    val updated = pluginService.forceUpdate(plugin.fileName)
                    if (!updated && failedPlugin.isBlank()) failedPlugin = plugin.fileName
                }
                loadPluginState()
                if (failedPlugin.isNotBlank()) {
                    _state.update { it.copy(error = "插件更新失败：$failedPlugin") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(updatingPluginId = "", isLoading = false) }
            }
        }
    }

    fun saveServerUrl(url: String, onSaved: () -> Unit = {}) {
        val validation = pluginServerValidationMessage(url)
        if (validation != null) {
            _state.update { it.copy(error = validation) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                settingRepo.upsert("plugin_server_url", url.trim())
                _state.update { it.copy(serverUrl = url.trim()) }
                withContext(Dispatchers.Main) { onSaved() }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun testServerUrl(url: String) {
        val requestId = ++testRequestId
        testJob?.cancel()
        val validation = pluginServerValidationMessage(url)
        if (validation != null) {
            _state.update { it.copy(isTesting = false, testMessage = validation, testSucceeded = false) }
            return
        }
        testJob = viewModelScope.launch(Dispatchers.IO) {
            if (requestId == testRequestId) {
                _state.update { it.copy(isTesting = true, testMessage = null, testSucceeded = null) }
            }
            try {
                val result = pluginService.testServer(url)
                if (requestId == testRequestId) {
                    _state.update {
                        it.copy(
                            isTesting = false,
                            testSucceeded = result.isSuccess,
                            testMessage = result.fold(
                                onSuccess = { "插件服务器可访问" },
                                onFailure = { error -> error.message ?: "插件服务器不可访问" },
                            ),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == testRequestId) {
                    _state.update {
                        it.copy(
                            isTesting = false,
                            testSucceeded = false,
                            testMessage = error.message ?: "插件服务器不可访问",
                        )
                    }
                }
            } finally {
                if (requestId == testRequestId) {
                    _state.update { it.copy(isTesting = false) }
                }
            }
        }
    }

    fun clearTestMessage() {
        testRequestId++
        testJob?.cancel()
        testJob = null
        _state.update {
            it.copy(isTesting = false, testMessage = null, testSucceeded = null, error = null)
        }
    }
}
