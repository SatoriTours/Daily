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
import kotlinx.datetime.toLocalDateTime

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

fun externalFavoriteManagementSummaryTitle(sources: List<ExternalFavoriteSourceUi>): String {
    if (sources.isEmpty()) return "还没有连接外部收藏来源"
    if (sources.all { !it.enabled || it.health == "paused" }) return "外部收藏同步已暂停"
    val attentionCount = sources.count { it.health in setOf("needs_auth", "limited", "failing") }
    return if (attentionCount > 0) "${attentionCount} 个来源需要处理" else "所有外部收藏来源同步正常"
}

fun externalFavoriteManagementSummarySubtitle(): String =
    "收藏会定期同步到本地收藏，可手动同步或导入历史收藏。"

fun externalFavoriteShouldShowAuthCheckNotice(sources: List<ExternalFavoriteSourceUi>): Boolean =
    sources.any { it.source.status == ExternalSourceStatus.auth_check_required.name }

fun externalFavoriteAuthCheckNoticeText(): String = "已恢复的授权需要重新连接后才能继续同步。"

fun externalFavoriteEmptyStateTitle(): String = "连接外部收藏"

fun externalFavoriteEmptyStateSubtitle(message: String? = null): String =
    listOfNotNull(
        "当前先支持 X 收藏。连接后，收藏会同步到本地收藏，并保留手动同步和历史导入入口。",
        message?.takeIf { it.isNotBlank() },
    ).joinToString("\n")

fun externalFavoriteAddServiceActionLabel(hasSources: Boolean = false): String =
    if (hasSources) "连接新来源" else "连接 X 收藏"

fun externalFavoriteAddPageTitle(): String = "新增外部收藏"

fun externalFavoriteAddPageHelperTitle(): String = "连接 X 收藏"

fun externalFavoriteAddPageHelperText(): String =
    "填写 OAuth Client ID 后，会打开浏览器完成 X 授权。授权完成后回到 Daily Satori，新来源会出现在列表里。"

fun externalFavoriteAddPageSyncNoteTitle(): String = "授权成功后启用定期同步"

fun externalFavoriteAddPageSyncNoteText(): String =
    "授权成功后，新来源会出现在来源列表，可在那里停用定期同步、手动同步或导入历史收藏。"

fun externalFavoriteShouldCloseAddPageAfterConnect(
    clientIdSaved: Boolean,
    authorizationLaunched: Boolean,
): Boolean = clientIdSaved && authorizationLaunched

fun externalFavoriteXClientIdLabel(): String = "X OAuth Client ID"

fun externalFavoriteConnectXActionLabel(): String = "保存并连接 X"

fun externalFavoritePrimaryActionLabel(health: String): String = when (health) {
    "never_synced" -> "开始同步"
    "paused" -> "启用同步"
    "needs_auth" -> "需要授权"
    "limited" -> "稍后自动恢复"
    "failing" -> "重试同步"
    else -> "同步"
}

fun externalFavoriteAccountIdentity(accountName: String, accountId: String): String =
    accountName.ifBlank { accountId }

fun externalFavoriteCanRunSyncAction(health: String, enabled: Boolean): Boolean =
    enabled && health !in setOf("paused", "needs_auth", "limited")

fun externalFavoriteDeleteDialogTitle(): String = "删除外部收藏来源？"

fun externalFavoriteDeleteDialogText(): String =
    "这会删除该来源的授权信息和同步记录。已经导入到本地收藏的内容不会被删除。"

fun externalFavoriteDeleteConfirmLabel(): String = "删除来源"

fun externalFavoriteDeleteCancelLabel(): String = "取消"

fun externalFavoriteSyncAttemptText(
    lastAttemptAt: Long?,
    lastSuccessAt: Long?,
    nowMillis: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): String = when {
    lastSuccessAt != null -> "上次成功：${externalFavoriteRelativeTimeText(lastSuccessAt, nowMillis)}"
    lastAttemptAt != null -> "上次尝试：${externalFavoriteRelativeTimeText(lastAttemptAt, nowMillis)}"
    else -> "尚未同步"
}

fun externalFavoriteSeenCountText(itemsSeen: Long, pagesSeen: Long): String? {
    val parts = buildList {
        if (itemsSeen > 0) add("上次看到 ${itemsSeen} 条收藏")
        if (pagesSeen > 1) add("读取 ${pagesSeen} 页")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

fun externalFavoriteRateLimitText(
    resetAt: Long?,
    nowMillis: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): String =
    resetAt?.takeIf { it > nowMillis }?.let {
        "平台限流中，预计 ${externalFavoriteFutureDurationText(it, nowMillis)}恢复"
    } ?: "平台限流中，稍后自动恢复"

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

private fun externalFavoriteRelativeTimeText(timestampMillis: Long, nowMillis: Long): String {
    val diffMinutes = ((nowMillis - timestampMillis).coerceAtLeast(0L) / 60_000L)
    if (diffMinutes < 1) return "刚刚"
    if (diffMinutes < 60) return "${diffMinutes} 分钟前"
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestampMillis)
    val local = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

private fun externalFavoriteFutureDurationText(timestampMillis: Long, nowMillis: Long): String {
    val diffMinutes = ((timestampMillis - nowMillis).coerceAtLeast(0L) / 60_000L).coerceAtLeast(1L)
    if (diffMinutes <= 60) return "${diffMinutes} 分钟后"
    val hours = diffMinutes / 60L
    val minutes = diffMinutes % 60L
    return if (minutes == 0L) "${hours} 小时后" else "${hours} 小时 ${minutes} 分钟后"
}
