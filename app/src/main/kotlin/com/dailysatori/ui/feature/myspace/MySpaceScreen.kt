package com.dailysatori.ui.feature.myspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import com.dailysatori.ui.feature.profile.localDayTicker
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.dailysatori.ui.feature.article.openArticleUrl
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.ui.component.appbar.MainPageHeader
import com.dailysatori.service.diary.DiaryThoughtState
import com.dailysatori.service.diary.DiaryThought
import com.dailysatori.service.reminder.ReminderSummary
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.ui.feature.diary.DiaryThoughtViewModel
import com.dailysatori.ui.feature.profile.ProfileUiState
import com.dailysatori.ui.feature.profile.ProfileViewModel
import com.dailysatori.ui.feature.reminder.ReminderListItemUi
import com.dailysatori.ui.feature.reminder.ReminderRepeatLabel
import com.dailysatori.ui.feature.reminder.ReminderViewModel
import com.dailysatori.ui.feature.reminder.repeatLabel
import com.dailysatori.ui.theme.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import org.koin.androidx.compose.koinViewModel

@Composable
fun MySpaceScreen(
    previewSeed: Int,
    listState: LazyListState,
    onThoughts: () -> Unit,
    onThought: (DiaryThought) -> Unit,
    onReminders: () -> Unit,
    onReminder: (String) -> Unit,
    onAddReminder: () -> Unit,
    onChat: () -> Unit,
    onSettings: () -> Unit,
    onFavorites: () -> Unit,
    onTasks: () -> Unit,
    onLifeArchive: () -> Unit = {},
) {
    val thoughts: DiaryThoughtViewModel = koinViewModel()
    val reminders: ReminderViewModel = koinViewModel()
    val profile: ProfileViewModel = koinViewModel()
    val profileState by profile.state.collectAsStateWithLifecycle()
    val thoughtState by thoughts.state.collectAsStateWithLifecycle()
    val allReminders by reminders.reminders.collectAsStateWithLifecycle()
    val today by remember { localDayTicker() }.collectAsState(initial = Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val upcoming = myUpcomingReminders(allReminders, today)
    val todayPendingCount = ReminderSummary.todayPendingCount(allReminders, today)
    // The home screen owns the saved seed and scroll state across detail navigation.
    val previews = remember(thoughtState.archive.thoughts, previewSeed) {
        myThoughtPreviews(thoughtState.archive.thoughts, Random(previewSeed))
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.s,
            bottom = Height.navBar + Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "header") {
            MainPageHeader(title = stringResource(R.string.personal_settings_my_title)) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, stringResource(R.string.personal_settings_title), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item(key = "quick-actions") {
            MyQuickActions(profileState, onAddReminder, onChat, onFavorites, onTasks)
        }
        item(key = "life-archive") {
            MySectionCard(compact = true, modifier = Modifier.clip(RoundedCornerShape(Radius.l))
                .clickable(role = Role.Button, onClick = onLifeArchive)) {
                MySectionHeading(stringResource(R.string.life_archive_title), Icons.Outlined.BookmarkBorder, onLifeArchive,
                    titleClickable = false)
                Text(stringResource(R.string.life_archive_entry_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "reminders") {
            MySectionCard {
                MySectionHeading(stringResource(R.string.my_space_reminders), Icons.Outlined.NotificationsNone, onReminders,
                    pendingCount = todayPendingCount)
                if (upcoming.isEmpty()) {
                    Text(stringResource(R.string.my_space_reminder_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else upcoming.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MyReminderRow(item, today) { onReminder(item.id) }
                }
            }
        }
        item(key = "thoughts") {
            MySectionCard {
                MySectionHeading(stringResource(R.string.my_space_thoughts), Icons.Outlined.AutoAwesome, onThoughts)
                MyThoughtSummary(thoughtState, previews, onThoughts, onThought)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyQuickActions(
    state: ProfileUiState,
    onAddReminder: () -> Unit,
    onChat: () -> Unit,
    onFavorites: () -> Unit,
    onTasks: () -> Unit,
) {
    val taskStatus = stringResource(R.string.management_task_status, state.activeTaskCount, state.failedTaskCount)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < Spacing.xxl * 6 || LocalDensity.current.fontScale > 1.3f) 2 else 4
        FlowRow(maxItemsInEachRow = columns, horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            MyQuickAction(Icons.Default.Add, stringResource(R.string.my_space_add_reminder), onAddReminder, Modifier.weight(1f))
            MyQuickAction(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.my_space_chat_shortcut), onChat, Modifier.weight(1f))
            MyQuickAction(Icons.Outlined.BookmarkBorder, stringResource(R.string.my_space_favorites_shortcut), onFavorites, Modifier.weight(1f))
            MyQuickAction(Icons.Outlined.TaskAlt, stringResource(R.string.personal_settings_tasks), onTasks,
                Modifier.weight(1f).semantics { stateDescription = taskStatus },
                showBadge = state.activeTaskCount > 0)
        }
    }
}

@Composable
private fun MyQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    showBadge: Boolean = false,
) {
    Column(modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        BadgedBox(badge = {
            if (showBadge) Badge(containerColor = MaterialTheme.colorScheme.error)
        }) {
            Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.size(Spacing.xxl - Spacing.xs), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(IconSize.l), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MySectionCard(compact: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(horizontal = Spacing.m, vertical = if (compact) Spacing.s + Spacing.xs else Spacing.m),
            verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.xs else Spacing.s),
            content = content,
        )
    }
}

@Composable
private fun MySectionHeading(title: String, icon: ImageVector, onAll: () -> Unit, titleClickable: Boolean = true, pendingCount: Int = 0) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(icon, null, Modifier.size(IconSize.l), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.weight(1f).then(if (titleClickable) Modifier.clickable(onClick = onAll) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pendingCount > 0) {
                val description = stringResource(R.string.my_space_today_pending_count, pendingCount)
                Badge(Modifier.semantics { contentDescription = description },
                    containerColor = MaterialTheme.colorScheme.error) { Text(pendingCount.toString()) }
            }
        }
        TextButton(onClick = onAll) {
            Text(stringResource(R.string.my_space_view_all))
            Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.s))
        }
    }
}

@Composable
private fun MyThoughtSummary(state: DiaryThoughtState, previews: List<DiaryThought>, onAll: () -> Unit, onThought: (DiaryThought) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (previews.isEmpty()) {
            MyEmptyBlock(stringResource(R.string.my_space_thought_empty), stringResource(R.string.my_space_thought_empty_hint), stringResource(R.string.my_space_organize), onAll)
        } else {
            previews.forEachIndexed { index, preview ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
                Text(preview.statement, Modifier.fillMaxWidth().clickable { onThought(preview) }.padding(vertical = Spacing.xs),
                    style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(stringResource(R.string.my_space_thought_meta, state.archive.diaryCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.isUpdating || state.error != null || state.isPaused) Text(state.error ?: state.progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun MyEmptyBlock(title: String, hint: String, action: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action.isNotBlank()) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun MyReminderRow(item: ReminderListItemUi, today: LocalDate, onClick: () -> Unit) {
    val day = when (item.daysUntil) {
        0 -> stringResource(R.string.reminder_list_today)
        1 -> stringResource(R.string.reminder_list_tomorrow)
        else -> stringResource(R.string.reminder_date_month_day, item.occurrenceDate.monthNumber, item.occurrenceDate.dayOfMonth)
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (item.occurrenceDate.year != today.year) {
                Text(item.occurrenceDate.year.toString(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(item.firstReminderTime, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        VerticalDivider(Modifier.height(Height.button), color = MaterialTheme.colorScheme.outlineVariant)
        MyReminderBody(item, Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MyReminderBody(item: ReminderListItemUi, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(item.content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge)
        Text(reminderRepeatText(item.repeatLabel()),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun reminderRepeatText(label: ReminderRepeatLabel): String = stringResource(when (label) {
    ReminderRepeatLabel.ONCE -> R.string.reminder_list_repeat_once
    ReminderRepeatLabel.DAILY -> R.string.reminder_rule_daily
    ReminderRepeatLabel.WEEKDAYS -> R.string.reminder_rule_weekdays
    ReminderRepeatLabel.WEEKLY -> R.string.reminder_list_repeat_weekly
    ReminderRepeatLabel.MONTHLY -> R.string.reminder_list_repeat_monthly
    ReminderRepeatLabel.YEARLY -> R.string.reminder_list_repeat_yearly
})

@Composable
internal fun OpportunitySummary(item: NewsOpportunity, onArticle: (Long) -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val localArticleId = item.article.localArticleId
    val hasSource = localArticleId != null || item.article.url?.startsWith("https://") == true || item.article.url?.startsWith("http://") == true
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.fact, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text(item.relevance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.article.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${item.category} · ${item.article.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TextButton(onClick = onClick) { Text(stringResource(R.string.my_space_view_opportunity)) }
            if (hasSource) TextButton(onClick = { localArticleId?.let(onArticle) ?: openArticleUrl(context, item.article.url) }) {
                Text(stringResource(R.string.my_space_original))
            }
        }
    }
}
