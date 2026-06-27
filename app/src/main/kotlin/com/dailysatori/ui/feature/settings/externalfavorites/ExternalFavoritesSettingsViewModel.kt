package com.dailysatori.ui.feature.settings.externalfavorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.dailysatori.core.worker.externalFavoriteSyncUniqueKey
import com.dailysatori.config.SettingKeys
import com.dailysatori.core.worker.ExternalFavoriteSyncWorker
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.externalfavorites.ExternalFavoriteProvider
import com.dailysatori.service.externalfavorites.ExternalSourceHealth
import com.dailysatori.service.externalfavorites.ExternalSourceStatus
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.XOAuthCoordinator
import com.dailysatori.service.externalfavorites.sourceHealth
import com.dailysatori.shared.db.Async_task
import com.dailysatori.shared.db.External_favorite_source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

data class ExternalFavoritesSettingsState(
    val sources: List<ExternalFavoriteSourceUi> = emptyList(),
    val message: String? = null,
    val syncingSourceId: Long? = null,
    val syncWorkBySourceId: Map<Long, ExternalFavoriteSyncWorkUi> = emptyMap(),
    val xOAuthClientId: String = "",
)

data class ExternalFavoriteSourceUi(
    val source: External_favorite_source,
    val health: ExternalSourceHealth,
) {
    val id: Long get() = source.id
    val enabled: Boolean get() = source.enabled == 1L
}

data class ExternalFavoriteSummaryMetric(
    val value: String,
    val label: String,
)

data class ExternalFavoriteProgressMetric(
    val value: String,
    val label: String,
)

data class ExternalFavoriteDetailLine(
    val label: String,
    val value: String,
)

data class ExternalFavoriteSyncWorkUi(
    val taskId: Long? = null,
    val createdAt: Long? = null,
    val state: WorkInfo.State,
    val pagesSeen: Int,
    val maxPages: Int,
    val itemsSeen: Int,
    val phase: String,
    val historyComplete: Boolean = false,
) {
    val active: Boolean get() = state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
}

