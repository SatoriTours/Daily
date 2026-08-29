package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.dailysatori.data.repository.ReminderEdit
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

private enum class DetailPicker { START, END, TIME }

@Composable
fun ReminderListScreen(
    modifier: Modifier = Modifier,
    latestProfile: ReminderProfileSnapshot = ReminderProfileSnapshot.standard(),
    viewModel: ReminderViewModel = koinViewModel(),
) {
    val ui by viewModel.state.collectAsState()
    val all by viewModel.reminders.collectAsState()
    val visible = remember(all, ui.filter) { filterReminders(all, ui.filter) }
    val selected = all.firstOrNull { it.id == ui.selectedReminderId }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ReminderFilter.entries.forEach { filter ->
                FilterChip(selected = ui.filter == filter, onClick = { viewModel.setFilter(filter) }, label = { Text(filter.label()) })
            }
        }
        if (selected != null) ReminderDetail(selected, latestProfile, viewModel)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(visible, key = { it.id }) { reminder ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectReminder(reminder.id) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.m),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(Spacing.m)) {
                        Text(reminder.content, style = MaterialTheme.typography.titleSmall)
                        Text("${reminder.startDate} ${reminder.firstReminderTime} — ${reminder.endDate}", style = MaterialTheme.typography.bodySmall)
                        Text(reminder.status.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderDetail(reminder: Reminder, latestProfile: ReminderProfileSnapshot, viewModel: ReminderViewModel) {
    var content by remember(reminder.id, reminder.version) { mutableStateOf(reminder.content) }
    var startDate by remember(reminder.id, reminder.version) { mutableStateOf(reminder.startDate) }
    var endDate by remember(reminder.id, reminder.version) { mutableStateOf(reminder.endDate) }
    var firstTime by remember(reminder.id, reminder.version) { mutableStateOf(reminder.firstReminderTime) }
    var rule by remember(reminder.id, reminder.version) { mutableStateOf(reminder.activeDayRule) }
    var picker by remember { mutableStateOf<DetailPicker?>(null) }
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("提醒内容") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { picker = DetailPicker.START }) { Text("开始 $startDate") }
                TextButton(onClick = { picker = DetailPicker.END }) { Text("结束 $endDate") }
                TextButton(onClick = { picker = DetailPicker.TIME }) { Text("首次 $firstTime") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf("每天" to ReminderActiveDayRule.Daily, "工作日" to ReminderActiveDayRule.Weekdays, "连续" to ReminderActiveDayRule.ConsecutiveDateRange).forEach { (label, value) ->
                    FilterChip(selected = rule == value, onClick = { rule = value }, label = { Text(label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                reminderActions(reminder).forEach { action ->
                    TextButton(onClick = {
                        when (action) {
                            ReminderAction.PAUSE -> viewModel.pause(reminder.id)
                            ReminderAction.RESUME -> viewModel.resume(reminder.id)
                            ReminderAction.EDIT -> viewModel.edit(reminder.id, ReminderEdit(reminder.version, content = content, startDate = startDate, endDate = endDate, firstReminderTime = firstTime, activeDayRule = rule))
                            ReminderAction.COMPLETE -> viewModel.complete(reminder.id)
                            ReminderAction.DELETE -> viewModel.delete(reminder.id)
                            ReminderAction.APPLY_LATEST_PROFILE -> viewModel.applyLatestProfile(reminder.id, latestProfile)
                        }
                    }) { Text(action.label()) }
                }
            }
        }
    }
    when (picker) {
        DetailPicker.START -> DateDialog(startDate, { picker = null }) { startDate = it; if (endDate < it) endDate = it; picker = null }
        DetailPicker.END -> DateDialog(endDate, { picker = null }) { endDate = it; picker = null }
        DetailPicker.TIME -> TimeDialog(firstTime, { picker = null }) { firstTime = it; picker = null }
        null -> Unit
    }
}

private fun ReminderFilter.label() = when (this) {
    ReminderFilter.ACTIVE -> "进行中"
    ReminderFilter.PAUSED -> "已暂停"
    ReminderFilter.COMPLETED -> "已完成"
    ReminderFilter.EXPIRED -> "已过期"
}

private fun ReminderAction.label() = when (this) {
    ReminderAction.PAUSE -> "暂停"
    ReminderAction.RESUME -> "恢复"
    ReminderAction.EDIT -> "保存编辑"
    ReminderAction.COMPLETE -> "完成"
    ReminderAction.DELETE -> "删除"
    ReminderAction.APPLY_LATEST_PROFILE -> "应用最新配置"
}
