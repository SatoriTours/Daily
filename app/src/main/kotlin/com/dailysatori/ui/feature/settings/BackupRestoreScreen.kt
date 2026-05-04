package com.dailysatori.ui.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun BackupRestoreScreen(onBack: () -> Unit = {}) {
    val viewModel: BackupRestoreViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        viewModel.loadBackupFiles()
    }

    AppScaffold(
        title = "从备份恢复",
        onBack = onBack,
        bottomBar = {
            if (state.backupList.isNotEmpty()) {
                Button(
                    onClick = {
                        showPasswordDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.m)
                        .height(Height.button),
                    enabled = state.selectedBackupIndex >= 0 && !state.isRestoring,
                ) {
                    if (state.isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("恢复备份")
                    }
                }
            }
        },
    ) { modifier ->
        if (showPasswordDialog) {
            RestorePasswordDialog(
                password = restorePassword,
                hint = viewModel.getPasswordHint(state.backupList.getOrNull(state.selectedBackupIndex).orEmpty()),
                onPasswordChange = { restorePassword = it },
                onDismiss = { showPasswordDialog = false },
                onConfirm = {
                    showPasswordDialog = false
                    scope.launch {
                        if (viewModel.restoreBackup(restorePassword)) {
                            restorePassword = ""
                        }
                    }
                },
            )
        }
        if (state.backupList.isEmpty()) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.xxl * 2),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(Spacing.m))
                        Text(
                            state.errorMessage.ifEmpty { "暂无备份信息" },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "请先在备份设置中创建备份",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Column(modifier = modifier.fillMaxSize().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                RestoreFeedback(state)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    itemsIndexed(state.backupList) { index, path ->
                        BackupFileCard(
                            path = path,
                            selected = index == state.selectedBackupIndex,
                            time = viewModel.getBackupTime(path),
                            passwordHint = viewModel.getPasswordHint(path),
                            onClick = { if (!state.isRestoring) viewModel.selectBackupIndex(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreFeedback(state: BackupRestoreState) {
    if (state.isRestoring) {
        LinearProgressIndicator(progress = { state.restoreProgress }, modifier = Modifier.fillMaxWidth())
        Text(
            state.statusMessage.ifBlank { "正在恢复备份..." },
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
    path: String,
    selected: Boolean,
    time: String,
    passwordHint: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.m))
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
                    "密码提示：末尾 $passwordHint",
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
    password: String,
    hint: String,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入备份密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("密码提示：末尾 $hint")
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("此备份文件的密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("恢复") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
