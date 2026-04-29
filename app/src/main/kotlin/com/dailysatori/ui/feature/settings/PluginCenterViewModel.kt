package com.dailysatori.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.plugin.PluginService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PluginInfo(
    val fileName: String,
    val content: String,
    val lastUpdated: Long = 0,
)

data class PluginCenterState(
    val plugins: List<PluginInfo> = emptyList(),
    val isLoading: Boolean = false,
    val updatingPluginId: String = "",
    val serverUrl: String = "",
    val error: String? = null,
)

class PluginCenterViewModel(
    private val pluginService: PluginService,
    private val settingRepo: SettingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PluginCenterState())
    val state: StateFlow<PluginCenterState> = _state.asStateFlow()

    init {
        loadPlugins()
    }

    fun loadPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val serverUrl = settingRepo.get("plugin_server_url") ?: ""
                val plugins = settingRepo.getAllKeys()
                    .filter { it.startsWith("plugin_content_") }
                    .map { key ->
                        val fileName = key.removePrefix("plugin_content_")
                        val content = settingRepo.get(key) ?: ""
                        PluginInfo(
                            fileName = fileName,
                            content = content,
                        )
                    }
                _state.update {
                    it.copy(
                        plugins = plugins,
                        serverUrl = serverUrl,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun updatePlugin(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(updatingPluginId = fileName, error = null) }
            try {
                pluginService.forceUpdate(fileName)
                loadPlugins()
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
                _state.value.plugins.forEach { plugin ->
                    _state.update { it.copy(updatingPluginId = plugin.fileName) }
                    pluginService.forceUpdate(plugin.fileName)
                }
                loadPlugins()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(updatingPluginId = "", isLoading = false) }
            }
        }
    }

    fun saveServerUrl(url: String) {
        settingRepo.upsert("plugin_server_url", url)
        _state.update { it.copy(serverUrl = url) }
    }
}
