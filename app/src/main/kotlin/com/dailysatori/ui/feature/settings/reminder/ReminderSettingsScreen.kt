package com.dailysatori.ui.feature.settings.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.R
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.reminder.ReminderListScreen
import com.dailysatori.ui.feature.reminder.label
import com.dailysatori.ui.feature.reminder.shortLabel
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
    initialReminderId: String? = null,
) {
    val state by viewModel.state.collectAsState()
    var timeField by remember { mutableStateOf<SettingsTimeField?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestProfile = state.profiles.firstOrNull { it.id == state.defaultProfileId }?.snapshot
        ?.copy(sleepStart = state.sleepStart, sleepEnd = state.sleepEnd, workDays = state.workDays, workStart = state.workStart, workEnd = state.workEnd, soundEnabled = state.defaultSoundEnabled, vibrationEnabled = state.defaultVibrationEnabled, importance = state.defaultImportance, lockScreenVisibility = state.defaultLockScreenVisibility)

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDeliveryAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppScaffold(title = stringResource(R.string.reminder_settings_title), onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().padding(horizontal = Spacing.m).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            DeliveryAccessSection(state.deliveryAccess, viewModel)
            Text(stringResource(R.string.reminder_default_section), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.profiles.forEach { profile ->
                    FilterChip(selected = profile.id == state.defaultProfileId, onClick = { viewModel.setDefaultProfile(profile.id) }, label = { Text(profile.localizedName()) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { timeField = SettingsTimeField.SLEEP_START }) { Text(stringResource(R.string.reminder_sleep_start, state.sleepStart)) }
                TextButton(onClick = { timeField = SettingsTimeField.SLEEP_END }) { Text(stringResource(R.string.reminder_sleep_end, state.sleepEnd)) }
                TextButton(onClick = { timeField = SettingsTimeField.WORK_START }) { Text(stringResource(R.string.reminder_work_start, state.workStart)) }
                TextButton(onClick = { timeField = SettingsTimeField.WORK_END }) { Text(stringResource(R.string.reminder_work_end, state.workEnd)) }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.reminder_default_sound))
                    Switch(state.defaultSoundEnabled, { viewModel.setDefaultDelivery(it, state.defaultVibrationEnabled, state.defaultImportance, state.defaultLockScreenVisibility) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.reminder_default_vibration))
                    Switch(state.defaultVibrationEnabled, { viewModel.setDefaultDelivery(state.defaultSoundEnabled, it, state.defaultImportance, state.defaultLockScreenVisibility) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ReminderImportance.entries.forEach { value -> FilterChip(selected = state.defaultImportance == value, onClick = { viewModel.setDefaultDelivery(state.defaultSoundEnabled, state.defaultVibrationEnabled, value, state.defaultLockScreenVisibility) }, label = { Text(value.label()) }) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ReminderLockScreenVisibility.entries.forEach { value -> FilterChip(selected = state.defaultLockScreenVisibility == value, onClick = { viewModel.setDefaultDelivery(state.defaultSoundEnabled, state.defaultVibrationEnabled, state.defaultImportance, value) }, label = { Text(value.label()) }) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in state.workDays,
                        onClick = { viewModel.setWorkHours(if (day in state.workDays) state.workDays - day else state.workDays + day, state.workStart, state.workEnd) },
                        label = { Text(day.shortLabel()) },
                    )
                }
            }
            Text(stringResource(R.string.reminder_profiles_section), style = MaterialTheme.typography.titleMedium)
            ProfileRows(state.profiles, viewModel)
            Button(onClick = { viewModel.editProfile() }) { Text(stringResource(R.string.reminder_new_custom_profile)) }
            Text(stringResource(R.string.reminder_list_section), style = MaterialTheme.typography.titleMedium)
            ReminderListScreen(
                latestProfile = latestProfile ?: com.dailysatori.service.reminder.ReminderProfileSnapshot.standard(),
                initialReminderId = initialReminderId,
            )
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
        if (!access.notificationsAllowed) TextButton(onClick = viewModel::openNotificationSettings) { Text(stringResource(R.string.reminder_notifications_settings_action)) }
        if (access.usesFallbackTiming) TextButton(onClick = viewModel::openExactAlarmSettings) { Text(stringResource(R.string.reminder_exact_alarm_settings_action)) }
        access.disabledChannelIds.forEach { id -> TextButton(onClick = { viewModel.openChannelSettings(id) }) { Text(stringResource(R.string.reminder_channel_settings_action, id)) } }
        if (access.notificationsAllowed && !access.usesFallbackTiming && access.disabledChannelIds.isEmpty()) Text(stringResource(R.string.reminder_delivery_ready), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProfileRows(profiles: List<ReminderProfile>, viewModel: ReminderSettingsViewModel) {
    profiles.forEach { profile ->
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reminder_profile_summary, profile.localizedName(), profile.snapshot.daytimeDismissalBackoffMinutes.joinToString(" / ")))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                val displayName = profile.localizedName()
                TextButton(onClick = { viewModel.editProfile(profile, duplicate = true, displayName = displayName) }) { Text(stringResource(R.string.reminder_action_duplicate)) }
                if (profile.kind == ReminderProfileKind.CUSTOM) {
                    TextButton(onClick = { viewModel.editProfile(profile) }) { Text(stringResource(R.string.reminder_action_edit)) }
                    TextButton(onClick = { viewModel.deleteProfile(profile) }) { Text(stringResource(R.string.reminder_action_delete)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(editor: ReminderProfileEditorState, settings: ReminderSettingsState, viewModel: ReminderSettingsViewModel) {
    var timeField by remember { mutableStateOf<ProfileTimeField?>(null) }
    AlertDialog(
        onDismissRequest = viewModel::dismissEditor,
        title = { Text(stringResource(R.string.reminder_custom_profile_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(editor.name, { viewModel.updateEditor(editor.copy(name = it)) }, label = { Text(stringResource(R.string.reminder_profile_name)) })
                OutlinedTextField(editor.daytimeBackoffInput, { viewModel.updateEditor(editor.editBackoffInput(it)) }, label = { Text(stringResource(R.string.reminder_backoff_input)) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.reminder_sound)); Switch(editor.soundEnabled, { viewModel.updateEditor(editor.copy(soundEnabled = it)) }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.reminder_vibration)); Switch(editor.vibrationEnabled, { viewModel.updateEditor(editor.copy(vibrationEnabled = it)) }) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { ReminderImportance.entries.forEach { value -> FilterChip(editor.importance == value, { viewModel.updateEditor(editor.copy(importance = value)) }, label = { Text(value.label()) }) } }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { ReminderLockScreenVisibility.entries.forEach { value -> FilterChip(editor.lockScreenVisibility == value, { viewModel.updateEditor(editor.copy(lockScreenVisibility = value)) }, label = { Text(value.label()) }) } }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(onClick = { timeField = ProfileTimeField.EVENING_START }) { Text(stringResource(R.string.reminder_evening_start, editor.eveningStart)) }
                    TextButton(onClick = { timeField = ProfileTimeField.CUTOFF }) { Text(stringResource(R.string.reminder_cutoff, editor.dailyCutoff)) }
                }
                OutlinedTextField(
                    value = editor.eveningIntervalMinutes?.toString().orEmpty(),
                    onValueChange = { viewModel.updateEditor(editor.copy(eveningIntervalMinutes = it.toIntOrNull())) },
                    label = { Text(stringResource(R.string.reminder_evening_interval)) },
                )
                if (!editor.isValid) Text(stringResource(R.string.reminder_profile_invalid), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveEditor, enabled = editor.toProfile(settings) != null) { Text(stringResource(R.string.reminder_action_save)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissEditor) { Text(stringResource(R.string.reminder_cancel)) } },
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
        title = { Text(stringResource(R.string.reminder_select_time)) },
        text = { TimePicker(picker) },
        confirmButton = { TextButton(onClick = { onSelected(LocalTime(picker.hour, picker.minute)) }) { Text(stringResource(R.string.reminder_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reminder_cancel)) } },
    )
}

@Composable
private fun ReminderProfile.localizedName(): String = when (kind) {
    ReminderProfileKind.STRONG -> stringResource(R.string.reminder_profile_strong)
    ReminderProfileKind.STANDARD -> stringResource(R.string.reminder_profile_standard)
    ReminderProfileKind.GENTLE -> stringResource(R.string.reminder_profile_gentle)
    ReminderProfileKind.CUSTOM -> name
}
