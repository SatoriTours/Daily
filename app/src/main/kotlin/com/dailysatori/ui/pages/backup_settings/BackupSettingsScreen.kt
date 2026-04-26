package com.dailysatori.ui.pages.backup_settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.ui.components.FeatureIcon
import com.dailysatori.ui.components.SAppBar
import com.dailysatori.ui.theme.AppColors
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun BackupSettingsScreen(onBack: () -> Unit = {}, onRestore: () -> Unit = {}) {
    var hasDirectory by remember { mutableStateOf(false) }

    Scaffold(topBar = { SAppBar(title = "备份与恢复", onBack = onBack) }) { padding ->
        if (!hasDirectory) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FeatureIcon(
                        icon = Icons.Default.Folder,
                        containerSize = IconSize.xxl * 2,
                        iconSize = IconSize.xl,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xl))
                    Text("请选择备份目录", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(Spacing.l))
                    Button(onClick = { hasDirectory = true }) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("选择备份目录")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.m)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    "备份目录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Card(
                    shape = RoundedCornerShape(Radius.m),
                    colors = CardDefaults.cardColors(
                        containerColor = AppColors.secondaryContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FeatureIcon(
                            icon = Icons.Default.Folder,
                            containerColor = AppColors.secondary.copy(alpha = 0.2f),
                            iconTint = AppColors.secondary,
                        )
                        Spacer(modifier = Modifier.width(Spacing.m))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("备份位置", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "/path/to/backups",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.l))

                Button(
                    onClick = { /* backup now */ },
                    modifier = Modifier.fillMaxWidth().height(Height.button),
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("立即备份")
                }
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth().height(Height.button),
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("恢复备份")
                }
            }
        }
    }
}
