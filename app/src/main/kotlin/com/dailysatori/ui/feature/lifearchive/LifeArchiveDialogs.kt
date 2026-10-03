package com.dailysatori.ui.feature.lifearchive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.service.lifearchive.LifeArchiveCategory
import com.dailysatori.ui.theme.*

@Composable
internal fun ArchiveTypeDialog(category: LifeArchiveCategory?, categories: List<LifeArchiveCategory>,
    onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: (String) -> Unit) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    val others = categories.filterNot { it.id == category?.id }
    var replacement by remember(category?.id) { mutableStateOf(others.firstOrNull()?.id) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (category == null) R.string.life_archive_add_type else R.string.life_archive_manage_type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.life_archive_type_name)) }, singleLine = true)
                if (category != null && others.isNotEmpty()) {
                    Text(stringResource(R.string.life_archive_migrate_to), style = MaterialTheme.typography.bodySmall)
                    ArchiveCategoryChips(others, replacement, { replacement = it })
                    TextButton(onClick = { replacement?.let(onDelete) }) { Text(stringResource(R.string.life_archive_delete_type), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank() && name.length <= 80) { Text(stringResource(R.string.life_archive_rename)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.life_archive_cancel)) } })
}

@Composable
internal fun ArchiveLocalNotice(onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.life_archive_local_notice_title)) },
        text = { Text(stringResource(R.string.life_archive_local_notice_body)) },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.life_archive_accept_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.life_archive_cancel)) } })
}

@Composable
internal fun ArchiveDeleteDialog(onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.life_archive_delete_title)) },
        text = { Text(stringResource(R.string.life_archive_delete_body)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.life_archive_delete_record), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.life_archive_cancel)) } })
}
