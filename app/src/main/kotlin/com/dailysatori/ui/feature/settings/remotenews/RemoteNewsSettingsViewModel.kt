package com.dailysatori.ui.feature.settings.remotenews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.dailysatori.core.task.RemoteArticleSyncTaskHandler
import com.dailysatori.core.task.remoteArticleSyncTaskPayloadJson
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteFeedHistoryRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.diagnostics.TaskHttpRedaction
import com.dailysatori.service.remotenews.RemoteFeedHistoryPolicy
import com.dailysatori.service.remotenews.RemoteFeedHistoryState
import com.dailysatori.service.remotenews.RemoteFeedHistoryStatus
import com.dailysatori.service.remotenews.RemoteFeedHistoryStrategy
import com.dailysatori.service.remotenews.RemoteNewsConfigValues
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.shared.db.Async_task
import com.dailysatori.shared.db.Remote_news_source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

data class RemoteNewsDraft(
    val name: String = "",
    val baseUrl: String = "",
    val enabled: Boolean = true,
    /** First-import range of a new subscription; an existing subscription keeps its stored one. */
    val historyStrategy: RemoteFeedHistoryStrategy = RemoteFeedHistoryStrategy.ALL_HISTORY,
)

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
    /** Saved with older versions for private feeds; never edited in the form and kept as-is on save. */
    val legacyApiToken: String = "",
    val enabled: Boolean = true,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val isDeleting: Boolean = false,
    val savedDraft: RemoteNewsDraft = RemoteNewsDraft(),
    /** First-import range chosen in the editor; only a new subscription stores it. */
    val historyStrategy: RemoteFeedHistoryStrategy = RemoteFeedHistoryStrategy.ALL_HISTORY,
    /** Progress of the older-document walk, keyed by subscription. */
    val historyBySourceId: Map<Long, RemoteNewsHistoryUi> = emptyMap(),
) {
    val busy: Boolean get() = isSaving || isTesting || isDeleting
    val draft: RemoteNewsDraft get() = RemoteNewsDraft(name, baseUrl, enabled, historyStrategy)
    val hasChanges: Boolean get() = isEditing && draft != savedDraft
}

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
    private val historyRepo: RemoteFeedHistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteNewsSettingsState())
    val state: StateFlow<RemoteNewsSettingsState> = _state.asStateFlow()
    private val observedSyncKeys = mutableSetOf<String>()
    private val observedTaskIds = mutableSetOf<Long>()
    private val observedHistorySourceIds = mutableSetOf<Long>()

    init { load() }

    /** Optional display name: a blank draft falls back to the feed channel title, then the host. */
    fun updateName(value: String) = _state.update { if (it.busy) it else it.copy(name = value, message = null, isError = false) }

    fun updateBaseUrl(value: String) = _state.update { if (it.busy) it else it.copy(baseUrl = value, message = null, isError = false) }

    fun updateEnabled(value: Boolean) = _state.update { if (it.busy) it else it.copy(enabled = value, message = null, isError = false) }

    fun openAdd() = _state.update {
        it.copy(isEditing = true, editingId = null, name = "", baseUrl = "", legacyApiToken = "", enabled = true,
            historyStrategy = RemoteFeedHistoryStrategy.ALL_HISTORY,
            message = null, isError = false, savedDraft = RemoteNewsDraft())
    }

    /** Only a new subscription may choose its first-import range; editing never restarts a walk. */
    fun updateHistoryStrategy(value: RemoteFeedHistoryStrategy) =
        _state.update { if (it.busy || it.editingId != null) it else it.copy(historyStrategy = value, message = null, isError = false) }

    fun openEdit(source: Remote_news_source) = _state.update {
        it.copy(
            isEditing = true,
            editingId = source.id,
            name = source.name,
            baseUrl = source.base_url,
            legacyApiToken = source.api_token,
            enabled = source.enabled == 1L,
            message = null,
            isError = false,
            // The stored range of an existing subscription stays untouched by an edit.
            savedDraft = RemoteNewsDraft(source.name, source.base_url, source.enabled == 1L, it.historyStrategy),
        )
    }

    fun closeEditor() = _state.update {
        if (it.busy) it else it.copy(isEditing = false, editingId = null, name = "", baseUrl = "", legacyApiToken = "", enabled = true, message = null)
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copyRemoteNewsSyncCounts() }
            _state.value.sources.forEach { observeSource(it.id) }
        }
    }

    fun save() {
        if (state.value.busy || !state.value.hasChanges) return
        val form = state.value
        val editingId = form.editingId
        // The RSS address is stored exactly as typed: a feed URL may carry its own query and token.
        val baseUrl = form.baseUrl.trim()
        if (baseUrl.isBlank()) {
            _state.update { it.copy(message = "请填写 RSS 地址", isError = true) }
            return
        }
        // A typed name wins; only a blank name may adopt the feed's own channel title.
        val typedName = form.name.trim()
        val legacyApiToken = remoteNewsLegacyTokenFor(baseUrl, form.legacyApiToken, form.savedDraft.baseUrl)
        val enabled = form.enabled
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            _state.update { it.copy(message = "URL 必须以 http:// 或 https:// 开头", isError = true) }
            return
        }
        _state.update { it.copy(isSaving = true, message = null, isError = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = remoteNewsSavedName(typedName, baseUrl) {
                    remoteNewsEntryTitle(baseUrl, legacyApiToken)
                }
                // The range travels with a new subscription only; an edit keeps the stored policy.
                sourceRepo.save(editingId, name, baseUrl, legacyApiToken, enabled, form.historyStrategy.dbValue)
                _state.update {
                    it.copyRemoteNewsSyncCounts().copy(
                        isEditing = false,
                        editingId = null,
                        name = "",
                        baseUrl = "",
                        legacyApiToken = "",
                        enabled = true,
                        historyStrategy = RemoteFeedHistoryStrategy.ALL_HISTORY,
                        message = "远程新闻设置已保存",
                        isError = false,
                    )
                }
                // A brand-new source starts unobserved, so its walk state has no subscriber yet.
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(message = "远程新闻设置保存失败", isError = true) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Reads only the entry document for its channel title; paging/archive documents are not probed. */
    private suspend fun remoteNewsEntryTitle(baseUrl: String, legacyApiToken: String): String? {
        val config = remoteNewsService.configOrFailure(baseUrl, legacyApiToken)
        if (config !is RemoteNewsResult.Success) return null
        val page = remoteNewsService.fetchFeedPage(
            config.value,
            baseUrl,
            timeoutMillis = REMOTE_NEWS_TITLE_TIMEOUT_MS,
        )
        return (page as? RemoteNewsResult.Success)?.value?.title
    }

    fun deleteSource(id: Long) {
        if (_state.value.busy) return
        _state.update { it.copy(isDeleting = true, message = null, isError = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sourceRepo.delete(id)
                _state.update { current ->
                    val editingDeleted = current.editingId == id
                    current.copyRemoteNewsSyncCounts().copy(
                        isEditing = if (editingDeleted) false else current.isEditing,
                        editingId = if (editingDeleted) null else current.editingId,
                        name = if (editingDeleted) "" else current.name,
                        baseUrl = if (editingDeleted) "" else current.baseUrl,
                        legacyApiToken = if (editingDeleted) "" else current.legacyApiToken,
                        enabled = if (editingDeleted) true else current.enabled,
                        historyBySourceId = current.historyBySourceId - id,
                        message = "远程新闻已删除",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(message = "删除失败，请重试", isError = true) }
            } finally {
                _state.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun syncSource(id: Long) {
        if (remoteNewsHasActiveSync(_state.value)) {
            _state.update { it.copy(message = remoteNewsDuplicateSyncMessage()) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) { enqueueSourceSync(id) }
    }

    /**
     * Explicit history backfill: the walk is persisted first so the queued sync run finds its intent,
     * then the ordinary source sync task carries it. Nothing here rewrites stored articles or AI state.
     */
    fun startHistory(id: Long) = launchHistoryWalk(id, restart = true)

    /** Continues a paused or blocked walk from its stored cursor. */
    fun resumeHistory(id: Long) = launchHistoryWalk(id, restart = false)

    private fun launchHistoryWalk(id: Long, restart: Boolean) {
        if (remoteNewsHasActiveSync(_state.value)) {
            _state.update { it.copy(message = remoteNewsDuplicateSyncMessage()) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val accepted = try {
                if (restart) {
                    historyRepo.start(id, RemoteFeedHistoryStrategy.ALL_HISTORY, nowMillis())
                    true
                } else {
                    historyRepo.resume(id, nowMillis())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!accepted) {
                _state.update {
                    it.copy(
                        message = if (restart) "历史补全启动失败" else "当前没有可继续的历史补全",
                        isError = true,
                    )
                }
                return@launch
            }
            enqueueSourceSync(id)
        }
    }

    /**
     * Pauses the walk after the page in flight. It never deletes stored articles, read flags or AI
     * results, and a paused walk is not continued by the background task.
     */
    fun pauseHistory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val paused = try {
                historyRepo.pause(id, nowMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            _state.update {
                it.copy(
                    message = if (paused) "历史补全已暂停，当前分页结束后停止" else "当前没有进行中的历史补全",
                    isError = !paused,
                )
            }
        }
    }

    private suspend fun enqueueSourceSync(id: Long) {
        _state.update {
            it.copy(
                syncingSourceId = id,
                message = null,
                syncWorkBySourceId = it.syncWorkBySourceId + (id to remoteNewsQueuedSyncWork()),
            )
        }
        observeSource(id)
        try {
            sourceRepo.getById(id) ?: return
            val taskId = asyncTaskRepo.enqueue(
                type = AsyncTaskType.remote_article_sync.name,
                payloadJson = remoteArticleSyncTaskPayloadJson(
                    mode = RemoteArticleSyncTaskHandler.MODE_MANUAL_SOURCE,
                    sourceId = id,
                ),
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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

    fun cancelSync(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            asyncTaskRepo.cancelLatestByUniqueKey(remoteNewsSyncUniqueKey(id))?.let(asyncTaskScheduler::cancel)
            _state.update {
                it.copy(
                    syncingSourceId = if (it.syncingSourceId == id) null else it.syncingSourceId,
                    message = "已取消本次同步",
                )
            }
        }
    }

    fun testConnection() {
        if (state.value.busy) return
        val baseUrl = state.value.baseUrl.trim()
        val legacyApiToken = remoteNewsLegacyTokenFor(baseUrl, state.value.legacyApiToken, state.value.savedDraft.baseUrl)
        if (baseUrl.isBlank()) {
            _state.update { it.copy(message = "请先配置远程新闻 RSS 地址", isError = true) }
            return
        }
        _state.update { it.copy(isTesting = true, message = null, isError = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = remoteNewsService.configOrFailure(baseUrl, legacyApiToken)
                val message = when (config) {
                    is RemoteNewsResult.Failure -> config.message to true
                    // Read-only compatibility check: it detects RFC 5005 paging/archive support, verifies a
                    // bounded number of declared follow-up documents and never stores, queues or syncs anything.
                    is RemoteNewsResult.Success<RemoteNewsConfigValues> -> when (
                        val result = remoteNewsService.probeSourceFeed(config.value)
                    ) {
                        is RemoteNewsResult.Success -> result.value.message to false
                        is RemoteNewsResult.Failure -> result.message to true
                    }
                }
                _state.update { it.copy(message = message.first, isError = message.second) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(message = "无法连接远程新闻服务", isError = true) }
            } finally {
                _state.update { it.copy(isTesting = false) }
            }
        }
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

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

    /** A source added on this screen must be observed at once; a new id was never subscribed before. */
    private fun observeSource(sourceId: Long) {
        observeSyncWork(sourceId)
        observeHistory(sourceId)
    }

    private fun observeHistory(sourceId: Long) {
        if (!observedHistorySourceIds.add(sourceId)) return
        viewModelScope.launch {
            historyRepo.observe(sourceId).collect { stored ->
                val ui = remoteNewsHistoryUi(stored)
                _state.update { current -> current.copy(historyBySourceId = current.historyBySourceId + (sourceId to ui)) }
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

/**
 * Resolves the name saved with a subscription. A typed name always wins and never touches the network.
 * A blank draft adopts the entry document's channel title behind a short total cap; a slow, failed or
 * cancelled-before-title fetch still saves with the host fallback. Caller cancellation is rethrown.
 */
internal suspend fun remoteNewsSavedName(
    typedName: String,
    baseUrl: String,
    timeoutMillis: Long = REMOTE_NEWS_TITLE_TIMEOUT_MS,
    fetchFeedTitle: suspend () -> String?,
): String {
    val trimmed = typedName.trim()
    if (trimmed.isNotEmpty()) return remoteNewsSourceName(trimmed, baseUrl)
    val feedTitle = try {
        withTimeoutOrNull(timeoutMillis) { fetchFeedTitle() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    return remoteNewsSourceName(trimmed, baseUrl, feedTitle)
}

internal const val REMOTE_NEWS_TITLE_TIMEOUT_MS = 5_000L

private val remoteNewsFinishedWorkStates = setOf(
    WorkInfo.State.SUCCEEDED,
    WorkInfo.State.FAILED,
    WorkInfo.State.CANCELLED,
)

/**
 * A source name only has to be human readable: a name the user typed always wins, then the feed's own
 * channel title, and only then the feed host so a single URL field is enough.
 */
internal fun remoteNewsSourceName(existingName: String, url: String, feedTitle: String? = null): String =
    existingName.trim()
        .ifBlank { feedTitle?.trim().orEmpty() }
        .ifBlank { remoteNewsSourceNameFromUrl(url) }

internal fun remoteNewsSourceNameFromUrl(url: String): String {
    val trimmed = url.trim()
    val authority = trimmed.substringAfter("://", trimmed)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val host = authority.substringAfter('@').substringBefore(':')
    return host.ifBlank { DEFAULT_REMOTE_NEWS_SOURCE_NAME }
}

/**
 * Old records may still carry a separately saved token. It is kept only for the exact origin the
 * feed was saved with: switching host, scheme or port must never forward that credential to a new
 * destination, so the token is dropped instead of being rebound.
 */
internal fun remoteNewsLegacyTokenFor(url: String, legacyToken: String, savedUrl: String): String {
    val token = legacyToken.trim()
    if (token.isEmpty()) return ""
    val origin = remoteNewsUrlOrigin(url) ?: return ""
    return token.takeIf { origin == remoteNewsUrlOrigin(savedUrl) }.orEmpty()
}

/** Canonical `scheme://host:port` origin, or null when the address is not a usable http(s) URL. */
internal fun remoteNewsUrlOrigin(url: String): String? {
    val trimmed = url.trim()
    val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
    if (scheme != "http" && scheme != "https") return null
    val authority = trimmed.substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    if (authority.isBlank()) return null
    val hostPort = authority.substringAfterLast('@')
    val host: String
    val portText: String
    if (hostPort.startsWith("[")) {
        val end = hostPort.indexOf(']')
        if (end <= 0) return null
        host = hostPort.substring(0, end + 1).lowercase()
        portText = hostPort.substring(end + 1).removePrefix(":")
    } else {
        host = hostPort.substringBefore(':').lowercase().trimEnd('.')
        portText = hostPort.substringAfter(':', "")
    }
    if (host.isBlank() || host.any { it.isWhitespace() }) return null
    val defaultPort = if (scheme == "https") "443" else "80"
    val port = when {
        portText.isEmpty() -> defaultPort
        portText.length <= 5 && portText.all(Char::isDigit) && portText.toInt() in 1..65535 -> portText.toInt().toString()
        else -> return null
    }
    return "$scheme://$host:$port"
}

/** The stored URL may carry credentials in its query; lists only ever show the masked form. */
internal fun remoteNewsDisplayUrl(url: String): String = TaskHttpRedaction.url(url)

private const val DEFAULT_REMOTE_NEWS_SOURCE_NAME = "远程新闻"

internal fun remoteNewsSyncUniqueKey(sourceId: Long): String =
    "remote_article_sync:source:$sourceId"

internal fun remoteNewsHasActiveSync(state: RemoteNewsSettingsState): Boolean =
    state.syncingSourceId != null || state.syncWorkBySourceId.values.any { it.active }

internal fun remoteNewsDuplicateSyncMessage(): String =
    "远程新闻同步任务正在执行，请稍后再试"

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

/** Compact, per-subscription view of the older-document walk. Unknown totals never become a percentage. */
data class RemoteNewsHistoryUi(
    val strategy: RemoteFeedHistoryStrategy = RemoteFeedHistoryStrategy.ALL_HISTORY,
    val status: RemoteFeedHistoryStatus = RemoteFeedHistoryStatus.OFF,
    val importedCount: Int = 0,
    val targetCount: Int = 0,
    val pagesRead: Int = 0,
    val lastError: String = "",
    val blockedReason: String = "",
) {
    /** A zero target means "the whole chain", so no progress fraction can be promised. */
    val target: Int? get() = targetCount.takeIf { it > 0 }
}

enum class RemoteNewsHistoryAction { NONE, START, PAUSE, RESUME }

internal fun remoteNewsHistoryUi(state: RemoteFeedHistoryState?): RemoteNewsHistoryUi =
    if (state == null) {
        RemoteNewsHistoryUi()
    } else {
        RemoteNewsHistoryUi(
            strategy = state.strategy,
            status = state.status,
            importedCount = state.importedCount,
            targetCount = state.targetCount,
            pagesRead = state.pagesRead,
            lastError = state.lastError,
            blockedReason = state.blockedReason,
        )
    }

fun remoteNewsHistoryRangeText(strategy: RemoteFeedHistoryStrategy): String = when (strategy) {
    RemoteFeedHistoryStrategy.ALL_HISTORY -> "全部历史"
    RemoteFeedHistoryStrategy.LATEST_500 -> "最近 500 条"
}

/** Stored first-import range of a subscription; a stored none keeps it incremental only. */
fun remoteNewsSourceHistoryRangeText(source: Remote_news_source): String =
    RemoteFeedHistoryPolicy.strategyOf(source.history_policy)?.let(::remoteNewsHistoryRangeText) ?: "仅增量"

fun remoteNewsHistoryStatusText(ui: RemoteNewsHistoryUi?): String = when (ui?.status ?: RemoteFeedHistoryStatus.OFF) {
    RemoteFeedHistoryStatus.OFF -> "未补全历史"
    RemoteFeedHistoryStatus.PENDING -> "等待补全"
    RemoteFeedHistoryStatus.RUNNING -> "补全中"
    RemoteFeedHistoryStatus.PAUSED -> "已暂停"
    RemoteFeedHistoryStatus.BLOCKED -> "补全受阻"
    // A single-window subscription never claims full history.
    RemoteFeedHistoryStatus.COMPLETE_WINDOW -> "仅近期窗口"
    RemoteFeedHistoryStatus.COMPLETE -> when (ui?.strategy ?: RemoteFeedHistoryStrategy.ALL_HISTORY) {
        RemoteFeedHistoryStrategy.LATEST_500 -> "最近 500 条已导入"
        RemoteFeedHistoryStrategy.ALL_HISTORY -> "全部历史已导入"
    }
}

fun remoteNewsHistoryProgressText(ui: RemoteNewsHistoryUi?): String {
    if (ui == null || ui.status == RemoteFeedHistoryStatus.OFF) return ""
    val imported = ui.importedCount.coerceAtLeast(0)
    if (imported <= 0 && ui.pagesRead <= 0) return ""
    // Factual wording: the counter is what this walk stored (a restart resets it), never a total.
    val count = ui.target?.let { "本次新增 $imported/$it 篇" } ?: "本次新增 $imported 篇"
    return if (ui.pagesRead > 0) "$count · 已读取 ${ui.pagesRead} 页" else count
}

fun remoteNewsHistoryErrorText(ui: RemoteNewsHistoryUi?): String? =
    ui?.lastError?.trim().orEmpty()
        .ifBlank { ui?.blockedReason?.trim().orEmpty() }
        .ifBlank { null }

fun remoteNewsHistoryAction(ui: RemoteNewsHistoryUi?, sourceEnabled: Boolean): RemoteNewsHistoryAction {
    val status = ui?.status ?: RemoteFeedHistoryStatus.OFF
    // An open walk stays pausable even when the subscription itself is disabled.
    if (status.open) return RemoteNewsHistoryAction.PAUSE
    if (!sourceEnabled) return RemoteNewsHistoryAction.NONE
    return when (status) {
        RemoteFeedHistoryStatus.PAUSED, RemoteFeedHistoryStatus.BLOCKED -> RemoteNewsHistoryAction.RESUME
        RemoteFeedHistoryStatus.OFF,
        RemoteFeedHistoryStatus.COMPLETE,
        RemoteFeedHistoryStatus.COMPLETE_WINDOW,
        -> RemoteNewsHistoryAction.START
        else -> RemoteNewsHistoryAction.NONE
    }
}

fun remoteNewsHistoryActionLabel(action: RemoteNewsHistoryAction, ui: RemoteNewsHistoryUi?): String = when (action) {
    RemoteNewsHistoryAction.NONE -> ""
    RemoteNewsHistoryAction.START -> if (ui?.status?.finished == true) "重新补全" else "补全历史"
    RemoteNewsHistoryAction.PAUSE -> "暂停"
    RemoteNewsHistoryAction.RESUME -> "继续"
}

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

