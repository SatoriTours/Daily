package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.data.repository.ReminderProfile
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
    profiles: List<ReminderProfile> = defaultCardProfiles(),
) {
    if (state.cancelled) return
    var picker by remember { mutableStateOf<DraftPicker?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.reminder_draft_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.content,
                onValueChange = { onChange(state.editContent(it)) },
                label = { Text(stringResource(R.string.reminder_content_label)) },
                isError = ReminderDraftField.CONTENT in state.validationErrors,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.reminder_absolute_time, state.absoluteDateTimeText.ifBlank { stringResource(R.string.reminder_select_date_time) }), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val missing = stringResource(R.string.reminder_not_selected)
                TextButton(onClick = { picker = DraftPicker.START_DATE }) { Text(stringResource(R.string.reminder_start_value, state.startDate ?: missing)) }
                TextButton(onClick = { picker = DraftPicker.END_DATE }) { Text(stringResource(R.string.reminder_end_value, state.endDate ?: missing)) }
                TextButton(onClick = { picker = DraftPicker.FIRST_TIME }) { Text(stringResource(R.string.reminder_first_value, state.firstReminderTime ?: missing)) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(
                    stringResource(R.string.reminder_rule_daily) to ReminderActiveDayRule.Daily,
                    stringResource(R.string.reminder_rule_weekdays) to ReminderActiveDayRule.Weekdays,
                    stringResource(R.string.reminder_rule_range) to ReminderActiveDayRule.ConsecutiveDateRange,
                    stringResource(R.string.reminder_rule_selected) to ReminderActiveDayRule.SelectedWeekdays(kotlinx.datetime.DayOfWeek.entries.toSet()),
                ).forEach { (label, rule) ->
                    FilterChip(selected = state.activeDayRule == rule, onClick = { onChange(state.editActiveDayRule(rule)) }, label = { Text(label) })
                }
            }
            (state.activeDayRule as? ReminderActiveDayRule.SelectedWeekdays)?.let { selected ->
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    kotlinx.datetime.DayOfWeek.entries.forEach { day ->
                        FilterChip(selected = day in selected.days, onClick = { onChange(state.editActiveDayRule(ReminderActiveDayRule.SelectedWeekdays(if (day in selected.days) selected.days - day else selected.days + day))) }, label = { Text(day.shortLabel()) })
                    }
                }
                if (selected.days.isEmpty()) Text(stringResource(R.string.reminder_selected_days_required), color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.reminder_recurrence_title))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(
                    R.string.reminder_list_repeat_once to ReminderRecurrence.Once,
                    R.string.reminder_list_repeat_monthly to state.startDate?.let { ReminderRecurrence.Monthly(it.dayOfMonth) },
                    R.string.reminder_list_repeat_yearly to state.startDate?.let { ReminderRecurrence.Yearly(it.monthNumber, it.dayOfMonth, LeapDayPolicy.FEBRUARY_28) },
                    R.string.reminder_recurrence_consecutive to ReminderRecurrence.Once,
                ).forEach { (label, recurrence) ->
                    if (recurrence != null) FilterChip(
                        selected = if (label == R.string.reminder_recurrence_consecutive) state.activeDayRule is ReminderActiveDayRule.ConsecutiveDateRange else state.recurrence::class == recurrence::class,
                        onClick = { onChange(state.selectRecurrenceMode(recurrence, label == R.string.reminder_recurrence_consecutive)) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Text(stringResource(R.string.reminder_profile_strength))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                profiles.forEach { profile ->
                    FilterChip(
                        selected = state.profileId == profile.id,
                        onClick = { onChange(state.selectProfile(profile)) },
                        label = { Text(profile.cardLabel()) },
                    )
                }
            }
            state.profile?.let { profile ->
                ToggleRow(stringResource(R.string.reminder_sound), profile.soundEnabled) { onChange(state.updateProfile(profile.copy(soundEnabled = it))) }
                ToggleRow(stringResource(R.string.reminder_vibration), profile.vibrationEnabled) { onChange(state.updateProfile(profile.copy(vibrationEnabled = it))) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ReminderImportance.entries.forEach { value -> FilterChip(selected = profile.importance == value, onClick = { onChange(state.updateProfile(profile.copy(importance = value))) }, label = { Text(value.label()) }) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ReminderLockScreenVisibility.entries.forEach { value -> FilterChip(selected = profile.lockScreenVisibility == value, onClick = { onChange(state.updateProfile(profile.copy(lockScreenVisibility = value))) }, label = { Text(value.label()) }) }
                }
                OutlinedTextField(
                    value = state.daytimeBackoffInput,
                    onValueChange = { input -> onChange(state.editBackoffInput(input)) },
                    label = { Text(stringResource(R.string.reminder_backoff_minutes)) },
                    isError = ReminderDraftField.ADVANCED_PROFILE in state.validationErrors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.eveningIntervalInput,
                    onValueChange = { input -> onChange(state.editEveningIntervalInput(input)) },
                    label = { Text(stringResource(R.string.reminder_evening_interval_optional)) },
                    isError = ReminderDraftField.ADVANCED_PROFILE in state.validationErrors,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ReminderDraftField.ADVANCED_PROFILE in state.validationErrors) {
                    Text(stringResource(R.string.reminder_profile_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    kotlinx.datetime.DayOfWeek.entries.forEach { day ->
                        FilterChip(selected = day in profile.workDays, onClick = { onChange(state.updateProfile(profile.copy(workDays = if (day in profile.workDays) profile.workDays - day else profile.workDays + day))) }, label = { Text(day.shortLabel()) })
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(onClick = { picker = DraftPicker.SLEEP_START }) { Text(stringResource(R.string.reminder_sleep_start, profile.sleepStart)) }
                    TextButton(onClick = { picker = DraftPicker.SLEEP_END }) { Text(stringResource(R.string.reminder_sleep_end, profile.sleepEnd)) }
                    TextButton(onClick = { picker = DraftPicker.WORK_START }) { Text(stringResource(R.string.reminder_work_start, profile.workStart)) }
                    TextButton(onClick = { picker = DraftPicker.WORK_END }) { Text(stringResource(R.string.reminder_work_end, profile.workEnd)) }
                    TextButton(onClick = { picker = DraftPicker.EVENING_START }) { Text(stringResource(R.string.reminder_evening_start, profile.eveningStart)) }
                    TextButton(onClick = { picker = DraftPicker.CUTOFF }) { Text(stringResource(R.string.reminder_cutoff, profile.dailyCutoff)) }
                }
            }
            state.notice?.let { Text(it.label(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel, enabled = !state.saving && !state.confirmed) { Text(stringResource(R.string.reminder_cancel)) }
                Button(onClick = onConfirm, enabled = state.canConfirm) { Text(stringResource(if (state.saving) R.string.reminder_saving else if (state.confirmed) R.string.reminder_confirmed else R.string.reminder_confirm)) }
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
                    DraftPicker.SLEEP_START -> state.updateProfile(profile.copy(sleepStart = selected))
                    DraftPicker.SLEEP_END -> state.updateProfile(profile.copy(sleepEnd = selected))
                    DraftPicker.WORK_START -> state.updateProfile(profile.copy(workStart = selected))
                    DraftPicker.WORK_END -> state.updateProfile(profile.copy(workEnd = selected))
                    DraftPicker.EVENING_START -> state.updateProfile(profile.copy(eveningStart = selected))
                    else -> state.updateProfile(profile.copy(dailyCutoff = selected))
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

private fun defaultCardProfiles() = listOf(
    ReminderProfile("builtin-strong", "Strong", ReminderProfileKind.STRONG, ReminderProfileSnapshot.strong()),
    ReminderProfile("builtin-standard", "Standard", ReminderProfileKind.STANDARD, ReminderProfileSnapshot.standard()),
    ReminderProfile("builtin-gentle", "Gentle", ReminderProfileKind.GENTLE, ReminderProfileSnapshot.gentle()),
)

@Composable
private fun ReminderProfile.cardLabel(): String = when (kind) {
    ReminderProfileKind.STRONG -> stringResource(R.string.reminder_profile_strong)
    ReminderProfileKind.STANDARD -> stringResource(R.string.reminder_profile_standard)
    ReminderProfileKind.GENTLE -> stringResource(R.string.reminder_profile_gentle)
    ReminderProfileKind.CUSTOM -> name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(initial: LocalDate?, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val millis = initial?.let { java.time.LocalDate.parse(it.toString()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val picker = rememberDatePickerState(initialSelectedDateMillis = millis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = { picker.selectedDateMillis?.let { onSelected(LocalDate.parse(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())) } }) { Text(stringResource(R.string.reminder_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reminder_cancel)) } },
    ) { DatePicker(state = picker) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onSelected: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_select_time)) },
        text = { TimePicker(state = picker) },
        confirmButton = { TextButton(onClick = { onSelected(LocalTime(picker.hour, picker.minute)) }) { Text(stringResource(R.string.reminder_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reminder_cancel)) } },
    )
}

@Composable
internal fun kotlinx.datetime.DayOfWeek.shortLabel(): String = stringResource(when (this) {
    kotlinx.datetime.DayOfWeek.MONDAY -> R.string.reminder_weekday_mon
    kotlinx.datetime.DayOfWeek.TUESDAY -> R.string.reminder_weekday_tue
    kotlinx.datetime.DayOfWeek.WEDNESDAY -> R.string.reminder_weekday_wed
    kotlinx.datetime.DayOfWeek.THURSDAY -> R.string.reminder_weekday_thu
    kotlinx.datetime.DayOfWeek.FRIDAY -> R.string.reminder_weekday_fri
    kotlinx.datetime.DayOfWeek.SATURDAY -> R.string.reminder_weekday_sat
    kotlinx.datetime.DayOfWeek.SUNDAY -> R.string.reminder_weekday_sun
})

@Composable
internal fun ReminderImportance.label(): String = stringResource(when (this) {
    ReminderImportance.LOW -> R.string.reminder_importance_low
    ReminderImportance.DEFAULT -> R.string.reminder_importance_default
    ReminderImportance.HIGH -> R.string.reminder_importance_high
})

@Composable
internal fun ReminderLockScreenVisibility.label(): String = stringResource(when (this) {
    ReminderLockScreenVisibility.PUBLIC -> R.string.reminder_visibility_public
    ReminderLockScreenVisibility.PRIVATE -> R.string.reminder_visibility_private
    ReminderLockScreenVisibility.SECRET -> R.string.reminder_visibility_secret
})

@Composable
private fun ReminderDraftNotice.label(): String = stringResource(when (this) {
    ReminderDraftNotice.NOTIFICATION_PERMISSION -> R.string.reminder_permission_warning
    ReminderDraftNotice.FALLBACK_TIMING -> R.string.reminder_fallback_warning
    ReminderDraftNotice.SCHEDULE_FAILED -> R.string.reminder_schedule_failed
    ReminderDraftNotice.SAVE_FAILED -> R.string.reminder_save_failed
})
