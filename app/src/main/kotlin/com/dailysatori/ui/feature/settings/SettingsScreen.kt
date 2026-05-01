package com.dailysatori.ui.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Key
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.settings.SettingsRow
import com.dailysatori.ui.feature.aiconfig.AiConfigScreen
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

private enum class SettingsPage {
    MAIN,
    AI_CONFIG,
    MCP_SERVER,
    PLUGIN_CENTER,
    BACKUP_SETTINGS,
    BACKUP_RESTORE,
    DATA_IMPORT,
}

@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    var showAboutDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = currentPage != SettingsPage.MAIN) {
        currentPage = SettingsPage.MAIN
    }

    when (currentPage) {
        SettingsPage.MAIN -> {
            if (showAboutDialog) {
                AlertDialog(
                    onDismissRequest = { showAboutDialog = false },
                    title = { Text("Daily Satori") },
                    text = { Text("v${state.currentVersion}\n个人知识管理与 AI 阅读助手\n基于 KMP + Compose Multiplatform") },
                    confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("确定") } },
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(
                    title = "设置",
                    showBack = false,
                    actions = {
                        IconButton(onClick = { showAboutDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "关于")
                        }
                    },
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.m)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    // AI & Services section
                    Text("AI 与服务", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        SettingsRow(
                            icon = Icons.Default.Star,
                            title = "AI 配置",
                            subtitle = "管理模型服务商与 API 密钥",
                            onClick = { currentPage = SettingsPage.AI_CONFIG },
                        )
                        SettingsRow(
                            icon = Icons.Default.Hub,
                            title = "MCP 服务",
                            subtitle = "管理外部工具服务连接",
                            onClick = { currentPage = SettingsPage.MCP_SERVER },
                        )
                        SettingsRow(
                            icon = Icons.Default.Settings,
                            title = "插件中心",
                            subtitle = "管理 AI 提示词插件",
                            onClick = { currentPage = SettingsPage.PLUGIN_CENTER },
                        )
                    }

                    // Web & Network section
                    Text("网络与同步", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        SettingsRow(
                            icon = Icons.Default.Language,
                            title = "Web 服务",
                            subtitle = when {
                                state.isTogglingWebServer -> "启动中..."
                                state.webServerError != null -> "错误: ${state.webServerError}"
                                state.webServerRunning -> state.webServerAddress
                                else -> "已停止"
                            },
                            trailing = {
                                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    if (state.isTogglingWebServer) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        Switch(
                                            checked = state.webServerRunning,
                                            onCheckedChange = { viewModel.toggleWebServer() },
                                        )
                                    }
                                }
                            },
                            onClick = { viewModel.toggleWebServer() },
                        )
                        if (state.webServerToken.isNotEmpty()) {
                            SettingsRow(
                                icon = Icons.Default.Key,
                                title = "API Token",
                                subtitle = state.webServerToken,
                                trailing = {
                                    IconButton(onClick = { viewModel.refreshToken() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "刷新 Token")
                                    }
                                },
                                onClick = {},
                            )
                        }
                        SettingsRow(
                            icon = Icons.Default.Refresh,
                            title = "检查更新",
                            subtitle = if (state.isCheckingUpdate) "检查中..." else "当前 v${state.currentVersion}",
                            onClick = { viewModel.checkUpdate() },
                        )
                    }

                    // Data section
                    Text("数据管理", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        SettingsRow(
                            icon = Icons.Default.Save,
                            title = "备份与恢复",
                            subtitle = "管理数据备份与还原",
                            onClick = { currentPage = SettingsPage.BACKUP_SETTINGS },
                        )
                        SettingsRow(
                            icon = Icons.Default.FileDownload,
                            title = "导入数据",
                            subtitle = "从 Flutter 版本迁移数据",
                            onClick = { currentPage = SettingsPage.DATA_IMPORT },
                        )
                        SettingsRow(
                            icon = Icons.Default.CloudDownload,
                            title = "下载图片",
                            subtitle = "下载文章图片到本地",
                            onClick = {},
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))
                }
            }
        }
        SettingsPage.AI_CONFIG -> AiConfigScreen(onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.MCP_SERVER -> McpServerScreen(onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.PLUGIN_CENTER -> PluginCenterScreen(onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.BACKUP_SETTINGS -> BackupSettingsScreen(onBack = { currentPage = SettingsPage.MAIN }, onRestore = { currentPage = SettingsPage.BACKUP_RESTORE })
        SettingsPage.BACKUP_RESTORE -> BackupRestoreScreen(onBack = { currentPage = SettingsPage.BACKUP_SETTINGS })
        SettingsPage.DATA_IMPORT -> DataImportScreen(onBack = { currentPage = SettingsPage.MAIN })
    }
}

