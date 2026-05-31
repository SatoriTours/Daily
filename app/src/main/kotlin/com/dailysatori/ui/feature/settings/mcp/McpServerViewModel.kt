package com.dailysatori.ui.feature.settings.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.McpProvider
import com.dailysatori.config.McpTemplate
import com.dailysatori.config.mcpTemplateDisplayName
import com.dailysatori.config.renderMcpConfigJson
import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.service.mcp.RemoteMcpClient
import com.dailysatori.shared.db.Mcp_server
import kotlinx.coroutines.CancellationException
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
    val isTesting: Boolean = false,
    val testMessage: String? = null,
    val testSucceeded: Boolean? = null,
    val error: String? = null,
)

internal data class McpBatchSaveResult(val added: Int, val skipped: Int)

internal class McpServerViewModel(
    private val repo: McpServerRepository,
    private val remoteMcpClient: RemoteMcpClient,
) : ViewModel() {
    private val _state = MutableStateFlow(McpServerUiState())
    val state: StateFlow<McpServerUiState> = _state.asStateFlow()
    private var observeJob: Job? = null
    private var testJob: Job? = null
    private var testRequestId = 0L

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearTestMessage() {
        testRequestId++
        testJob?.cancel()
        testJob = null
        _state.update { it.copy(isTesting = false, testMessage = null, testSucceeded = null) }
    }

    fun testServer(name: String, serverUrl: String, apiKey: String) {
        val requestId = ++testRequestId
        testJob?.cancel()
        val validation = mcpConnectionValidationMessage(name, serverUrl)
        if (validation != null) {
            _state.update { it.copy(isTesting = false, testMessage = validation, testSucceeded = false) }
            return
        }
        testJob = viewModelScope.launch(Dispatchers.IO) {
            if (requestId == testRequestId) {
                _state.update { it.copy(isTesting = true, testMessage = null, testSucceeded = null) }
            }
            try {
                val result = remoteMcpClient.testConnection(testMcpServer(name, serverUrl, apiKey))
                if (requestId == testRequestId) {
                    _state.update {
                        it.copy(
                            isTesting = false,
                            testSucceeded = result.isSuccess,
                            testMessage = result.fold(
                                onSuccess = { count -> mcpConnectionSuccessMessage(count) },
                                onFailure = { error -> error.message ?: "连接失败" },
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
                            testMessage = error.message ?: "连接失败",
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

    private fun testMcpServer(name: String, serverUrl: String, apiKey: String): Mcp_server =
        Mcp_server(
            id = -1L,
            name = name.trim(),
            server_url = serverUrl.trim(),
            api_key = apiKey.trim(),
            enabled = 1L,
            provider = "",
            template_id = "",
            template_type = "",
            config_json = "",
            created_at = 0L,
            updated_at = 0L,
        )

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

    fun saveSelectedTemplates(
        provider: McpProvider,
        templates: List<McpTemplate>,
        apiKey: String,
        onSuccess: (McpBatchSaveResult) -> Unit,
    ) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = saveTemplates(provider, templates, apiKey)
                withContext(Dispatchers.Main) { onSuccess(result) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
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

    fun saveServer(
        serverId: Long?,
        name: String,
        serverUrl: String,
        apiKey: String,
        enabled: Boolean,
        onSaved: () -> Unit,
    ) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (serverId != null && serverId > 0) {
                    repo.update(serverId, name, serverUrl, apiKey, if (enabled) 1L else 0L)
                } else {
                    repo.insert(name, serverUrl, apiKey, if (enabled) 1L else 0L)
                }
                withContext(Dispatchers.Main) { onSaved() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
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
