package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
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
    initialReminderId: String? = null,
) {
    val ui by viewModel.state.collectAsState()
    val all by viewModel.reminders.collectAsState()
    val visible = remember(all, ui.filter) { filterReminders(all, ui.filter) }
    val selected = all.firstOrNull { it.id == ui.selectedReminderId }
    LaunchedEffect(initialReminderId) {
        if (initialReminderId != null) viewModel.selectReminder(initialReminderId)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ReminderFilter.entries.forEach { filter ->
                FilterChip(selected = ui.filter == filter, onClick = { viewModel.setFilter(filter) }, label = { Text(filter.label()) })
            }
        }
        if (selected != null) ReminderDetail(selected, latestProfile, viewModel)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            visible.forEach { reminder ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectReminder(reminder.id) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.m),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(Spacing.m)) {
                        Text(reminder.content, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.reminder_date_range, reminder.startDate, reminder.firstReminderTime, reminder.endDate), style = MaterialTheme.typography.bodySmall)
                        Text(reminder.status.label(), style = MaterialTheme.typography.labelSmall)
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
            OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text(stringResource(R.string.reminder_content_label)) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { picker = DetailPicker.START }) { Text(stringResource(R.string.reminder_start_value, startDate)) }
                TextButton(onClick = { picker = DetailPicker.END }) { Text(stringResource(R.string.reminder_end_value, endDate)) }
                TextButton(onClick = { picker = DetailPicker.TIME }) { Text(stringResource(R.string.reminder_first_value, firstTime)) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(stringResource(R.string.reminder_rule_daily) to ReminderActiveDayRule.Daily, stringResource(R.string.reminder_rule_weekdays) to ReminderActiveDayRule.Weekdays, stringResource(R.string.reminder_rule_range) to ReminderActiveDayRule.ConsecutiveDateRange).forEach { (label, value) ->
                    FilterChip(selected = rule == value, onClick = { rule = value }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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

@Composable
private fun ReminderFilter.label() = stringResource(when (this) {
    ReminderFilter.ACTIVE -> R.string.reminder_filter_active
    ReminderFilter.PAUSED -> R.string.reminder_filter_paused
    ReminderFilter.COMPLETED -> R.string.reminder_filter_completed
    ReminderFilter.EXPIRED -> R.string.reminder_filter_expired
})

@Composable
private fun ReminderAction.label() = stringResource(when (this) {
    ReminderAction.PAUSE -> R.string.reminder_action_pause
    ReminderAction.RESUME -> R.string.reminder_action_resume
    ReminderAction.EDIT -> R.string.reminder_action_save_edit
    ReminderAction.COMPLETE -> R.string.reminder_action_complete
    ReminderAction.DELETE -> R.string.reminder_action_delete
    ReminderAction.APPLY_LATEST_PROFILE -> R.string.reminder_action_apply_latest
})

@Composable
private fun com.dailysatori.service.reminder.ReminderStatus.label() = stringResource(when (this) {
    com.dailysatori.service.reminder.ReminderStatus.ACTIVE,
    com.dailysatori.service.reminder.ReminderStatus.NOTIFIED,
    com.dailysatori.service.reminder.ReminderStatus.DISMISSED -> R.string.reminder_status_active
    com.dailysatori.service.reminder.ReminderStatus.PAUSED -> R.string.reminder_status_paused
    com.dailysatori.service.reminder.ReminderStatus.COMPLETED -> R.string.reminder_status_completed
    com.dailysatori.service.reminder.ReminderStatus.EXPIRED -> R.string.reminder_status_expired
    com.dailysatori.service.reminder.ReminderStatus.DRAFT -> R.string.reminder_draft_title
})
