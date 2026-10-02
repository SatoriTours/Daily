package com.dailysatori.ui.feature.reminder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.ui.theme.*

@Composable
fun ReminderBatchPreview(
    batch: ReminderBatchUiState,
    profiles: List<ReminderProfile>,
    onToggleItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onConfirmItem: (String) -> Unit,
    onUpdateItem: (String, (ReminderBatchUiItem) -> ReminderBatchUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedCount = batch.items.values.count { it.saveStatus == BatchSaveStatus.SAVED }
    val failedCount = batch.items.values.count { it.saveStatus == BatchSaveStatus.FAILED }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(IconSize.xl), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.reminder_batch_review_title, batch.items.size), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.reminder_batch_review_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.reminder_batch_preview_summary, batch.selectedCount, batch.items.size), style = MaterialTheme.typography.labelLarge)
        if (savedCount > 0 || failedCount > 0) Text(stringResource(R.string.reminder_batch_result_summary, savedCount, failedCount))
        batch.failure?.let { Text(batchErrorText(it), color = MaterialTheme.colorScheme.error) }
        batch.items.values.forEachIndexed { index, item ->
            BatchReminderCard(index + 1, item, profiles, onToggleItem, onRemoveItem, onConfirmItem, onUpdateItem)
        }
    }
}

@Composable
private fun BatchReminderCard(
    ordinal: Int,
    item: ReminderBatchUiItem,
    profiles: List<ReminderProfile>,
    onToggleItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onConfirmItem: (String) -> Unit,
    onUpdateItem: (String, (ReminderBatchUiItem) -> ReminderBatchUiItem) -> Unit,
) {
    var editing by remember(item.id) { mutableStateOf(item.parseError != null || item.draft.validationErrors.isNotEmpty()) }
    val saved = item.saveStatus == BatchSaveStatus.SAVED
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            BatchReminderCardHeader(ordinal, item, onToggleItem, onRemoveItem)
            Text(item.draft.content.ifBlank { stringResource(R.string.reminder_batch_review_content_needed) }, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BatchReminderSummaryRow(Icons.Outlined.CalendarToday, stringResource(R.string.reminder_batch_review_next), item.draft.startDate?.toString() ?: stringResource(R.string.reminder_not_selected))
            BatchReminderSummaryRow(Icons.Outlined.Schedule, stringResource(R.string.reminder_first_time), item.draft.firstReminderTime?.toString() ?: stringResource(R.string.reminder_not_selected))
            BatchReminderSummaryRow(Icons.Outlined.Repeat, stringResource(R.string.reminder_detail_rule_label), item.draft.repeatSummary())
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.reminder_batch_review_work_quiet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.parseError?.let { Text(batchErrorText(it), color = MaterialTheme.colorScheme.error) }
            item.saveError?.let { Text(batchErrorText(it), color = MaterialTheme.colorScheme.error) }
            if (!editing && !saved && item.draft.validationErrors.isNotEmpty()) {
                Text(item.draft.validationHint(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            when {
                saved -> Text(stringResource(R.string.reminder_batch_saved_read_only), style = MaterialTheme.typography.bodySmall)
                item.isSaving -> Text(stringResource(R.string.reminder_batch_editing_disabled), style = MaterialTheme.typography.bodySmall)
                else -> {
                    TextButton(onClick = { editing = !editing }) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(IconSize.m))
                        Spacer(Modifier.width(Spacing.s))
                        Text(stringResource(if (editing) R.string.reminder_batch_review_collapse_edit else R.string.reminder_detail_edit_kicker))
                    }
                    AnimatedVisibility(visible = editing) {
                        ReminderEditorForm(
                            state = item.draft, profiles = profiles,
                            onChange = { draft -> onUpdateItem(item.id) { current -> current.copy(draft = draft) } },
                        )
                    }
                    if (item.requiresConfirmation) {
                        Text(stringResource(R.string.reminder_batch_confirmation_required), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onConfirmItem(item.id) }, enabled = item.parseError == null && item.draft.canConfirm) {
                            Text(stringResource(R.string.reminder_batch_confirm_item))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderDraftUiState.validationHint(): String = stringResource(when (validationErrors.firstOrNull()) {
    ReminderDraftField.CONTENT -> R.string.reminder_batch_review_content_needed
    ReminderDraftField.START_DATE, ReminderDraftField.END_DATE -> R.string.reminder_batch_review_dates_needed
    ReminderDraftField.FIRST_TIME -> R.string.reminder_batch_review_time_needed
    ReminderDraftField.ACTIVE_DAY_RULE -> R.string.reminder_selected_days_required
    else -> R.string.reminder_profile_invalid
})

@Composable
private fun BatchReminderCardHeader(ordinal: Int, item: ReminderBatchUiItem, onToggleItem: (String) -> Unit, onRemoveItem: (String) -> Unit) {
    var showMenu by remember(item.id) { mutableStateOf(false) }
    val editable = item.saveStatus in setOf(BatchSaveStatus.PENDING, BatchSaveStatus.FAILED)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        IconToggleButton(
            checked = item.selected, onCheckedChange = { onToggleItem(item.id) },
            enabled = editable && item.parseError == null && !item.requiresConfirmation,
        ) {
            Icon(
                if (item.selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = stringResource(R.string.reminder_batch_review_select, ordinal),
                tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.reminder_batch_review_item, ordinal), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(
                stringResource(statusLabel(item.saveStatus)), Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.saveStatus == BatchSaveStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editable) Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(R.string.reminder_detail_more))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.reminder_batch_remove), color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onRemoveItem(item.id) })
            }
        }
    }
}

@Composable
private fun BatchReminderSummaryRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun ReminderDraftUiState.repeatSummary(): String {
    val missing = stringResource(R.string.reminder_not_selected)
    val repeat = when (val rule = recurrence) {
        ReminderRecurrence.Once -> when {
            activeDayRule is ReminderActiveDayRule.ConsecutiveDateRange || startDate != endDate ->
                stringResource(R.string.reminder_batch_review_date_range, startDate?.toString() ?: missing, endDate?.toString() ?: missing)
            else -> stringResource(R.string.reminder_list_repeat_once)
        }
        is ReminderRecurrence.Monthly -> stringResource(R.string.reminder_batch_review_monthly, rule.dayOfMonth)
        is ReminderRecurrence.Yearly -> stringResource(R.string.reminder_batch_review_yearly, rule.month, rule.dayOfMonth)
    }
    val end = endDate
    return if (recurrence != ReminderRecurrence.Once && end != null && startDate != end) {
        stringResource(R.string.reminder_batch_review_repeat_window, repeat, end.monthNumber, end.dayOfMonth)
    } else repeat
}

private fun statusLabel(status: BatchSaveStatus): Int = when (status) {
    BatchSaveStatus.PENDING -> R.string.reminder_batch_status_pending
    BatchSaveStatus.SAVING -> R.string.reminder_batch_status_saving
    BatchSaveStatus.SAVED -> R.string.reminder_batch_status_saved
    BatchSaveStatus.FAILED -> R.string.reminder_batch_status_failed
}

@Composable
fun batchErrorText(error: String): String = when (error) {
    ReminderBatchErrorCode.PARSE_FAILED -> stringResource(R.string.reminder_batch_error_parse)
    ReminderBatchErrorCode.SCHEDULING_FAILED -> stringResource(R.string.reminder_batch_error_scheduling)
    ReminderBatchErrorCode.SAVE_FAILED -> stringResource(R.string.reminder_batch_error_save)
    else -> stringResource(R.string.reminder_batch_error_parse)
}
