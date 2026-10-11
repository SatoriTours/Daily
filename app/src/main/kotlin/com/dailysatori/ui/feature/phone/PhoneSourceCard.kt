package com.dailysatori.ui.feature.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.bookkeeping.LedgerEntry
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.phone.PhoneChannel
import com.dailysatori.ui.theme.*
import kotlinx.datetime.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
internal fun PhoneTextPreview(text: String) {
    val i18n: I18nService = koinInject()
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflow by remember(text) { mutableStateOf(false) }
    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
    if (overflow || expanded) TextButton(onClick = { expanded = !expanded },
        contentPadding = PaddingValues(), modifier = Modifier.heightIn(min = Height.buttonSmall)) {
        Text(i18n.t(if (expanded) "phone.collapse_text" else "phone.expand_text"), style = MaterialTheme.typography.labelMedium)
        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, Modifier.size(IconSize.xs))
    }
}

internal fun phoneDisplayTime(receivedAt: Long, zone: String, full: Boolean = false): String {
    val value = Instant.fromEpochMilliseconds(receivedAt).toLocalDateTime(TimeZone.of(zone)).toString().replace('T', ' ').take(16)
    return if (full) value else value.drop(5)
}

@Composable
internal fun PhoneSourcePanel(onOpenSource: (String) -> Unit, ledger: LedgerEntry? = null,
    reminderId: String? = null, vm: PhoneSourceViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    val sources = remember(state.messages, state.legacy, ledger, reminderId) {
        when {
            ledger != null -> phoneLedgerSources(ledger, state.messages)
            reminderId != null -> phoneReminderSources(reminderId, state.messages, state.legacy)
            else -> emptyList()
        }
    }
    when {
        state.failed -> Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
                Text(i18n.t("phone.source_load_failed"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = vm::reload) { Text(i18n.t("phone.load_again")) }
            }
        }
        !state.loaded -> LinearProgressIndicator(Modifier.fillMaxWidth())
        sources.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            sources.forEach { source -> PhoneSourceCard(source, state.labels[source.origin] ?: source.origin, onOpenSource) }
        }
        ledger != null && ledger.eventKeys.none { it.startsWith("sms:") || it.startsWith("notification:") } -> {
            // Older bookkeeping-only entries predate PhoneMessage; never use this copy for phone records.
            PhoneSourceCard(PhoneSourceInfo(null, PhoneChannel.NOTIFICATION, ledger.source, ledger.text,
                ledger.receivedAt, TimeZone.currentSystemDefault().id, ledger.text.isBlank()), ledger.source, onOpenSource)
        }
        ledger != null || reminderId?.startsWith("sms:") == true -> Text(i18n.t("phone.source_not_found"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhoneSourceCard(source: PhoneSourceInfo, label: String, onOpenSource: (String) -> Unit) {
    val i18n: I18nService = koinInject()
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(if (source.channel == PhoneChannel.SMS) Icons.AutoMirrored.Filled.Message else Icons.Default.NotificationsNone,
                    null, Modifier.size(IconSize.xs), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(i18n.t("phone.source_info"), style = MaterialTheme.typography.titleSmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(i18n.t("phone.${source.channel.name.lowercase()}") + " · " + label,
                    Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(phoneDisplayTime(source.receivedAt, source.zone), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (source.erased) Text(i18n.t("phone.source_erased"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else PhoneTextPreview(source.text)
            source.messageId?.let { id ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = { onOpenSource(id) }, contentPadding = PaddingValues(), modifier = Modifier.fillMaxWidth()) {
                    Text(i18n.t("phone.open_source_record"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(IconSize.xs))
                }
            }
        }
    }
}
