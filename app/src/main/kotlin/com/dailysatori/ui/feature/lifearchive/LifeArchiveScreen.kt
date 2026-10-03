package com.dailysatori.ui.feature.lifearchive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.lifearchive.*
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun LifeArchiveScreen(
    onBack: () -> Unit, onConfigureAi: () -> Unit, onReminder: (String) -> Unit,
    viewModel: LifeArchiveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingType by remember { mutableStateOf(false) }
    var managingType by remember { mutableStateOf<LifeArchiveCategory?>(null) }
    var deletingRecord by remember { mutableStateOf<String?>(null) }
    val back = { if (!state.saving) { if (state.draft != null) viewModel.closeEditor() else onBack() } }
    BackHandler(onBack = back)
    DisposableEffect(viewModel) { onDispose { viewModel.cancelRequest() } }
    AppScaffold(
        title = stringResource(if (state.draft == null) R.string.life_archive_title else if (state.original == null) R.string.life_archive_add else R.string.life_archive_edit),
        onBack = back,
        actions = {
            if (state.draft == null) {
                Text(stringResource(R.string.life_archive_local), style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = viewModel::toggleSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.life_archive_search)) }
            } else if (state.original != null) {
                IconButton(onClick = { deletingRecord = state.draft?.id }, enabled = !state.busy) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.life_archive_delete_record))
                }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.m)) {
                Button(onClick = { if (state.draft == null) viewModel.beginNew() else viewModel.save() },
                    modifier = Modifier.fillMaxWidth().height(Height.button), enabled = !state.busy && !state.loading && state.error != LifeArchiveError.STORAGE) {
                    Text(stringResource(if (state.draft == null) R.string.life_archive_add else R.string.life_archive_save))
                }
            }
        },
    ) { modifier ->
        Column(modifier.fillMaxSize()) {
            ArchiveStatus(state, viewModel, onConfigureAi)
            if (state.draft == null) ArchiveList(state, viewModel, { addingType = true }, { managingType = it })
            else LifeArchiveEditor(state, viewModel, onReminder, { addingType = true })
        }
    }
    if (addingType || managingType != null) ArchiveTypeDialog(managingType, state.categories,
        onDismiss = { addingType = false; managingType = null },
        onSave = { name -> managingType?.let { viewModel.renameCategory(it.id, name) } ?: viewModel.addCategory(name)
            addingType = false; managingType = null },
        onDelete = { replacement -> managingType?.let { viewModel.deleteCategory(it.id, replacement) }; managingType = null })
    if (state.showLocalNotice) ArchiveLocalNotice(viewModel::dismissLocalNotice) { viewModel.save(confirmLocalOnly = true) }
    deletingRecord?.let { id -> ArchiveDeleteDialog(
        onDismiss = { deletingRecord = null }, onDelete = { viewModel.deleteRecord(id); deletingRecord = null }) }
}

