package com.dailysatori.ui.feature.settings.remotenews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.remotenews.RemoteNewsConfigValues
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.service.remotenews.normalizeTopArticlesTodayUrl
import com.dailysatori.shared.db.Remote_news_source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteNewsSettingsState(
    val sources: List<Remote_news_source> = emptyList(),
    val isEditing: Boolean = false,
    val editingId: Long? = null,
    val name: String = "",
    val baseUrl: String = "",
    val token: String = "",
    val enabled: Boolean = true,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
)

class RemoteNewsSettingsViewModel(
    private val sourceRepo: RemoteNewsSourceRepository,
    private val remoteNewsService: RemoteNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteNewsSettingsState())
    val state: StateFlow<RemoteNewsSettingsState> = _state.asStateFlow()

    init { load() }

    fun updateName(value: String) = _state.update { it.copy(name = value, message = null) }

    fun updateBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, message = null) }

    fun updateToken(value: String) = _state.update { it.copy(token = value, message = null) }

    fun updateEnabled(value: Boolean) = _state.update { it.copy(enabled = value, message = null) }

    fun openAdd() = _state.update {
        it.copy(isEditing = true, editingId = null, name = "", baseUrl = "", token = "", enabled = true, message = null)
    }

    fun openEdit(source: Remote_news_source) = _state.update {
        it.copy(
            isEditing = true,
            editingId = source.id,
            name = source.name,
            baseUrl = source.base_url,
            token = source.api_token,
            enabled = source.enabled == 1L,
            message = null,
        )
    }

    fun closeEditor() = _state.update {
        it.copy(isEditing = false, editingId = null, name = "", baseUrl = "", token = "", enabled = true, message = null)
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(sources = sourceRepo.getAll()) }
        }
    }

    fun save() {
        if (state.value.isSaving) return
        val form = state.value
        val editingId = form.editingId
        val name = form.name.trim()
        val rawBaseUrl = form.baseUrl.trim()
        val baseUrl = normalizeTopArticlesTodayUrl(rawBaseUrl)
        val token = form.token.trim()
        val enabled = form.enabled
        if (name.isBlank() || rawBaseUrl.isBlank() || token.isBlank()) {
            _state.update { it.copy(message = "请填写名称、URL 和 Token") }
            return
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            _state.update { it.copy(message = "URL 必须以 http:// 或 https:// 开头") }
            return
        }
        _state.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sourceRepo.save(editingId, name, baseUrl, token, enabled)
                _state.update {
                    it.copy(
                        sources = sourceRepo.getAll(),
                        isEditing = false,
                        editingId = null,
                        name = "",
                        baseUrl = "",
                        token = "",
                        enabled = true,
                        message = "远程新闻设置已保存",
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(message = "远程新闻设置保存失败") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteSource(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceRepo.delete(id)
            _state.update { current ->
                val editingDeleted = current.editingId == id
                current.copy(
                    sources = sourceRepo.getAll(),
                    isEditing = if (editingDeleted) false else current.isEditing,
                    editingId = if (editingDeleted) null else current.editingId,
                    name = if (editingDeleted) "" else current.name,
                    baseUrl = if (editingDeleted) "" else current.baseUrl,
                    token = if (editingDeleted) "" else current.token,
                    enabled = if (editingDeleted) true else current.enabled,
                    message = "远程新闻已删除",
                )
            }
        }
    }

    fun testConnection() {
        if (state.value.isTesting) return
        val baseUrl = normalizeTopArticlesTodayUrl(state.value.baseUrl.trim())
        val token = state.value.token.trim()
        if (state.value.baseUrl.isBlank() || token.isBlank()) {
            _state.update { it.copy(message = "请先配置远程新闻服务") }
            return
        }
        _state.update { it.copy(isTesting = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = remoteNewsService.configOrFailure(baseUrl, token)
                val message = when (config) {
                    is RemoteNewsResult.Failure -> config.message
                    is RemoteNewsResult.Success<RemoteNewsConfigValues> -> when (
                        val result = remoteNewsService.fetchTopArticlesToday(config.value, page = 1, limit = 1)
                    ) {
                        is RemoteNewsResult.Success -> "连接成功，获取到 ${result.value.articles.size} 篇文章"
                        is RemoteNewsResult.Failure -> result.message
                    }
                }
                _state.update { it.copy(message = message) }
            } catch (_: Exception) {
                _state.update { it.copy(message = "无法连接远程新闻服务") }
            } finally {
                _state.update { it.copy(isTesting = false) }
            }
        }
    }
}
