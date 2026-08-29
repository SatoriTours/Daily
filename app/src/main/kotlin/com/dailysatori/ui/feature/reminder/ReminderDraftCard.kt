package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.time.Instant
import java.time.ZoneOffset

private enum class DraftPicker { START_DATE, END_DATE, FIRST_TIME, SLEEP_START, SLEEP_END, WORK_START, WORK_END, EVENING_START, CUTOFF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDraftCard(
    state: ReminderDraftUiState,
    onChange: (ReminderDraftUiState) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.cancelled) return
    var picker by remember { mutableStateOf<DraftPicker?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("确认提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.content,
                onValueChange = { onChange(state.editContent(it)) },
                label = { Text("提醒内容") },
                isError = ReminderDraftField.CONTENT in state.validationErrors,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("绝对时间：${state.absoluteDateTimeText.ifBlank { "请选择日期和时间" }}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { picker = DraftPicker.START_DATE }) { Text("开始 ${state.startDate ?: "未选"}") }
                TextButton(onClick = { picker = DraftPicker.END_DATE }) { Text("结束 ${state.endDate ?: "未选"}") }
                TextButton(onClick = { picker = DraftPicker.FIRST_TIME }) { Text("首次 ${state.firstReminderTime ?: "未选"}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(
                    "每天" to ReminderActiveDayRule.Daily,
                    "工作日" to ReminderActiveDayRule.Weekdays,
                    "连续日期" to ReminderActiveDayRule.ConsecutiveDateRange,
                    "自选" to ReminderActiveDayRule.SelectedWeekdays(kotlinx.datetime.DayOfWeek.entries.toSet()),
                ).forEach { (label, rule) ->
                    FilterChip(selected = state.activeDayRule == rule, onClick = { onChange(state.editActiveDayRule(rule)) }, label = { Text(label) })
                }
            }
            (state.activeDayRule as? ReminderActiveDayRule.SelectedWeekdays)?.let { selected ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    kotlinx.datetime.DayOfWeek.entries.forEach { day ->
                        FilterChip(selected = day in selected.days, onClick = { onChange(state.editActiveDayRule(ReminderActiveDayRule.SelectedWeekdays(if (day in selected.days) selected.days - day else selected.days + day))) }, label = { Text(day.name.take(2)) })
                    }
                }
            }
            Text("提醒强度")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf("强" to ReminderProfileSnapshot.strong(), "标准" to ReminderProfileSnapshot.standard(), "轻柔" to ReminderProfileSnapshot.gentle()).forEach { (label, profile) ->
                    FilterChip(selected = state.profile?.kind == profile.kind, onClick = { onChange(state.editProfile(profile.withRulesFrom(state.profile))) }, label = { Text(label) })
                }
            }
            state.profile?.let { profile ->
                ToggleRow("声音", profile.soundEnabled) { onChange(state.editProfile(profile.copy(soundEnabled = it))) }
                ToggleRow("振动", profile.vibrationEnabled) { onChange(state.editProfile(profile.copy(vibrationEnabled = it))) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ReminderImportance.entries.forEach { value -> FilterChip(selected = profile.importance == value, onClick = { onChange(state.editProfile(profile.copy(importance = value))) }, label = { Text(value.name) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ReminderLockScreenVisibility.entries.forEach { value -> FilterChip(selected = profile.lockScreenVisibility == value, onClick = { onChange(state.editProfile(profile.copy(lockScreenVisibility = value))) }, label = { Text(value.name) }) }
                }
                OutlinedTextField(
                    value = profile.daytimeDismissalBackoffMinutes.joinToString(","),
                    onValueChange = { input -> onChange(state.editProfile(profile.copy(daytimeDismissalBackoffMinutes = input.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..1_440 }.take(8)))) },
                    label = { Text("退避分钟") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = profile.eveningIntervalMinutes?.toString().orEmpty(),
                    onValueChange = { input -> onChange(state.editProfile(profile.copy(eveningIntervalMinutes = input.toIntOrNull()?.takeIf { it in 1..1_440 }))) },
                    label = { Text("晚间间隔分钟（留空使用固定时点）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    kotlinx.datetime.DayOfWeek.entries.forEach { day ->
                        FilterChip(selected = day in profile.workDays, onClick = { onChange(state.editProfile(profile.copy(workDays = if (day in profile.workDays) profile.workDays - day else profile.workDays + day))) }, label = { Text(day.name.take(2)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(onClick = { picker = DraftPicker.SLEEP_START }) { Text("免打扰开始 ${profile.sleepStart}") }
                    TextButton(onClick = { picker = DraftPicker.SLEEP_END }) { Text("结束 ${profile.sleepEnd}") }
                    TextButton(onClick = { picker = DraftPicker.WORK_START }) { Text("工作 ${profile.workStart}") }
                    TextButton(onClick = { picker = DraftPicker.WORK_END }) { Text("至 ${profile.workEnd}") }
                    TextButton(onClick = { picker = DraftPicker.EVENING_START }) { Text("晚间 ${profile.eveningStart}") }
                    TextButton(onClick = { picker = DraftPicker.CUTOFF }) { Text("截止 ${profile.dailyCutoff}") }
                }
            }
            state.scheduleWarning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel, enabled = !state.saving && !state.confirmed) { Text("取消") }
                Button(onClick = onConfirm, enabled = state.canConfirm) { Text(if (state.saving) "保存中…" else if (state.confirmed) "已确认" else "确认") }
            }
        }
    }

    when (val current = picker) {
        DraftPicker.START_DATE, DraftPicker.END_DATE -> DateDialog(
            initial = if (current == DraftPicker.START_DATE) state.startDate else state.endDate,
            onDismiss = { picker = null },
            onSelected = { selected ->
                onChange(if (current == DraftPicker.START_DATE) state.editDates(selected, state.endDate ?: selected) else state.editDates(state.startDate, selected))
                picker = null
            },
        )
        DraftPicker.FIRST_TIME, DraftPicker.SLEEP_START, DraftPicker.SLEEP_END, DraftPicker.WORK_START, DraftPicker.WORK_END, DraftPicker.EVENING_START, DraftPicker.CUTOFF -> {
            val profile = state.profile ?: ReminderProfileSnapshot.standard()
            val initial = when (current) {
                DraftPicker.FIRST_TIME -> state.firstReminderTime ?: LocalTime(9, 0)
                DraftPicker.SLEEP_START -> profile.sleepStart
                DraftPicker.SLEEP_END -> profile.sleepEnd
                DraftPicker.WORK_START -> profile.workStart
                DraftPicker.WORK_END -> profile.workEnd
                DraftPicker.EVENING_START -> profile.eveningStart
                else -> profile.dailyCutoff
            }
            TimeDialog(initial, { picker = null }) { selected ->
                onChange(when (current) {
                    DraftPicker.FIRST_TIME -> state.editFirstTime(selected)
                    DraftPicker.SLEEP_START -> state.editProfile(profile.copy(sleepStart = selected))
                    DraftPicker.SLEEP_END -> state.editProfile(profile.copy(sleepEnd = selected))
                    DraftPicker.WORK_START -> state.editProfile(profile.copy(workStart = selected))
                    DraftPicker.WORK_END -> state.editProfile(profile.copy(workEnd = selected))
                    DraftPicker.EVENING_START -> state.editProfile(profile.copy(eveningStart = selected))
                    else -> state.editProfile(profile.copy(dailyCutoff = selected))
                })
                picker = null
            }
        }
        null -> Unit
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun ReminderProfileSnapshot.withRulesFrom(old: ReminderProfileSnapshot?) = old?.let {
    copy(sleepStart = it.sleepStart, sleepEnd = it.sleepEnd, workDays = it.workDays, workStart = it.workStart, workEnd = it.workEnd, dailyCutoff = it.dailyCutoff)
} ?: this

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(initial: LocalDate?, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val millis = initial?.let { java.time.LocalDate.parse(it.toString()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val picker = rememberDatePickerState(initialSelectedDateMillis = millis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = { picker.selectedDateMillis?.let { onSelected(LocalDate.parse(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())) } }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = picker) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onSelected: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = { TimePicker(state = picker) },
        confirmButton = { TextButton(onClick = { onSelected(LocalTime(picker.hour, picker.minute)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
