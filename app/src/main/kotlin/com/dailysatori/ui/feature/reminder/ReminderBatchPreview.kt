package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.R
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.ui.theme.Spacing

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
    val selectedCount = batch.selectedCount
    val savedCount = batch.items.values.count { it.saveStatus == BatchSaveStatus.SAVED }
    val failedCount = batch.items.values.count { it.saveStatus == BatchSaveStatus.FAILED }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            stringResource(R.string.reminder_batch_preview_summary, selectedCount, batch.items.size),
            style = MaterialTheme.typography.titleMedium,
        )
        if (savedCount > 0 || failedCount > 0) {
            Text(stringResource(R.string.reminder_batch_result_summary, savedCount, failedCount))
        }
        batch.failure?.let { Text(batchErrorText(it), color = MaterialTheme.colorScheme.error) }
        batch.items.values.forEach { item ->
            BatchReminderCard(item, profiles, onToggleItem, onRemoveItem, onConfirmItem, onUpdateItem)
        }
    }
}

@Composable
private fun BatchReminderCard(
    item: ReminderBatchUiItem,
    profiles: List<ReminderProfile>,
    onToggleItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onConfirmItem: (String) -> Unit,
    onUpdateItem: (String, (ReminderBatchUiItem) -> ReminderBatchUiItem) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Checkbox(
                        checked = item.selected,
                        onCheckedChange = { onToggleItem(item.id) },
                        enabled = !item.isSaving && item.saveStatus != BatchSaveStatus.SAVED && item.parseError == null && !item.requiresConfirmation,
                    )
                    Text(
                        item.sourceText,
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                TextButton(onClick = { onRemoveItem(item.id) }, enabled = !item.isSaving) {
                    Text(stringResource(R.string.reminder_batch_remove))
                }
            }
            Text(
                stringResource(R.string.reminder_batch_occurrence, item.draft.absoluteDateTimeText),
                style = MaterialTheme.typography.bodySmall,
            )
            item.parseError?.let { Text(batchErrorText(it), color = MaterialTheme.colorScheme.error) }
            item.saveError?.let { Text(batchErrorText(it), color = MaterialTheme.colorScheme.error) }
            Text(stringResource(statusLabel(item.saveStatus)), style = MaterialTheme.typography.bodySmall)
            if (item.isSaving) {
                Text(stringResource(R.string.reminder_batch_editing_disabled), style = MaterialTheme.typography.bodySmall)
            } else {
                if (item.requiresConfirmation) {
                    Text(stringResource(R.string.reminder_batch_confirmation_required), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onConfirmItem(item.id) }, enabled = item.parseError == null && item.draft.canConfirm) {
                        Text(stringResource(R.string.reminder_batch_confirm_item))
                    }
                }
                ReminderDraftCard(
                    state = item.draft,
                    onChange = { draft -> onUpdateItem(item.id) { current -> current.copy(draft = draft) } },
                    onConfirm = {},
                    onCancel = {},
                    profiles = profiles,
                    showActions = false,
                )
            }
        }
    }
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
    else -> error
}