class ExternalFavoritesSettingsViewModel(
    private val sourceRepo: ExternalFavoriteSourceRepository,
    private val scheduler: ExternalFavoriteSyncScheduler,
    private val asyncTaskRepo: AsyncTaskRepository,
    private val xOAuthCoordinator: XOAuthCoordinator,
    private val settingRepo: SettingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ExternalFavoritesSettingsState())
    val state: StateFlow<ExternalFavoritesSettingsState> = _state.asStateFlow()
    private val observedSyncKeys = mutableSetOf<String>()
    private val observedTaskIds = mutableSetOf<Long>()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    sources = sourceRepo.getAll().map(::toUiSource),
                    xOAuthClientId = settingRepo.get(SettingKeys.xOAuthClientId).orEmpty(),
                )
            }
            _state.value.sources.forEach {
                FavoriteSyncMode.entries.forEach { mode -> observeSyncWork(it.id, mode) }
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

    fun createXAuthorizationUrl(): String? = runCatching {
        xOAuthCoordinator.beginAuthorization()
    }.onFailure {
        _state.update { state -> state.copy(message = "请先配置 X OAuth Client ID") }
    }.getOrNull()

    fun syncNow(sourceId: Long) {
        enqueueManualSync(sourceId, FavoriteSyncMode.sync)
    }

    fun cancelSync(sourceId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduler.cancelSync(sourceId)
            _state.update {
                it.copy(
                    syncingSourceId = if (it.syncingSourceId == sourceId) null else it.syncingSourceId,
                    message = "已取消本次同步",
                )
            }
        }
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
            if (enabled) {
                sourceRepo.getById(sourceId)?.let { scheduler.enqueuePeriodic(it.id, it.sync_interval_minutes) }
            } else {
                scheduler.cancelPeriodic(sourceId)
            }
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
            scheduler.cancelPeriodic(sourceId)
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

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    private fun enqueueManualSync(sourceId: Long, mode: FavoriteSyncMode) {
        if (_state.value.syncingSourceId != null) return
        _state.update { it.copy(syncingSourceId = sourceId, message = null) }
        _state.update { state ->
            state.copy(syncWorkBySourceId = state.syncWorkBySourceId + (sourceId to externalFavoriteQueuedSyncWork()))
        }
        observeSyncWork(sourceId, mode)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val taskId = scheduler.enqueue(sourceId, mode.name)
                if (taskId != null) observeExactSyncTask(sourceId, taskId)
                _state.update {
                    it.copy(
                        syncingSourceId = sourceId,
                        message = externalFavoriteSyncQueuedMessage(mode),
                        syncWorkBySourceId = if (taskId == null) {
                            it.syncWorkBySourceId
                        } else {
                            val current = it.syncWorkBySourceId[sourceId] ?: externalFavoriteQueuedSyncWork()
                            it.syncWorkBySourceId + (sourceId to current.copy(taskId = taskId))
                        },
                        sources = sourceRepo.getAll().map(::toUiSource),
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        syncingSourceId = null,
                        syncWorkBySourceId = it.syncWorkBySourceId - sourceId,
                        message = "同步任务创建失败",
                    )
                }
            }
        }
    }

    private fun observeSyncWork(sourceId: Long, mode: FavoriteSyncMode) {
        val key = externalFavoriteSyncUniqueKey(sourceId, mode.name)
        if (!observedSyncKeys.add(key)) return
        viewModelScope.launch {
            asyncTaskRepo.observeLatestByUniqueKey(externalFavoriteSyncUniqueKey(sourceId, mode.name)).collect { task ->
                val workUi = task?.let(::externalFavoriteSyncWorkFromAsyncTask)
                applySyncWorkState(sourceId, workUi)
                if (workUi?.state in finishedWorkStates) load()
            }
        }
    }

    private fun observeExactSyncTask(sourceId: Long, taskId: Long) {
        if (!observedTaskIds.add(taskId)) return
        viewModelScope.launch {
            asyncTaskRepo.observeTaskById(taskId).collect { task ->
                val workUi = task?.let(::externalFavoriteSyncWorkFromAsyncTask)
                applySyncWorkState(sourceId, workUi)
                if (workUi?.state in finishedWorkStates) load()
            }
        }
    }

    private fun applySyncWorkState(sourceId: Long, workUi: ExternalFavoriteSyncWorkUi?) {
        _state.update { state -> externalFavoriteApplySyncWorkState(state, sourceId, workUi) }
    }

    private fun toUiSource(source: External_favorite_source): ExternalFavoriteSourceUi =
        ExternalFavoriteSourceUi(
            source = source,
            health = sourceHealth(
                status = source.status,
                lastSuccessAt = source.last_success_at,
                lastErrorCode = source.last_error_code,
            ),
        )
}

private val finishedWorkStates = setOf(
    WorkInfo.State.SUCCEEDED,
    WorkInfo.State.FAILED,
    WorkInfo.State.CANCELLED,
)

internal fun externalFavoriteApplySyncWorkState(
    state: ExternalFavoritesSettingsState,
    sourceId: Long,
    workUi: ExternalFavoriteSyncWorkUi?,
): ExternalFavoritesSettingsState {
    val currentWork = state.syncWorkBySourceId[sourceId]
    val staleFinishedTask = workUi?.state in finishedWorkStates &&
        currentWork?.active == true &&
        when {
            currentWork.taskId == null -> true
            currentWork.taskId != workUi?.taskId -> true
            currentWork.createdAt != null && workUi?.createdAt != null -> workUi.createdAt < currentWork.createdAt
            else -> false
        }
    val nextMap = when {
        workUi == null -> state.syncWorkBySourceId - sourceId
        staleFinishedTask -> state.syncWorkBySourceId
        workUi.state in finishedWorkStates -> state.syncWorkBySourceId - sourceId
        else -> state.syncWorkBySourceId + (sourceId to workUi)
    }
    val nextWork = nextMap[sourceId]
    return state.copy(
        syncWorkBySourceId = nextMap,
        syncingSourceId = when {
            nextWork?.active == true -> sourceId
            state.syncingSourceId == sourceId -> null
            else -> state.syncingSourceId
        },
    )
}

