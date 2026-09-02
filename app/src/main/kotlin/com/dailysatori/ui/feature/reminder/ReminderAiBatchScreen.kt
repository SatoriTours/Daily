package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.service.reminder.ReminderAiBatchStatus
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
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
    AppScaffold(title = stringResource(R.string.reminder_ai_batch_title), onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            when (val batch = state.batch) {
                null -> item { Text(stringResource(R.string.reminder_ai_batch_loading)) }
                else -> when (batch.status) {
                    ReminderAiBatchStatus.PARSING, ReminderAiBatchStatus.RUNNING -> item {
                        Card(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.reminder_ai_batch_processing),
                                Modifier.padding(Spacing.m),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    ReminderAiBatchStatus.PARSE_FAILED -> item {
                        Card(Modifier.fillMaxWidth()) {
                            androidx.compose.foundation.layout.Column(
                                Modifier.padding(Spacing.m),
                                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                            ) {
                                Text(stringResource(R.string.reminder_ai_batch_failed), style = MaterialTheme.typography.titleMedium)
                                Text(batch.originalInput, style = MaterialTheme.typography.bodyMedium)
                                if (batch.errorSummary.isNotBlank()) Text(batch.errorSummary, color = MaterialTheme.colorScheme.error)
                                Button(onClick = { viewModel.retryBatch()?.let(onOpenSuccessor) }) { Text(stringResource(R.string.reminder_ai_batch_retry)) }
                            }
                        }
                    }
                    ReminderAiBatchStatus.READY_FOR_CONFIRMATION -> {
                        state.preview?.let { preview ->
                            item {
                                ReminderBatchPreview(
                                    batch = preview,
                                    profiles = profiles,
                                    onToggleItem = viewModel::toggleItem,
                                    onRemoveItem = viewModel::removeItem,
                                    onConfirmItem = viewModel::confirmItem,
                                    onUpdateItem = viewModel::updateItem,
                                )
                            }
                            item {
                                Button(
                                    onClick = viewModel::confirmSelected,
                                    enabled = preview.selectedCount > 0 && !state.isSaving,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.reminder_batch_save_selected, preview.selectedCount)) }
                            }
                            item {
                                TextButton(onClick = viewModel::discardBatch, enabled = !state.isSaving) {
                                    Text(stringResource(R.string.reminder_ai_batch_discard))
                                }
                            }
                        }
                    }
                    ReminderAiBatchStatus.CONFIRMED, ReminderAiBatchStatus.DISCARDED -> item {
                        Text(stringResource(R.string.reminder_ai_batch_terminal), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
