package com.dailysatori.ui.feature.settings.reminder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dailysatori.R
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.service.reminder.*
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.reminder.label
import com.dailysatori.ui.feature.reminder.shortLabel
import com.dailysatori.ui.theme.Spacing
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.koin.androidx.compose.koinViewModel

private enum class SettingsTimeField { SLEEP_START, SLEEP_END, WORK_START, WORK_END }
internal enum class ProfileTimeField { EVENING_START, CUTOFF }

@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    onAddReminder: () -> Unit = {},
    onOpenReminder: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ReminderSettingsViewModel = koinViewModel(),
    initialReminderId: String? = null,
) {
    var managingProfiles by remember { mutableStateOf(false) }
    if (managingProfiles) {
        ReminderProfileManagementScreen(onBack = { managingProfiles = false }, viewModel = viewModel)
        return
    }
    val state by viewModel.state.collectAsState()
    var timeField by remember { mutableStateOf<SettingsTimeField?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDeliveryAccess() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    AppScaffold(title = stringResource(R.string.reminder_settings_title), onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().padding(horizontal = Spacing.m).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            DefaultRhythmCard(state, viewModel)
            NotificationEffectCard(state, viewModel)
            QuietRulesCard(state, viewModel) { timeField = it }
            AdvancedCard(state.deliveryAccess, viewModel) { managingProfiles = true }
        }
    }
    timeField?.let { field ->
        val initial = when (field) {
            SettingsTimeField.SLEEP_START -> state.sleepStart; SettingsTimeField.SLEEP_END -> state.sleepEnd
            SettingsTimeField.WORK_START -> state.workStart; SettingsTimeField.WORK_END -> state.workEnd
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
}

@Composable private fun DefaultRhythmCard(state: ReminderSettingsState, viewModel: ReminderSettingsViewModel) = SettingsCard(R.string.reminder_default_section) {
    ChoiceRow(state.profiles, { it.id == state.defaultProfileId }, { profile -> profile.localizedName() }) { viewModel.setDefaultProfile(it.id) }
    Text(state.defaultRhythm.eveningSummary, style = MaterialTheme.typography.bodyMedium)
}

@Composable private fun NotificationEffectCard(state: ReminderSettingsState, viewModel: ReminderSettingsViewModel) = SettingsCard(R.string.reminder_profile_strength) {
    ToggleRow(stringResource(R.string.reminder_default_sound), state.defaultSoundEnabled) { viewModel.setDefaultDelivery(it, state.defaultVibrationEnabled, state.defaultImportance, state.defaultLockScreenVisibility) }
    ToggleRow(stringResource(R.string.reminder_default_vibration), state.defaultVibrationEnabled) { viewModel.setDefaultDelivery(state.defaultSoundEnabled, it, state.defaultImportance, state.defaultLockScreenVisibility) }
    ChoiceRow(ReminderImportance.entries, { it == state.defaultImportance }, { it.label() }) { viewModel.setDefaultDelivery(state.defaultSoundEnabled, state.defaultVibrationEnabled, it, state.defaultLockScreenVisibility) }
    ChoiceRow(ReminderLockScreenVisibility.entries, { it == state.defaultLockScreenVisibility }, { it.label() }) { viewModel.setDefaultDelivery(state.defaultSoundEnabled, state.defaultVibrationEnabled, state.defaultImportance, it) }
}

@Composable private fun QuietRulesCard(state: ReminderSettingsState, viewModel: ReminderSettingsViewModel, onSelectTime: (SettingsTimeField) -> Unit) = SettingsCard(R.string.reminder_default_section) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TextButton({ onSelectTime(SettingsTimeField.SLEEP_START) }) { Text(stringResource(R.string.reminder_sleep_start, state.sleepStart)) }
        TextButton({ onSelectTime(SettingsTimeField.SLEEP_END) }) { Text(stringResource(R.string.reminder_sleep_end, state.sleepEnd)) }
        TextButton({ onSelectTime(SettingsTimeField.WORK_START) }) { Text(stringResource(R.string.reminder_work_start, state.workStart)) }
        TextButton({ onSelectTime(SettingsTimeField.WORK_END) }) { Text(stringResource(R.string.reminder_work_end, state.workEnd)) }
    }
    ChoiceRow(DayOfWeek.entries, state.workDays, { it.shortLabel() }) { day -> viewModel.setWorkHours(if (day in state.workDays) state.workDays - day else state.workDays + day, state.workStart, state.workEnd) }
}

@Composable private fun AdvancedCard(access: ReminderDeliveryAccess, viewModel: ReminderSettingsViewModel, onManageProfiles: () -> Unit) = SettingsCard(R.string.reminder_profiles_section) {
    TextButton(onManageProfiles) { Text(stringResource(R.string.reminder_profiles_section)) }
    DeliveryAccessSection(access, viewModel)
}

@Composable private fun SettingsCard(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) { Text(stringResource(title), style = MaterialTheme.typography.titleMedium); content() } }
}

@Composable private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked, onChange) }
}

