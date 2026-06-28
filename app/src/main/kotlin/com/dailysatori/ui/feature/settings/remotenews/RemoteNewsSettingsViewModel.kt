package com.dailysatori.ui.feature.settings.remotenews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.dailysatori.core.task.remoteArticleSyncTaskPayloadJson
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.remotenews.RemoteNewsConfigValues
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.service.remotenews.normalizeTopArticlesTodayUrl
import com.dailysatori.shared.db.Async_task
import com.dailysatori.shared.db.Remote_news_source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class RemoteNewsSettingsState(
    val sources: List<Remote_news_source> = emptyList(),
    val syncedArticleCount: Long = 0,
    val syncedArticleCountBySourceId: Map<Long, Long> = emptyMap(),
    val syncingSourceId: Long? = null,
    val syncWorkBySourceId: Map<Long, RemoteNewsSyncWorkUi> = emptyMap(),
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

data class RemoteNewsSyncWorkUi(
    val taskId: Long? = null,
    val createdAt: Long? = null,
    val state: WorkInfo.State,
    val current: Int,
    val total: Int,
    val message: String,
    val inserted: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
) {
    val active: Boolean get() = state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
}

class RemoteNewsSettingsViewModel(
    private val sourceRepo: RemoteNewsSourceRepository,
    private val syncRepo: RemoteArticleSyncRepository,
    private val asyncTaskRepo: AsyncTaskRepository,
    private val asyncTaskScheduler: AsyncTaskScheduler,
    private val remoteNewsService: RemoteNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteNewsSettingsState())
    val state: StateFlow<RemoteNewsSettingsState> = _state.asStateFlow()
    private val observedSyncKeys = mutableSetOf<String>()
    private val observedTaskIds = mutableSetOf<Long>()

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
            _state.update { it.copyRemoteNewsSyncCounts() }
            _state.value.sources.forEach { observeSyncWork(it.id) }
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
                    it.copyRemoteNewsSyncCounts().copy(
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
                current.copyRemoteNewsSyncCounts().copy(
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

    fun syncSource(id: Long) {
        if (_state.value.syncingSourceId != null) return
        _state.update {
            it.copy(
                syncingSourceId = id,
                message = null,
                syncWorkBySourceId = it.syncWorkBySourceId + (id to remoteNewsQueuedSyncWork()),
            )
        }
        observeSyncWork(id)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = sourceRepo.getById(id) ?: return@launch
                val taskId = asyncTaskRepo.enqueue(
                    type = AsyncTaskType.remote_article_sync.name,
                    payloadJson = remoteArticleSyncTaskPayloadJson(mode = "manual_source", sourceId = id),
                    uniqueKey = "remote_article_sync:source:$id",
                )
                asyncTaskScheduler.enqueue(taskId)
                observeExactSyncTask(id, taskId)
                _state.update {
                    val current = it.syncWorkBySourceId[id] ?: remoteNewsQueuedSyncWork()
                    it.copyRemoteNewsSyncCounts().copy(
                        syncingSourceId = id,
                        message = null,
                        syncWorkBySourceId = it.syncWorkBySourceId + (id to current.copy(taskId = taskId)),
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        syncingSourceId = null,
                        syncWorkBySourceId = it.syncWorkBySourceId - id,
                        message = "远程新闻同步任务创建失败",
                    )
                }
            }
        }
    }

    fun cancelSync(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            asyncTaskRepo.cancelLatestByUniqueKey(remoteNewsSyncUniqueKey(id))
            _state.update {
                it.copy(
                    syncingSourceId = if (it.syncingSourceId == id) null else it.syncingSourceId,
                    message = "已取消本次同步",
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

    private fun RemoteNewsSettingsState.copyRemoteNewsSyncCounts(): RemoteNewsSettingsState {
        val loadedSources = sourceRepo.getAll()
        return copy(
            sources = loadedSources,
            syncedArticleCount = syncRepo.count(),
            syncedArticleCountBySourceId = remoteNewsSyncedArticleCountBySourceId(loadedSources, syncRepo),
        )
    }

    private fun observeSyncWork(sourceId: Long) {
        val key = remoteNewsSyncUniqueKey(sourceId)
        if (!observedSyncKeys.add(key)) return
        viewModelScope.launch {
            asyncTaskRepo.observeLatestByUniqueKey(key).collect { task ->
                val workUi = task?.let(::remoteNewsSyncWorkFromAsyncTask)
                applySyncWorkState(sourceId, workUi)
                if (workUi?.state in remoteNewsFinishedWorkStates) load()
            }
        }
    }

    private fun observeExactSyncTask(sourceId: Long, taskId: Long) {
        if (!observedTaskIds.add(taskId)) return
        viewModelScope.launch {
            asyncTaskRepo.observeTaskById(taskId).collect { task ->
                val workUi = task?.let(::remoteNewsSyncWorkFromAsyncTask)
                applySyncWorkState(sourceId, workUi)
                if (workUi?.state in remoteNewsFinishedWorkStates) load()
            }
        }
    }

    private fun applySyncWorkState(sourceId: Long, workUi: RemoteNewsSyncWorkUi?) {
        _state.update { state -> remoteNewsApplySyncWorkState(state, sourceId, workUi) }
    }
}

private val remoteNewsFinishedWorkStates = setOf(
    WorkInfo.State.SUCCEEDED,
    WorkInfo.State.FAILED,
    WorkInfo.State.CANCELLED,
)

internal fun remoteNewsSyncUniqueKey(sourceId: Long): String =
    "remote_article_sync:source:$sourceId"

internal fun remoteNewsApplySyncWorkState(
    state: RemoteNewsSettingsState,
    sourceId: Long,
    workUi: RemoteNewsSyncWorkUi?,
): RemoteNewsSettingsState {
    val currentWork = state.syncWorkBySourceId[sourceId]
    val staleFinishedTask = workUi?.state in remoteNewsFinishedWorkStates &&
        currentWork?.active == true &&
        when {
            currentWork.taskId == null -> true
            currentWork.taskId != workUi?.taskId -> true
            currentWork.createdAt != null && workUi?.createdAt != null -> workUi.createdAt < currentWork.createdAt
            else -> false
        }
    val nextMap = when {
        workUi == null && currentWork?.active == true -> state.syncWorkBySourceId
        workUi == null -> state.syncWorkBySourceId - sourceId
        staleFinishedTask -> state.syncWorkBySourceId
        workUi.state in remoteNewsFinishedWorkStates -> state.syncWorkBySourceId - sourceId
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

private fun remoteNewsQueuedSyncWork(): RemoteNewsSyncWorkUi =
    RemoteNewsSyncWorkUi(
        createdAt = Clock.System.now().toEpochMilliseconds(),
        state = WorkInfo.State.ENQUEUED,
        current = 0,
        total = 1,
        message = "等待同步",
    )

fun remoteNewsSyncWorkFromAsyncTask(task: Async_task): RemoteNewsSyncWorkUi? {
    val status = AsyncTaskStatus.entries.firstOrNull { it.name == task.status } ?: return null
    return RemoteNewsSyncWorkUi(
        taskId = task.id,
        createdAt = task.created_at,
        state = status.toRemoteNewsWorkInfoState(),
        current = task.progress_current.toInt(),
        total = task.progress_total.toInt().coerceAtLeast(1),
        message = task.progress_message,
        inserted = remoteNewsTaskCheckpointInt(task.checkpoint_json, "inserted") ?: 0,
        updated = remoteNewsTaskCheckpointInt(task.checkpoint_json, "updated") ?: 0,
        skipped = remoteNewsTaskCheckpointInt(task.checkpoint_json, "skipped") ?: 0,
    )
}

private fun AsyncTaskStatus.toRemoteNewsWorkInfoState(): WorkInfo.State = when (this) {
    AsyncTaskStatus.queued,
    AsyncTaskStatus.retrying -> WorkInfo.State.ENQUEUED
    AsyncTaskStatus.running -> WorkInfo.State.RUNNING
    AsyncTaskStatus.succeeded -> WorkInfo.State.SUCCEEDED
    AsyncTaskStatus.failed -> WorkInfo.State.FAILED
    AsyncTaskStatus.cancelled -> WorkInfo.State.CANCELLED
}

private fun remoteNewsTaskCheckpointInt(json: String, key: String): Int? =
    Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)""")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

private fun remoteNewsSyncedArticleCountBySourceId(
    sources: List<Remote_news_source>,
    syncRepo: RemoteArticleSyncRepository,
): Map<Long, Long> =
    sources.associate { source -> source.id to syncRepo.countBySource(source.id) }

data class RemoteNewsSummaryMetric(
    val value: String,
    val label: String,
)

fun remoteNewsSummaryMetrics(state: RemoteNewsSettingsState): List<RemoteNewsSummaryMetric> = listOf(
    RemoteNewsSummaryMetric(state.sources.size.toString(), "已连接来源"),
    RemoteNewsSummaryMetric(state.syncedArticleCount.coerceAtLeast(0).toString(), "一共同步"),
)

fun remoteNewsSummarySubtitle(state: RemoteNewsSettingsState): String =
    if (state.sources.isEmpty()) {
        "尚未连接远程新闻源"
    } else {
        "已同步 ${state.syncedArticleCount.coerceAtLeast(0)} 篇远程文章"
    }

fun remoteNewsSourceSyncedCountText(source: Remote_news_source, state: RemoteNewsSettingsState): String =
    "一共同步 ${state.syncedArticleCountBySourceId[source.id]?.coerceAtLeast(0) ?: 0} 条"

fun remoteNewsSyncActionLabel(work: RemoteNewsSyncWorkUi?): String =
    if (work?.active == true) "取消同步" else "同步"

fun remoteNewsSyncActionEnabled(source: Remote_news_source, work: RemoteNewsSyncWorkUi?): Boolean =
    work?.active == true || source.enabled == 1L

fun remoteNewsEffectiveStatusLabel(source: Remote_news_source, work: RemoteNewsSyncWorkUi?): String =
    if (work?.active == true) "同步中" else if (source.enabled == 1L) "已启用" else "已停用"

fun remoteNewsSyncProgressTitle(work: RemoteNewsSyncWorkUi): String = when (work.state) {
    WorkInfo.State.ENQUEUED -> "等待同步"
    WorkInfo.State.RUNNING -> work.message.takeIf { it.isNotBlank() } ?: "正在同步远程文章"
    else -> "同步结束"
}

fun remoteNewsSyncProgressText(work: RemoteNewsSyncWorkUi): String =
    "${work.current.coerceAtLeast(0)} / ${work.total.coerceAtLeast(1)} 个来源"

fun remoteNewsSyncProgressFraction(work: RemoteNewsSyncWorkUi): Float =
    (work.current.toFloat() / work.total.coerceAtLeast(1)).coerceIn(0.04f, 1f)

fun remoteNewsProgressMetrics(work: RemoteNewsSyncWorkUi): List<RemoteNewsSummaryMetric> = listOf(
    RemoteNewsSummaryMetric(work.inserted.coerceAtLeast(0).toString(), "新增"),
    RemoteNewsSummaryMetric(work.updated.coerceAtLeast(0).toString(), "更新"),
    RemoteNewsSummaryMetric(work.skipped.coerceAtLeast(0).toString(), "跳过"),
)

fun remoteNewsRunningDetailLines(work: RemoteNewsSyncWorkUi): List<RemoteNewsSummaryMetric> = listOf(
    RemoteNewsSummaryMetric(remoteNewsSyncProgressTitle(work), "当前阶段"),
    RemoteNewsSummaryMetric(remoteNewsSyncProgressText(work), "同步进度"),
)
