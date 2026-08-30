package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

private enum class EditorPicker { START, END, TIME }

@Composable
fun ReminderEditScreen(
    reminderId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ReminderViewModel = koinViewModel(),
) {
    val reminders by viewModel.reminders.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val existing = reminders.firstOrNull { it.id == reminderId }
    var editor by remember(existing?.id) { mutableStateOf(existing?.let(ReminderEditorState::from) ?: ReminderEditorState.createDefault()) }
    var picker by remember { mutableStateOf<EditorPicker?>(null) }
    AppScaffold(title = if (existing == null) "新建提醒" else "编辑提醒", onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedTextField(editor.content, { editor = editor.copy(content = it, notice = null) }, label = { Text("提醒内容") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { picker = EditorPicker.START }) { Text("开始 ${editor.startDate}") }
                TextButton(onClick = { picker = EditorPicker.END }) { Text("结束 ${editor.endDate}") }
                TextButton(onClick = { picker = EditorPicker.TIME }) { Text("时间 ${editor.firstReminderTime}") }
            }
            Text("重复方式", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf("一次" to ReminderRecurrence.Once, "每月" to ReminderRecurrence.Monthly(editor.startDate.dayOfMonth), "每年" to ReminderRecurrence.Yearly(editor.startDate.monthNumber, editor.startDate.dayOfMonth, LeapDayPolicy.FEBRUARY_28), "连续" to ReminderRecurrence.Once).forEach { (label, recurrence) ->
                    FilterChip(selected = when (label) { "连续" -> editor.activeDayRule is com.dailysatori.service.reminder.ReminderActiveDayRule.ConsecutiveDateRange; else -> editor.recurrence::class == recurrence::class }, onClick = {
                        editor = if (label == "连续") {
                            editor.copy(activeDayRule = com.dailysatori.service.reminder.ReminderActiveDayRule.ConsecutiveDateRange)
                        } else {
                            editor.copy(
                                recurrence = recurrence,
                                leapDayFallbackChosen = !(recurrence is ReminderRecurrence.Yearly && recurrence.month == 2 && recurrence.dayOfMonth == 29),
                            )
                        }
                    }, label = { Text(label) })
                }
            }
            if (editor.recurrence is ReminderRecurrence.Yearly && editor.startDate.monthNumber == 2 && editor.startDate.dayOfMonth == 29) {
                Text("非闰年时使用：")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    LeapDayPolicy.entries.forEach { policy ->
                        FilterChip(selected = (editor.recurrence as ReminderRecurrence.Yearly).leapDayPolicy == policy && editor.leapDayFallbackChosen, onClick = { editor = editor.copy(recurrence = ReminderRecurrence.Yearly(2, 29, policy), leapDayFallbackChosen = true) }, label = { Text(if (policy == LeapDayPolicy.FEBRUARY_28) "2 月 28 日" else "3 月 1 日") })
                    }
                }
            }
            Text("提醒配置", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                profiles.forEach { profile -> FilterChip(selected = editor.profile.kind == profile.kind, onClick = { editor = editor.copy(profile = profile.snapshot, notice = null) }, label = { Text(profile.name) }) }
            }
            Text(editor.actualBehaviorSummary(), style = MaterialTheme.typography.bodyMedium)
            editor.validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            editor.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val submitted = editor
                editor = submitted.copy(saving = true)
                viewModel.saveEditor(existing, submitted) { id, next -> editor = next; id?.let(onSaved) }
            }, enabled = editor.canSave) { Text(if (editor.saving) "保存中…" else "保存") }
        }
    }
    when (picker) {
        EditorPicker.START -> DateDialog(editor.startDate, { picker = null }) { selected -> editor = editor.copy(startDate = selected, endDate = if (editor.endDate < selected) selected else editor.endDate); picker = null }
        EditorPicker.END -> DateDialog(editor.endDate, { picker = null }) { editor = editor.copy(endDate = it); picker = null }
        EditorPicker.TIME -> TimeDialog(editor.firstReminderTime, { picker = null }) { editor = editor.copy(firstReminderTime = it); picker = null }
        null -> Unit
    }
}
