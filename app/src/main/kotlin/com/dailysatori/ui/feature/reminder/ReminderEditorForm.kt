@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.ui.theme.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

private enum class EditorPicker { START, END, TIME }

@Composable
internal fun ReminderEditorForm(
    state: ReminderDraftUiState,
    profiles: List<ReminderProfile>,
    onChange: (ReminderDraftUiState) -> Unit,
    leapDayFallbackChosen: Boolean = true,
    onLeapDayPolicySelected: (LeapDayPolicy) -> Unit = { policy ->
        (state.recurrence as? ReminderRecurrence.Yearly)?.let { onChange(state.editRecurrence(it.copy(leapDayPolicy = policy))) }
    },
) {
    var picker by remember(state.id) { mutableStateOf<EditorPicker?>(null) }
    val missing = stringResource(R.string.reminder_not_selected)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        OutlinedTextField(
            value = state.content,
            onValueChange = { onChange(state.editContent(it)) },
            label = { Text(stringResource(R.string.reminder_content_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ReminderEditorRecurrence(state, onChange, leapDayFallbackChosen, onLeapDayPolicySelected)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ReminderSettingRow(stringResource(R.string.reminder_start_date), state.startDate?.toString() ?: missing) { picker = EditorPicker.START }
            ReminderSettingRow(stringResource(R.string.reminder_end_date), state.endDate?.toString() ?: missing) { picker = EditorPicker.END }
            ReminderSettingRow(stringResource(R.string.reminder_first_time), state.firstReminderTime?.toString() ?: missing) { picker = EditorPicker.TIME }
        }
        ReminderAdvancedProfileEditor(state, onChange) {
            if (state.activeDayRule !is ReminderActiveDayRule.ConsecutiveDateRange) {
                Text(stringResource(R.string.reminder_editor_active_days), style = MaterialTheme.typography.titleSmall)
                ReminderEditorDayRules(state, onChange)
            }
            Text(stringResource(R.string.reminder_editor_profile), style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                profiles.forEach { profile ->
                    FilterChip(
                        selected = state.profileId == profile.id || state.profileId == null &&
                            (state.profile == profile.snapshot || profile.kind != com.dailysatori.service.reminder.ReminderProfileKind.CUSTOM && state.profile?.kind == profile.kind),
                        onClick = { onChange(state.selectProfile(profile)) },
                        label = { Text(profile.name) },
                    )
                }
            }
        }
        state.toEditorStateOrNull()?.let { editor ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.reminder_editor_behavior), style = MaterialTheme.typography.titleSmall)
                    Text(editor.actualBehaviorSummary(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.validationErrors.isNotEmpty()) {
            Text(stringResource(R.string.reminder_detail_invalid), color = MaterialTheme.colorScheme.error)
        }
    }
    when (picker) {
        EditorPicker.START -> DateDialog(state.startDate, { picker = null }) { selected ->
            onChange(state.editDates(selected, state.endDate?.takeIf { it >= selected } ?: selected))
            picker = null
        }
        EditorPicker.END -> DateDialog(state.endDate, { picker = null }) { onChange(state.editDates(state.startDate, it)); picker = null }
        EditorPicker.TIME -> TimeDialog(state.firstReminderTime ?: LocalTime(9, 0), { picker = null }) { onChange(state.editFirstTime(it)); picker = null }
        null -> Unit
    }
}

@Composable
private fun ReminderEditorRecurrence(
    state: ReminderDraftUiState,
    onChange: (ReminderDraftUiState) -> Unit,
    leapDayFallbackChosen: Boolean,
    onLeapDayPolicySelected: (LeapDayPolicy) -> Unit,
) {
    Text(stringResource(R.string.reminder_recurrence_title), style = MaterialTheme.typography.titleSmall)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        val date = state.startDate
        listOf(
            R.string.reminder_list_repeat_once to ReminderRecurrence.Once,
            R.string.reminder_list_repeat_monthly to date?.let { ReminderRecurrence.Monthly(it.dayOfMonth) },
            R.string.reminder_list_repeat_yearly to date?.let { ReminderRecurrence.Yearly(it.monthNumber, it.dayOfMonth, LeapDayPolicy.FEBRUARY_28) },
            R.string.reminder_recurrence_consecutive to ReminderRecurrence.Once,
        ).forEach { (label, recurrence) ->
            val consecutive = label == R.string.reminder_recurrence_consecutive
            val selected = (state.activeDayRule is ReminderActiveDayRule.ConsecutiveDateRange) == consecutive &&
                state.recurrence::class == recurrence?.let { it::class }
            FilterChip(
                selected = selected,
                enabled = recurrence != null,
                onClick = { if (!selected) recurrence?.let { onChange(state.selectRecurrenceMode(it, consecutive)) } },
                label = { Text(stringResource(label)) },
            )
        }
    }
    val yearly = state.recurrence as? ReminderRecurrence.Yearly
    if (yearly?.month == 2 && yearly.dayOfMonth == 29) {
        Text(stringResource(R.string.reminder_editor_leap_policy))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LeapDayPolicy.entries.forEach { policy ->
                FilterChip(selected = yearly.leapDayPolicy == policy && leapDayFallbackChosen, onClick = { onLeapDayPolicySelected(policy) }, label = { Text(stringResource(if (policy == LeapDayPolicy.FEBRUARY_28) R.string.reminder_editor_feb_28 else R.string.reminder_editor_mar_1)) })
            }
        }
    }
}

