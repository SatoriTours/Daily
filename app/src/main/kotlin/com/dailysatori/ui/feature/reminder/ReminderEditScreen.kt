package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReminderEditScreen(
    reminderId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onBatchSubmitted: (String) -> Unit,
    viewModel: ReminderViewModel = koinViewModel(),
) {
    val reminders by viewModel.reminders.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val ui by viewModel.state.collectAsState()
    val existing = reminders.firstOrNull { it.id == reminderId }
    var editor by remember(existing?.id) { mutableStateOf(existing?.let(ReminderEditorState::from) ?: ReminderEditorState.createDefault()) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val batch = ui.aiParse.batch
    val hasUnsavedBatch = batch?.items?.values?.any { it.saveStatus != BatchSaveStatus.SAVED } == true
    val requestBack = { if (hasUnsavedBatch) showDiscardDialog = true else onBack() }
    BackHandler(enabled = hasUnsavedBatch, onBack = requestBack)
    val save = {
        val submitted = editor
        editor = submitted.copy(saving = true)
        viewModel.saveEditor(existing, submitted) { id, next -> editor = next; id?.let(onSaved) }
    }
    AppScaffold(
        title = if (existing == null) "新建提醒" else "编辑提醒",
        onBack = requestBack,
        bottomBar = {
            Surface(shadowElevation = Spacing.xs) {
                if (batch == null) {
                    Button(onClick = save, enabled = editor.canSave, modifier = Modifier.fillMaxWidth().padding(Spacing.m)) {
                        Text(if (editor.saving) "保存中…" else "保存提醒")
                    }
                } else {
                    val savingBatch = batch.items.values.any { it.saveStatus == BatchSaveStatus.SAVING }
                    Button(
                        onClick = { viewModel.saveSelectedBatch { onBack() } },
                        enabled = batch.selectedCount > 0 && !savingBatch,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                    ) {
                        Text(stringResource(R.string.reminder_batch_save_selected, batch.selectedCount))
                    }
                }
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("提醒我什么", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = ui.aiParse.prompt,
                            onValueChange = viewModel::onAiPromptChanged,
                            placeholder = { Text("例如：每年 9 月 2 日晚上 8 点提醒我充值") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = { viewModel.submitReminderAiBatch(ui.aiParse.prompt)?.let(onBatchSubmitted) },
                                enabled = ui.aiParse.prompt.isNotBlank() && !ui.aiParse.isInterpreting,
                            ) {
                                Text(if (ui.aiParse.isInterpreting) "提交中…" else "AI 解析")
                            }
                        }
                        ui.aiParse.error?.let { Text(stringResource(R.string.reminder_batch_parse_incomplete, batchErrorText(it)), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            batch?.let { preview ->
                item {
                    ReminderBatchPreview(
                        batch = preview,
                        profiles = profiles,
                        onToggleItem = viewModel::toggleBatchItem,
                        onRemoveItem = viewModel::removeBatchItem,
                        onConfirmItem = viewModel::confirmBatchItem,
                        onUpdateItem = viewModel::updateBatchItem,
                    )
                }
            }
            item {
                ReminderEditorForm(
                    state = editor.toFormState(existing?.id ?: "new-reminder"),
                    profiles = profiles,
                    onChange = { updated -> editor = editor.applyFormState(updated) },
                    leapDayFallbackChosen = editor.leapDayFallbackChosen,
                    onLeapDayPolicySelected = { policy ->
                        val yearly = editor.recurrence as? ReminderRecurrence.Yearly
                        if (yearly != null) editor = editor.copy(recurrence = yearly.copy(leapDayPolicy = policy), leapDayFallbackChosen = true)
                    },
                )
            }
            editor.validationMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            editor.notice?.let { notice -> item { Text(notice, color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.reminder_batch_discard_title)) },
            text = { Text(stringResource(R.string.reminder_batch_discard_message)) },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.reminder_batch_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.reminder_batch_discard_cancel)) }
            },
        )
    }
}
