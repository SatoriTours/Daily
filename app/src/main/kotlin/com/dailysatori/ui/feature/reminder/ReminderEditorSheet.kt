package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderEditorSheet(
    reminder: Reminder,
    latestProfile: ReminderProfileSnapshot,
    viewModel: ReminderViewModel,
    onDismiss: () -> Unit,
) {
    // Save against the version actually opened, so a concurrent update cannot be overwritten.
    val original = remember(reminder.id) { reminder }
    var editor by remember(reminder.id) { mutableStateOf(ReminderEditorState.from(original)) }
    val profiles by viewModel.profiles.collectAsState()
    var confirmDelete by remember(reminder.id) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { if (!editor.saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reminder_detail_edit_kicker), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                ReminderEditorMoreActions(reminder, enabled = !editor.saving) { action ->
                    if (action == ReminderAction.DELETE) confirmDelete = true else {
                        when (action) {
                            ReminderAction.PAUSE -> viewModel.pause(reminder.id)
                            ReminderAction.RESUME -> viewModel.resume(reminder.id)
                            ReminderAction.COMPLETE -> viewModel.complete(reminder.id)
                            ReminderAction.APPLY_LATEST_PROFILE -> viewModel.applyLatestProfile(reminder.id, latestProfile)
                            else -> Unit
                        }
                        onDismiss()
                    }
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (reminder.dataIssue != null) {
                    Text(stringResource(R.string.reminder_corrupt_profile_warning), color = MaterialTheme.colorScheme.error)
                }
                ReminderEditorForm(
                    state = editor.toFormState(reminder.id), profiles = profiles,
                    onChange = { if (!editor.saving) editor = editor.applyFormState(it) },
                    leapDayFallbackChosen = editor.leapDayFallbackChosen,
                    onLeapDayPolicySelected = { policy ->
                        val yearly = editor.recurrence as? ReminderRecurrence.Yearly
                        if (!editor.saving && yearly != null) {
                            editor = editor.copy(recurrence = yearly.copy(leapDayPolicy = policy), leapDayFallbackChosen = true)
                        }
                    },
                )
            }
            editor.notice?.let { Text(it, Modifier.padding(horizontal = Spacing.l), color = MaterialTheme.colorScheme.error) }
            ReminderEditorFooter(editor, canEdit = ReminderAction.EDIT in reminderActions(reminder), onCancel = onDismiss) {
                val snapshot = editor
                editor = snapshot.copy(saving = true, notice = null)
                viewModel.saveEditor(original, snapshot) { id, result ->
                    editor = result
                    if (id != null) onDismiss()
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.reminder_delete_title)) },
            text = { Text(stringResource(R.string.reminder_delete_message)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(reminder.id); onDismiss() }) {
                Text(stringResource(R.string.reminder_action_delete), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.reminder_cancel)) } },
        )
    }
}

@Composable
private fun ReminderEditorFooter(editor: ReminderEditorState, canEdit: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel, enabled = !editor.saving, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.reminder_cancel))
        }
        Button(onClick = onSave, enabled = canEdit && editor.canSave, modifier = Modifier.weight(1f)) {
            Text(stringResource(if (editor.saving) R.string.reminder_saving else R.string.reminder_action_save))
        }
    }
}

@Composable
private fun ReminderEditorMoreActions(reminder: Reminder, enabled: Boolean, onAction: (ReminderAction) -> Unit) {
    var expanded by remember(reminder.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.reminder_editor_more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            reminderActions(reminder).filterNot { it == ReminderAction.EDIT }.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label(), color = if (action == ReminderAction.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                    enabled = enabled && (action != ReminderAction.RESUME || canResumeReminder(reminder)),
                    onClick = { expanded = false; onAction(action) },
                )
            }
        }
    }
}