@Composable
private fun ArchiveStatus(state: LifeArchiveUiState, vm: LifeArchiveViewModel, onConfigureAi: () -> Unit) {
    if (state.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.life_archive_working), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (!state.saving && !state.loading) TextButton(onClick = vm::cancelRequest) { Text(stringResource(R.string.life_archive_cancel)) }
        }
    }
    state.error?.let { error ->
        Surface(Modifier.fillMaxWidth().padding(Spacing.m), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(Radius.m)) {
            Column(Modifier.padding(Spacing.m)) {
                Text(stringResource(archiveErrorResource(error)), color = MaterialTheme.colorScheme.onErrorContainer)
                Row {
                    if (error == LifeArchiveError.AI_CONFIG) TextButton(onClick = onConfigureAi) { Text(stringResource(R.string.life_archive_configure)) }
                    if (error == LifeArchiveError.STORAGE || error == LifeArchiveError.CONFLICT) TextButton(onClick = {
                        state.draft?.id?.let(vm::removePending); vm.closeEditor(); vm.reload()
                    }, enabled = !state.busy) { Text(stringResource(R.string.life_archive_retry)) }
                    if (error != LifeArchiveError.STORAGE) TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.life_archive_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun ArchiveList(state: LifeArchiveUiState, vm: LifeArchiveViewModel, onAddType: () -> Unit, onManageType: (LifeArchiveCategory) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (state.searchVisible) item { OutlinedTextField(state.query, vm::setQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.life_archive_search)) },
            trailingIcon = { IconButton(onClick = vm::toggleSearch) { Icon(Icons.Outlined.Close, stringResource(R.string.life_archive_cancel)) } }) }
        item { ArchiveCategoryChips(state.categories, state.selectedCategoryId, vm::selectCategory, onAddType, onManageType, !state.busy, all = true) }
        item { Text(stringResource(R.string.life_archive_type_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(onClick = vm::importReminders, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !state.loading && state.error != LifeArchiveError.STORAGE) {
                    Text(stringResource(R.string.life_archive_import))
                }
                Text(stringResource(R.string.life_archive_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.importTotal > 0) Text(stringResource(R.string.life_archive_progress, state.importDone, state.importTotal), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.pendingDrafts.isNotEmpty()) item { Text(stringResource(R.string.life_archive_pending, state.pendingDrafts.size), style = MaterialTheme.typography.titleMedium) }
        items(state.pendingDrafts, key = { "pending_${it.id}" }) { draft -> ArchiveRecordCard(draft, state.categories,
            onClick = { vm.openPending(draft.id) }, onDiscard = { vm.removePending(draft.id) }, enabled = !state.busy) }
        items(state.visibleRecords, key = { it.id }) { record -> ArchiveRecordCard(record, state.categories, { vm.open(record) }, enabled = !state.busy) }
        if (state.visibleRecords.isEmpty() && !state.loading && state.error != LifeArchiveError.STORAGE) item {
            Text(stringResource(if (state.records.isEmpty()) R.string.life_archive_empty else R.string.life_archive_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ArchiveRecordCard(record: LifeArchiveRecord, categories: List<LifeArchiveCategory>, onClick: () -> Unit,
    onDiscard: (() -> Unit)? = null, enabled: Boolean) {
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(Radius.l)) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(Icons.Outlined.BookmarkBorder, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(record.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(categories.find { it.id == record.categoryId }?.let { archiveCategoryLabel(it) }.orEmpty(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text(record.fields.take(2).joinToString(" · ") { it.value }.ifBlank { record.body }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onDiscard != null) IconButton(onClick = onDiscard, enabled = enabled) { Icon(Icons.Outlined.Close, stringResource(R.string.life_archive_pending_remove)) }
            else Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
internal fun ArchiveCategoryChips(categories: List<LifeArchiveCategory>, selected: String?, onSelect: (String?) -> Unit,
    onAdd: (() -> Unit)? = null, onManage: ((LifeArchiveCategory) -> Unit)? = null, enabled: Boolean = true, all: Boolean = false) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (all) item { ArchiveChip(stringResource(R.string.life_archive_all), selected == null, enabled, { onSelect(null) }) }
        items(categories, key = { it.id }) { category -> ArchiveChip(archiveCategoryLabel(category), selected == category.id, enabled,
            { onSelect(category.id) }, { onManage?.invoke(category) }) }
        if (onAdd != null) item { TextButton(onClick = onAdd, enabled = enabled) { Text(stringResource(R.string.life_archive_add_type)) } }
    }
}

@Composable
private fun ArchiveChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Surface(Modifier.combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(Radius.circular), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(label, Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun archiveCategoryLabel(category: LifeArchiveCategory): String {
    val original = defaultLifeArchiveCategories().find { it.id == category.id }
    if (original?.name != category.name) return category.name
    return stringResource(when (category.id) {
        "domain" -> R.string.life_archive_domain
        "subscription" -> R.string.life_archive_subscription
        "payment" -> R.string.life_archive_payment
        else -> R.string.life_archive_other
    })
}

private fun archiveErrorResource(error: LifeArchiveError): Int = when (error) {
    LifeArchiveError.AI_CONFIG -> R.string.life_archive_error_config
    LifeArchiveError.AI_RESPONSE -> R.string.life_archive_error_response
    LifeArchiveError.AI_REQUEST -> R.string.life_archive_error_request
    LifeArchiveError.STORAGE -> R.string.life_archive_error_storage
    LifeArchiveError.CONFLICT -> R.string.life_archive_error_conflict
    LifeArchiveError.INPUT -> R.string.life_archive_error_input
    LifeArchiveError.IMPORT_EMPTY -> R.string.life_archive_error_import_empty
}
