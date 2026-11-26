package com.dailysatori.ui.feature.lifearchive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.service.lifearchive.*
import com.dailysatori.ui.theme.*

@Composable
internal fun LifeArchiveEditor(state: LifeArchiveUiState, vm: LifeArchiveViewModel, onReminder: (String) -> Unit, onAddType: () -> Unit) {
    val draft = state.draft ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (state.original == null && draft.sourceReminderId == null && !state.manualEditing) item {
            Surface(
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.life_archive_input_label), style = MaterialTheme.typography.titleMedium.editorial())
                    OutlinedTextField(
                        state.input,
                        vm::setInput,
                        Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 7,
                        enabled = !state.saving,
                        shape = RoundedCornerShape(Radius.m),
                        placeholder = { Text(stringResource(R.string.life_archive_input_placeholder)) },
                    )
                    ArchiveAiButton(R.string.life_archive_organize, !state.busy && state.input.isNotBlank(), vm::organize)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.life_archive_cloud_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::toggleManualEditing, enabled = !state.busy) { Text(stringResource(R.string.life_archive_manual)) }
                    }
                }
            }
        }
        if (draft.title.isNotBlank() || state.manualEditing || state.original != null || draft.sourceReminderId != null) item {
            ArchiveRecordContent(state, vm, onAddType)
        }
        draft.sourceReminderId?.let { source -> item {
            Surface(
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    state.sourceDescriptions[source]?.let {
                        Text(stringResource(R.string.life_archive_source_text, it), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { onReminder(source) }, enabled = !state.busy) { Text(stringResource(R.string.life_archive_source)) }
                }
            }
        } }
        if (draft.title.isNotBlank()) item {
            Surface(
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.life_archive_optimize_label), style = MaterialTheme.typography.titleMedium.editorial())
                    OutlinedTextField(
                        state.instruction,
                        vm::setInstruction,
                        Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        enabled = !state.saving,
                        shape = RoundedCornerShape(Radius.m),
                        placeholder = { Text(stringResource(R.string.life_archive_instruction_hint)) },
                    )
                    ArchiveAiButton(R.string.life_archive_optimize, !state.busy && state.instruction.isNotBlank(), vm::optimize)
                    Text(stringResource(R.string.life_archive_preserve_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.life_archive_cloud_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.beforeOptimization != null) TextButton(onClick = vm::discardOptimization, enabled = !state.busy, contentPadding = PaddingValues()) { Text(stringResource(R.string.life_archive_discard)) }
                }
            }
        }
    }
}

@Composable
private fun ArchiveAiButton(label: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.m),
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = Height.button),
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(IconSize.s))
        Spacer(Modifier.width(Spacing.s))
        Text(stringResource(label))
    }
}

@Composable
private fun ArchiveRecordContent(state: LifeArchiveUiState, vm: LifeArchiveViewModel, onAddType: () -> Unit) {
    val draft = state.draft ?: return
    val baseline = state.original ?: state.beforeOptimization
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.life_archive_results), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = vm::toggleManualEditing, enabled = !state.busy, contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Radius.none)) {
                Text(stringResource(if (state.manualEditing) R.string.life_archive_done_editing else R.string.life_archive_edit_fields))
            }
        }
        Surface(
            shape = RoundedCornerShape(Radius.card),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (state.manualEditing) {
                    OutlinedTextField(draft.title, vm::setTitle, Modifier.fillMaxWidth(), singleLine = true, enabled = !state.saving,
                        shape = RoundedCornerShape(Radius.m),
                        label = { Text(stringResource(R.string.life_archive_record_title)) })
                    ArchiveCategoryChips(state.categories, draft.categoryId, { it?.let(vm::setDraftCategory) }, onAddType, enabled = !state.busy)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(draft.title, style = MaterialTheme.typography.headlineSmall.editorial())
                        state.categories.find { it.id == draft.categoryId }?.let {
                            Surface(
                                shape = RoundedCornerShape(Radius.circular),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            ) {
                                Text(
                                    archiveCategoryLabel(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                                )
                            }
                        }
                    }
                }
                draft.fields.forEachIndexed { index, field ->
                    if (index > 0) HorizontalDivider(thickness = BorderWidth.xs, color = MaterialTheme.colorScheme.outline)
                    if (state.manualEditing) ArchiveEditableField(field, index, vm, !state.saving)
                    else ArchiveField(field, baseline?.fields?.find { it.name == field.name }, baseline != null)
                }
                if (state.manualEditing) {
                    TextButton(onClick = vm::addField, enabled = !state.busy && draft.fields.size < 50, contentPadding = PaddingValues()) { Text(stringResource(R.string.life_archive_add_field)) }
                    OutlinedTextField(draft.body, vm::setBody, Modifier.fillMaxWidth(), minLines = 2, maxLines = 8, enabled = !state.saving,
                        shape = RoundedCornerShape(Radius.m),
                        label = { Text(stringResource(R.string.life_archive_body)) })
                } else if (draft.body.isNotBlank()) {
                    HorizontalDivider(thickness = BorderWidth.xs, color = MaterialTheme.colorScheme.outline)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(stringResource(R.string.life_archive_body), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(draft.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val deleted = baseline?.fields?.filter { old -> draft.fields.none { it.name == old.name } }.orEmpty()
                if (deleted.isNotEmpty()) Text(stringResource(R.string.life_archive_deleted_fields, deleted.joinToString { it.name }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ArchiveField(field: LifeArchiveField, original: LifeArchiveField?, hasBaseline: Boolean) {
    val changed = hasBaseline && original?.value != field.value
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(field.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(field.value, style = MaterialTheme.typography.bodyMedium, color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (changed) Text(stringResource(R.string.life_archive_modified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ArchiveEditableField(field: LifeArchiveField, index: Int, vm: LifeArchiveViewModel, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row {
            OutlinedTextField(field.name, { vm.setField(index, it, field.value) }, Modifier.weight(1f), enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.life_archive_field_name)) })
            IconButton(onClick = { vm.removeField(index) }, enabled = enabled) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.life_archive_remove_field)) }
        }
        OutlinedTextField(field.value, { vm.setField(index, field.name, it) }, Modifier.fillMaxWidth(), enabled = enabled, maxLines = 6,
            label = { Text(stringResource(R.string.life_archive_field_value)) })
    }
}
