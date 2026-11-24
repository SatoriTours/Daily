package com.dailysatori.ui.feature.settings.taskcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.data.repository.AsyncTaskCenterPage
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.service.asynctask.AsyncTaskOverview
import com.dailysatori.service.asynctask.RECENT_TASK_FAILURE_WINDOW_MS
import com.dailysatori.service.asynctask.recentFailedTaskFilter
import com.dailysatori.service.asynctask.recentTaskFailureCutoffs
import com.dailysatori.service.articleai.ArticleAiQueue
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.shared.db.Async_task
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.service.diagnostics.TaskHttpCapture
import com.dailysatori.service.diagnostics.TaskHttpCaptureState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import com.dailysatori.service.asynctask.AsyncTaskStatus

internal sealed interface TaskCenterBulkCancelState {
    data object Idle : TaskCenterBulkCancelState
    data object Loading : TaskCenterBulkCancelState
    data object Cancelling : TaskCenterBulkCancelState
    data object Empty : TaskCenterBulkCancelState
    data object Failed : TaskCenterBulkCancelState
    data class Confirm(
        val ids: Set<Long>,
        val queued: Int,
        val running: Int,
        val retrying: Int,
    ) : TaskCenterBulkCancelState
}

data class TaskCenterState(
    val types: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val showTerminal: Boolean = true,
    val updatedSince: Long? = null,
    val tasks: List<AsyncTaskListItem> = emptyList(),
    val hasMore: Boolean = false,
    val loadedCount: Int = 0,
    val tasksLoaded: Boolean = false,
    val requestedLimit: Int = DEFAULT_TASK_CENTER_PAGE_SIZE,
    val selectedTask: Async_task? = null,
    val taskLog: String = "",
    val selectedFailureSuperseded: Boolean = false,
    val attentionTasks: List<AsyncTaskListItem> = emptyList(),
    val attentionOnly: Boolean = false,
    val sourceNames: TaskCenterSourceNames = TaskCenterSourceNames(),
    val assistantResumeBlocked: Boolean = false,
    /** Schema1 capture file for the selected task; empty when capture was never enabled. */
    val httpLog: String = "",
    val captureState: TaskHttpCaptureState = TaskHttpCaptureState.Off,
    val tab: TaskCenterTab = TaskCenterTab.Current,
    /** Real repository totals; null means "unknown", never the size of the loaded page. */
    val activeTotal: Long? = null,
    val pendingTotal: Long? = null,
    val filtersExpanded: Boolean = false,
)

private data class SelectedTaskState(
    val task: Async_task?,
    val log: String,
    val failureSuperseded: Boolean,
    val httpLog: String = "",
    val captureState: TaskHttpCaptureState = TaskHttpCaptureState.Off,
)

internal fun taskFailureIsSuperseded(
    selectedId: Long,
    selectedStatus: String,
    latestId: Long?,
    latestStatus: String?,
): Boolean =
    selectedStatus in setOf(AsyncTaskStatus.failed.name, AsyncTaskStatus.cancelled.name) &&
        latestId != null &&
        latestId != selectedId &&
        latestStatus == AsyncTaskStatus.succeeded.name

internal const val TASK_CENTER_NO_MATCH_STATUS = "__task_center_no_match__"

/** Only replaying a whole task is safe: these handlers never duplicate user-visible side effects. */
private val taskCenterRetryableTypes = setOf(
    AsyncTaskType.article_ai.name,
    AsyncTaskType.remote_news_fetch.name,
    AsyncTaskType.remote_article_sync.name,
    AsyncTaskType.news_opportunity_analysis.name,
    AsyncTaskType.save_article.name,
    AsyncTaskType.remote_article_reprocess.name,
    AsyncTaskType.article_cover_download.name,
    AsyncTaskType.book_viewpoint_generate.name,
)

internal fun taskCenterCanRetry(task: Async_task, failureSuperseded: Boolean): Boolean =
    !failureSuperseded &&
        task.status in setOf(AsyncTaskStatus.failed.name, AsyncTaskStatus.cancelled.name) &&
        task.type in taskCenterRetryableTypes

