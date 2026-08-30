package com.dailysatori.ui.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.reminder.ReminderSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class ProfileDestination(val id: String, val title: String, val subtitle: String = "")

data class ProfileUiState(
    val todayReminderCount: Int = 0,
    val favoriteCount: Int = 0,
    val externalFavoriteCount: Int = 0,
    val enabledExternalSourceCount: Int = 0,
    val activeTaskCount: Int = 0,
    val failedTaskCount: Int = 0,
    val destinations: List<ProfileDestination> = profileDestinations,
)

val profileDestinations = listOf(
    ProfileDestination("reminders", "今日提醒"),
    ProfileDestination("favorites", "收藏库"),
    ProfileDestination("external_favorites", "外部收藏"),
    ProfileDestination("tasks", "同步与任务"),
    ProfileDestination("settings", "设置"),
    ProfileDestination("privacy", "数据与隐私"),
)

class ProfileViewModel(
    reminders: ReminderRepository,
    articles: ArticleRepository,
    externalSources: ExternalFavoriteSourceRepository,
    tasks: AsyncTaskRepository,
) : ViewModel() {
    private val enabledSourceCount = externalSources.getEnabled().size

    val state = combine(
        reminders.observeAll(),
        articles.getFavorites(),
        articles.getExternalFavorites(),
        tasks.observeTaskCenter(AsyncTaskFilter(showTerminal = true)),
    ) { reminderItems, favoriteItems, externalFavoriteItems, taskPage ->
        val taskItems = taskPage.tasks
        ProfileUiState(
            todayReminderCount = ReminderSummary.todayPendingCount(reminderItems, Clock.System.todayIn(TimeZone.currentSystemDefault())),
            favoriteCount = favoriteItems.size,
            externalFavoriteCount = externalFavoriteItems.size,
            enabledExternalSourceCount = enabledSourceCount,
            activeTaskCount = taskItems.count { it.status in activeTaskStatuses },
            failedTaskCount = taskItems.count { it.status == AsyncTaskStatus.failed.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())
}

private val activeTaskStatuses = setOf(AsyncTaskStatus.queued.name, AsyncTaskStatus.running.name, AsyncTaskStatus.retrying.name)
