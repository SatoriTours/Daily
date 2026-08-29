package com.dailysatori.ui.feature.settings.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.reminder.ReminderListScreen
import com.dailysatori.ui.theme.Spacing
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.koin.androidx.compose.koinViewModel

private enum class SettingsTimeField { SLEEP_START, SLEEP_END, WORK_START, WORK_END }
private enum class ProfileTimeField { EVENING_START, CUTOFF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    viewModel: ReminderSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var timeField by remember { mutableStateOf<SettingsTimeField?>(null) }
    val latestProfile = state.profiles.firstOrNull { it.id == state.defaultProfileId }?.snapshot
        ?.copy(sleepStart = state.sleepStart, sleepEnd = state.sleepEnd, workDays = state.workDays, workStart = state.workStart, workEnd = state.workEnd, soundEnabled = state.defaultSoundEnabled, vibrationEnabled = state.defaultVibrationEnabled, importance = state.defaultImportance, lockScreenVisibility = state.defaultLockScreenVisibility)

    AppScaffold(title = "提醒设置", onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            DeliveryAccessSection(state.deliveryAccess, viewModel)
            Text("默认配置与时段", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.profiles.forEach { profile ->
                    FilterChip(selected = profile.id == state.defaultProfileId, onClick = { viewModel.setDefaultProfile(profile.id) }, label = { Text(profile.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { timeField = SettingsTimeField.SLEEP_START }) { Text("睡眠开始 ${state.sleepStart}") }
                TextButton(onClick = { timeField = SettingsTimeField.SLEEP_END }) { Text("结束 ${state.sleepEnd}") }
                TextButton(onClick = { timeField = SettingsTimeField.WORK_START }) { Text("工作开始 ${state.workStart}") }
                TextButton(onClick = { timeField = SettingsTimeField.WORK_END }) { Text("结束 ${state.workEnd}") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("默认声音")
                Switch(state.defaultSoundEnabled, { viewModel.setDefaultDelivery(it, state.defaultVibrationEnabled, state.defaultImportance, state.defaultLockScreenVisibility) })
                Text("默认振动")
                Switch(state.defaultVibrationEnabled, { viewModel.setDefaultDelivery(state.defaultSoundEnabled, it, state.defaultImportance, state.defaultLockScreenVisibility) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ReminderImportance.entries.forEach { value -> FilterChip(selected = state.defaultImportance == value, onClick = { viewModel.setDefaultDelivery(state.defaultSoundEnabled, state.defaultVibrationEnabled, value, state.defaultLockScreenVisibility) }, label = { Text("优先级 ${value.name}") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ReminderLockScreenVisibility.entries.forEach { value -> FilterChip(selected = state.defaultLockScreenVisibility == value, onClick = { viewModel.setDefaultDelivery(state.defaultSoundEnabled, state.defaultVibrationEnabled, state.defaultImportance, value) }, label = { Text("锁屏 ${value.name}") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in state.workDays,
                        onClick = { viewModel.setWorkHours(if (day in state.workDays) state.workDays - day else state.workDays + day, state.workStart, state.workEnd) },
                        label = { Text(day.shortLabel()) },
                    )
                }
            }
            Text("提醒配置", style = MaterialTheme.typography.titleMedium)
            ProfileRows(state.profiles, viewModel)
            Button(onClick = { viewModel.editProfile() }) { Text("新建自定义配置") }
            Text("提醒", style = MaterialTheme.typography.titleMedium)
            ReminderListScreen(modifier = Modifier.weight(1f), latestProfile = latestProfile ?: com.dailysatori.service.reminder.ReminderProfileSnapshot.standard())
        }
    }

    timeField?.let { field ->
        val initial = when (field) {
            SettingsTimeField.SLEEP_START -> state.sleepStart
            SettingsTimeField.SLEEP_END -> state.sleepEnd
            SettingsTimeField.WORK_START -> state.workStart
            SettingsTimeField.WORK_END -> state.workEnd
        }
        SettingsTimeDialog(initial, { timeField = null }) { value ->
            when (field) {
                SettingsTimeField.SLEEP_START -> viewModel.setQuietHours(value, state.sleepEnd)
                SettingsTimeField.SLEEP_END -> viewModel.setQuietHours(state.sleepStart, value)
                SettingsTimeField.WORK_START -> viewModel.setWorkHours(state.workDays, value, state.workEnd)
                SettingsTimeField.WORK_END -> viewModel.setWorkHours(state.workDays, state.workStart, value)
            }
            timeField = null
        }
    }
    state.editor?.let { ProfileEditorDialog(it, state, viewModel) }
}

@Composable
private fun DeliveryAccessSection(access: ReminderDeliveryAccess, viewModel: ReminderSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (!access.notificationsAllowed) TextButton(onClick = viewModel::openNotificationSettings) { Text("通知权限未开启 · 前往系统设置") }
        if (access.usesFallbackTiming) TextButton(onClick = viewModel::openExactAlarmSettings) { Text("正在使用延迟容错调度 · 开启精确闹钟") }
        access.disabledChannelIds.forEach { id -> TextButton(onClick = { viewModel.openChannelSettings(id) }) { Text("通知渠道已关闭 · 修复 $id") } }
        if (access.notificationsAllowed && !access.usesFallbackTiming && access.disabledChannelIds.isEmpty()) Text("通知与精确调度可用", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProfileRows(profiles: List<ReminderProfile>, viewModel: ReminderSettingsViewModel) {
    profiles.forEach { profile ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${profile.name} · ${profile.snapshot.daytimeDismissalBackoffMinutes.joinToString(" / ")} 分钟")
            Row {
                TextButton(onClick = { viewModel.editProfile(profile, duplicate = true) }) { Text("复制") }
                if (profile.kind == ReminderProfileKind.CUSTOM) {
                    TextButton(onClick = { viewModel.editProfile(profile) }) { Text("编辑") }
                    TextButton(onClick = { viewModel.deleteProfile(profile) }) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(editor: ReminderProfileEditorState, settings: ReminderSettingsState, viewModel: ReminderSettingsViewModel) {
    var backoffText by remember(editor.id) { mutableStateOf(editor.daytimeBackoffMinutes.joinToString(",")) }
    var timeField by remember { mutableStateOf<ProfileTimeField?>(null) }
    AlertDialog(
        onDismissRequest = viewModel::dismissEditor,
        title = { Text("自定义提醒配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(editor.name, { viewModel.updateEditor(editor.copy(name = it)) }, label = { Text("名称") })
                OutlinedTextField(backoffText, {
                    backoffText = it
                    viewModel.updateEditor(editor.copy(daytimeBackoffMinutes = it.split(',').mapNotNull { value -> value.trim().toIntOrNull() }))
                }, label = { Text("白天退避分钟（逗号分隔）") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("声音"); Switch(editor.soundEnabled, { viewModel.updateEditor(editor.copy(soundEnabled = it)) }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("振动"); Switch(editor.vibrationEnabled, { viewModel.updateEditor(editor.copy(vibrationEnabled = it)) }) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { ReminderImportance.entries.forEach { value -> FilterChip(editor.importance == value, { viewModel.updateEditor(editor.copy(importance = value)) }, label = { Text(value.name) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { ReminderLockScreenVisibility.entries.forEach { value -> FilterChip(editor.lockScreenVisibility == value, { viewModel.updateEditor(editor.copy(lockScreenVisibility = value)) }, label = { Text(value.name) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(onClick = { timeField = ProfileTimeField.EVENING_START }) { Text("晚间开始 ${editor.eveningStart}") }
                    TextButton(onClick = { timeField = ProfileTimeField.CUTOFF }) { Text("截止 ${editor.dailyCutoff}") }
                }
                OutlinedTextField(
                    value = editor.eveningIntervalMinutes?.toString().orEmpty(),
                    onValueChange = { viewModel.updateEditor(editor.copy(eveningIntervalMinutes = it.toIntOrNull())) },
                    label = { Text("晚间间隔分钟") },
                )
                if (!editor.isValid) Text("请填写名称，并使用 1–1440 分钟的有效间隔", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveEditor, enabled = editor.toProfile(settings) != null) { Text("保存") } },
        dismissButton = { TextButton(onClick = viewModel::dismissEditor) { Text("取消") } },
    )
    timeField?.let { field ->
        SettingsTimeDialog(if (field == ProfileTimeField.EVENING_START) editor.eveningStart else editor.dailyCutoff, { timeField = null }) { value ->
            viewModel.updateEditor(if (field == ProfileTimeField.EVENING_START) editor.copy(eveningStart = value) else editor.copy(dailyCutoff = value))
            timeField = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onSelected: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = { TimePicker(picker) },
        confirmButton = { TextButton(onClick = { onSelected(LocalTime(picker.hour, picker.minute)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun DayOfWeek.shortLabel() = listOf("一", "二", "三", "四", "五", "六", "日")[value - 1]
