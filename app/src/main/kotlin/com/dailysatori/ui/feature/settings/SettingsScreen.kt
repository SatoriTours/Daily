package com.dailysatori.ui.feature.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.dailysatori.R
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.*
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.feature.aiconfig.AiConfigScreen
import com.dailysatori.ui.feature.aiconfig.AiPurposeScreen
import com.dailysatori.ui.feature.profile.DataPrivacyScreen
import com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsScreen
import com.dailysatori.ui.feature.settings.remotenews.RemoteNewsSettingsScreen
import com.dailysatori.ui.feature.settings.speech.SpeechSettingsScreen
import com.dailysatori.ui.feature.settings.backup.BackupRestoreScreen
import com.dailysatori.ui.feature.settings.backup.BackupSettingsScreen
import com.dailysatori.ui.feature.settings.importing.DataImportScreen
import com.dailysatori.ui.feature.settings.mcp.McpServerScreen
import com.dailysatori.ui.feature.settings.plugin.PluginCenterScreen
import com.dailysatori.ui.feature.settings.reminder.ReminderSettingsScreen
import com.dailysatori.ui.feature.settings.skills.SkillSettingsScreen
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsState()
    val i18n: I18nService = koinInject()
    val context = LocalContext.current
    val tokenResetMessage = stringResource(R.string.settings_token_reset_done)
    LaunchedEffect(state.webServerMessage) {
        if (state.webServerMessage != null) {
            Toast.makeText(context, tokenResetMessage, Toast.LENGTH_SHORT).show()
            viewModel.consumeWebServerMessage()
        }
    }
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val rootBack = onBack
    val childBack = { currentPage = currentPage.parent(); section = null }
    val navigate: (SettingsDestination) -> Unit = { currentPage = it.page; section = it.section }
    BackHandler(enabled = currentPage != SettingsPage.MAIN, onBack = childBack)
    BackHandler(enabled = currentPage == SettingsPage.MAIN && rootBack != null) { rootBack?.invoke() }

    val pages = currentPage.groupPages()
    val group = currentPage.groupTitleKey()?.let { title ->
        SettingsGroupNavigation(i18n.t(title), pages.map { i18n.t(it.tabKey()) }, pages.indexOf(currentPage)) {
            navigate(SettingsDestination(pages[it]))
        }
    }
    CompositionLocalProvider(LocalSettingsGroupNavigation provides group) {
        when (currentPage) {
            SettingsPage.MAIN -> SettingsMainPage(state, scrollState, navigate, onBack)
            SettingsPage.AI_CONFIG -> AiConfigScreen(onBack = childBack)
            SettingsPage.AI_PURPOSE -> AiPurposeScreen(
                onBack = childBack,
                onNavigateToAiConfig = { navigate(SettingsDestination(SettingsPage.AI_CONFIG)) },
            )
            SettingsPage.SPEECH -> SpeechSettingsScreen(onBack = childBack)
            SettingsPage.DIARY_TAGS -> com.dailysatori.ui.feature.diary.DiaryTagSettingsScreen(onBack = childBack)
            SettingsPage.MCP_SERVER -> McpServerScreen(onBack = childBack)
            SettingsPage.PLUGIN_CENTER -> PluginCenterScreen(onBack = childBack)
            SettingsPage.BACKUP_SETTINGS -> BackupSettingsScreen(onBack = childBack,
                onRestore = { navigate(SettingsDestination(SettingsPage.BACKUP_RESTORE)) })
            SettingsPage.BACKUP_RESTORE -> BackupRestoreScreen(onBack = childBack)
            SettingsPage.DATA_IMPORT -> DataImportScreen(onBack = childBack)
            SettingsPage.SKILLS -> SkillSettingsScreen(onBack = childBack)
            SettingsPage.DIAGNOSTICS -> com.dailysatori.ui.feature.settings.diagnostics.DiagnosticSettingsScreen(onBack = childBack)
            SettingsPage.REMINDERS -> ReminderSettingsScreen(onBack = childBack, initialSection = section)
            SettingsPage.SMS_REMINDERS, SettingsPage.BOOKKEEPING, SettingsPage.PHONE_ASSISTANT ->
                com.dailysatori.ui.feature.phone.PhoneAssistantScreen(onBack = childBack,
                    initialSettings = true, initialSection = section)
            SettingsPage.REMOTE_NEWS -> RemoteNewsSettingsScreen(onBack = childBack)
            SettingsPage.EXTERNAL_FAVORITES -> ExternalFavoritesSettingsScreen(onBack = childBack)
            SettingsPage.PRIVACY -> DataPrivacyScreen(onBack = childBack)
            SettingsPage.WEB_SERVICE -> WebServicePage(state, viewModel, childBack)
            SettingsPage.UPDATES -> UpdatesPage(state, viewModel, childBack)
        }
    }
}

