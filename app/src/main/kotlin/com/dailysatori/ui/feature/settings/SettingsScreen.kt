package com.dailysatori.ui.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import com.dailysatori.R
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.component.settings.SettingsRow
import com.dailysatori.ui.component.settings.SettingsSectionCard
import com.dailysatori.ui.feature.aiconfig.AiConfigScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.feature.profile.DataPrivacyScreen
import com.dailysatori.ui.feature.profile.ProfileViewModel
import com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsScreen
import com.dailysatori.ui.feature.settings.remotenews.RemoteNewsSettingsScreen
import com.dailysatori.ui.feature.settings.speech.SpeechSettingsScreen
import org.koin.androidx.compose.koinViewModel
import com.dailysatori.ui.feature.settings.backup.BackupRestoreScreen
import com.dailysatori.ui.feature.settings.backup.BackupSettingsScreen
import com.dailysatori.ui.feature.settings.importing.DataImportScreen
import com.dailysatori.ui.feature.settings.mcp.McpServerScreen
import com.dailysatori.ui.feature.settings.plugin.PluginCenterScreen
import com.dailysatori.ui.feature.settings.reminder.ReminderSettingsScreen
import com.dailysatori.ui.feature.settings.skills.SkillSettingsScreen
import com.dailysatori.ui.feature.settings.skills.skillSettingsRowSubtitle
import com.dailysatori.ui.feature.settings.skills.skillSettingsRowTitle
import com.dailysatori.ui.theme.*

internal enum class SettingsPage {
    MAIN,
    AI_CONFIG,
    SPEECH,
    MCP_SERVER,
    PLUGIN_CENTER,
    BACKUP_SETTINGS,
    BACKUP_RESTORE,
    DATA_IMPORT,
    SKILLS,
    DIAGNOSTICS,
    REMINDERS,
    REMOTE_NEWS,
    EXTERNAL_FAVORITES,
    PRIVACY,
}

internal fun SettingsPage.parent(): SettingsPage =
    if (this == SettingsPage.BACKUP_RESTORE) SettingsPage.BACKUP_SETTINGS else SettingsPage.MAIN

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()

    var currentPage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val rootBack = onBack

    val childBack = { currentPage = currentPage.parent() }
    BackHandler(enabled = currentPage != SettingsPage.MAIN, onBack = childBack)
    BackHandler(enabled = currentPage == SettingsPage.MAIN && rootBack != null) {
        rootBack?.invoke()
    }

    when (currentPage) {
        SettingsPage.MAIN -> SettingsMainPage(
            state = state,
            scrollState = scrollState,
            showAboutDialog = showAboutDialog,
            onShowAbout = { showAboutDialog = true },
            onDismissAbout = { showAboutDialog = false },
            onNavigate = { currentPage = it },
            viewModel = viewModel,
            onBack = onBack,
        )
        SettingsPage.AI_CONFIG -> AiConfigScreen(onBack = childBack)
        SettingsPage.SPEECH -> SpeechSettingsScreen(onBack = childBack)
        SettingsPage.MCP_SERVER -> McpServerScreen(onBack = childBack)
        SettingsPage.PLUGIN_CENTER -> PluginCenterScreen(onBack = childBack)
        SettingsPage.BACKUP_SETTINGS -> BackupSettingsScreen(onBack = childBack, onRestore = { currentPage = SettingsPage.BACKUP_RESTORE })
        SettingsPage.BACKUP_RESTORE -> BackupRestoreScreen(onBack = childBack)
        SettingsPage.DATA_IMPORT -> DataImportScreen(onBack = childBack)
        SettingsPage.SKILLS -> SkillSettingsScreen(onBack = childBack)
        SettingsPage.DIAGNOSTICS -> com.dailysatori.ui.feature.settings.diagnostics.DiagnosticSettingsScreen(onBack = childBack)
        SettingsPage.REMINDERS -> ReminderSettingsScreen(onBack = childBack)
        SettingsPage.REMOTE_NEWS -> RemoteNewsSettingsScreen(onBack = childBack)
        SettingsPage.EXTERNAL_FAVORITES -> ExternalFavoritesSettingsScreen(onBack = childBack)
        SettingsPage.PRIVACY -> DataPrivacyScreen(onBack = childBack)
    }
}

