package com.dailysatori.ui.feature.settings.taskcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.asynctask.AsyncTaskListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class TaskCenterState(
    val type: String? = null,
    val status: String? = null,
    val showTerminal: Boolean = false,
    val tasks: List<AsyncTaskListItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCenterViewModel(private val repository: AsyncTaskRepository) : ViewModel() {
    private val filter = MutableStateFlow(AsyncTaskFilter())

    val state: StateFlow<TaskCenterState> = filter
        .flatMapLatest { taskFilter ->
            repository.observeTaskCenter(taskFilter).combine(filter) { tasks, latestFilter ->
                TaskCenterState(
                    type = latestFilter.type,
                    status = latestFilter.status,
                    showTerminal = latestFilter.showTerminal,
                    tasks = tasks,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskCenterState())

    fun setType(type: String?) {
        filter.update { it.copy(type = type) }
    }

    fun setStatus(status: String?) {
        filter.update { it.copy(status = status) }
    }

    fun setShowTerminal(show: Boolean) {
        filter.update { it.copy(showTerminal = show) }
    }

    fun cancel(taskId: Long) {
        repository.cancel(taskId)
    }
}
