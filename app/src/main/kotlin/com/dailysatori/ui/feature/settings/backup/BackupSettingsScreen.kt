package com.dailysatori.ui.feature.settings.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import com.dailysatori.core.storage.BackupDirectoryPicker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dailysatori.ui.component.settings.*
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
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
    val directoryPicker = rememberLauncherForActivityResult(remember { BackupDirectoryPicker() }) { uri ->
        val activity = context as? Activity ?: return@rememberLauncherForActivityResult
        uri?.let { viewModel.saveBackupDirectory(it, activity) }
    }
    AppScaffold(title = "备份与恢复", onBack = onBack, useGroupNavigation = true,
        navigationBusy = busy,
        bottomBar = {
            Button(onClick = viewModel::startBackup, enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(Spacing.m).heightIn(min = Height.button),
                shape = RoundedCornerShape(Radius.l)) {
                if (busy) CircularProgressIndicator(Modifier.size(IconSize.m),
                    strokeWidth = BorderWidth.l, color = MaterialTheme.colorScheme.onPrimary)
                else {
                    Icon(Icons.Default.Backup, null)
                    Spacer(Modifier.width(Spacing.s))
                    Text(i18n.t("settings_design.backup_now"))
                }
            }
        },
    ) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            SettingsSectionCard(i18n.t("settings_design.backup_location")) {
                SettingsRow(Icons.Default.Folder, i18n.t("settings_design.backup_directory"),
                    state.backupDirectory.ifBlank { i18n.t("settings_design.choose_directory_hint") },
                    onClick = { directoryPicker.launch(state.backupDirectory) }, enabled = !busy, showDivider = false)
            }
            SettingsSectionCard(i18n.t("settings_design.backup_encryption")) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(i18n.t(if (state.hasBackupPassword) "settings_design.backup_password_set" else "settings_design.backup_password_missing"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(state.passwordInput, viewModel::updatePasswordInput,
                        modifier = Modifier.fillMaxWidth(), label = { Text(i18n.t("settings_design.backup_password")) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, enabled = !busy)
                    OutlinedButton(onClick = viewModel::saveBackupPassword, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Height.button)) { Text(i18n.t("settings_design.save_backup_password")) }
                }
            }
            BackupVerificationCard(state, busy, i18n, viewModel)
            state.error?.let { SettingsEditorMessage(it, isError = true) }
            state.message?.let { SettingsEditorMessage(it, isError = false) }
            if (state.isBackingUp && !state.isVerifying) LinearProgressIndicator(progress = { state.backupProgress },
                modifier = Modifier.fillMaxWidth())
            if (!grouped) OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(i18n.t("settings_design.restore_backup")) }
        }
    }
}

@Composable
private fun BackupVerificationCard(state: BackupSettingsState, busy: Boolean, i18n: I18nService, viewModel: BackupSettingsViewModel) {
    SettingsSectionCard(i18n.t("settings_design.backup_verify_title")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(i18n.t("settings_design.backup_verify_description"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                OutlinedButton(onClick = viewModel::verifyLatestBackup, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Height.button)) {
                    Text(i18n.t("settings_design.backup_verify_start"))
                }
                val result = state.verificationResult
                if (result?.issue == BackupVerificationIssue.NO_PASSWORD ||
                    (result?.status == BackupVerificationStatus.FAILED && result.stage == BackupVerificationStage.DECRYPTING)) {
                    OutlinedTextField(state.verificationPasswordInput, viewModel::updateVerificationPassword,
                        modifier = Modifier.fillMaxWidth(), label = { Text(i18n.t("settings_design.backup_verify_password")) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, enabled = !busy)
                    OutlinedButton(onClick = viewModel::retryVerification,
                        enabled = !busy && state.verificationPasswordInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(i18n.t("settings_design.backup_verify_retry"))
                    }
                }
            }
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
}
