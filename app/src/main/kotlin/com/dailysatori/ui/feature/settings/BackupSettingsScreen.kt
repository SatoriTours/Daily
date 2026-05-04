package com.dailysatori.ui.feature.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.misc.FeatureIcon
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun BackupSettingsScreen(onBack: () -> Unit = {}, onRestore: () -> Unit = {}) {
    val viewModel: BackupSettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val activity = context as? Activity ?: return@rememberLauncherForActivityResult
        uri?.let { viewModel.saveBackupDirectory(it, activity) }
    }

    AppScaffold(title = "备份与恢复", onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(Spacing.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("备份目录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            BackupDirectoryCard(state)
            OutlinedButton(
                onClick = { directoryPicker.launch(null) },
                modifier = Modifier.fillMaxWidth().height(Height.button),
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(if (state.backupDirectory.isBlank()) "选择备份目录" else "更换备份目录")
            }

            Text("备份密码", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                if (state.hasBackupPassword) "已设置。新备份会使用当前保存的密码。" else "未设置。密码至少 10 位。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.passwordInput,
                onValueChange = viewModel::updatePasswordInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备份加密密码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            OutlinedButton(
                onClick = { viewModel.saveBackupPassword() },
                modifier = Modifier.fillMaxWidth().height(Height.button),
            ) {
                Text("保存备份密码")
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

            if (state.isBackingUp) {
                LinearProgressIndicator(progress = { state.backupProgress }, modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = { viewModel.startBackup() },
                modifier = Modifier.fillMaxWidth().height(Height.button),
                enabled = !state.isBackingUp,
            ) {
                if (state.isBackingUp) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("立即备份")
                }
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().height(Height.button)) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("恢复备份")
            }
        }
    }
}

@Composable
private fun BackupDirectoryCard(state: BackupSettingsState) {
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
    ) {
        Row(modifier = Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            FeatureIcon(
                icon = Icons.Default.Folder,
                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                iconTint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text("备份位置", style = MaterialTheme.typography.bodySmall)
                Text(
                    state.backupDirectoryDisplay.ifBlank { "未选择" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
