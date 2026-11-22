package com.dailysatori.ui.feature.settings

import com.dailysatori.core.diagnostics.MeasuredUi
import com.dailysatori.core.diagnostics.rememberUiVisit
import com.dailysatori.service.diagnostics.PerformanceScreen
import com.dailysatori.core.diagnostics.performanceContent
import com.dailysatori.ui.component.appbar.PageAction

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
import com.dailysatori.ui.feature.settings.hotnews.HotNewsSettingsScreen
import com.dailysatori.ui.feature.settings.speech.SpeechSettingsScreen
import com.dailysatori.ui.feature.settings.backup.BackupRestoreScreen
import com.dailysatori.ui.feature.settings.backup.BackupSettingsScreen
import com.dailysatori.ui.feature.settings.diagnostics.DiagnosticExportAccess
import com.dailysatori.ui.feature.settings.diagnostics.rememberDiagnosticExportAccess
import com.dailysatori.core.diagnostics.DiagnosticExportPhase
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

    val diagnostics = rememberDiagnosticExportAccess()
    val pages = currentPage.groupPages()
    val group = currentPage.groupTitleKey()?.let { title ->
        SettingsGroupNavigation(i18n.t(title), pages.map { i18n.t(it.tabKey()) }, pages.indexOf(currentPage)) {
            navigate(SettingsDestination(pages[it]))
        }
    }
    val tab = if (group != null) rememberUiVisit(PerformanceScreen.valueOf("SETTINGS_" + currentPage.name)) else null
    CompositionLocalProvider(LocalSettingsGroupNavigation provides group) {
        val parent = com.dailysatori.core.diagnostics.LocalUiVisit.current
        MeasuredUi(tab ?: parent) {
        when (currentPage) {
            SettingsPage.MAIN -> SettingsMainPage(state, scrollState, navigate, onBack, viewModel, diagnostics)
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
            SettingsPage.SKILLS -> SkillSettingsScreen(onBack = childBack)
            SettingsPage.DIAGNOSTICS -> com.dailysatori.ui.feature.settings.diagnostics.DiagnosticSettingsScreen(
                onBack = childBack, diagnostics = diagnostics,
            )
            SettingsPage.REMINDERS -> ReminderSettingsScreen(onBack = childBack, initialSection = section)
            SettingsPage.SMS_REMINDERS, SettingsPage.BOOKKEEPING, SettingsPage.PHONE_ASSISTANT, SettingsPage.AUTHORIZATION ->
                com.dailysatori.ui.feature.phone.AuthorizationScreen(onBack = childBack, initialSection = section)
            SettingsPage.HOT_NEWS -> HotNewsSettingsScreen(onBack = childBack)
            SettingsPage.REMOTE_NEWS -> RemoteNewsSettingsScreen(onBack = childBack)
            SettingsPage.EXTERNAL_FAVORITES -> ExternalFavoritesSettingsScreen(onBack = childBack)
            SettingsPage.PRIVACY -> DataPrivacyScreen(onBack = childBack)
            SettingsPage.WEB_SERVICE -> WebServicePage(state, viewModel, childBack)
            SettingsPage.UPDATES -> UpdatesPage(state, viewModel, childBack)
        }
        }
    }
}

@Composable
private fun SettingsMainPage(
    state: SettingsState,
    scrollState: ScrollState,
    onNavigate: (SettingsDestination) -> Unit,
    onBack: (() -> Unit)?,
    viewModel: SettingsViewModel,
    diagnostics: DiagnosticExportAccess,
) {
    val i18n: I18nService = koinInject()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = searching) { searching = false; query = "" }
    val results = searchSettings(query) { i18n.t(it) }
    AppScaffold(
        title = stringResource(R.string.personal_settings_title),
        onBack = onBack,
        showBack = onBack != null,
        search = if (searching) com.dailysatori.ui.component.appbar.PageSearch(
            query, i18n.t("settings_design.search_hint"), { query = it }, { searching = false; query = "" },
        ) else null,
        pageActions = listOf(PageAction(i18n.t(if (searching) "settings_design.close_search" else "settings_design.search"),
            if (searching) Icons.Default.Close else Icons.Default.Search) { searching = !searching; query = "" }),
    ) { modifier ->
        Column(modifier.fillMaxSize().performanceContent()) {
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
                            SettingsHomeEntryRow(item, state, diagnostics, index < results.lastIndex,
                                { onNavigate(item.destination) }, { viewModel.checkUpdate() }, i18n.t(item.group))
                        }
                    }
                } else settingsHomeEntries.groupBy { it.group }.forEach { (group, entries) ->
                    SettingsSectionCard(i18n.t(group)) {
                        entries.forEachIndexed { index, item ->
                            SettingsHomeEntryRow(item, state, diagnostics, index < entries.lastIndex,
                                { onNavigate(item.destination) }, { viewModel.checkUpdate() })
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.l))
            }
        }
    }
}

