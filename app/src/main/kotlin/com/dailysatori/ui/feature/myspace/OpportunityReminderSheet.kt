package com.dailysatori.ui.feature.myspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.ui.feature.reminder.*
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpportunityReminderSheet(item: NewsOpportunity, myViewModel: MySpaceViewModel, onDismiss: () -> Unit) {
    val viewModel: ReminderViewModel = koinViewModel()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val existing = reminders.firstOrNull { it.id == item.reminderId || it.id == opportunityReminderId(item.id) }
    val failed by myViewModel.operationFailed.collectAsStateWithLifecycle()
    var savedId by remember(item.id) { mutableStateOf<String?>(null) }
    var editor by remember(item.id) { mutableStateOf(ReminderEditorState.createDefault().copy(content = item.action.take(2_000))) }
    if (existing != null && savedId == null) {
        LaunchedEffect(existing.id) { if (item.reminderId != existing.id) myViewModel.linkReminder(item.id, existing.id) {} }
        ReminderEditorSheet(existing, profiles.firstOrNull()?.snapshot ?: existing.profile, viewModel, onDismiss)
        return
    }
    ModalBottomSheet(onDismissRequest = { if (!editor.saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight().imePadding()) {
            Text(stringResource(R.string.my_space_add_reminder), Modifier.padding(Spacing.l), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.my_space_from_reading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReminderEditorForm(editor.toFormState(item.id), profiles, { if (!editor.saving && savedId == null) editor = editor.applyFormState(it) },
                    leapDayFallbackChosen = editor.leapDayFallbackChosen,
                    onLeapDayPolicySelected = { policy ->
                        val rule = editor.recurrence as? com.dailysatori.service.reminder.ReminderRecurrence.Yearly
                        if (!editor.saving && rule != null) editor = editor.copy(recurrence = rule.copy(leapDayPolicy = policy), leapDayFallbackChosen = true)
                    })
                editor.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (failed) Text(stringResource(R.string.my_space_error), color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth().padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                TextButton(onClick = onDismiss, enabled = !editor.saving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.my_space_cancel)) }
                Button(enabled = editor.canSave, modifier = Modifier.weight(1f), onClick = {
                    val alreadySaved = savedId
                    if (alreadySaved != null) myViewModel.linkReminder(item.id, alreadySaved, onDismiss) else {
                        val snapshot = editor
                        editor = snapshot.copy(saving = true)
                        viewModel.saveEditor(null, snapshot, creationId = opportunityReminderId(item.id)) { id, result ->
                            editor = result
                            if (id != null) { savedId = id; myViewModel.linkReminder(item.id, id, onDismiss) }
                        }
                    }
                }) { Text(stringResource(if (editor.saving) R.string.my_space_saving else R.string.my_space_save_changes)) }
            }
        }
    }
}