@Composable
private fun SettingsMainPage(
    state: SettingsState,
    scrollState: ScrollState,
    onNavigate: (SettingsDestination) -> Unit,
    onBack: (() -> Unit)?,
) {
    val i18n: I18nService = koinInject()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    BackHandler(enabled = searching) { searching = false; query = "" }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    val results = searchSettings(query) { i18n.t(it) }
    AppScaffold(
        title = stringResource(R.string.personal_settings_title),
        onBack = onBack,
        showBack = onBack != null,
        actions = {
            IconButton(onClick = { searching = !searching; query = "" }) {
                Icon(if (searching) Icons.Default.Close else Icons.Default.Search,
                    i18n.t(if (searching) "settings_design.close_search" else "settings_design.search"))
            }
        },
    ) { modifier ->
        Column(modifier.fillMaxSize()) {
            if (searching) OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text(i18n.t("settings_design.search_hint")) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Close, i18n.t("settings_design.clear_search"))
                } },
                singleLine = true,
                shape = RoundedCornerShape(Radius.l),
                modifier = Modifier.fillMaxWidth().padding(Spacing.m).focusRequester(focus),
            )
            Column(
                Modifier.weight(1f).verticalScroll(if (searching) rememberScrollState() else scrollState)
                    .padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Spacer(Modifier.height(Spacing.xs))
                if (searching && query.isNotBlank()) {
                    if (results.isEmpty()) Text(i18n.t("settings_design.no_results"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else SettingsSectionCard(i18n.t("settings_design.results")) {
                        results.forEachIndexed { index, item ->
                            SettingsRow(settingsEntryIcon(item.destination.page), i18n.t(item.title),
                                i18n.t(item.group), onClick = { onNavigate(item.destination) },
                                showDivider = index < results.lastIndex)
                        }
                    }
                } else settingsHomeEntries.groupBy { it.group }.forEach { (group, entries) ->
                    SettingsSectionCard(i18n.t(group)) {
                        entries.forEachIndexed { index, item ->
                            SettingsRow(settingsEntryIcon(item.destination.page), i18n.t(item.title), "",
                                onClick = { onNavigate(item.destination) },
                                value = if (item.destination.page == SettingsPage.UPDATES) state.currentVersion else i18n.t(item.description),
                                showDivider = index < entries.lastIndex)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.l))
            }
        }
    }
}

private fun settingsEntryIcon(page: SettingsPage) = when (page) {
    SettingsPage.REMINDERS -> Icons.Default.Notifications
    SettingsPage.PHONE_ASSISTANT, SettingsPage.SMS_REMINDERS -> Icons.Default.Sms
    SettingsPage.DIARY_TAGS -> Icons.Default.Label
    SettingsPage.AI_CONFIG, SettingsPage.AI_PURPOSE, SettingsPage.SPEECH -> Icons.Default.AutoAwesome
    SettingsPage.SKILLS, SettingsPage.PLUGIN_CENTER -> Icons.Default.Extension
    SettingsPage.MCP_SERVER, SettingsPage.WEB_SERVICE -> Icons.Default.Hub
    SettingsPage.REMOTE_NEWS, SettingsPage.EXTERNAL_FAVORITES -> Icons.Default.Article
    SettingsPage.BACKUP_SETTINGS, SettingsPage.BACKUP_RESTORE, SettingsPage.DATA_IMPORT -> Icons.Default.CloudSync
    SettingsPage.PRIVACY -> Icons.Outlined.PrivacyTip
    SettingsPage.UPDATES -> Icons.Default.SystemUpdate
    else -> Icons.Default.BugReport
}

@Composable
private fun WebServicePage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val i18n: I18nService = koinInject()
    AppScaffold(title = i18n.t("settings_design.tools"), onBack = onBack, useGroupNavigation = true) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            SettingsSectionCard(i18n.t("settings_design.web")) {
                WebServerRow(state, viewModel)
            }
            if (state.webServerToken.isNotEmpty()) SettingsSectionCard(stringResource(R.string.settings_token_title)) {
                ApiTokenRow(state, viewModel)
            }
        }
    }
}

@Composable
private fun UpdatesPage(state: SettingsState, viewModel: SettingsViewModel, onBack: () -> Unit) {
    val i18n: I18nService = koinInject()
    var showAbout by rememberSaveable { mutableStateOf(false) }
    AboutDialog(showAbout, state.currentVersion) { showAbout = false }
    AppScaffold(title = i18n.t("settings_design.updates"), onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            UpdateSettingsSection(state, viewModel)
            SettingsSectionCard(i18n.t("settings_design.about")) {
                SettingsRow(Icons.Default.Info, "Daily Satori", state.currentVersion,
                    onClick = { showAbout = true }, showDivider = false)
            }
        }
    }
}

@Composable
private fun AboutDialog(show: Boolean, currentVersion: String, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = Radius.none,
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

private fun webServerSubtitle(state: SettingsState): String = when {
    state.isTogglingWebServer -> if (state.webServerRunning) "停止中..." else "启动中..."
    state.webServerError != null -> "错误: ${state.webServerError}"
    state.webServerRunning -> state.webServerAddress
    else -> "已停止"
}
