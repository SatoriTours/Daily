package com.dailysatori.ui.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.reminder.ReminderSummary
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.asynctask.AsyncTaskListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.atStartOfDayIn

data class ProfileDestination(val id: String, val title: String, val subtitle: String = "")

data class ProfileUiState(
    val todayReminderCount: Int = 0,
    val favoriteCount: Int = 0,
    val externalFavoriteCount: Int = 0,
    val enabledExternalSourceCount: Int = 0,
    val remoteNewsArticleCount: Long = 0,
    val enabledRemoteNewsSourceCount: Long = 0,
    val activeTaskCount: Int = 0,
    val failedTaskCount: Int = 0,
    val nextReminderContent: String? = null,
    val nextReminderTime: String? = null,
    val taskProgressLabel: String? = null,
    val destinations: List<ProfileDestination> = profileDestinations,
)

data class ProfileReminderSummary(val count: Int, val nextContent: String?, val nextTime: String?)
data class ProfileTaskSummary(val activeCount: Int, val failedCount: Int, val progressLabel: String?, val canOpenFailedTasks: Boolean)
private data class ProfileExternalFavorites(val itemCount: Int, val enabledSourceCount: Int)
private data class ProfileRemoteNews(val articleCount: Long, val enabledSourceCount: Long)

fun remoteNewsProfileSubtitle(articleCount: Long, enabledSourceCount: Long): String =
    if (enabledSourceCount == 0L) "尚未配置来源" else "已同步 $articleCount 篇 · $enabledSourceCount 个来源"

fun profileReminderSummary(reminders: List<Reminder>, today: LocalDate): ProfileReminderSummary {
    val pending = ReminderSummary.todayPendingReminders(reminders, today)
    val next = pending.minByOrNull { it.firstReminderTime }
    return ProfileReminderSummary(pending.size, next?.content, next?.firstReminderTime?.toString())
}

fun profileTaskSummary(
    tasks: List<AsyncTaskListItem>,
    nowMs: Long = Clock.System.now().toEpochMilliseconds(),
): ProfileTaskSummary {
    val active = tasks.filter { it.status in activeTaskStatuses }
    val recentFailures = tasks.filter {
        it.status == AsyncTaskStatus.failed.name && it.updatedAt >= nowMs - PROFILE_FAILURE_WINDOW_MS
    }
    val progress = active.firstOrNull { it.progressTotal > 0 }
    return ProfileTaskSummary(
        activeCount = active.size,
        failedCount = recentFailures.size,
        progressLabel = progress?.let { "${it.progressCurrent}/${it.progressTotal}" },
        canOpenFailedTasks = recentFailures.isNotEmpty(),
    )
}

fun localDayTicker(): Flow<LocalDate> = flow {
    while (true) {
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(zone).date
        emit(today)
        val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        delay((nextMidnight - now).inWholeMilliseconds.coerceAtLeast(1))
    }
}

val profileDestinations = listOf(
    ProfileDestination("reminders", "今日提醒"),
    ProfileDestination("favorites", "收藏库"),
    ProfileDestination("external_favorites", "外部收藏"),
    ProfileDestination("remote_news", "远程新闻"),
    ProfileDestination("tasks", "同步与任务"),
    ProfileDestination("settings", "设置"),
    ProfileDestination("privacy", "数据与隐私"),
)

class ProfileViewModel(
    reminders: ReminderRepository,
    articles: ArticleRepository,
    externalSources: ExternalFavoriteSourceRepository,
    remoteNewsSources: RemoteNewsSourceRepository,
    remoteArticleSync: RemoteArticleSyncRepository,
    tasks: AsyncTaskRepository,
) : ViewModel() {

    private val remindersForToday = combine(reminders.observeAll(), localDayTicker()) { items, today -> items to today }
    private val externalFavorites = combine(articles.getExternalFavorites(), externalSources.observeEnabled()) { items, sources ->
        ProfileExternalFavorites(items.size, sources.size)
    }
    private val remoteNews = combine(remoteArticleSync.observeCount(), remoteNewsSources.observeEnabledCount()) { articleCount, sourceCount ->
        ProfileRemoteNews(articleCount, sourceCount)
    }

    val state = combine(
        remindersForToday,
        articles.getFavorites(),
        externalFavorites,
        remoteNews,
        tasks.observeTaskCenter(AsyncTaskFilter(showTerminal = true)),
    ) { reminderInput, favoriteItems, externalFavoriteItems, remoteNews, taskPage ->
        val (reminderItems, today) = reminderInput
        val taskItems = taskPage.tasks
        val reminders = profileReminderSummary(reminderItems, today)
        val tasks = profileTaskSummary(taskItems)
        ProfileUiState(
            todayReminderCount = reminders.count,
            favoriteCount = favoriteItems.size,
            externalFavoriteCount = externalFavoriteItems.itemCount,
            enabledExternalSourceCount = externalFavoriteItems.enabledSourceCount,
            remoteNewsArticleCount = remoteNews.articleCount,
            enabledRemoteNewsSourceCount = remoteNews.enabledSourceCount,
            activeTaskCount = tasks.activeCount,
            failedTaskCount = tasks.failedCount,
            nextReminderContent = reminders.nextContent,
            nextReminderTime = reminders.nextTime,
            taskProgressLabel = tasks.progressLabel,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())
}

private val activeTaskStatuses = setOf(AsyncTaskStatus.queued.name, AsyncTaskStatus.running.name, AsyncTaskStatus.retrying.name)
private const val PROFILE_FAILURE_WINDOW_MS = 24 * 60 * 60 * 1000L
