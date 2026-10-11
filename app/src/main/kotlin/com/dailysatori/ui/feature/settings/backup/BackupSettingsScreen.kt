package com.dailysatori.ui.feature.settings.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import com.dailysatori.core.storage.BackupDirectoryPicker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.dailysatori.ui.component.settings.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.backup.*
import org.koin.compose.koinInject
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun BackupSettingsScreen(onBack: () -> Unit = {}, onRestore: () -> Unit = {}) {
    val viewModel: BackupSettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val busy = state.isBackingUp || state.isSavingDirectory || state.isVerifying
    val i18n: I18nService = koinInject()
    val context = LocalContext.current
    val grouped = LocalSettingsGroupNavigation.current != null
    var editingPassword by remember { mutableStateOf(false) }
    if (editingPassword) BackupPasswordDialog(
        title = i18n.t(if (state.hasBackupPassword) "settings_design.change_backup_password" else "settings_design.set_backup_password"),
        description = i18n.t("settings_design.backup_password_help"),
        label = i18n.t("settings_design.backup_password"),
        confirmLabel = i18n.t("settings_design.save_backup_password"),
        minimumLength = MinBackupPasswordLength,
        onDismiss = { editingPassword = false },
        onConfirm = { password ->
            viewModel.updatePasswordInput(password)
            viewModel.saveBackupPassword()
            editingPassword = false
        },
    )
    val directoryPicker = rememberLauncherForActivityResult(remember { BackupDirectoryPicker() }) { uri ->
        val activity = context as? Activity ?: return@rememberLauncherForActivityResult
        uri?.let { viewModel.saveBackupDirectory(it, activity) }
    }
    BackupPageScaffold(title = i18n.t("settings_design.storage"), onBack = onBack, busy = busy) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(i18n.t("settings_design.backup_configuration"), style = MaterialTheme.typography.titleMedium)
                if (state.hasBackupPassword) Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Default.Lock, null, Modifier.size(IconSize.xs), tint = AppColors.success)
                    Text(i18n.t("settings_design.backup_protected"), style = MaterialTheme.typography.labelMedium,
                        color = AppColors.success)
                }
            }
            Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surface) {
                Column {
                    BackupSettingRow(Icons.Default.Folder, i18n.t("settings_design.backup_directory"),
                        state.backupDirectoryDisplay.ifBlank { i18n.t("settings_design.choose_directory_hint") },
                        onClick = { directoryPicker.launch(state.backupDirectory) }, enabled = !busy, showDivider = true)
                    BackupSettingRow(Icons.Default.Lock, i18n.t("settings_design.backup_encryption"),
                        i18n.t(if (state.hasBackupPassword) "settings_design.backup_password_set" else "settings_design.backup_password_missing"),
                        onClick = { editingPassword = true }, enabled = !busy)
                }
            }
            Button(onClick = viewModel::startBackup,
                enabled = !busy && state.backupDirectory.isNotBlank() && state.hasBackupPassword,
                modifier = Modifier.fillMaxWidth().heightIn(min = Height.button), shape = RoundedCornerShape(Radius.m)) {
                if (state.isBackingUp && !state.isVerifying) CircularProgressIndicator(Modifier.size(IconSize.m),
                    strokeWidth = BorderWidth.l, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Default.Backup, null, Modifier.size(IconSize.m))
                Spacer(Modifier.width(Spacing.s))
                Text(i18n.t("settings_design.backup_now"))
            }
            Text(i18n.t("settings_design.backup_content_hint"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { SettingsEditorMessage(it, isError = true) }
            state.message?.let { SettingsEditorMessage(it, isError = false) }
            if (state.isBackingUp && !state.isVerifying) LinearProgressIndicator(progress = { state.backupProgress },
                modifier = Modifier.fillMaxWidth())
            HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
            BackupVerificationCard(state, busy, i18n, viewModel)
            if (!grouped) OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(i18n.t("settings_design.restore_backup")) }
        }
    }
}

