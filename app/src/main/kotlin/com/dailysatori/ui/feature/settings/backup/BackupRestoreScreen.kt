package com.dailysatori.ui.feature.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject


@Composable
fun BackupRestoreScreen(onBack: () -> Unit = {}) {
    val viewModel: BackupRestoreViewModel = koinViewModel()
    val i18n: I18nService = koinInject()
    val state by viewModel.state.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectFile(it.toString()) }
    }
    LaunchedEffect(Unit) {
        viewModel.loadBackupFiles()
    }
    LaunchedEffect(state.successMessage) {
        if (state.successMessage.isNotBlank()) {
            restorePassword = ""
        }
    }

    AppScaffold(
        useGroupNavigation = true,
        navigationBusy = state.isRestoring,
        title = i18n.t("backup_restore.title"),
        onBack = onBack,
        bottomBar = {
            if (state.selectedName.isNotBlank()) {
                Button(
                    onClick = {
                        showPasswordDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.m,
                            top = Spacing.m,
                            end = Spacing.m,
                            bottom = Spacing.m,
                        )
                        .height(Height.button),
                    enabled = !state.isRestoring && !state.isRestorePending,
                ) {
                    if (state.isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(IconSize.m), strokeWidth = BorderWidth.l, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(i18n.t("backup_restore.restore"))
                    }
                }
            }
        },
    ) { modifier ->
        if (showPasswordDialog) {
            RestorePasswordDialog(
                name = state.selectedName,
                i18n = i18n,
                password = restorePassword,
                onPasswordChange = { restorePassword = it },
                onDismiss = { showPasswordDialog = false; restorePassword = "" },
                onConfirm = {
                    showPasswordDialog = false
                    viewModel.restoreBackup(restorePassword)
                    restorePassword = ""
                },
            )
        }
        Column(modifier = modifier.fillMaxSize().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                enabled = !state.isRestoring && !state.isRestorePending,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(i18n.t("backup_restore.choose_file")) }
            RestoreFeedback(state)
            if (state.selectedFileUri != null) {
                BackupFileCard(selected = true, time = state.selectedFileName, onClick = {})
            }
            if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(IconSize.m))
            if (state.backupList.isEmpty() && state.selectedFileUri == null && !state.isLoading) {
                Text(i18n.t("backup_restore.empty"), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(bottom = Spacing.l),
            ) {
                itemsIndexed(state.backupList) { index, path ->
                    BackupFileCard(
                        selected = index == state.selectedBackupIndex,
                        time = viewModel.getBackupTime(path),
                        onClick = { viewModel.selectBackupIndex(index) },
                    )
                }
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
    time: String,
    onClick: () -> Unit,
) {
    val i18n: I18nService = koinInject()
    Card(
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.l))
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Restore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(time, style = MaterialTheme.typography.titleSmall)
                Text(
                    i18n.t("backup_restore.encrypted_file"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RestorePasswordDialog(
    name: String,
    i18n: I18nService,
    password: String,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(i18n.t("backup_restore.confirm_title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(i18n.t("backup_restore.warning"))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(i18n.t("backup_restore.password_label")) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = password.isNotBlank()) { Text(i18n.t("backup_restore.confirm")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t("backup_restore.cancel")) } },
    )
}