private fun WorkInfo.toExternalFavoriteSyncWorkUi(): ExternalFavoriteSyncWorkUi =
    ExternalFavoriteSyncWorkUi(
        state = state,
        pagesSeen = progress.getInt(ExternalFavoriteSyncWorker.PROGRESS_PAGES_SEEN, 0),
        maxPages = progress.getInt(ExternalFavoriteSyncWorker.PROGRESS_MAX_PAGES, 3).coerceAtLeast(1),
        itemsSeen = progress.getInt(ExternalFavoriteSyncWorker.PROGRESS_ITEMS_SEEN, 0),
        phase = progress.getString(ExternalFavoriteSyncWorker.PROGRESS_PHASE).orEmpty(),
        historyComplete = progress.getBoolean(ExternalFavoriteSyncWorker.PROGRESS_HISTORY_COMPLETE, false),
    )

fun externalFavoriteSyncWorkFromAsyncTask(task: Async_task): ExternalFavoriteSyncWorkUi? {
    val status = AsyncTaskStatus.entries.firstOrNull { it.name == task.status } ?: return null
    return ExternalFavoriteSyncWorkUi(
        taskId = task.id,
        createdAt = task.created_at,
        state = status.toWorkInfoState(),
        pagesSeen = externalFavoriteTaskCheckpointLong(task.checkpoint_json, "pagesSeen")
            ?.toInt()
            ?: task.progress_current.toInt(),
        maxPages = task.progress_total.toInt().coerceAtLeast(1),
        itemsSeen = externalFavoriteTaskCheckpointLong(task.checkpoint_json, "itemsSeen")?.toInt() ?: 0,
        phase = externalFavoriteTaskCheckpointString(task.checkpoint_json, "phase").orEmpty(),
        historyComplete = externalFavoriteTaskCheckpointBoolean(task.checkpoint_json, "historyComplete") ?: false,
    )
}

private fun externalFavoriteQueuedSyncWork(): ExternalFavoriteSyncWorkUi =
    ExternalFavoriteSyncWorkUi(
        createdAt = Clock.System.now().toEpochMilliseconds(),
        state = WorkInfo.State.ENQUEUED,
        pagesSeen = 0,
        maxPages = 3,
        itemsSeen = 0,
        phase = "",
    )

private fun AsyncTaskStatus.toWorkInfoState(): WorkInfo.State = when (this) {
    AsyncTaskStatus.queued,
    AsyncTaskStatus.retrying -> WorkInfo.State.ENQUEUED
    AsyncTaskStatus.running -> WorkInfo.State.RUNNING
    AsyncTaskStatus.succeeded -> WorkInfo.State.SUCCEEDED
    AsyncTaskStatus.failed -> WorkInfo.State.FAILED
    AsyncTaskStatus.cancelled -> WorkInfo.State.CANCELLED
}

