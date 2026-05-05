package com.dailysatori.ui.feature.settings.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.McpProvider
import com.dailysatori.config.McpTemplate
import com.dailysatori.config.mcpTemplateDisplayName
import com.dailysatori.config.renderMcpConfigJson
import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.shared.db.Mcp_server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class McpServerUiState(
    val servers: List<Mcp_server> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
)

internal data class McpBatchSaveResult(val added: Int, val skipped: Int)

internal class McpServerViewModel(
    private val repo: McpServerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(McpServerUiState())
    val state: StateFlow<McpServerUiState> = _state.asStateFlow()
    private var observeJob: Job? = null

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun observeServers() {
        if (observeJob?.isActive == true) return
        observeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.getAll().collect { servers ->
                    _state.update { it.copy(servers = servers, error = null) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun toggleServerEnabled(server: Mcp_server, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.update(server.id, server.name, server.server_url, server.api_key, if (enabled) 1L else 0L)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    suspend fun saveSelectedTemplates(
        provider: McpProvider,
        templates: List<McpTemplate>,
        apiKey: String,
    ): McpBatchSaveResult? {
        _state.update { it.copy(isSaving = true, error = null) }
        return try {
            withContext(Dispatchers.IO) { saveTemplates(provider, templates, apiKey) }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
            null
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun loadServer(serverId: Long, onLoaded: (Mcp_server) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val server = repo.getById(serverId) ?: return@launch
                withContext(Dispatchers.Main) { onLoaded(server) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    suspend fun saveServer(
        serverId: Long?,
        name: String,
        serverUrl: String,
        apiKey: String,
        enabled: Boolean,
    ): Boolean {
        _state.update { it.copy(isSaving = true, error = null) }
        return try {
            withContext(Dispatchers.IO) {
                if (serverId != null && serverId > 0) {
                    repo.update(serverId, name, serverUrl, apiKey, if (enabled) 1L else 0L)
                } else {
                    repo.insert(name, serverUrl, apiKey, if (enabled) 1L else 0L)
                }
            }
            true
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
            false
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun saveTemplates(
        provider: McpProvider,
        templates: List<McpTemplate>,
        apiKey: String,
    ): McpBatchSaveResult {
        var added = 0
        var skipped = 0
        templates.forEach { template ->
            if (repo.getByServerUrl(template.serverUrl) != null) {
                skipped += 1
            } else {
                repo.insertPreset(
                    name = mcpTemplateDisplayName(provider, template),
                    serverUrl = template.serverUrl,
                    apiKey = apiKey,
                    provider = provider.id,
                    templateId = template.id,
                    templateType = template.type.name.lowercase(),
                    configJson = renderMcpConfigJson(template),
                )
                added += 1
            }
        }
        return McpBatchSaveResult(added = added, skipped = skipped)
    }
}
