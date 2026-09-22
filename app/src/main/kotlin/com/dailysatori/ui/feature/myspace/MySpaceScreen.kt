package com.dailysatori.ui.feature.myspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.dailysatori.ui.feature.article.openArticleUrl
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.diary.DiaryThoughtState
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.ui.feature.diary.DiaryThoughtViewModel
import com.dailysatori.ui.feature.reminder.ReminderListItemUi
import com.dailysatori.ui.feature.reminder.ReminderRepeatLabel
import com.dailysatori.ui.feature.reminder.ReminderViewModel
import com.dailysatori.ui.feature.reminder.repeatLabel
import com.dailysatori.ui.theme.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.androidx.compose.koinViewModel

@Composable
fun MySpaceScreen(
    onThoughts: () -> Unit,
    onReminders: () -> Unit,
    onReminder: (String) -> Unit,
    onAddReminder: () -> Unit,
    onOpportunities: () -> Unit,
    onOpportunity: (String) -> Unit,
    onArticle: (Long) -> Unit,
    onChat: () -> Unit,
    onManagement: () -> Unit,
) {
    val thoughts: DiaryThoughtViewModel = koinViewModel()
    val reminders: ReminderViewModel = koinViewModel()
    val viewModel: MySpaceViewModel = koinViewModel()
    val thoughtState by thoughts.state.collectAsStateWithLifecycle()
    val allReminders by reminders.reminders.collectAsStateWithLifecycle()
    val opportunities by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.operationFailed.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    val busy = opportunities.isUpdating || task?.status in listOf("queued", "running", "retrying")
    val upcoming = myUpcomingReminders(allReminders, Clock.System.todayIn(TimeZone.currentSystemDefault()))
    LaunchedEffect(thoughtState.archive.generatedAt, thoughtState.useInChat) { viewModel.recommend() }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = Height.navBar + Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        item(key = "thoughts") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min = Height.button).clickable(onClick = onThoughts), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(stringResource(R.string.my_space_thoughts), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onChat) { Icon(Icons.Outlined.AutoAwesome, stringResource(R.string.my_space_chat), Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onManagement) { Icon(Icons.Outlined.Settings, stringResource(R.string.my_space_settings), Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                MyThoughtSummary(thoughtState, onThoughts)
            }
        }
        item(key = "reminders") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MySectionHeading(stringResource(R.string.my_space_next), onReminders)
                if (upcoming.isEmpty()) MyEmptyBlock(stringResource(R.string.my_space_reminder_empty), "", stringResource(R.string.my_space_add_reminder), onAddReminder)
                else upcoming.forEach { item -> MyReminderRow(item) { onReminder(item.id) } }
            }
        }
        item(key = "opportunities") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MySectionHeading(stringResource(R.string.my_space_useful), onOpportunities)
                if (busy) Text(opportunities.progress.ifBlank { stringResource(R.string.my_space_queued) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val error = opportunities.error ?: task?.last_error_message?.takeIf { task?.status == "failed" }
                if (failure || error != null) {
                    Text(error ?: stringResource(R.string.my_space_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.analyze() }, enabled = !busy) { Text(stringResource(R.string.my_space_retry)) }
                }
                val entries = recommendedArticles(opportunities.items)
                if (entries.isEmpty() && !busy) MyEmptyBlock(stringResource(R.string.my_space_opportunity_empty), stringResource(when {
                    !opportunities.hasAnalysisContext -> R.string.my_space_analyze_missing
                    opportunities.candidateCount == 0 -> R.string.my_space_opportunity_hint
                    else -> R.string.my_space_no_relation
                }), stringResource(R.string.my_space_expand), onOpportunities)
                entries.forEach { entry -> OpportunitySummary(entry, onArticle) { onOpportunity(entry.id) } }
            }
        }
    }
}

@Composable
internal fun MySectionHeading(title: String, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onAll) { Icon(Icons.Default.ChevronRight, "$title · ${stringResource(R.string.my_space_all)}", Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun MyThoughtSummary(state: DiaryThoughtState, onClick: () -> Unit) {
    val previews = myThoughtPreviews(state.archive.thoughts)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (previews.isEmpty()) {
            MyEmptyBlock(stringResource(R.string.my_space_thought_empty), stringResource(R.string.my_space_thought_empty_hint), stringResource(R.string.my_space_organize), onClick)
        } else {
            previews.forEachIndexed { index, preview ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
                MyThoughtPreviewBlock(preview)
            }
            TextButton(onClick = onClick) { Text(stringResource(R.string.my_space_thought_more)) }
            val updated = state.archive.generatedAt.takeIf { it > 0 }?.let { java.text.DateFormat.getDateInstance().format(java.util.Date(it)) }
            Text(listOfNotNull(stringResource(R.string.my_space_thought_meta, state.archive.diaryCount), updated).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.isUpdating || state.error != null || state.isPaused) Text(state.error ?: state.progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MyThoughtPreviewBlock(preview: MyThoughtPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(if (preview.isInference) R.string.my_space_thought_inferred else R.string.my_space_thought_explicit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(preview.statement, style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.padding(start = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.my_space_thought_quote), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(preview.quote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
private fun MyReminderRow(item: ReminderListItemUi, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Column(Modifier.width(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.occurrenceDate.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(item.occurrenceDate.toString().take(7), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.content, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text("${item.firstReminderTime} · ${reminderRepeatText(item.repeatLabel())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(Modifier.fillMaxWidth().clickable {
        when {
            localArticleId != null -> onArticle(localArticleId)
            item.article.url?.startsWith("https://") == true || item.article.url?.startsWith("http://") == true -> openArticleUrl(context, item.article.url)
            else -> onClick()
        }
    }.padding(vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(item.article.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.fact, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text(item.relevance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${item.category} · ${item.article.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text(stringResource(R.string.my_space_relevance)) }
    }
}
