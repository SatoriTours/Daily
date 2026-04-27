package com.dailysatori.ui.pages.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailysatori.ui.components.FeatureIcon
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.ui.pages.aiconfig.AiConfigScreen
import com.dailysatori.ui.pages.plugin_center.PluginCenterScreen
import com.dailysatori.ui.pages.backup_settings.BackupSettingsScreen
import com.dailysatori.ui.pages.backup_restore.BackupRestoreScreen
import com.dailysatori.ui.pages.data_import.DataImportScreen

private enum class SettingsPage {
    MAIN,
    AI_CONFIG,
    PLUGIN_CENTER,
    BACKUP_SETTINGS,
    BACKUP_RESTORE,
    DATA_IMPORT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showGoogleApiDialog by remember { mutableStateOf(false) }
    var googleApiKey by remember { mutableStateOf("") }
    var showWebServerDialog by remember { mutableStateOf(false) }

    when (currentPage) {
        SettingsPage.MAIN -> {
            if (showAboutDialog) {
                AlertDialog(
                    onDismissRequest = { showAboutDialog = false },
                    title = { Text("Daily Satori") },
                    text = { Text("v1.0.0\n个人知识管理与 AI 阅读助手\n基于 KMP + Compose Multiplatform") },
                    confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("确定") } },
                )
            }
            if (showGoogleApiDialog) {
                AlertDialog(
                    onDismissRequest = { showGoogleApiDialog = false },
                    title = { Text("Google Books API Key") },
                    text = {
                        OutlinedTextField(
                            value = googleApiKey,
                            onValueChange = { googleApiKey = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            // Save Google API key
                            showGoogleApiDialog = false
                        }) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGoogleApiDialog = false }) { Text("取消") }
                    },
                )
            }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("设置", color = MaterialTheme.colorScheme.onPrimary) },
                        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        actions = {
                            IconButton(onClick = { showAboutDialog = true }) {
                                Icon(Icons.Default.Info, contentDescription = "关于", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = Spacing.m)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text("功能", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        SettingItem("AI 配置", "管理 AI 模型配置", Icons.Default.Star, onClick = { currentPage = SettingsPage.AI_CONFIG })
                        SettingItem("插件中心", "管理 AI 提示词插件", Icons.Default.Settings, onClick = { currentPage = SettingsPage.PLUGIN_CENTER })
                        SettingItem("Google Books API", "配置图书搜索密钥", Icons.Default.Share, onClick = { showGoogleApiDialog = true })
                    }

                    Text("系统", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        SettingItem("备份与恢复", "管理数据备份", Icons.Default.Save, onClick = { currentPage = SettingsPage.BACKUP_SETTINGS })
                        SettingItem("导入数据", "从 Flutter 版本迁移数据", Icons.Default.Refresh, onClick = { currentPage = SettingsPage.DATA_IMPORT })
                        SettingItem("下载图片", "下载文章图片到本地", Icons.Default.Dns, onClick = {})
                        SettingItem("Web 服务", "本地 HTTP 服务", Icons.Default.Share, onClick = {})
                        SettingItem("检查更新", "v1.0.0", Icons.Default.Refresh, onClick = {})
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))
                }
            }
        }
        SettingsPage.AI_CONFIG -> {
            AiConfigScreen(onBack = { currentPage = SettingsPage.MAIN })
        }
        SettingsPage.PLUGIN_CENTER -> {
            PluginCenterScreen(onBack = { currentPage = SettingsPage.MAIN })
        }
        SettingsPage.BACKUP_SETTINGS -> {
            BackupSettingsScreen(onBack = { currentPage = SettingsPage.MAIN }, onRestore = { currentPage = SettingsPage.BACKUP_RESTORE })
        }
        SettingsPage.BACKUP_RESTORE -> {
            BackupRestoreScreen(onBack = { currentPage = SettingsPage.BACKUP_SETTINGS })
        }
        SettingsPage.DATA_IMPORT -> {
            DataImportScreen(onBack = { currentPage = SettingsPage.MAIN })
        }
    }
}

@Composable
private fun SettingItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeatureIcon(icon = icon, containerSize = IconSize.xl, iconSize = IconSize.s)
            Spacer(modifier = Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