@Composable
private fun ReminderEditorDayRules(state: ReminderDraftUiState, onChange: (ReminderDraftUiState) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        listOf(
            R.string.reminder_rule_daily to ReminderActiveDayRule.Daily,
            R.string.reminder_rule_weekdays to ReminderActiveDayRule.Weekdays,
            R.string.reminder_rule_selected to ReminderActiveDayRule.SelectedWeekdays(
                (state.activeDayRule as? ReminderActiveDayRule.SelectedWeekdays)?.days ?: DayOfWeek.entries.toSet(),
            ),
        ).forEach { (label, rule) ->
            FilterChip(selected = state.activeDayRule::class == rule::class, onClick = { onChange(state.editActiveDayRule(rule)) }, label = { Text(stringResource(label)) })
        }
    }
    if (state.activeDayRule is ReminderActiveDayRule.SelectedWeekdays) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            DayOfWeek.entries.forEach { day ->
                val days = state.activeDayRule.days
                FilterChip(selected = day in days, onClick = { onChange(state.editActiveDayRule(ReminderActiveDayRule.SelectedWeekdays(if (day in days) days - day else days + day))) }, label = { Text(day.shortLabel()) })
            }
        }
    } else if (state.activeDayRule is ReminderActiveDayRule.Weekdays) {
        Text(stringResource(R.string.reminder_rule_weekdays), style = MaterialTheme.typography.bodySmall)
    }
}

internal fun ReminderEditorState.toFormState(id: String) = ReminderDraftUiState(
    id = id, content = content, startDate = startDate, endDate = endDate,
    firstReminderTime = firstReminderTime, activeDayRule = activeDayRule,
    recurrence = recurrence, profile = profile, saving = saving,
    daytimeBackoffInput = daytimeBackoffInput, eveningIntervalInput = eveningIntervalInput,
)

internal fun ReminderDraftUiState.toEditorStateOrNull(): ReminderEditorState? {
    return ReminderEditorState(
        content = content, startDate = startDate ?: return null, endDate = endDate ?: return null,
        firstReminderTime = firstReminderTime ?: return null, activeDayRule = activeDayRule,
        recurrence = recurrence, profile = profile ?: return null, saving = saving,
        daytimeBackoffInput = daytimeBackoffInput, eveningIntervalInput = eveningIntervalInput,
    )
}

internal fun ReminderEditorState.applyFormState(state: ReminderDraftUiState): ReminderEditorState {
    val updated = state.toEditorStateOrNull() ?: return this
    val yearly = updated.recurrence as? ReminderRecurrence.Yearly
    val chosen = if (recurrence == updated.recurrence) leapDayFallbackChosen
        else yearly?.month != 2 || yearly.dayOfMonth != 29
    return updated.copy(leapDayFallbackChosen = chosen)
}
