package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
import com.dailysatori.R
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.ui.component.scaffold.AppScaffold
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
    val reminders by viewModel.reminders.collectAsState()
    val reminder = reminders.firstOrNull { it.id == reminderId }
    var confirmDelete by remember(reminderId) { mutableStateOf(false) }
    AppScaffold(title = "提醒详情", onBack = onBack) { modifier ->
        if (reminder == null) {
            Text("未找到提醒。", modifier = modifier.padding(Spacing.m))
        } else {
            val timeline = buildReminderTimeline(reminder, Clock.System.todayIn(TimeZone.currentSystemDefault()))
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.m)) {
                        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(reminder.content, style = MaterialTheme.typography.headlineSmall)
                            Text("下次 ${timeline.occurrenceDate ?: "—"} ${reminder.firstReminderTime}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(reminder.recurrence.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { Text("提醒过程", style = MaterialTheme.typography.titleMedium) }
                item {
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
                            ReminderAction.COMPLETE -> viewModel.complete(reminder.id)
                            ReminderAction.PAUSE -> viewModel.pause(reminder.id)
                            ReminderAction.RESUME -> viewModel.resume(reminder.id)
                            ReminderAction.EDIT -> onEdit(reminder.id)
                            ReminderAction.DELETE -> confirmDelete = true
                            ReminderAction.APPLY_LATEST_PROFILE -> viewModel.applyLatestProfile(reminder.id)
                        }
                    }
                }
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
        if (ReminderAction.COMPLETE in actions) {
            Button(
                onClick = { onAction(ReminderAction.COMPLETE) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Height.button),
                shape = RoundedCornerShape(Radius.m),
                colors = ButtonDefaults.buttonColors(contentColor = AppColors.onAccent),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(IconSize.m))
                    Text(stringResource(R.string.reminder_detail_complete))
                }
            }
            Text(
                stringResource(R.string.reminder_detail_complete_hint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (ReminderAction.EDIT in actions) {
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.m),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                ReminderDetailActionRow(
                    Icons.Outlined.Edit, stringResource(R.string.reminder_detail_edit_kicker),
                    stringResource(R.string.reminder_detail_edit_hint),
                    onClick = { onAction(ReminderAction.EDIT) },
                )
                HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
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
