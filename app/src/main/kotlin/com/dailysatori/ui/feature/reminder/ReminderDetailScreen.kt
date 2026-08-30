package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
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
    AppScaffold(title = "提醒详情", onBack = onBack) { modifier ->
        if (reminder == null) {
            Text("未找到提醒。", modifier = modifier.padding(Spacing.m))
        } else {
            Column(modifier.fillMaxSize().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(reminder.content, style = MaterialTheme.typography.headlineSmall)
                Text("${reminder.startDate} ${reminder.firstReminderTime} · ${reminder.recurrence}", style = MaterialTheme.typography.bodyMedium)
                Text("时间线", style = MaterialTheme.typography.titleMedium)
                TimelineRow("创建", reminder.startDate.toString())
                TimelineRow("下次提醒", reminder.startDate.toString())
                if (reminder.dataIssue != null) Text("提醒配置需要修复。", color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    reminderActions(reminder).forEach { action ->
                        when (action) {
                            ReminderAction.COMPLETE -> Button(onClick = { viewModel.complete(reminder.id) }) { Text("完成") }
                            ReminderAction.PAUSE -> TextButton(onClick = { viewModel.pause(reminder.id) }) { Text("暂停") }
                            ReminderAction.RESUME -> TextButton(onClick = { viewModel.resume(reminder.id) }, enabled = canResumeReminder(reminder)) { Text("恢复") }
                            ReminderAction.EDIT -> TextButton(onClick = { onEdit(reminder.id) }) { Text("编辑") }
                            ReminderAction.DELETE -> TextButton(onClick = { viewModel.delete(reminder.id); onBack() }) { Text("删除") }
                            ReminderAction.APPLY_LATEST_PROFILE -> TextButton(onClick = { viewModel.applyLatestProfile(reminder.id) }) { Text("应用最新配置") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
