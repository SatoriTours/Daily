package com.dailysatori.ui.feature.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.phone.*
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.component.settings.openAppPermissionSettings
import com.dailysatori.ui.feature.bookkeeping.BookkeepingSourcesDialog
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Authorizations for SMS and notification intake. This page explains why each permission is needed
 * and never shows business data — todos land in 我的提醒 and transactions in 账目.
 */
@Composable
fun AuthorizationScreen(onBack: () -> Unit, initialSection: String? = null,
    vm: PhoneAssistantViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var history by rememberSaveable { mutableStateOf(false) }
    var sources by rememberSaveable { mutableStateOf(false) }
    var consent by remember { mutableStateOf<PhoneChannel?>(null) }
    var restrictedHelp by remember { mutableStateOf(false) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshAccess() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.configure(PhoneChannel.SMS, state.preferences.sms.copy(enabled = true))
        vm.refreshAccess()
    }
    val grant = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); Unit }
    if (history) {
        PhoneHistoryScreen(onBack = { history = false }, vm = vm)
    } else {
        BackHandler(onBack = onBack)
        SettingsScaffold(title = i18n.t("auth.title"), onBack = onBack) { modifier ->
            LazyColumn(modifier.fillMaxSize(), state = rememberLazyListState(
                initialFirstVisibleItemIndex = when (initialSection) { "sms" -> 1; "notification" -> 2; else -> 0 }),
                contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                item { AuthorizationIntro() }
                PhoneChannel.entries.forEach { channel ->
                    item {
                        val options = state.preferences.forChannel(channel)
                        ChannelAuthorization(state, channel, options, vm,
                            onSmsEnable = { if (it) smsPermission.launch(Manifest.permission.RECEIVE_SMS) else vm.configure(PhoneChannel.SMS, options.copy(enabled = false)) },
                            onGrant = grant, onSources = { sources = true }, onCloud = { consent = channel },
                            onAppSettings = { if (!openAppPermissionSettings(context)) settingsUnavailable = true },
                            onRestrictedHelp = { restrictedHelp = true })
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(i18n.t("auth.section.data"), Modifier.padding(horizontal = Spacing.s),
                            style = MaterialTheme.typography.titleMedium)
                        Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
                            Column {
                                AuthorizationAction(i18n.t("auth.records"), i18n.t("auth.records_hint"),
                                    Icons.Default.History, true) { history = true }
                                AuthorizationDivider()
                                AuthorizationNote(i18n.t("auth.retention"), i18n.t("auth.retention_hint"))
                            }
                        }
                    }
                }
                if (!state.canNotify || !state.exactAlarms) item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(i18n.t("phone.reminders_title"), Modifier.padding(horizontal = Spacing.s),
                            style = MaterialTheme.typography.titleMedium)
                        Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
                            Column {
                                AuthorizationAction(i18n.t("phone.reminder_notifications"),
                                    i18n.t(if (state.canNotify) "phone.permission_ready" else "phone.notify_permission"),
                                    Icons.Default.NotificationsActive, true) {
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                }
                                AuthorizationDivider()
                                AuthorizationAction(i18n.t("phone.reminder_timing"),
                                    i18n.t(if (state.exactAlarms) "phone.permission_ready" else "phone.alarm_permission"),
                                    Icons.Default.Alarm, true) {
                                    if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}")))
                                    else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")))
                                }
                            }
                        }
                    }
                }
                if (state.failed) item { Text(i18n.t("phone.operation_failed"), color = MaterialTheme.colorScheme.error) }
                item { PhoneInfoNote(i18n.t("phone.privacy")) }
            }
        }
    }
    if (sources) BookkeepingSourcesDialog(state.bookkeeping.copy(busy = state.busy, error = state.failed), vm::selectSource) { sources = false }
    consent?.let { channel -> PhoneConsentDialog(channel, { consent = null }) {
        vm.configure(channel, state.preferences.forChannel(channel).copy(cloud = true)); consent = null
    } }
    if (restrictedHelp) AlertDialog(onDismissRequest = { restrictedHelp = false }, title = { Text(i18n.t("phone.restricted_help")) },
        text = { Text(i18n.t("phone.restricted_settings_help"), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { restrictedHelp = false; if (!openAppPermissionSettings(context)) settingsUnavailable = true }) { Text(i18n.t("phone.open_app_settings")) } },
        dismissButton = { TextButton(onClick = { restrictedHelp = false }) { Text(i18n.t("bookkeeping.cancel")) } })
    if (settingsUnavailable) AlertDialog(onDismissRequest = { settingsUnavailable = false }, title = { Text(i18n.t("phone.open_app_settings")) },
        text = { Text(i18n.t("phone.settings_unavailable")) }, confirmButton = {
            TextButton(onClick = { settingsUnavailable = false }) { Text(i18n.t("bookkeeping.done")) }
        })
}