@Composable
private fun BackupVerificationCard(state: BackupSettingsState, busy: Boolean, i18n: I18nService, viewModel: BackupSettingsViewModel) {
    var enteringPassword by remember { mutableStateOf(false) }
    val retry = state.verificationResult?.let {
        it.status == BackupVerificationStatus.FAILED && it.stage == BackupVerificationStage.DECRYPTING
    } == true
    if (enteringPassword) BackupPasswordDialog(
        title = i18n.t("settings_design.backup_verify_title"),
        description = i18n.t("settings_design.backup_verify_password_manual"),
        label = i18n.t("backup_restore.password_label"),
        confirmLabel = i18n.t(if (retry) "settings_design.backup_verify_retry" else "settings_design.backup_verify_start"),
        onDismiss = { enteringPassword = false },
        onConfirm = { password ->
            viewModel.updateVerificationPassword(password)
            if (retry) viewModel.retryVerification() else viewModel.verifyLatestBackup()
            enteringPassword = false
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, null, Modifier.size(IconSize.l), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(i18n.t("settings_design.backup_verify_start"), style = MaterialTheme.typography.titleSmall)
                Text(i18n.t("settings_design.backup_verify_caption"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!state.isVerifying) TextButton(onClick = { enteringPassword = true },
                enabled = !busy && state.backupDirectory.isNotBlank()) {
                Text(i18n.t(if (retry) "button.retry" else "settings_design.backup_verify_action"))
            }
        }
        if (state.isVerifying) {
            state.verificationFileName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(i18n.t("settings_design.backup_verify_stage_${state.verificationStage.name.lowercase()}"),
                style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { state.backupProgress }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = viewModel::cancelVerification, modifier = Modifier.fillMaxWidth()) {
                Text(i18n.t("button.cancel"))
            }
        } else {
            state.verificationResult?.let { BackupVerificationResultView(it, i18n) }
        }
    }
}

@Composable
private fun BackupVerificationResultView(result: BackupVerificationResult, i18n: I18nService) {
    SettingsEditorMessage(i18n.t("settings_design.backup_verify_status_${result.status.name.lowercase()}"),
        isError = result.status == BackupVerificationStatus.FAILED)
    result.fileName?.let { Text(i18n.t("settings_design.backup_verify_file") + it, style = MaterialTheme.typography.bodySmall) }
    result.backupTime?.let { Text(i18n.t("settings_design.backup_verify_backup_time") + it, style = MaterialTheme.typography.bodySmall) }
    Text(i18n.t("settings_design.backup_verify_checked_at") + result.checkedAt, style = MaterialTheme.typography.bodySmall)
    val issue = result.issue
    val detail = when {
        issue == BackupVerificationIssue.CHECK_FAILED -> "error_${result.stage.name.lowercase()}"
        issue != null -> "issue_${issue.name.lowercase()}"
        result.status == BackupVerificationStatus.LIMITED -> "limited_detail"
        else -> "passed_detail"
    }
    Text(i18n.t("settings_design.backup_verify_$detail"), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    result.summary.forEach { (kind, count) ->
        Text(i18n.t("settings_design.backup_verify_summary_$kind", count), style = MaterialTheme.typography.bodySmall)
    }
    if (result.status == BackupVerificationStatus.FAILED || result.status == BackupVerificationStatus.INCOMPLETE) {
        BackupVerificationDiagnostics(result, i18n)
    }
}

@Composable
private fun BackupVerificationDiagnostics(result: BackupVerificationResult, i18n: I18nService) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(result) { mutableStateOf(false) }
    var copied by remember(result) { mutableStateOf(false) }
    val report = remember(result) {
        "appVersion=${com.dailysatori.BuildConfig.VERSION_NAME} (${com.dailysatori.BuildConfig.VERSION_CODE})\n" +
            "androidSdk=${android.os.Build.VERSION.SDK_INT}\n" + result.diagnosticReport()
    }
    OutlinedButton(onClick = {
        clipboard.setText(AnnotatedString(report))
        copied = true
    }, modifier = Modifier.fillMaxWidth()) {
        Text(i18n.t(if (copied) "settings_design.backup_verify_log_copied" else "settings_design.backup_verify_copy_log"))
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(i18n.t(if (expanded) "settings_design.backup_verify_hide_log" else "settings_design.backup_verify_show_log"))
    }
    if (expanded) SelectionContainer {
        Text(report, style = MaterialTheme.typography.bodySmall)
    }
}
