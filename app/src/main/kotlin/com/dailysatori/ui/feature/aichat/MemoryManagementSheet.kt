package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.R
import com.dailysatori.service.assistant.AssistantMemoryView
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryManagementSheet(
    memories: List<AssistantMemoryView>,
    onForget: (Long) -> Unit,
    onCorrect: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var memoryToCorrect by remember { mutableStateOf<AssistantMemoryView?>(null) }
    var memoryToForget by remember { mutableStateOf<AssistantMemoryView?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m)
                .padding(bottom = Spacing.l),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.assistant_memory_management),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(Spacing.xxl),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.assistant_memory_close),
                        modifier = Modifier.size(IconSize.m),
                    )
                }
            }

            Text(
                text = stringResource(R.string.assistant_memory_forget_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.xs),
            )

            Spacer(modifier = Modifier.height(Spacing.s))

            if (memories.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(Radius.m),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.l),
                ) {
                    Box(modifier = Modifier.padding(Spacing.l), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.assistant_memory_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    contentPadding = PaddingValues(bottom = Spacing.m),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryItemCard(
                            memory = memory,
                            onCorrectClick = { memoryToCorrect = memory },
                            onForgetClick = { memoryToForget = memory },
                        )
                    }
                }
            }
        }
    }

    memoryToForget?.let { memory ->
        AlertDialog(
            onDismissRequest = { memoryToForget = null },
            title = { Text(stringResource(R.string.assistant_memory_forget)) },
            text = { Text(stringResource(R.string.assistant_memory_forget_notice)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onForget(memory.id)
                        memoryToForget = null
                    },
                ) {
                    Text(stringResource(R.string.assistant_memory_forget), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToForget = null }) {
                    Text(stringResource(R.string.assistant_memory_cancel))
                }
            },
        )
    }

    memoryToCorrect?.let { memory ->
        var correctedText by remember { mutableStateOf(memory.content) }
        AlertDialog(
            onDismissRequest = { memoryToCorrect = null },
            title = { Text(stringResource(R.string.assistant_memory_correct_title)) },
            text = {
                OutlinedTextField(
                    value = correctedText,
                    onValueChange = { correctedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (correctedText.isNotBlank()) {
                            onCorrect(memory.id, correctedText.trim())
                            memoryToCorrect = null
                        }
                    },
                ) {
                    Text(stringResource(R.string.assistant_memory_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToCorrect = null }) {
                    Text(stringResource(R.string.assistant_memory_cancel))
                }
            },
        )
    }
}

@Composable
private fun MemoryItemCard(
    memory: AssistantMemoryView,
    onCorrectClick: () -> Unit,
    onForgetClick: () -> Unit,
) {
    val kindText = when (memory.kind.lowercase()) {
        "fact" -> stringResource(R.string.assistant_memory_kind_fact)
        "preference" -> stringResource(R.string.assistant_memory_kind_preference)
        "goal" -> stringResource(R.string.assistant_memory_kind_goal)
        "constraint" -> stringResource(R.string.assistant_memory_kind_constraint)
        else -> memory.kind
    }
    val sourceText = when (memory.source.lowercase()) {
        "diary" -> stringResource(R.string.assistant_memory_source_diary)
        "article" -> stringResource(R.string.assistant_memory_source_article)
        "reminder" -> stringResource(R.string.assistant_memory_source_reminder)
        "conversation" -> stringResource(R.string.assistant_memory_source_conversation)
        "manual" -> stringResource(R.string.assistant_memory_source_manual)
        else -> memory.source
    }
    val sourceLabel = if (memory.sourceId.isNotBlank()) "$sourceText #${memory.sourceId}" else sourceText

    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.assistant_memory_kind, kindText),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = stringResource(R.string.assistant_memory_source, sourceLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = if (memory.active) stringResource(R.string.assistant_memory_active) else stringResource(R.string.assistant_memory_inactive),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (memory.active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onCorrectClick,
                        modifier = Modifier.size(Spacing.xxl),
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.assistant_memory_correct),
                            modifier = Modifier.size(IconSize.m),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onForgetClick,
                        modifier = Modifier.size(Spacing.xxl),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.assistant_memory_forget),
                            modifier = Modifier.size(IconSize.m),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (memory.evidence.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(Radius.s),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs),
                ) {
                    Column(modifier = Modifier.padding(Spacing.s)) {
                        Text(
                            text = stringResource(R.string.assistant_memory_evidence),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = memory.evidence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