private fun externalFavoriteTaskCheckpointLong(json: String, key: String): Long? =
    Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)""")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

private fun externalFavoriteTaskCheckpointString(json: String, key: String): String? =
    Regex(""""${Regex.escape(key)}"\s*:\s*"([^"]*)"""")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)

private fun externalFavoriteTaskCheckpointBoolean(json: String, key: String): Boolean? =
    Regex(""""${Regex.escape(key)}"\s*:\s*(true|false)""")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.toBooleanStrictOrNull()

fun externalFavoriteSettingsRowTitle(): String = "外部收藏同步"

fun externalFavoriteSettingsRowSubtitle(): String = "同步 X 等平台收藏到本地文章库"

fun externalFavoriteManagementSummaryTitle(sources: List<ExternalFavoriteSourceUi>): String {
    if (sources.isEmpty()) return "还没有连接外部收藏来源"
    if (sources.all { !it.enabled || it.health == ExternalSourceHealth.paused }) return "外部收藏同步已暂停"
    val attentionCount = sources.count {
        it.health in setOf(ExternalSourceHealth.needs_auth, ExternalSourceHealth.limited, ExternalSourceHealth.failing)
    }
    return if (attentionCount > 0) "${attentionCount} 个来源需要处理" else "所有外部收藏来源同步正常"
}

fun externalFavoriteManagementSummarySubtitle(): String =
    "同步会先检查最新收藏，并逐步补全较早收藏。"

fun externalFavoriteShouldShowAuthCheckNotice(sources: List<ExternalFavoriteSourceUi>): Boolean =
    sources.any { it.source.status == ExternalSourceStatus.auth_check_required.name }

fun externalFavoriteAuthCheckNoticeText(): String = "已恢复的授权需要重新连接后才能继续同步。"

fun externalFavoriteEmptyStateTitle(): String = "连接外部收藏"

fun externalFavoriteEmptyStateSubtitle(message: String? = null): String =
    listOfNotNull(
        "当前先支持 X 收藏。连接后，收藏会同步到本地文章库，并在后台逐步补全历史。",
        message?.takeIf { it.isNotBlank() },
    ).joinToString("\n")

fun externalFavoriteAddServiceActionLabel(hasSources: Boolean = false): String =
    if (hasSources) "连接新来源" else "连接 X 收藏"

fun externalFavoriteAddPageTitle(): String = "新增外部收藏"

fun externalFavoriteAddPageHelperTitle(): String = "连接 X 收藏"

fun externalFavoriteAddPageHelperText(): String =
    "填写 X OAuth Client ID 后，会用 OAuth2 + PKCE 打开 X 授权页面。请在 X Developer Portal 配置下面的回调地址。"

fun externalFavoriteAddPageSyncNoteTitle(): String = "授权成功后启用定期同步"

fun externalFavoriteAddPageSyncNoteText(): String =
    "授权成功后，新来源会出现在来源列表，也会作为新闻汇总页的来源筛选。"

fun externalFavoriteAddPageOrganizeNoteText(): String =
    "导入到本地文章库后，再交给本地配置的 AI 整理内容。"

fun externalFavoriteShouldCloseAddPageAfterConnect(
    clientIdSaved: Boolean,
    authorizationLaunched: Boolean,
): Boolean = clientIdSaved && authorizationLaunched

fun externalFavoriteXClientIdLabel(): String = "X OAuth Client ID"

fun externalFavoriteConnectXActionLabel(): String = "保存并打开 X 授权"

fun externalFavoriteXOAuthRedirectUriLabel(): String = "回调地址"

fun externalFavoriteXOAuthRedirectUri(): String = "dailysatori://oauth/x"

fun externalFavoritePrimaryActionLabel(health: ExternalSourceHealth): String = when (health) {
    ExternalSourceHealth.never_synced -> "同步收藏"
    ExternalSourceHealth.paused -> "启用同步"
    ExternalSourceHealth.needs_auth -> "重新连接"
    ExternalSourceHealth.limited -> "稍后自动恢复"
    ExternalSourceHealth.failing -> "重试同步"
    ExternalSourceHealth.healthy -> "同步收藏"
}

fun externalFavoriteSyncActionLabel(
    health: ExternalSourceHealth,
    work: ExternalFavoriteSyncWorkUi?,
): String = if (work?.active == true) "取消同步" else externalFavoritePrimaryActionLabel(health)

fun externalFavoriteSyncActionEnabled(
    health: ExternalSourceHealth,
    enabled: Boolean,
    work: ExternalFavoriteSyncWorkUi?,
): Boolean = if (work?.active == true) {
    true
} else {
    when (health) {
        ExternalSourceHealth.limited -> false
        ExternalSourceHealth.paused, ExternalSourceHealth.needs_auth -> true
        else -> externalFavoriteCanRunSyncAction(health, enabled)
    }
}

fun externalFavoriteEffectiveHealthLabel(
    health: ExternalSourceHealth,
    work: ExternalFavoriteSyncWorkUi?,
): String = if (work?.active == true) "同步中" else externalFavoriteHealthLabel(health)

fun externalFavoriteSyncProgressTitle(work: ExternalFavoriteSyncWorkUi): String = when {
    work.state == WorkInfo.State.ENQUEUED -> "等待同步"
    work.phase == "backfill" -> "正在补全较早收藏"
    work.phase == "import" -> "正在导入收藏文章"
    work.phase == "repair" -> "正在修复收藏文章"
    work.phase == "organize" -> "正在整理收藏内容"
    work.phase == "complete" -> "正在完成同步"
    else -> "正在同步最新收藏"
}

fun externalFavoriteSyncProgressPageText(work: ExternalFavoriteSyncWorkUi): String = when (work.phase) {
    "import", "repair", "organize" ->
        "已读取 ${work.pagesSeen.coerceAtLeast(0)} 页 · ${work.itemsSeen.coerceAtLeast(0)} 条"
    "complete" -> "读取完成"
    else -> "第 ${work.pagesSeen.coerceAtLeast(0)} / ${work.maxPages.coerceAtLeast(1)} 页"
}

fun externalFavoriteSyncProgressFraction(work: ExternalFavoriteSyncWorkUi): Float = when (work.phase) {
    "import" -> 0.78f
    "repair" -> 0.88f
    "organize" -> 0.94f
    "complete" -> 0.72f
    else -> (work.pagesSeen.toFloat() / work.maxPages.coerceAtLeast(1)).coerceIn(0f, 0.72f)
}

fun externalFavoriteProgressMetrics(
    work: ExternalFavoriteSyncWorkUi,
    historyComplete: Boolean,
): List<ExternalFavoriteProgressMetric> = listOf(
    ExternalFavoriteProgressMetric("${work.pagesSeen.coerceAtLeast(0)} 页", "本次已读取"),
    ExternalFavoriteProgressMetric("${work.itemsSeen.coerceAtLeast(0)} 条", "本次看到"),
    ExternalFavoriteProgressMetric(if (historyComplete || work.historyComplete) "已完成" else "未完成", "历史补全"),
)

fun externalFavoriteRunningDetailLines(work: ExternalFavoriteSyncWorkUi): List<ExternalFavoriteDetailLine> = listOf(
    ExternalFavoriteDetailLine("当前阶段", externalFavoriteRunningPhaseLabel(work.phase)),
    ExternalFavoriteDetailLine("同步策略", "每次最多 ${work.maxPages.coerceAtLeast(1)} 页 / 300 条"),
    ExternalFavoriteDetailLine("取消后", "保留已同步内容，下次继续"),
)

private fun externalFavoriteRunningPhaseLabel(phase: String): String = when (phase) {
    "backfill" -> "补全历史收藏"
    "import" -> "导入本地文章"
    "repair" -> "修复文章状态"
    "organize" -> "整理收藏内容"
    "complete" -> "收尾同步"
    else -> "读取 X bookmarks"
}

fun externalFavoriteIdleDetailLines(item: ExternalFavoriteSourceUi): List<ExternalFavoriteDetailLine> = listOf(
    ExternalFavoriteDetailLine("上次结果", externalFavoriteLastResultText(item.source.last_items_seen_count, item.source.last_pages_seen_count)),
    ExternalFavoriteDetailLine("历史状态", externalFavoriteHistoryStatusText(item.source.config_json)),
    ExternalFavoriteDetailLine("本地收藏", "不会自动标记"),
)

fun externalFavoriteSourceSubtitle(
    identity: String,
    lastSuccessAt: Long?,
    syncIntervalMinutes: Long,
    nowMillis: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): String {
    val suffix = lastSuccessAt?.let { "上次成功：${externalFavoriteRelativeTimeText(it, nowMillis)}" }
        ?: "每 ${externalFavoriteReadableIntervalText(syncIntervalMinutes)}自动同步"
    return listOf(identity, suffix)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

fun externalFavoriteLastResultText(itemsSeen: Long, pagesSeen: Long): String =
    if (itemsSeen <= 0 && pagesSeen <= 0) {
        "尚未同步"
    } else {
        buildList {
            if (pagesSeen > 0) add("读取 ${pagesSeen} 页")
            if (itemsSeen > 0) add("看到 ${itemsSeen} 条")
        }.joinToString(" · ")
    }

fun externalFavoriteHistoryStatusText(configJson: String): String =
    if (configJson.contains(""""history_complete":true""")) "已完成" else "仍在逐步补全"

fun externalFavoriteSummaryMetrics(sources: List<ExternalFavoriteSourceUi>): List<ExternalFavoriteSummaryMetric> {
    if (sources.isEmpty()) {
        return listOf(
            ExternalFavoriteSummaryMetric("0", "已连接来源"),
            ExternalFavoriteSummaryMetric("X", "当前支持平台"),
            ExternalFavoriteSummaryMetric("12h", "默认同步间隔"),
        )
    }
    val latestItemsSeen = sources.maxOfOrNull { it.source.last_items_seen_count } ?: 0L
    val syncIntervalMinutes = sources
        .map { it.source.sync_interval_minutes }
        .filter { it > 0L }
        .minOrNull() ?: 360L
    return listOf(
        ExternalFavoriteSummaryMetric(sources.size.toString(), "已连接来源"),
        ExternalFavoriteSummaryMetric(latestItemsSeen.toString(), "上次看到收藏"),
        ExternalFavoriteSummaryMetric(externalFavoriteIntervalText(syncIntervalMinutes), "定期同步间隔"),
    )
}

fun externalFavoriteProviderBadge(provider: String): String = when (provider.lowercase()) {
    ExternalFavoriteProvider.X.id -> "X"
    else -> provider.take(1).uppercase().ifBlank { "?" }
}

fun externalFavoriteDeleteMenuLabel(): String = "删除"

fun externalFavoriteToggleSyncMenuLabel(enabled: Boolean): String =
    if (enabled) "停用同步" else "启用同步"

fun externalFavoriteSyncQueuedMessage(mode: FavoriteSyncMode): String? = when (mode) {
    FavoriteSyncMode.sync,
    FavoriteSyncMode.history,
    FavoriteSyncMode.full_rescan,
    FavoriteSyncMode.recent -> null
    FavoriteSyncMode.retry_failed -> "已开始重试失败项"
}

fun externalFavoriteReadOnlyStepLabel(): String = "只读"

fun externalFavoriteAccountIdentity(accountName: String, accountId: String): String =
    accountName.ifBlank { accountId }

fun externalFavoriteCanRunSyncAction(health: ExternalSourceHealth, enabled: Boolean): Boolean =
    enabled && health !in setOf(ExternalSourceHealth.paused, ExternalSourceHealth.needs_auth, ExternalSourceHealth.limited)

fun externalFavoritePendingDeleteSource(
    pendingDeleteSourceId: Long?,
    sources: List<ExternalFavoriteSourceUi>,
): ExternalFavoriteSourceUi? =
    pendingDeleteSourceId?.let { id -> sources.firstOrNull { it.id == id } }

fun externalFavoriteDeleteDialogTitle(): String = "删除外部收藏来源？"

fun externalFavoriteDeleteDialogText(): String =
    "这会删除该来源的授权信息和同步记录。已经导入的文章不会被删除。"

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

fun externalFavoritePeriodicSyncSubtitle(health: ExternalSourceHealth): String = when (health) {
    ExternalSourceHealth.needs_auth -> "需要重新授权后才能定期同步"
    ExternalSourceHealth.limited -> "平台限流中，稍后自动恢复定期同步"
    ExternalSourceHealth.paused -> "已停用，不会定期同步"
    ExternalSourceHealth.failing -> "最近同步失败，可手动重试"
    ExternalSourceHealth.never_synced -> "尚未同步，启用后将定期导入"
    ExternalSourceHealth.healthy -> "已启用定期同步"
}

fun externalFavoriteHealthLabel(health: ExternalSourceHealth): String = when (health) {
    ExternalSourceHealth.needs_auth -> "需要授权"
    ExternalSourceHealth.limited -> "限流中"
    ExternalSourceHealth.paused -> "已暂停"
    ExternalSourceHealth.failing -> "异常"
    ExternalSourceHealth.never_synced -> "未同步"
    ExternalSourceHealth.healthy -> "正常"
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

private fun externalFavoriteIntervalText(minutes: Long): String {
    if (minutes < 60L) return "${minutes}m"
    val hours = minutes / 60L
    val restMinutes = minutes % 60L
    return if (restMinutes == 0L) "${hours}h" else "${hours}h ${restMinutes}m"
}

private fun externalFavoriteReadableIntervalText(minutes: Long): String {
    if (minutes < 60L) return "${minutes} 分钟"
    val hours = minutes / 60L
    val restMinutes = minutes % 60L
    return if (restMinutes == 0L) "${hours} 小时" else "${hours} 小时 ${restMinutes} 分钟"
}