@Composable
private fun SettingsMainPage(
    state: SettingsState,
    scrollState: ScrollState,
    showAboutDialog: Boolean,
    onShowAbout: () -> Unit,
    onDismissAbout: () -> Unit,
    onNavigate: (SettingsPage) -> Unit,
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val tokenResetMessage = stringResource(R.string.settings_token_reset_done)
    LaunchedEffect(state.webServerMessage) {
        if (state.webServerMessage != null) {
            Toast.makeText(context, tokenResetMessage, Toast.LENGTH_SHORT).show()
            viewModel.consumeWebServerMessage()
        }
    }
    AboutDialog(showAboutDialog, state.currentVersion, onDismissAbout)
    AppScaffold(
        title = stringResource(R.string.personal_settings_title),
        onBack = onBack,
        showBack = onBack != null,
        actions = {
            IconButton(onClick = onShowAbout) {
                Icon(Icons.Default.Info, contentDescription = "关于")
            }
        },
    ) { modifier ->
        SettingsList(
            state = state,
            scrollState = scrollState,
            viewModel = viewModel,
            onNavigate = onNavigate,
            modifier = modifier,
        )
    }
}

@Composable
private fun AboutDialog(show: Boolean, currentVersion: String, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Daily Satori") },
        text = { Text("v$currentVersion\n个人知识管理与 AI 阅读助手\n基于 KMP + Compose Multiplatform") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } },
    )
}