private data class TaskCenterQuery(
    val effectiveFilter: AsyncTaskFilter,
    val userFilter: AsyncTaskFilter,
    val limit: Int,
    val tab: TaskCenterTab,
    val filtersExpanded: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCenterViewModel(
    private val repository: AsyncTaskRepository,
    private val logStore: AsyncTaskLogStore,
    private val scheduler: AsyncTaskScheduler,
    remoteSourceRepository: RemoteNewsSourceRepository,
    favoriteSourceRepository: ExternalFavoriteSourceRepository,
    private val articleAiQueue: ArticleAiQueue? = null,
) : ViewModel() {
    private val bulkCancelState = MutableStateFlow<TaskCenterBulkCancelState>(TaskCenterBulkCancelState.Idle)
    internal val bulkCancel = bulkCancelState.asStateFlow()

    private val sourceNames = remoteSourceRepository.observeAll()
        .combine(favoriteSourceRepository.observeAll()) { remote, favorites ->
            TaskCenterSourceNames(
                remote = remote.associate { it.id to it.name.ifBlank { "新闻源 #${it.id}" } },
                enabledRemote = remote.filter { it.enabled == 1L }
                    .associate { it.id to it.name.ifBlank { "新闻源 #${it.id}" } },
                favorites = favorites.associate {
                    it.id to taskCenterFavoriteSourceName(it.provider, it.display_name, it.account_name)
                },
            )
        }
    private val filter = MutableStateFlow(AsyncTaskFilter())
    private var entryFilterApplied = false
    private val pageLimit = MutableStateFlow(DEFAULT_TASK_CENTER_PAGE_SIZE)
    private val tab = MutableStateFlow(TaskCenterTab.Current)
    private val filtersExpanded = MutableStateFlow(false)
    private val selectedTaskId = MutableStateFlow<Long?>(null)
    private val assistantResumeBlocked = MutableStateFlow(false)
    /** Re-read of the capture session; bumped on arm/disarm so the UI shows the real state. */
    private val captureTick = MutableStateFlow(0)
    private val selected = selectedTaskId.flatMapLatest { id ->
        if (id == null) {
            flowOf(SelectedTaskState(null, "", false))
        } else {
            repository.observeTaskById(id)
                .combine(logStore.observe(id)) { task, log -> task to log }
                .combine(logStore.observeHttp(id)) { (task, log), http -> Triple(task, log, http) }
                .map { (task, log, http) ->
                    val latest = task?.unique_key?.let(repository::getLatestByUniqueKey)
                    SelectedTaskState(
                        task = task,
                        log = log,
                        failureSuperseded = task?.let {
                            taskFailureIsSuperseded(it.id, it.status, latest?.id, latest?.status)
                        } == true,
                        httpLog = http,
                        captureState = task?.let { TaskHttpCapture.state(it.id) } ?: TaskHttpCaptureState.Off,
                    )
                }
        }
    }

    /**
     * The tab decides which statuses are in play; a user status filter narrows that set instead of
     * overriding it, so switching tabs can never silently show statuses the tab excludes.
     */
    private val effectiveFilter = combine(filter, tab, recentTaskFailureCutoffs()) { current, currentTab, cutoff ->
        val tabStatuses = taskCenterTabStatuses(currentTab)
        val statuses = if (current.statuses.isEmpty()) {
            tabStatuses
        } else {
            current.statuses.intersect(tabStatuses).ifEmpty { setOf(TASK_CENTER_NO_MATCH_STATUS) }
        }
        AsyncTaskFilter(
            types = current.types,
            statuses = statuses,
            showTerminal = true,
            updatedSince = current.updatedSince?.let { cutoff },
        )
    }.distinctUntilChanged()

    val state: StateFlow<TaskCenterState> = combine(
        effectiveFilter,
        filter,
        pageLimit,
        tab,
        filtersExpanded,
    ) { effective, user, limit, currentTab, expanded ->
        TaskCenterQuery(effective, user, limit, currentTab, expanded)
    }
        .flatMapLatest { query ->
            val attention = recentTaskFailureCutoffs().flatMapLatest(repository::observeTasksNeedingAttention)
            val overview = recentTaskFailureCutoffs().flatMapLatest(repository::observeTaskOverview)
            val page = if (query.tab == TaskCenterTab.Pending) {
                attention.map { tasks ->
                    AsyncTaskCenterPage(tasks.take(query.limit), tasks.take(query.limit).size, query.limit)
                }
            } else {
                repository.observeTaskCenter(query.effectiveFilter, query.limit)
            }
            combine(page, attention, overview) { taskPage, attentionTasks, overviewCounts ->
                Triple(taskPage, attentionTasks, overviewCounts)
            }.combine(selected) { (taskPage, attentionTasks, overviewCounts), selectedTask ->
                TaskCenterState(
                    types = query.userFilter.types,
                    statuses = query.userFilter.statuses,
                    showTerminal = true,
                    updatedSince = query.userFilter.updatedSince,
                    tasks = taskPage.tasks,
                    hasMore = if (query.tab == TaskCenterTab.Pending) {
                        attentionTasks.size > query.limit
                    } else {
                        taskPage.hasMore
                    },
                    loadedCount = taskPage.tasks.size,
                    tasksLoaded = true,
                    requestedLimit = taskPage.requestedLimit,
                    selectedTask = selectedTask.task,
                    taskLog = selectedTask.log,
                    httpLog = selectedTask.httpLog,
                    captureState = selectedTask.captureState,
                    selectedFailureSuperseded = selectedTask.failureSuperseded,
                    attentionTasks = attentionTasks,
                    attentionOnly = query.tab == TaskCenterTab.Pending,
                    tab = query.tab,
                    activeTotal = overviewCounts.activeCount,
                    pendingTotal = attentionTasks.size.toLong(),
                    filtersExpanded = query.filtersExpanded,
                )
            }
        }
        .combine(sourceNames) { state, names -> state.copy(sourceNames = names) }
        .combine(assistantResumeBlocked) { state, blocked -> state.copy(assistantResumeBlocked = blocked) }
        .combine(captureTick) { state, _ ->
            state.copy(
                captureState = state.selectedTask?.let { TaskHttpCapture.state(it.id) } ?: TaskHttpCaptureState.Off,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskCenterState())

    fun applyEntryFilter(recentFailures: Boolean, initialType: String? = null) {
        if (entryFilterApplied) return
        entryFilterApplied = true
        if (recentFailures) {
            resetPaging()
            tab.value = TaskCenterTab.Pending
            filter.value = recentFailedTaskFilter(Clock.System.now().toEpochMilliseconds())
        } else if (initialType != null) {
            resetPaging()
            tab.value = TaskCenterTab.Current
            filter.value = AsyncTaskFilter(types = setOf(initialType))
        }
    }

    fun selectTab(value: TaskCenterTab) {
        if (tab.value == value) return
        resetPaging()
        tab.value = value
        filter.update { it.copy(updatedSince = null) }
    }

    fun toggleFilters() {
        filtersExpanded.update { !it }
    }

    fun showAllTasks() {
        resetPaging()
        tab.value = TaskCenterTab.Current
        filter.value = AsyncTaskFilter()
    }

    fun showAttentionTasks() {
        resetPaging()
        filter.value = AsyncTaskFilter()
        tab.value = TaskCenterTab.Pending
    }

    fun acknowledgeFailure(taskId: Long, updatedAt: Long) {
        viewModelScope.launch(Dispatchers.IO) { repository.acknowledgeFailure(taskId, updatedAt) }
    }

    fun toggleType(type: String) {
        resetPaging()
        filter.update {
            it.copy(types = if (type in it.types) it.types - type else it.types + type)
        }
    }

    fun clearTypes() {
        resetPaging()
        filter.update { it.copy(types = emptySet()) }
    }

    fun toggleStatus(status: String) {
        resetPaging()
        filter.update {
            it.copy(statuses = if (status in it.statuses) it.statuses - status else it.statuses + status)
        }
    }

    fun clearStatuses() {
        resetPaging()
        filter.update { it.copy(statuses = emptySet()) }
    }

    fun setShowTerminal(show: Boolean) {
        resetPaging()
        filter.update { it.copy(showTerminal = show) }
    }

    fun loadMore() {
        pageLimit.update { it + DEFAULT_TASK_CENTER_PAGE_SIZE }
    }

    fun cancel(taskId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduler.cancel(taskId)
        }
    }

    fun requestBulkCancel() {
        if (!bulkCancelState.compareAndSet(TaskCenterBulkCancelState.Idle, TaskCenterBulkCancelState.Loading)) return
        runBulkCancelAction {
            val snapshot = repository.activeCancellationSnapshot()
            if (snapshot.isEmpty()) TaskCenterBulkCancelState.Empty else TaskCenterBulkCancelState.Confirm(
                ids = snapshot.mapTo(hashSetOf()) { it.id },
                queued = snapshot.count { it.status == AsyncTaskStatus.queued.name },
                running = snapshot.count { it.status == AsyncTaskStatus.running.name },
                retrying = snapshot.count { it.status == AsyncTaskStatus.retrying.name },
            )
        }
    }

    fun dismissBulkCancel() {
        val current = bulkCancelState.value
        when (current) {
            is TaskCenterBulkCancelState.Confirm, TaskCenterBulkCancelState.Empty, TaskCenterBulkCancelState.Failed ->
                bulkCancelState.compareAndSet(current, TaskCenterBulkCancelState.Idle)
            else -> Unit
        }
    }

    fun confirmBulkCancel() {
        val snapshot = bulkCancelState.value as? TaskCenterBulkCancelState.Confirm ?: return
        if (!bulkCancelState.compareAndSet(snapshot, TaskCenterBulkCancelState.Cancelling)) return
        runBulkCancelAction {
            scheduler.cancelSnapshot(snapshot.ids)
            TaskCenterBulkCancelState.Idle
        }
    }

    private fun runBulkCancelAction(action: () -> TaskCenterBulkCancelState) {
        viewModelScope.launch(Dispatchers.IO) {
            bulkCancelState.value = try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                TaskCenterBulkCancelState.Failed
            }
        }
    }

    fun resumeAssistantBackground(taskId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val resumed = repository.resumeAssistantBackground(taskId)
            assistantResumeBlocked.value = !resumed
            if (resumed) runCatching { scheduler.enqueue(taskId) }
        }
    }

    /** Enables capture for one task only; the session expires itself and never survives a restart. */
    fun armCapture(taskId: Long, label: String) {
        TaskHttpCapture.arm(taskId, label)
        captureTick.update { it + 1 }
    }

    fun disarmCapture(taskId: Long) {
        TaskHttpCapture.disarm(taskId)
        captureTick.update { it + 1 }
    }

    /** Re-runs one failed or cancelled task as a fresh row; the old terminal row stays as history. */
    fun retryTask(task: Async_task) {
        if (!taskCenterCanRetry(task, false)) return
        viewModelScope.launch(Dispatchers.IO) { retryTaskNow(task) }
    }

    /**
     * The caller may hold a stale detail-page snapshot, so the durable row is re-read before replay:
     * a task that a newer same-key success has already replaced must never be queued again.
     */
    internal fun retryTaskNow(task: Async_task) {
        if (!taskCenterCanRetry(task, false)) return
        val current = repository.getById(task.id) ?: return
        val latest = current.unique_key?.let(repository::getLatestByUniqueKey)
        val superseded = taskFailureIsSuperseded(current.id, current.status, latest?.id, latest?.status)
        if (!taskCenterCanRetry(current, superseded)) return
        val newId = try {
            if (current.type == AsyncTaskType.article_ai.name) retryArticleAi(current) else reenqueue(current)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (newId != null) {
            runCatching { scheduler.enqueue(newId) }
            openTask(newId)
        }
    }

    /** Article AI work is owned by its own queue, so restart it there instead of duplicating a job row. */
    private fun retryArticleAi(task: Async_task): Long? {
        val queue = articleAiQueue ?: return reenqueue(task)
        val articleId = taskCenterArticleId(task.payload_json)
        if (articleId == null) queue.resume() else queue.manual(articleId)
        return null
    }

    private fun reenqueue(task: Async_task): Long = repository.enqueue(
        type = task.type,
        payloadJson = task.payload_json,
        uniqueKey = task.unique_key,
        maxAttempts = task.max_attempts.coerceAtLeast(1),
    )

    fun openTask(taskId: Long) {
        assistantResumeBlocked.value = false
        selectedTaskId.value = taskId
    }

    fun closeTask() {
        selectedTaskId.value = null
    }

    private fun resetPaging() {
        pageLimit.value = DEFAULT_TASK_CENTER_PAGE_SIZE
    }
}

internal const val DEFAULT_TASK_CENTER_PAGE_SIZE = 50