@Composable private fun <T> ChoiceRow(values: Iterable<T>, isSelected: (T) -> Boolean, label: @Composable (T) -> String, onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { values.forEach { value -> FilterChip(isSelected(value), { onSelected(value) }, label = { Text(label(value)) }) } }
}

@Composable private fun ChoiceRow(values: Iterable<DayOfWeek>, selected: Set<DayOfWeek>, label: @Composable (DayOfWeek) -> String, onSelected: (DayOfWeek) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { values.forEach { value -> FilterChip(value in selected, { onSelected(value) }, label = { Text(label(value)) }) } }
}

@Composable private fun DeliveryAccessSection(access: ReminderDeliveryAccess, viewModel: ReminderSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (!access.notificationsAllowed) TextButton(viewModel::openNotificationSettings) { Text(stringResource(R.string.reminder_notifications_settings_action)) }
        if (access.usesFallbackTiming) TextButton(viewModel::openExactAlarmSettings) { Text(stringResource(R.string.reminder_exact_alarm_settings_action)) }
        access.disabledChannelIds.forEach { id -> TextButton({ viewModel.openChannelSettings(id) }) { Text(stringResource(R.string.reminder_channel_settings_action, id)) } }
        if (access.notificationsAllowed && !access.usesFallbackTiming && access.disabledChannelIds.isEmpty()) Text(stringResource(R.string.reminder_delivery_ready), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun ProfileEditorDialog(editor: ReminderProfileEditorState, settings: ReminderSettingsState, viewModel: ReminderSettingsViewModel) {
    var timeField by remember { mutableStateOf<ProfileTimeField?>(null) }
    AlertDialog(onDismissRequest = viewModel::dismissEditor, title = { Text(stringResource(R.string.reminder_custom_profile_title)) }, text = { ProfileEditorFields(editor, viewModel) { timeField = it } }, confirmButton = { TextButton(viewModel::saveEditor, enabled = editor.toProfile(settings) != null) { Text(stringResource(R.string.reminder_action_save)) } }, dismissButton = { TextButton(viewModel::dismissEditor) { Text(stringResource(R.string.reminder_cancel)) } })
    timeField?.let { field -> SettingsTimeDialog(if (field == ProfileTimeField.EVENING_START) editor.eveningStart else editor.dailyCutoff, { timeField = null }) { value -> viewModel.updateEditor(if (field == ProfileTimeField.EVENING_START) editor.copy(eveningStart = value) else editor.copy(dailyCutoff = value)); timeField = null } }
}

@Composable private fun ProfileEditorFields(editor: ReminderProfileEditorState, viewModel: ReminderSettingsViewModel, onSelectTime: (ProfileTimeField) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(editor.name, { viewModel.updateEditor(editor.copy(name = it)) }, label = { Text(stringResource(R.string.reminder_profile_name)) })
        OutlinedTextField(editor.daytimeBackoffInput, { viewModel.updateEditor(editor.editBackoffInput(it)) }, label = { Text(stringResource(R.string.reminder_backoff_input)) })
        ToggleRow(stringResource(R.string.reminder_sound), editor.soundEnabled) { viewModel.updateEditor(editor.copy(soundEnabled = it)) }; ToggleRow(stringResource(R.string.reminder_vibration), editor.vibrationEnabled) { viewModel.updateEditor(editor.copy(vibrationEnabled = it)) }
        ChoiceRow(ReminderImportance.entries, { it == editor.importance }, { it.label() }) { viewModel.updateEditor(editor.copy(importance = it)) }; ChoiceRow(ReminderLockScreenVisibility.entries, { it == editor.lockScreenVisibility }, { it.label() }) { viewModel.updateEditor(editor.copy(lockScreenVisibility = it)) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { TextButton({ onSelectTime(ProfileTimeField.EVENING_START) }) { Text(stringResource(R.string.reminder_evening_start, editor.eveningStart)) }; TextButton({ onSelectTime(ProfileTimeField.CUTOFF) }) { Text(stringResource(R.string.reminder_cutoff, editor.dailyCutoff)) } }
        OutlinedTextField(editor.eveningIntervalMinutes?.toString().orEmpty(), { viewModel.updateEditor(editor.copy(eveningIntervalMinutes = it.toIntOrNull())) }, label = { Text(stringResource(R.string.reminder_evening_interval)) })
        if (!editor.isValid) Text(stringResource(R.string.reminder_profile_invalid), color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class) @Composable internal fun SettingsTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onSelected: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.reminder_select_time)) }, text = { TimePicker(picker) }, confirmButton = { TextButton({ onSelected(LocalTime(picker.hour, picker.minute)) }) { Text(stringResource(R.string.reminder_ok)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.reminder_cancel)) } })
}

@Composable internal fun ReminderProfile.localizedName(): String = when (kind) { ReminderProfileKind.STRONG -> stringResource(R.string.reminder_profile_strong); ReminderProfileKind.STANDARD -> stringResource(R.string.reminder_profile_standard); ReminderProfileKind.GENTLE -> stringResource(R.string.reminder_profile_gentle); ReminderProfileKind.CUSTOM -> name }
