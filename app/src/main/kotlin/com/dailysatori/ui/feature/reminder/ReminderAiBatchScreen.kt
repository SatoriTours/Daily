package com.dailysatori.ui.feature.reminder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.service.reminder.ReminderAiBatchStatus
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReminderAiBatchScreen(
    batchId: String,
    onBack: () -> Unit,
    onOpenSuccessor: (String) -> Unit,
    viewModel: ReminderAiBatchViewModel = koinViewModel { parametersOf(batchId) },
) {
    val state by viewModel.state.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val batch = state.batch
    val ready = batch?.status == ReminderAiBatchStatus.READY_FOR_CONFIRMATION
    val processing = batch?.status in setOf(ReminderAiBatchStatus.PARSING, ReminderAiBatchStatus.RUNNING)
    var showOriginal by remember(batchId) { mutableStateOf(false) }
    var confirmDiscard by remember(batchId) { mutableStateOf(false) }
    AppScaffold(
        title = stringResource(when (batch?.status) {
            ReminderAiBatchStatus.READY_FOR_CONFIRMATION -> R.string.reminder_confirm
            ReminderAiBatchStatus.CONFIRMED -> R.string.reminder_batch_review_saved
            ReminderAiBatchStatus.DISCARDED -> R.string.reminder_batch_review_discarded
            else -> R.string.reminder_ai_batch_title
        }),
        onBack = onBack,
        actions = {
            if (ready) ReminderBatchMoreActions(enabled = !state.isSaving) { confirmDiscard = true }
        },
        bottomBar = {
            if (ready) ReminderBatchSaveBar(state, viewModel::confirmSelected)
            else if (processing) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.m).heightIn(min = Height.button),
                        shape = RoundedCornerShape(Radius.m),
                    ) { Text(stringResource(R.string.reminder_ai_progress_back)) }
                }
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            when (batch?.status) {
                null -> item {
                    Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ReminderAiBatchStatus.PARSING, ReminderAiBatchStatus.RUNNING -> item {
                    ReminderAiProcessingContent(batch, state.progress)
                }
                ReminderAiBatchStatus.READY_FOR_CONFIRMATION -> {
                    state.preview?.let { preview ->
                        item {
                            ReminderBatchPreview(
                                batch = preview, profiles = profiles,
                                onToggleItem = viewModel::toggleItem, onRemoveItem = viewModel::removeItem,
                                onConfirmItem = viewModel::confirmItem, onUpdateItem = viewModel::updateItem,
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Info, null, Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.reminder_batch_review_unsaved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item {
                            Column {
                                ReminderSettingRow(
                                    stringResource(R.string.reminder_batch_review_original),
                                    stringResource(if (showOriginal) R.string.reminder_advanced_collapse else R.string.reminder_advanced_expand),
                                ) { showOriginal = !showOriginal }
                                AnimatedVisibility(showOriginal) {
                                    Text(batch.originalInput, Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                ReminderAiBatchStatus.PARSE_FAILED -> item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.l)) {
                        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            Text(stringResource(R.string.reminder_ai_batch_failed), style = MaterialTheme.typography.titleMedium)
                            Text(batch.originalInput, style = MaterialTheme.typography.bodyMedium)
                            if (batch.errorSummary.isNotBlank()) Text(batch.errorSummary, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.retryBatch()?.let(onOpenSuccessor) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.reminder_ai_batch_retry))
                            }
                        }
                    }
                }
                ReminderAiBatchStatus.CONFIRMED, ReminderAiBatchStatus.DISCARDED -> item {
                    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        val saved = batch.status == ReminderAiBatchStatus.CONFIRMED
                        Icon(if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.DeleteOutline, null, Modifier.size(IconSize.xxl), tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(if (saved) R.string.reminder_batch_review_saved else R.string.reminder_batch_review_discarded), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onBack) { Text(stringResource(R.string.reminder_ai_progress_back)) }
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.reminder_batch_discard_title)) },
            text = { Text(stringResource(R.string.reminder_batch_review_discard_hint)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.discardBatch() }, enabled = !state.isSaving) {
                    Text(stringResource(R.string.reminder_ai_batch_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.reminder_cancel)) } },
        )
    }
}

@Composable
private fun ReminderBatchSaveBar(state: ReminderAiBatchScreenState, onSave: () -> Unit) {
    val selected = state.preview?.saveableCount ?: 0
    val invalid = state.preview?.invalidSelectedCount ?: 0
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            val hint = when {
                invalid > 0 -> stringResource(R.string.reminder_batch_review_invalid_count, invalid)
                state.preview?.selectedCount == 0 -> stringResource(R.string.reminder_batch_review_select_hint)
                else -> stringResource(R.string.reminder_batch_review_save_hint)
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = if (invalid > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onSave, enabled = selected > 0 && !state.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = Height.button), shape = RoundedCornerShape(Radius.m),
                colors = ButtonDefaults.buttonColors(contentColor = AppColors.onAccent),
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = BorderWidth.m)
                else Icon(Icons.Outlined.CheckCircle, null, Modifier.size(IconSize.m))
                Spacer(Modifier.width(Spacing.s))
                Text(if (state.isSaving) stringResource(R.string.reminder_saving) else stringResource(R.string.reminder_batch_review_save, selected))
            }
        }
    }
}

@Composable
private fun ReminderBatchMoreActions(enabled: Boolean, onDiscard: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(R.string.reminder_detail_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reminder_ai_batch_discard), color = MaterialTheme.colorScheme.error) },
                enabled = enabled, onClick = { expanded = false; onDiscard() },
            )
        }
    }
}
