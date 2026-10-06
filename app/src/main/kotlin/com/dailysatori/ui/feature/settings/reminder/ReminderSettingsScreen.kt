package com.dailysatori.ui.feature.settings.reminder

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import com.dailysatori.ui.component.settings.SettingsValueRow
import com.dailysatori.service.i18n.I18nService
import org.koin.compose.koinInject
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
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
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.component.settings.SettingsSectionCard
import com.dailysatori.ui.feature.reminder.label
import com.dailysatori.ui.feature.reminder.shortLabel
import com.dailysatori.ui.theme.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.koin.androidx.compose.koinViewModel

private enum class SettingsChoiceField { PROFILE, IMPORTANCE, VISIBILITY, WORK_DAYS }
private enum class SettingsTimeField { SLEEP_START, SLEEP_END, WORK_START, WORK_END }
internal enum class ProfileTimeField { EVENING_START, CUTOFF }

@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    viewModel: ReminderSettingsViewModel = koinViewModel(),
    initialReminderId: String? = null,
    initialSection: String? = null,
) {
    var profilesExpanded by rememberSaveable { mutableStateOf(false) }
    var choice by rememberSaveable { mutableStateOf<SettingsChoiceField?>(null) }
    val i18n: I18nService = koinInject()
    val state by viewModel.state.collectAsState()
    var timeField by remember { mutableStateOf<SettingsTimeField?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDeliveryAccess() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    AppScaffold(title = stringResource(R.string.reminder_settings_title), onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState(
            initial = if (initialSection == "permissions") Int.MAX_VALUE else 0)).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Text(stringResource(R.string.settings_immediate_changes), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.primarySections.forEach { section ->
                when (section.id) {
                    "default-rhythm" -> DefaultRhythmCard(state) { choice = SettingsChoiceField.PROFILE }
                    "notification-effect" -> NotificationEffectCard(state, viewModel) { choice = it }
                    "quiet-rules" -> QuietRulesCard(state, { timeField = it }) { choice = SettingsChoiceField.WORK_DAYS }
                    "advanced" -> AccessAndProfilesCards(state, viewModel, profilesExpanded) { profilesExpanded = !profilesExpanded }
                }
            }
        }
    }
    state.editor?.let { ProfileEditorDialog(it, state, viewModel) }
    choice?.let { field ->
        val dismiss = { choice = null }
        when (field) {
            SettingsChoiceField.PROFILE -> SettingsChoiceDialog(
                stringResource(R.string.reminder_profiles_section), state.profiles,
                { it.id == state.defaultProfileId }, { it.localizedName() }, dismiss,
            ) { viewModel.setDefaultProfile(it.id) }
            SettingsChoiceField.IMPORTANCE -> SettingsChoiceDialog(
                i18n.t("settings_design.importance"), ReminderImportance.entries,
                { it == state.defaultImportance }, { it.label() }, dismiss,
            ) { viewModel.setDefaultDelivery(state.defaultSoundEnabled, state.defaultVibrationEnabled, it, state.defaultLockScreenVisibility) }
            SettingsChoiceField.VISIBILITY -> SettingsChoiceDialog(
                i18n.t("settings_design.lock_screen"), ReminderLockScreenVisibility.entries,
                { it == state.defaultLockScreenVisibility }, { it.label() }, dismiss,
            ) { viewModel.setDefaultDelivery(state.defaultSoundEnabled, state.defaultVibrationEnabled, state.defaultImportance, it) }
            SettingsChoiceField.WORK_DAYS -> SettingsChoiceDialog(
                i18n.t("settings_design.work_days"), DayOfWeek.entries, { it in state.workDays },
                { it.shortLabel() }, dismiss, multiple = true,
            ) { day -> viewModel.setWorkHours(if (day in state.workDays) state.workDays - day else state.workDays + day, state.workStart, state.workEnd) }
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

@Composable private fun DefaultRhythmCard(state: ReminderSettingsState, onSelect: () -> Unit) =
    SettingsCard(R.string.reminder_settings_default_rhythm) {
        val profile = state.profiles.firstOrNull { it.id == state.defaultProfileId }
        SettingsValueRow(stringResource(R.string.reminder_profiles_section), profile?.localizedName().orEmpty(), onSelect)
        val rhythm = state.defaultRhythm
        val interval = rhythm.intervalMinutes?.let { minutes ->
            if (minutes == 60) stringResource(R.string.reminder_settings_interval_hourly)
            else stringResource(R.string.reminder_settings_interval_minutes, minutes)
        }
        Text(interval?.let { stringResource(R.string.reminder_settings_rhythm_summary, rhythm.timeRange, it) } ?: rhythm.timeRange,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable private fun NotificationEffectCard(state: ReminderSettingsState, viewModel: ReminderSettingsViewModel,
    onChoose: (SettingsChoiceField) -> Unit) = SettingsCard(R.string.reminder_settings_notification_effect) {
    val i18n: I18nService = koinInject()
    ToggleRow(stringResource(R.string.reminder_default_sound), state.defaultSoundEnabled) {
        viewModel.setDefaultDelivery(it, state.defaultVibrationEnabled, state.defaultImportance, state.defaultLockScreenVisibility)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    ToggleRow(stringResource(R.string.reminder_default_vibration), state.defaultVibrationEnabled) {
        viewModel.setDefaultDelivery(state.defaultSoundEnabled, it, state.defaultImportance, state.defaultLockScreenVisibility)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsValueRow(i18n.t("settings_design.importance"), state.defaultImportance.label(),
        { onChoose(SettingsChoiceField.IMPORTANCE) })
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsValueRow(i18n.t("settings_design.lock_screen"), state.defaultLockScreenVisibility.label(),
        { onChoose(SettingsChoiceField.VISIBILITY) })
}

@Composable private fun QuietRulesCard(state: ReminderSettingsState, onSelectTime: (SettingsTimeField) -> Unit,
    onSelectDays: () -> Unit) = SettingsCard(R.string.reminder_settings_quiet_rules) {
    val i18n: I18nService = koinInject()
    SettingsValueRow(stringResource(R.string.reminder_sleep_start_label), state.sleepStart.toString(),
        { onSelectTime(SettingsTimeField.SLEEP_START) })
    SettingsValueRow(stringResource(R.string.reminder_sleep_end_label), state.sleepEnd.toString(),
        { onSelectTime(SettingsTimeField.SLEEP_END) })
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsValueRow(i18n.t("settings_design.work_days"),
        DayOfWeek.entries.filter { it in state.workDays }.map { it.shortLabel() }.joinToString(" "),
        onSelectDays)
    SettingsValueRow(stringResource(R.string.reminder_work_start_label), state.workStart.toString(),
        { onSelectTime(SettingsTimeField.WORK_START) })
    SettingsValueRow(stringResource(R.string.reminder_work_end_label), state.workEnd.toString(),
        { onSelectTime(SettingsTimeField.WORK_END) })
}

@Composable private fun AccessAndProfilesCards(state: ReminderSettingsState, viewModel: ReminderSettingsViewModel,
    expanded: Boolean, onExpand: () -> Unit) {
    val i18n: I18nService = koinInject()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        SettingsCard(R.string.reminder_profiles_section) {
            SettingsValueRow(stringResource(R.string.reminder_settings_manage_profiles),
                i18n.t(if (expanded) "settings_design.collapse" else "settings_design.expand"), onExpand)
            if (expanded) ReminderProfilesContent(state, viewModel)
        }
        SettingsCard(R.string.reminder_settings_delivery_access) { DeliveryAccessSection(state.deliveryAccess, viewModel) }
    }
}

@Composable private fun <T> SettingsChoiceDialog(title: String, values: Iterable<T>, selected: (T) -> Boolean,
    label: @Composable (T) -> String, onDismiss: () -> Unit, multiple: Boolean = false, onSelected: (T) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.heightIn(max = Spacing.xxl * 8).verticalScroll(rememberScrollState())) {
            values.forEach { value ->
                Row(Modifier.fillMaxWidth().selectable(selected(value), role = if (multiple) Role.Checkbox else Role.RadioButton,
                    onClick = { onSelected(value); if (!multiple) onDismiss() })
                    .heightIn(min = Height.listItem), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (multiple) Checkbox(selected(value), onCheckedChange = null)
                    else RadioButton(selected(value), onClick = null)
                    Text(label(value), Modifier.padding(start = Spacing.s))
                }
            }
        }
    }, confirmButton = {
        TextButton(onDismiss) { Text(stringResource(if (multiple) R.string.reminder_ok else R.string.reminder_cancel)) }
    })
}

@Composable private fun SettingsCard(title: Int, content: @Composable ColumnScope.() -> Unit) {
    SettingsSectionCard(stringResource(title)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
    }
}

@Composable private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange)
        .heightIn(min = Height.listItem), horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null)
    }
}

@Composable private fun <T> ChoiceRow(values: Iterable<T>, isSelected: (T) -> Boolean, label: @Composable (T) -> String, onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { values.forEach { value -> FilterChip(isSelected(value), { onSelected(value) }, label = { Text(label(value)) }) } }
}

@Composable private fun ChoiceRow(values: Iterable<DayOfWeek>, selected: Set<DayOfWeek>, label: @Composable (DayOfWeek) -> String, onSelected: (DayOfWeek) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { values.forEach { value -> FilterChip(value in selected, { onSelected(value) }, label = { Text(label(value)) }) } }
}

@Composable private fun DeliveryAccessSection(access: ReminderDeliveryAccess, viewModel: ReminderSettingsViewModel) {
    val i18n: I18nService = koinInject()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsValueRow(i18n.t("phone.reminder_notifications"),
            i18n.t(if (access.notificationsAllowed) "phone.permission_ready" else "phone.notify_permission"),
            viewModel::openNotificationSettings)
        SettingsValueRow(i18n.t("phone.reminder_timing"),
            i18n.t(if (access.usesFallbackTiming) "phone.alarm_permission" else "phone.permission_ready"),
            viewModel::openExactAlarmSettings)
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
