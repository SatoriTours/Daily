package com.dailysatori.ui.feature.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.dailysatori.service.phone.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

@Composable internal fun PhoneSettingsDialog(state: PhoneUiState, onDismiss: () -> Unit,
    onConfigure: (PhoneChannel, PhoneOptions) -> Unit, onSmsEnable: (Boolean) -> Unit,
    onGrant: () -> Unit, onSources: () -> Unit, onCloud: (PhoneChannel) -> Unit) {
    val i18n: I18nService = koinInject()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(i18n.t("phone.settings")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(i18n.t("phone.privacy"), style = MaterialTheme.typography.bodySmall)
            PhoneChannel.entries.forEach { channel ->
                val options = state.preferences.forChannel(channel)
                Text(i18n.t("phone.${channel.name.lowercase()}"), style = MaterialTheme.typography.titleMedium)
                PhoneToggle(i18n.t("phone.receive"), options.enabled, state.busy) {
                    if (channel == PhoneChannel.SMS) onSmsEnable(it) else onConfigure(channel, options.copy(enabled = it))
                }
                PhoneToggle(i18n.t("phone.extract_todos"), options.todos, state.busy) { onConfigure(channel, options.copy(todos = it)) }
                PhoneToggle(i18n.t("phone.extract_ledger"), options.ledger, state.busy) { onConfigure(channel, options.copy(ledger = it)) }
                if (options.enabled && !options.todos && !options.ledger) Text(i18n.t("phone.choose_purpose"), color = MaterialTheme.colorScheme.error)
                PhoneToggle(i18n.t("phone.cloud"), options.cloud, state.busy) {
                    if (it) onCloud(channel) else onConfigure(channel, options.copy(cloud = false))
                }
                if (channel == PhoneChannel.SMS && !state.smsGranted) Text(i18n.t("phone.sms_permission"), style = MaterialTheme.typography.bodySmall)
                if (channel == PhoneChannel.NOTIFICATION) {
                    Text(i18n.t("bookkeeping.status.${when {
                        !options.enabled -> "off"
                        !state.bookkeeping.granted -> "grant_required"
                        state.preferences.sources.isEmpty() -> "select_required"
                        state.bookkeeping.capture.connected -> "connected"
                        else -> "disconnected"
                    }}"), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onGrant) { Text(i18n.t("bookkeeping.manage_access")) }
                    TextButton(onClick = onSources) { Text(i18n.t("bookkeeping.sources", state.preferences.sources.size)) }
                }
                HorizontalDivider()
            }
            Text(i18n.t("phone.changes_notice"), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(i18n.t("bookkeeping.done")) } })
}

@Composable private fun PhoneToggle(label: String, value: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, onCheckedChange = onChange, enabled = !busy)
    }
}
