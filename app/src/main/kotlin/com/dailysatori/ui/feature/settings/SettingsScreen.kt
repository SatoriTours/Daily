package com.dailysatori.ui.feature.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.updateMessage) {
        state.updateMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUpdateMessage()
        }
    }

    BackHandler(enabled = currentPage != SettingsPage.MAIN) {
        currentPage = SettingsPage.MAIN
    }

    when (currentPage) {
        SettingsPage.MAIN -> SettingsMainPage(
            state = state,
            snackbarHostState = snackbarHostState,
            showAboutDialog = showAboutDialog,
            onShowAbout = { showAboutDialog = true },
            onDismissAbout = { showAboutDialog = false },
            onNavigate = { currentPage = it },
            viewModel = viewModel,
            context = context,
        )
        SettingsPage.AI_CONFIG -> AiConfigScreen(onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.MCP_SERVER -> McpServerScreen(onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.PLUGIN_CENTER -> PluginCenterScreen(onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.BACKUP_SETTINGS -> BackupSettingsScreen(onBack = { currentPage = SettingsPage.MAIN }, onRestore = { currentPage = SettingsPage.BACKUP_RESTORE })
        SettingsPage.BACKUP_RESTORE -> BackupRestoreScreen(onBack = { currentPage = SettingsPage.BACKUP_SETTINGS })
        SettingsPage.DATA_IMPORT -> DataImportScreen(onBack = { currentPage = SettingsPage.MAIN })
    }
}

@Composable
private fun SettingsMainPage(
    state: SettingsState,
    snackbarHostState: SnackbarHostState,
    showAboutDialog: Boolean,
    onShowAbout: () -> Unit,
    onDismissAbout: () -> Unit,
    onNavigate: (SettingsPage) -> Unit,
    viewModel: SettingsViewModel,
    context: Context,
) {
    AboutDialog(showAboutDialog, state.currentVersion, onDismissAbout)
    UpdateDialog(state, viewModel, context)
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AppTopBar(
                title = "设置",
                showBack = false,
                actions = {
                    IconButton(onClick = onShowAbout) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                },
            )
            SettingsList(state, viewModel, onNavigate)
        }
    }
}

@Composable
private fun AboutDialog(show: Boolean, currentVersion: String, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Satori") },
        text = { Text("v$currentVersion\n个人知识管理与 AI 阅读助手\n基于 KMP + Compose Multiplatform") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } },
    )
}

@Composable
private fun UpdateDialog(state: SettingsState, viewModel: SettingsViewModel, context: Context) {
    val release = state.availableRelease ?: return
    if (!state.showUpdateDialog) return
    AlertDialog(
        onDismissRequest = { viewModel.dismissUpdateDialog() },
        title = { Text("发现新版本") },
        text = { Text("当前版本 v${state.currentVersion}\n最新版本 ${release.version}\n是否立即更新？") },
        dismissButton = { TextButton(onClick = { viewModel.dismissUpdateDialog() }) { Text("稍后") } },
        confirmButton = { TextButton(onClick = { viewModel.startUpdateDownload(context) }) { Text("立即更新") } },
    )
}

@Composable
private fun SettingsList(
    state: SettingsState,
    viewModel: SettingsViewModel,
    onNavigate: (SettingsPage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.m)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(modifier = Modifier.height(Spacing.xs))
        AiServicesSection(onNavigate)
        NetworkSection(state, viewModel)
        DataSection(onNavigate)
        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

@Composable
private fun AiServicesSection(onNavigate: (SettingsPage) -> Unit) {
    Text("AI 与服务", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Card(shape = RoundedCornerShape(Radius.m), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        SettingsRow(Icons.Default.Star, "AI 配置", "管理模型服务商与 API 密钥", onClick = { onNavigate(SettingsPage.AI_CONFIG) })
        SettingsRow(Icons.Default.Hub, "MCP 服务", "管理外部工具服务连接", onClick = { onNavigate(SettingsPage.MCP_SERVER) })
        SettingsRow(Icons.Default.Settings, "插件中心", "管理 AI 提示词插件", onClick = { onNavigate(SettingsPage.PLUGIN_CENTER) })
    }
}

@Composable
private fun NetworkSection(state: SettingsState, viewModel: SettingsViewModel) {
    Text("网络与同步", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Card(shape = RoundedCornerShape(Radius.m), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        WebServerRow(state, viewModel)
        if (state.webServerToken.isNotEmpty()) ApiTokenRow(state, viewModel)
        SettingsRow(
            icon = Icons.Default.Refresh,
            title = "检查更新",
            subtitle = if (state.isCheckingUpdate) "检查中..." else "当前 v${state.currentVersion}",
            onClick = { viewModel.checkUpdate() },
        )
    }
}

@Composable
private fun WebServerRow(state: SettingsState, viewModel: SettingsViewModel) {
    SettingsRow(
        icon = Icons.Default.Language,
        title = "Web 服务",
        subtitle = webServerSubtitle(state),
        trailing = {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (state.isTogglingWebServer) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else Switch(checked = state.webServerRunning, onCheckedChange = { viewModel.toggleWebServer() })
            }
        },
        onClick = { viewModel.toggleWebServer() },
    )
}

@Composable
private fun ApiTokenRow(state: SettingsState, viewModel: SettingsViewModel) {
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

@Composable
private fun DataSection(onNavigate: (SettingsPage) -> Unit) {
    Text("数据管理", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Card(shape = RoundedCornerShape(Radius.m), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        SettingsRow(Icons.Default.Save, "备份与恢复", "管理数据备份与还原", onClick = { onNavigate(SettingsPage.BACKUP_SETTINGS) })
        SettingsRow(Icons.Default.FileDownload, "导入数据", "从 Flutter 版本迁移数据", onClick = { onNavigate(SettingsPage.DATA_IMPORT) })
        SettingsRow(Icons.Default.CloudDownload, "下载图片", "下载文章图片到本地", onClick = {})
    }
}

private fun webServerSubtitle(state: SettingsState): String = when {
    state.isTogglingWebServer -> "启动中..."
    state.webServerError != null -> "错误: ${state.webServerError}"
    state.webServerRunning -> state.webServerAddress
    else -> "已停止"
}
