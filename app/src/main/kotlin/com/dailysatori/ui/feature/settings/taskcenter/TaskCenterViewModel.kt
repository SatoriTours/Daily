package com.dailysatori.ui.feature.settings.taskcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.shared.db.Async_task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class TaskCenterState(
    val types: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val showTerminal: Boolean = false,
    val tasks: List<AsyncTaskListItem> = emptyList(),
    val hasMore: Boolean = false,
    val loadedCount: Int = 0,
    val requestedLimit: Int = DEFAULT_TASK_CENTER_PAGE_SIZE,
    val selectedTask: Async_task? = null,
    val taskLog: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCenterViewModel(
    private val repository: AsyncTaskRepository,
    private val logStore: AsyncTaskLogStore,
) : ViewModel() {
    private val filter = MutableStateFlow(AsyncTaskFilter())
    private val pageLimit = MutableStateFlow(DEFAULT_TASK_CENTER_PAGE_SIZE)
    private val selectedTaskId = MutableStateFlow<Long?>(null)
    private val selected = selectedTaskId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null to "")
        } else {
            repository.observeTaskById(id).combine(logStore.observe(id)) { task, log -> task to log }
        }
    }

    val state: StateFlow<TaskCenterState> = filter
        .combine(pageLimit) { taskFilter, limit -> taskFilter to limit }
        .flatMapLatest { (taskFilter, limit) ->
            repository.observeTaskCenter(taskFilter, limit)
                .combine(filter) { page, latestFilter -> page to latestFilter }
                .combine(selected) { (page, latestFilter), selectedTask ->
                TaskCenterState(
                    types = latestFilter.types,
                    statuses = latestFilter.statuses,
                    showTerminal = latestFilter.showTerminal,
                    tasks = page.tasks,
                    hasMore = page.hasMore,
                    loadedCount = page.loadedCount,
                    requestedLimit = page.requestedLimit,
                    selectedTask = selectedTask.first,
                    taskLog = selectedTask.second,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskCenterState())

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
        repository.cancel(taskId)
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