@Composable
internal fun UpdateDownloadProgress(state: SettingsState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(state.updateDownloadProgressText)
        val progress = state.updateDownloadProgress
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsList(
    state: SettingsState,
    scrollState: ScrollState,
    viewModel: SettingsViewModel,
    onNavigate: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.m)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Spacer(modifier = Modifier.height(Spacing.s))
        ContentSourcesSection(onNavigate)
        AiServicesSection(onNavigate)
        AccessSection(state, viewModel, onNavigate)
        DataSection(onNavigate)
        UpdateSettingsSection(state, viewModel)
        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

@Composable
private fun ContentSourcesSection(onNavigate: (SettingsPage) -> Unit) {
    val profile: ProfileViewModel = koinViewModel()
    val state by profile.state.collectAsStateWithLifecycle()
    SettingsSectionCard(stringResource(R.string.personal_settings_sources)) {
        SettingsRow(Icons.Outlined.Newspaper, stringResource(R.string.personal_settings_news),
            if (state.enabledRemoteNewsSourceCount == 0L) stringResource(R.string.management_no_sources)
            else stringResource(R.string.management_news_count, state.remoteNewsArticleCount, state.enabledRemoteNewsSourceCount),
            onClick = { onNavigate(SettingsPage.REMOTE_NEWS) })
        SettingsRow(Icons.Outlined.CloudSync, stringResource(R.string.personal_settings_external),
            stringResource(R.string.management_external_count, state.externalFavoriteCount, state.enabledExternalSourceCount),
            onClick = { onNavigate(SettingsPage.EXTERNAL_FAVORITES) })
    }
}

@Composable
private fun AiServicesSection(onNavigate: (SettingsPage) -> Unit) {
    SettingsSectionCard(stringResource(R.string.personal_settings_ai)) {
        SettingsRow(Icons.Default.Star, "AI 配置", "管理模型服务商与 API 密钥", onClick = { onNavigate(SettingsPage.AI_CONFIG) })
        SettingsRow(Icons.Default.Mic, "语音模型", "设置日记转写的提供商、模型与 API Key", onClick = { onNavigate(SettingsPage.SPEECH) })
        SettingsRow(Icons.AutoMirrored.Filled.MenuBook, skillSettingsRowTitle(), skillSettingsRowSubtitle(), onClick = { onNavigate(SettingsPage.SKILLS) })
        SettingsRow(Icons.Default.Hub, "MCP 服务", "管理外部工具服务连接", onClick = { onNavigate(SettingsPage.MCP_SERVER) })
        SettingsRow(Icons.Default.Settings, "插件中心", "管理 AI 提示词插件", onClick = { onNavigate(SettingsPage.PLUGIN_CENTER) })
    }
}

@Composable
private fun AccessSection(state: SettingsState, viewModel: SettingsViewModel, onNavigate: (SettingsPage) -> Unit) {
    SettingsSectionCard(stringResource(R.string.personal_settings_access)) {
        SettingsRow(Icons.Default.Notifications, stringResource(R.string.reminder_settings_row_title),
            stringResource(R.string.reminder_settings_row_subtitle), onClick = { onNavigate(SettingsPage.REMINDERS) })
        WebServerRow(state, viewModel)
        if (state.webServerToken.isNotEmpty()) ApiTokenRow(state, viewModel)
    }
}

@Composable
private fun WebServerRow(state: SettingsState, viewModel: SettingsViewModel) {
    SettingsRow(
        icon = Icons.Default.Language,
        title = "Web 服务",
        subtitle = webServerSubtitle(state),
        enabled = !state.isTogglingWebServer && !state.isRefreshingToken,
        trailing = {
            Box(modifier = Modifier.size(IconSize.xxl), contentAlignment = Alignment.Center) {
                if (state.isTogglingWebServer) CircularProgressIndicator(modifier = Modifier.size(IconSize.l), strokeWidth = BorderWidth.l)
                else Switch(checked = state.webServerRunning, onCheckedChange = { viewModel.toggleWebServer() },
                    enabled = !state.isRefreshingToken)
            }
        },
        onClick = { viewModel.toggleWebServer() },
    )
}

@Composable
private fun ApiTokenRow(state: SettingsState, viewModel: SettingsViewModel) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copied = stringResource(R.string.settings_token_copied)
    val copyToken = {
        clipboard.setText(AnnotatedString(state.webServerToken))
        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }
    val busy = state.isRefreshingToken || state.isTogglingWebServer
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.settings_token_reset)) },
        text = { Text(stringResource(R.string.settings_token_reset_message)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { confirmReset = false; visible = false; viewModel.refreshToken() }) {
                Text(stringResource(R.string.settings_token_reset))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
    SettingsRow(
        icon = Icons.Default.Key,
        title = stringResource(R.string.settings_token_title),
        subtitle = if (visible) state.webServerToken else stringResource(R.string.settings_token_hidden),
        enabled = !busy,
        trailing = {
            Row {
                IconButton(onClick = { visible = !visible }, enabled = !busy) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        stringResource(if (visible) R.string.settings_token_hide else R.string.settings_token_show))
                }
                IconButton(onClick = copyToken, enabled = !busy) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.settings_token_copy))
                }
                IconButton(onClick = { confirmReset = true }, enabled = !busy) {
                    if (state.isRefreshingToken) CircularProgressIndicator(Modifier.size(IconSize.m), strokeWidth = BorderWidth.l)
                    else Icon(Icons.Default.Refresh, stringResource(R.string.settings_token_reset))
                }
            }
        },
        onClick = copyToken,
    )
}

@Composable
private fun DataSection(onNavigate: (SettingsPage) -> Unit) {
    val i18n = org.koin.compose.koinInject<com.dailysatori.service.i18n.I18nService>()
    SettingsSectionCard(stringResource(R.string.management_privacy)) {
        SettingsRow(Icons.Default.Save, "备份与恢复", "管理数据备份与还原", onClick = { onNavigate(SettingsPage.BACKUP_SETTINGS) })
        SettingsRow(Icons.Default.FileDownload, "导入数据", "从 Flutter 版本迁移数据", onClick = { onNavigate(SettingsPage.DATA_IMPORT) })
        SettingsRow(Icons.Outlined.PrivacyTip, stringResource(R.string.personal_settings_privacy), stringResource(R.string.management_privacy_hint),
            onClick = { onNavigate(SettingsPage.PRIVACY) })
        SettingsRow(Icons.Default.FileDownload, i18n.t("diagnostics.title"), i18n.t("diagnostics.subtitle"),
            onClick = { onNavigate(SettingsPage.DIAGNOSTICS) })
    }
}

private fun webServerSubtitle(state: SettingsState): String = when {
    state.isTogglingWebServer -> if (state.webServerRunning) "停止中..." else "启动中..."
    state.webServerError != null -> "错误: ${state.webServerError}"
    state.webServerRunning -> state.webServerAddress
    else -> "已停止"
}
