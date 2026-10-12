package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.dailysatori.R
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.feature.profile.localDayTicker
import org.koin.compose.koinInject
import com.dailysatori.ui.theme.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReminderDetailScreen(
    reminderId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ReminderViewModel = koinViewModel(),
) {
    val i18n: I18nService = koinInject()
    val navigation = com.dailysatori.core.navigation.LocalPhoneNavigation.current
    val reminders by viewModel.reminders.collectAsState()
    val loaded by viewModel.remindersLoaded.collectAsState()
    val uiState by viewModel.state.collectAsState()
    val sourceViewModel: com.dailysatori.ui.feature.phone.PhoneSourceViewModel = koinViewModel()
    val sources by sourceViewModel.state.collectAsState()
    val sourceLabels = remember(sources.messages) { ReminderSources.labels(sources.messages) }
    val reminder = reminders.firstOrNull { it.id == reminderId }
    val today by remember { localDayTicker() }.collectAsState(initial = Clock.System.todayIn(TimeZone.currentSystemDefault()))
    var confirmDelete by remember(reminderId) { mutableStateOf(false) }
    AppScaffold(title = i18n.t("phone.todo_detail"), onBack = onBack) { modifier ->
        if (reminder == null) {
            when {
                uiState.error != null -> Text(uiState.error.orEmpty(), modifier = modifier.padding(Spacing.m), color = MaterialTheme.colorScheme.error)
                !loaded -> androidx.compose.material3.LinearProgressIndicator(modifier = modifier.fillMaxWidth().padding(Spacing.m))
                else -> Text(i18n.t("phone.result_not_found"), modifier = modifier.padding(Spacing.m))
            }
        } else {
            val timeline = buildReminderTimeline(reminder, today)
            Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    item { ReminderOverviewInformation(reminder, sourceLabels[reminder.id]) }
                    item(key = "source") {
                        com.dailysatori.ui.feature.phone.PhoneSourcePanel(onOpenSource = { navigation.openHistory(it) }, reminderId = reminder.id)
                    }
                    if (reminder.deadlineAt != null) item {
                        val i18n: com.dailysatori.service.i18n.I18nService = org.koin.compose.koinInject()
                        Text(i18n.t("sms.behavior"), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (reminder.deadlineAt == null && !reminder.id.startsWith("sms:")) item { Text("提醒过程", style = MaterialTheme.typography.titleMedium) }
                    if (reminder.deadlineAt == null && !reminder.id.startsWith("sms:")) item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = Spacing.m)) {
                                timeline.steps.forEachIndexed { index, step ->
                                    TimelineRow(step.title, step.detail)
                                    if (index != timeline.steps.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                    if (reminder.dataIssue != null) item {
                        Text(stringResource(R.string.reminder_detail_resume_blocked), color = MaterialTheme.colorScheme.error)
                    }
                    item {
                        ReminderDetailActions(reminder) { action ->
                            when (action) {
                                ReminderAction.COMPLETE -> viewModel.complete(reminder.id, reminder.version)
                                ReminderAction.PAUSE -> viewModel.pause(reminder.id)
                                ReminderAction.RESUME -> viewModel.resume(reminder.id)
                                ReminderAction.EDIT -> onEdit(reminder.id)
                                ReminderAction.DELETE -> confirmDelete = true
                                ReminderAction.APPLY_LATEST_PROFILE -> viewModel.applyLatestProfile(reminder.id)
                            }
                        }
                    }
                }
                ReminderPrimaryActions(reminder, reminder.id !in uiState.completingIds,
                    onComplete = { viewModel.complete(reminder.id, reminder.version) }, onEdit = { onEdit(reminder.id) })
            }
            ReminderCompletionSnackbar(uiState, viewModel, Modifier.align(Alignment.BottomCenter).padding(Spacing.m))
            }
        }
    }
    if (confirmDelete && reminder != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.reminder_delete_title)) },
            text = { Text(stringResource(R.string.reminder_delete_message)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(reminder.id); onBack() }) {
                Text(stringResource(R.string.reminder_action_delete), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.reminder_cancel)) } },
        )
    }
}

@Composable
private fun ReminderDetailActions(reminder: Reminder, onAction: (ReminderAction) -> Unit) {
    val actions = reminderActions(reminder)
    var showMore by remember(reminder.id, reminder.dataIssue) { mutableStateOf(reminder.dataIssue != null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (ReminderAction.EDIT in actions) {
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.m),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                val paused = ReminderAction.RESUME in actions
                ReminderDetailActionRow(
                    icon = if (paused) Icons.Outlined.PlayCircleOutline else Icons.Outlined.PauseCircleOutline,
                    title = stringResource(if (paused) R.string.reminder_detail_resume else R.string.reminder_detail_pause),
                    description = stringResource(when {
                        paused && !canResumeReminder(reminder) -> R.string.reminder_detail_resume_blocked
                        paused -> R.string.reminder_detail_resume_hint
                        else -> R.string.reminder_detail_pause_hint
                    }),
                    enabled = !paused || canResumeReminder(reminder),
                    onClick = { onAction(if (paused) ReminderAction.RESUME else ReminderAction.PAUSE) },
                )
            }
        }
        if (ReminderAction.APPLY_LATEST_PROFILE in actions) {
            TextButton(onClick = { showMore = !showMore }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (showMore) R.string.reminder_detail_less else R.string.reminder_detail_more))
                Icon(if (showMore) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(IconSize.m))
            }
        }
        AnimatedVisibility(visible = ReminderAction.DELETE in actions && (showMore || ReminderAction.APPLY_LATEST_PROFILE !in actions)) {
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.m),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                if (ReminderAction.APPLY_LATEST_PROFILE in actions) {
                    ReminderDetailActionRow(
                        Icons.Outlined.Tune, stringResource(R.string.reminder_detail_apply_defaults),
                        stringResource(R.string.reminder_detail_apply_defaults_hint),
                        onClick = { onAction(ReminderAction.APPLY_LATEST_PROFILE) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (ReminderAction.DELETE in actions) {
                    ReminderDetailActionRow(
                        Icons.Outlined.DeleteOutline, stringResource(R.string.reminder_detail_delete),
                        stringResource(R.string.reminder_delete_message), destructive = true,
                        onClick = { onAction(ReminderAction.DELETE) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderDetailActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick, enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
    ) {
        Row(
            Modifier.heightIn(min = Height.listItem).padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(IconSize.l), tint = tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (destructive || !enabled) tint else MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TimelineRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(.32f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.68f))
    }
}
