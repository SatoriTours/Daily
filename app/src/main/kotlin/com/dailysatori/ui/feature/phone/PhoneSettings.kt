package com.dailysatori.ui.feature.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.dailysatori.service.phone.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

@Composable internal fun PhoneSettingsScreen(state: PhoneUiState, onDismiss: () -> Unit,
    onConfigure: (PhoneChannel, PhoneOptions) -> Unit, onSmsEnable: (Boolean) -> Unit,
    onGrant: () -> Unit, onSources: () -> Unit, onCloud: (PhoneChannel) -> Unit,
    onAppSettings: () -> Unit, onRestrictedHelp: () -> Unit, onNotify: () -> Unit, onAlarm: () -> Unit) {
    val i18n: I18nService = koinInject()
    BackHandler(onBack = onDismiss)
    SettingsScaffold(title = i18n.t("phone.settings"), onBack = onDismiss) { modifier ->
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            item { PhoneInfoNote(i18n.t("phone.settings_intro")) }
            PhoneChannel.entries.forEach { channel ->
                item {
                    val options = state.preferences.forChannel(channel)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Row(Modifier.padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Icon(if (channel == PhoneChannel.SMS) Icons.Default.Sms else Icons.Default.Notifications,
                                null, Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.primary)
                            Text(i18n.t("phone.${channel.name.lowercase()}"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            PhoneStatusLabel(phoneChannelStatus(state, channel))
                        }
                        Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
                            Column {
                                PhoneToggle(i18n.t("phone.receive"), i18n.t("phone.receive_hint"), options.enabled, state.busy) {
                                    if (channel == PhoneChannel.SMS) onSmsEnable(it) else onConfigure(channel, options.copy(enabled = it))
                                }
                                PhoneSettingsDivider()
                                if (channel == PhoneChannel.SMS) {
                                    PhoneSettingAction(i18n.t(if (state.smsGranted) "phone.manage_sms_access" else "phone.grant_sms_access"),
                                        i18n.t("phone.sms_access_hint"), Icons.Default.Security, !state.busy) {
                                        if (state.smsGranted) onAppSettings() else onSmsEnable(true)
                                    }
                                } else {
                                    PhoneSettingAction(i18n.t("bookkeeping.manage_access"), i18n.t("phone.notification_access_hint"),
                                        Icons.Default.Security, !state.busy, onGrant)
                                    PhoneSettingsDivider()
                                    PhoneSettingAction(i18n.t("bookkeeping.sources", state.preferences.sources.size),
                                        i18n.t("phone.sources_hint"), Icons.Default.Apps, !state.busy, onSources)
                                    PhoneSettingsDivider()
                                    PhoneSettingAction(i18n.t("phone.restricted_help"), i18n.t("phone.restricted_hint"),
                                        Icons.Default.HelpOutline, true, onRestrictedHelp)
                                }
                                PhoneSettingsDivider()
                                PhoneToggle(i18n.t("phone.extract_todos"), i18n.t("phone.todos_hint"), options.todos, state.busy) {
                                    onConfigure(channel, options.copy(todos = it))
                                }
                                PhoneSettingsDivider()
                                PhoneToggle(i18n.t("phone.extract_ledger"), i18n.t("phone.ledger_hint"), options.ledger, state.busy) {
                                    onConfigure(channel, options.copy(ledger = it))
                                }
                                PhoneSettingsDivider()
                                PhoneToggle(i18n.t("phone.cloud_short"), i18n.t("phone.cloud_hint"), options.cloud, state.busy) {
                                    if (it) onCloud(channel) else onConfigure(channel, options.copy(cloud = false))
                                }
                            }
                        }
                        if (options.enabled && !options.todos && !options.ledger) Text(i18n.t("phone.choose_purpose"),
                            Modifier.padding(horizontal = Spacing.s), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(i18n.t("phone.reminders_title"), Modifier.padding(horizontal = Spacing.s), style = MaterialTheme.typography.titleMedium)
                    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
                        Column {
                            PhoneSettingAction(i18n.t("phone.reminder_notifications"),
                                i18n.t(if (state.canNotify) "phone.permission_ready" else "phone.notify_permission"),
                                Icons.Default.NotificationsActive, true, onNotify)
                            PhoneSettingsDivider()
                            PhoneSettingAction(i18n.t("phone.reminder_timing"),
                                i18n.t(if (state.exactAlarms) "phone.permission_ready" else "phone.alarm_permission"),
                                Icons.Default.Alarm, true, onAlarm)
                        }
                    }
                }
            }
            if (state.failed) item { Text(i18n.t("phone.operation_failed"), color = MaterialTheme.colorScheme.error) }
            item { PhoneInfoNote(i18n.t("phone.privacy")) }
            item { PhoneInfoNote(i18n.t("phone.changes_notice"), Icons.Default.Info) }
        }
    }
}

@Composable private fun PhoneToggle(label: String, hint: String, value: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, enabled = !busy, role = Role.Switch, onValueChange = onChange)
        .heightIn(min = Height.listItem).padding(Spacing.m), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onCheckedChange = null, enabled = !busy)
    }
}

@Composable private fun PhoneSettingAction(label: String, hint: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = Height.listItem).padding(Spacing.m), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(icon, null, Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun PhoneSettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
}
