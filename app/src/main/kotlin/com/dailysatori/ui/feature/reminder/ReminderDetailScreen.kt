package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.ui.theme.Radius
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
                if (reminder.dataIssue != null) item { Text("提醒配置需要修复，修复前不会发送通知。", color = MaterialTheme.colorScheme.error) }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    reminderActions(reminder).forEach { action ->
                        when (action) {
                            ReminderAction.COMPLETE -> Button(onClick = { viewModel.complete(reminder.id) }) { Text("完成") }
                            ReminderAction.PAUSE -> TextButton(onClick = { viewModel.pause(reminder.id) }) { Text("暂停") }
                            ReminderAction.RESUME -> TextButton(onClick = { viewModel.resume(reminder.id) }, enabled = canResumeReminder(reminder)) { Text("恢复") }
                            ReminderAction.EDIT -> TextButton(onClick = { onEdit(reminder.id) }) { Text("编辑") }
                            ReminderAction.DELETE -> TextButton(onClick = { confirmDelete = true }) { Text("删除") }
                            ReminderAction.APPLY_LATEST_PROFILE -> TextButton(onClick = { viewModel.applyLatestProfile(reminder.id) }) { Text("应用最新配置") }
                        }
                    }
                }
                }
            }
        }
    }
    if (confirmDelete && reminder != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除提醒？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(reminder.id); onBack() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TimelineRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(.32f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.68f))
    }
}
