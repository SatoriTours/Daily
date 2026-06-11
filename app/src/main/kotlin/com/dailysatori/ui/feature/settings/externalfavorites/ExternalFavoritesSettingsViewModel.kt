package com.dailysatori.ui.feature.settings.externalfavorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.externalfavorites.ExternalSourceStatus
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.XOAuthCoordinator
import com.dailysatori.service.externalfavorites.sourceHealth
import com.dailysatori.shared.db.External_favorite_source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExternalFavoritesSettingsState(
    val sources: List<ExternalFavoriteSourceUi> = emptyList(),
    val message: String? = null,
    val syncingSourceId: Long? = null,
    val xOAuthClientId: String = "",
)

data class ExternalFavoriteSourceUi(
    val source: External_favorite_source,
    val health: String,
) {
    val id: Long get() = source.id
    val enabled: Boolean get() = source.enabled == 1L
}

class ExternalFavoritesSettingsViewModel(
    private val sourceRepo: ExternalFavoriteSourceRepository,
    private val scheduler: ExternalFavoriteSyncScheduler,
    private val xOAuthCoordinator: XOAuthCoordinator,
    private val settingRepo: SettingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ExternalFavoritesSettingsState())
    val state: StateFlow<ExternalFavoritesSettingsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    sources = sourceRepo.getAll().map(::toUiSource),
                    xOAuthClientId = settingRepo.get(SettingKeys.xOAuthClientId).orEmpty(),
                )
            }
        }
    }

    fun updateXOAuthClientId(value: String) =
        _state.update { it.copy(xOAuthClientId = value, message = null) }

    fun saveXOAuthClientIdForConnect(): Boolean {
        val clientId = _state.value.xOAuthClientId.trim()
        if (clientId.isBlank()) {
            _state.update { it.copy(message = "请先填写 X OAuth Client ID") }
            return false
        }
        return runCatching {
            settingRepo.upsert(SettingKeys.xOAuthClientId, clientId)
            _state.update { it.copy(xOAuthClientId = clientId, message = null) }
        }.onFailure {
            _state.update { state -> state.copy(message = "X OAuth Client ID 保存失败") }
        }.isSuccess
    }

    fun syncNow(sourceId: Long) {
        enqueueManualSync(sourceId, FavoriteSyncMode.recent)
    }

    fun importOlder(sourceId: Long) {
        enqueueManualSync(sourceId, FavoriteSyncMode.history)
    }

    fun toggleEnabled(sourceId: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val source = sourceRepo.getById(sourceId) ?: return@launch
            sourceRepo.save(
                id = source.id,
                provider = source.provider,
                displayName = source.display_name,
                accountId = source.account_id,
                accountName = source.account_name,
                authJson = source.auth_json,
                enabled = enabled,
                syncIntervalMinutes = source.sync_interval_minutes,
                status = externalFavoriteStatusAfterToggle(source.status, enabled),
                configJson = source.config_json,
                capabilitiesJson = source.capabilities_json,
            )
            _state.update {
                it.copy(
                    sources = sourceRepo.getAll().map(::toUiSource),
                    message = if (enabled) "外部收藏同步已启用" else "外部收藏同步已停用",
                )
            }
        }
    }

    fun deleteSource(sourceId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceRepo.delete(sourceId)
            _state.update {
                it.copy(
                    sources = sourceRepo.getAll().map(::toUiSource),
                    message = "外部收藏来源已删除",
                    syncingSourceId = if (it.syncingSourceId == sourceId) null else it.syncingSourceId,
                )
            }
        }
    }

    fun markRestoredSourcesAuthCheckRequired() {
        viewModelScope.launch(Dispatchers.IO) {
            sourceRepo.markAuthCheckRequiredAfterRestore()
            _state.update {
                it.copy(
                    sources = sourceRepo.getAll().map(::toUiSource),
                    message = "已标记需要重新验证授权",
                )
            }
        }
    }

    fun createXAuthorizationUrl(): String? = runCatching {
        xOAuthCoordinator.beginAuthorization()
    }.onFailure {
        _state.update { state -> state.copy(message = "请先配置 X OAuth Client ID") }
    }.getOrNull()

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    private fun enqueueManualSync(sourceId: Long, mode: FavoriteSyncMode) {
        if (_state.value.syncingSourceId != null) return
        _state.update { it.copy(syncingSourceId = sourceId, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scheduler.enqueue(sourceId, mode.name)
                _state.update {
                    it.copy(
                        syncingSourceId = null,
                        message = if (mode == FavoriteSyncMode.history) "已加入历史收藏导入队列" else "已加入同步队列",
                        sources = sourceRepo.getAll().map(::toUiSource),
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(syncingSourceId = null, message = "同步任务创建失败") }
            }
        }
    }

    private fun toUiSource(source: External_favorite_source): ExternalFavoriteSourceUi =
        ExternalFavoriteSourceUi(
            source = source,
            health = sourceHealth(
                status = source.status,
                lastSuccessAt = source.last_success_at,
                lastErrorCode = source.last_error_code,
            ).name,
        )
}

fun externalFavoriteSettingsRowTitle(): String = "外部收藏同步"

fun externalFavoriteSettingsRowSubtitle(): String = "同步 X 等平台收藏到本地收藏"

fun externalFavoriteEmptyStateTitle(): String = "添加外部收藏服务"

fun externalFavoriteEmptyStateSubtitle(message: String? = null): String =
    listOfNotNull(
        "添加 X 等平台后，收藏会定期同步到本地收藏，并由 AI 整理内容。",
        message?.takeIf { it.isNotBlank() },
    ).joinToString("\n")

fun externalFavoriteAddServiceActionLabel(): String = "添加服务"

fun externalFavoriteAddPageTitle(): String = "新增外部收藏"

fun externalFavoriteAddPageHelperTitle(): String = "连接 X 收藏"

fun externalFavoriteAddPageHelperText(): String = "填写 OAuth Client ID 后，会打开浏览器完成 X 授权。授权成功后，收藏会定期同步到本地收藏。"

fun externalFavoriteAddPageSyncNoteTitle(): String = "授权成功后启用定期同步"

fun externalFavoriteAddPageSyncNoteText(): String =
    "授权成功后，新来源会出现在来源列表，可在那里停用定期同步、手动同步或导入历史收藏。"

fun externalFavoriteXClientIdLabel(): String = "X OAuth Client ID"

fun externalFavoriteConnectXActionLabel(): String = "保存并连接 X"

fun externalFavoritePeriodicSyncSubtitle(health: String): String = when (health) {
    "needs_auth" -> "需要重新授权后才能定期同步"
    "limited" -> "平台限流中，稍后自动恢复定期同步"
    "paused" -> "已停用，不会定期同步"
    "failing" -> "最近同步失败，可手动重试"
    "never_synced" -> "尚未同步，启用后将定期导入"
    else -> "已启用定期同步"
}

fun externalFavoriteHealthLabel(health: String): String = when (health) {
    "needs_auth" -> "需要授权"
    "limited" -> "限流中"
    "paused" -> "已暂停"
    "failing" -> "异常"
    "never_synced" -> "未同步"
    else -> "正常"
}

fun externalFavoriteStatusAfterToggle(currentStatus: String, enabled: Boolean): String = when {
    !enabled -> ExternalSourceStatus.paused.name
    currentStatus == ExternalSourceStatus.paused.name -> ExternalSourceStatus.idle.name
    else -> currentStatus
}