@Composable
private fun AuthorizationIntro() {
    val i18n: I18nService = koinInject()
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(i18n.t("auth.intro_title"), style = MaterialTheme.typography.titleSmall)
            Text(i18n.t("auth.intro_todos"), style = MaterialTheme.typography.bodySmall)
            Text(i18n.t("auth.intro_ledger"), style = MaterialTheme.typography.bodySmall)
            Text(i18n.t("auth.intro_note"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChannelAuthorization(state: PhoneUiState, channel: PhoneChannel, options: PhoneOptions,
    vm: PhoneAssistantViewModel, onSmsEnable: (Boolean) -> Unit, onGrant: () -> Unit, onSources: () -> Unit,
    onCloud: () -> Unit, onAppSettings: () -> Unit, onRestrictedHelp: () -> Unit) {
    val i18n: I18nService = koinInject()
    val name = i18n.t("phone.${channel.name.lowercase()}")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(if (channel == PhoneChannel.SMS) Icons.Default.Sms else Icons.Default.Notifications, null,
                Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.primary)
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            PhoneStatusLabel(phoneChannelStatus(state, channel))
        }
        Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
            Column {
                AuthorizationToggle(i18n.t("auth.read", *arrayOf(name)), i18n.t("auth.read_hint"), options.enabled, state.busy) {
                    if (channel == PhoneChannel.SMS) onSmsEnable(it) else vm.configure(channel, options.copy(enabled = it))
                }
                AuthorizationDivider()
                if (channel == PhoneChannel.SMS) {
                    AuthorizationAction(i18n.t(if (state.smsGranted) "phone.manage_sms_access" else "phone.grant_sms_access"),
                        i18n.t("phone.sms_access_hint"), Icons.Default.Security, !state.busy) {
                        if (state.smsGranted) onAppSettings() else onSmsEnable(true)
                    }
                } else {
                    AuthorizationAction(i18n.t("bookkeeping.manage_access"), i18n.t("phone.notification_access_hint"),
                        Icons.Default.Security, !state.busy, onGrant)
                    AuthorizationDivider()
                    AuthorizationAction(i18n.t("bookkeeping.sources", state.preferences.sources.size),
                        i18n.t("phone.sources_hint"), Icons.Default.Apps, !state.busy, onSources)
                    AuthorizationDivider()
                    AuthorizationAction(i18n.t("phone.restricted_help"), i18n.t("phone.restricted_hint"),
                        Icons.Default.HelpOutline, true, onRestrictedHelp)
                }
                AuthorizationDivider()
                AuthorizationToggle(i18n.t("phone.extract_todos"), i18n.t("auth.todo_hint", *arrayOf(name)), options.todos, state.busy) {
                    vm.configure(channel, options.copy(todos = it))
                }
                AuthorizationDivider()
                AuthorizationToggle(i18n.t("phone.extract_ledger"), i18n.t("auth.ledger_hint", *arrayOf(name)), options.ledger, state.busy) {
                    vm.configure(channel, options.copy(ledger = it))
                }
                AuthorizationDivider()
                AuthorizationToggle(i18n.t("phone.cloud_short"), i18n.t("phone.cloud_hint"), options.cloud, state.busy) {
                    if (it) onCloud() else vm.configure(channel, options.copy(cloud = false))
                }
            }
        }
        if (options.enabled && !options.todos && !options.ledger) Text(i18n.t("phone.choose_purpose"),
            Modifier.padding(horizontal = Spacing.s), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AuthorizationToggle(label: String, hint: String, value: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, enabled = !busy, role = Role.Switch, onValueChange = onChange)
        .heightIn(min = Height.listItem).padding(Spacing.m), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        AuthorizationText(label, hint, Modifier.weight(1f))
        Switch(value, onCheckedChange = null, enabled = !busy)
    }
}

@Composable
private fun AuthorizationAction(label: String, hint: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = Height.listItem).padding(Spacing.m), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(icon, null, Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.primary)
            AuthorizationText(label, hint, Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AuthorizationNote(label: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        AuthorizationText(label, hint, Modifier.weight(1f))
    }
}

@Composable
private fun AuthorizationText(label: String, hint: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AuthorizationDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
}
