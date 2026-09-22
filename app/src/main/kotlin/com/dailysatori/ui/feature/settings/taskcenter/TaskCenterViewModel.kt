package com.dailysatori.ui.feature.settings.taskcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.asynctask.recentFailedTaskFilter
import com.dailysatori.service.asynctask.recentTaskFailureCutoffs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.shared.db.Async_task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.core.worker.ExternalFavoriteTaskCancellationRegistry

data class TaskCenterState(
    val types: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val showTerminal: Boolean = true,
    val updatedSince: Long? = null,
    val tasks: List<AsyncTaskListItem> = emptyList(),
    val hasMore: Boolean = false,
    val loadedCount: Int = 0,
    val requestedLimit: Int = DEFAULT_TASK_CENTER_PAGE_SIZE,
    val selectedTask: Async_task? = null,
    val taskLog: String = "",
    val selectedFailureSuperseded: Boolean = false,
)

private data class SelectedTaskState(
    val task: Async_task?,
    val log: String,
    val failureSuperseded: Boolean,
)

internal fun taskFailureIsSuperseded(
    selectedId: Long,
    selectedStatus: String,
    latestId: Long?,
    latestStatus: String?,
): Boolean = selectedStatus == "failed" && latestId != null && latestId != selectedId && latestStatus == "succeeded"

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCenterViewModel(
    private val repository: AsyncTaskRepository,
    private val logStore: AsyncTaskLogStore,
    private val scheduler: AsyncTaskScheduler,
) : ViewModel() {
    private val filter = MutableStateFlow(AsyncTaskFilter())
    private var entryFilterApplied = false
    private val pageLimit = MutableStateFlow(DEFAULT_TASK_CENTER_PAGE_SIZE)
    private val selectedTaskId = MutableStateFlow<Long?>(null)
    private val selected = selectedTaskId.flatMapLatest { id ->
        if (id == null) {
            flowOf(SelectedTaskState(null, "", false))
        } else {
            repository.observeTaskById(id).combine(logStore.observe(id)) { task, log -> task to log }
                .map { (task, log) ->
                    val latest = task?.unique_key?.let(repository::getLatestByUniqueKey)
                    SelectedTaskState(
                        task = task,
                        log = log,
                        failureSuperseded = task?.let {
                            taskFailureIsSuperseded(it.id, it.status, latest?.id, latest?.status)
                        } == true,
                    )
                }
        }
    }

    private val effectiveFilter = combine(filter, recentTaskFailureCutoffs()) { current, cutoff ->
        if (current.updatedSince == null) current else current.copy(updatedSince = cutoff)
    }.distinctUntilChanged()

    val state: StateFlow<TaskCenterState> = effectiveFilter
        .combine(pageLimit) { taskFilter, limit -> taskFilter to limit }
        .flatMapLatest { (taskFilter, limit) ->
            repository.observeTaskCenter(taskFilter, limit)
                .combine(selected) { page, selectedTask ->
                TaskCenterState(
                    types = taskFilter.types,
                    statuses = taskFilter.statuses,
                    showTerminal = taskFilter.showTerminal,
                    updatedSince = taskFilter.updatedSince,
                    tasks = page.tasks,
                    hasMore = page.hasMore,
                    loadedCount = page.loadedCount,
                    requestedLimit = page.requestedLimit,
                    selectedTask = selectedTask.task,
                    taskLog = selectedTask.log,
                    selectedFailureSuperseded = selectedTask.failureSuperseded,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskCenterState())

    fun applyEntryFilter(recentFailures: Boolean) {
        if (entryFilterApplied) return
        entryFilterApplied = true
        if (recentFailures) filter.value = recentFailedTaskFilter(Clock.System.now().toEpochMilliseconds())
    }

    fun showAllTasks() {
        resetPaging()
        filter.value = AsyncTaskFilter()
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
            repository.cancel(taskId)
            ExternalFavoriteTaskCancellationRegistry.cancel(taskId)
            scheduler.cancel(taskId)
            scheduler.recoverAndEnqueueRunnable()
        }
    }

    fun openTask(taskId: Long) {
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