@Composable
private fun SettingsHomeEntryRow(
    item: SettingsEntry,
    state: SettingsState,
    diagnostics: DiagnosticExportAccess,
    showDivider: Boolean,
    onNavigate: () -> Unit,
    onCheckUpdate: () -> Unit,
    subtitleOverride: String? = null,
) {
    val i18n: I18nService = koinInject()
    val checking = state.isCheckingUpdate
    when (item.action) {
        SettingsAction.CHECK_UPDATE -> SettingsRow(
            icon = settingsEntryIcon(item.destination.page),
            title = i18n.t(item.title),
            subtitle = i18n.t("settings_design.current_version", state.currentVersion as Any),
            value = if (checking) i18n.t("settings_design.checking_update") else null,
            onClick = onCheckUpdate,
            enabled = !checking && !state.isDownloadingUpdate,
            trailing = if (checking) {
                { CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = BorderWidth.m) }
            } else null,
            showDivider = showDivider,
        )
        SettingsAction.EXPORT_LOGS -> SettingsRow(
            icon = settingsEntryIcon(item.destination.page),
            title = i18n.t(item.title),
            subtitle = exportLogSubtitle(diagnostics, i18n),
            value = if (diagnostics.export.phase == DiagnosticExportPhase.SAVED) diagnostics.info.savedName else null,
            onClick = { diagnostics.prepare(false) },
            enabled = !diagnostics.export.phase.isBusy(),
            trailing = if (diagnostics.export.phase.isBusy()) {
                { CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = BorderWidth.m) }
            } else null,
            showDivider = showDivider,
        )
        null -> SettingsRow(
            icon = settingsEntryIcon(item.destination.page),
            title = i18n.t(item.title),
            subtitle = subtitleOverride ?: "",
            onClick = onNavigate,
            value = if (item.destination.page == SettingsPage.UPDATES) state.currentVersion else i18n.t(item.description),
            showDivider = showDivider,
        )
    }
}

private fun DiagnosticExportPhase.isBusy(): Boolean =
    this == DiagnosticExportPhase.PREPARING ||
        this == DiagnosticExportPhase.AWAITING_DESTINATION ||
        this == DiagnosticExportPhase.SAVING

private fun exportLogSubtitle(diagnostics: DiagnosticExportAccess, i18n: I18nService): String {
    diagnostics.info.messageKey?.let { return i18n.t(it) }
    return when (diagnostics.export.phase) {
        DiagnosticExportPhase.PREPARING -> i18n.t("diagnostics.preparing")
        DiagnosticExportPhase.AWAITING_DESTINATION -> i18n.t("diagnostics.chooseDestination")
        DiagnosticExportPhase.SAVING -> i18n.t("diagnostics.saving")
        DiagnosticExportPhase.SAVED -> i18n.t("diagnostics.saved")
        DiagnosticExportPhase.FAILED ->
            i18n.t(if (diagnostics.export.residualFile) "diagnostics.failedResidual" else "diagnostics.failed")
        DiagnosticExportPhase.IDLE -> i18n.t("settings_design.export_logs_hint")
    }
}

private fun settingsEntryIcon(page: SettingsPage) = when (page) {
    SettingsPage.REMINDERS -> Icons.Default.Notifications
    SettingsPage.PHONE_ASSISTANT, SettingsPage.SMS_REMINDERS -> Icons.Default.Sms
    SettingsPage.DIARY_TAGS -> Icons.Default.Label
    SettingsPage.AI_CONFIG, SettingsPage.AI_PURPOSE, SettingsPage.SPEECH -> Icons.Default.AutoAwesome
    SettingsPage.SKILLS, SettingsPage.PLUGIN_CENTER -> Icons.Default.Extension
    SettingsPage.MCP_SERVER, SettingsPage.WEB_SERVICE -> Icons.Default.Hub
    SettingsPage.HOT_NEWS, SettingsPage.REMOTE_NEWS, SettingsPage.EXTERNAL_FAVORITES -> Icons.Default.Article
    SettingsPage.BACKUP_SETTINGS, SettingsPage.BACKUP_RESTORE -> Icons.Default.CloudSync
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
    val i18n: I18nService = koinInject()
    val fraction = state.updateDownloadProgress
    val total = state.updateTotalBytes
    val text = when {
        state.updateDownloadPhase == UpdateDownloadPhase.PREPARING -> i18n.t("settings_design.update_preparing")
        state.updateDownloadPhase == UpdateDownloadPhase.VERIFYING -> i18n.t("settings_design.update_verifying")
        fraction != null && total != null -> i18n.t(
            "settings_design.update_downloading_known",
            (fraction * 100).toInt(),
            formatDownloadBytes(state.updateDownloadedBytes),
            formatDownloadBytes(total),
        )
        else -> i18n.t("settings_design.update_downloading_unknown",
            formatDownloadBytes(state.updateDownloadedBytes) as Any)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(text)
        if (fraction == null || state.updateDownloadPhase == UpdateDownloadPhase.PREPARING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
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
