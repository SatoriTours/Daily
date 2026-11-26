package com.dailysatori.ui.feature.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject


@Composable
fun BackupRestoreScreen(onBack: () -> Unit = {}) {
    val viewModel: BackupRestoreViewModel = koinViewModel()
    val i18n: I18nService = koinInject()
    val state by viewModel.state.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }
    val busy = state.isRestoring || state.isRestorePending
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectFile(it.toString()) }
    }
    LaunchedEffect(Unit) {
        viewModel.loadBackupFiles()
    }

    BackupPageScaffold(
        busy = busy,
        title = i18n.t("backup_restore.title"),
        onBack = onBack,
    ) { modifier ->
        if (showPasswordDialog) {
            BackupPasswordDialog(
                title = i18n.t("backup_restore.confirm_title"),
                description = state.selectedName + "\n\n" + i18n.t("backup_restore.warning"),
                label = i18n.t("backup_restore.password_label"),
                confirmLabel = i18n.t("backup_restore.confirm"),
                onDismiss = { showPasswordDialog = false },
                onConfirm = { restorePassword ->
                    showPasswordDialog = false
                    viewModel.restoreBackup(restorePassword)
                },
            )
        }
        LazyColumn(
            modifier = modifier.fillMaxSize().selectableGroup(),
            contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(i18n.t("backup_restore.select_title"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !busy,
                        shape = RoundedCornerShape(Radius.circular),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.s + Spacing.xxs, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(IconSize.xs), tint = MaterialTheme.colorScheme.primary)
                            Text(i18n.t("backup_restore.choose_file_short"), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (state.isRestoring || state.errorMessage.isNotBlank() || state.successMessage.isNotBlank()) item {
                Column(Modifier.fillMaxWidth().padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    RestoreFeedback(state)
                }
            }
            if (state.selectedFileUri != null) item {
                BackupFileCard(selected = true, title = state.selectedFileName,
                    subtitle = i18n.t("backup_restore.selected_file"), enabled = false, onClick = {})
            }
            if (state.backupList.isNotEmpty()) item {
                Text(i18n.t("backup_restore.directory_files"), Modifier.padding(horizontal = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.isLoading) item { CircularProgressIndicator(Modifier.size(IconSize.m)) }
            if (state.backupList.isEmpty() && state.selectedFileUri == null && !state.isLoading) item {
                Surface(
                    shape = RoundedCornerShape(Radius.card),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(IconSize.xl), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(i18n.t("backup_restore.empty"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            itemsIndexed(state.backupList, key = { _, path -> path }) { index, path ->
                BackupFileCard(
                    selected = state.selectedFileUri == null && index == state.selectedBackupIndex,
                    title = viewModel.getBackupTime(path), subtitle = i18n.t("backup_restore.encrypted_file"),
                    first = index == 0, last = index == state.backupList.lastIndex,
                    enabled = !busy, onClick = { viewModel.selectBackupIndex(index) },
                )
            }
            if (state.selectedName.isNotBlank()) item {
                Button(
                    onClick = { showPasswordDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).heightIn(min = Height.button),
                    shape = RoundedCornerShape(Radius.m), enabled = !busy,
                ) {
                    if (state.isRestoring) CircularProgressIndicator(Modifier.size(IconSize.m), strokeWidth = BorderWidth.l,
                        color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Default.Restore, null, Modifier.size(IconSize.m))
                    Spacer(Modifier.width(Spacing.s))
                    Text(i18n.t("backup_restore.restore_selected"))
                }
            }
            item {
                Row(Modifier.padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(Icons.Default.Info, null, Modifier.size(IconSize.xs), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(i18n.t("backup_restore.warning_short"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.backupList.isNotEmpty()) item {
                Text(i18n.t("backup_restore.backup_count", state.backupList.size),
                    Modifier.fillMaxWidth().padding(top = Spacing.m), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RestoreFeedback(state: BackupRestoreState) {
    if (state.isRestoring) {
        if (state.restoreProgress <= 0f) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { state.restoreProgress }, modifier = Modifier.fillMaxWidth())
        Text(
            state.statusMessage + if (state.restoreProgress > 0f) " ${(state.restoreProgress * 100).toInt()}%" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (state.errorMessage.isNotBlank()) {
        Text(state.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (state.successMessage.isNotBlank()) {
        Text(state.successMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BackupFileCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    enabled: Boolean,
    first: Boolean = true,
    last: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) BorderWidth.s else BorderWidth.xs,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent,
            ).padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
        ) {
            Icon(
                Icons.Default.Description,
                null,
                Modifier.size(IconSize.m),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
