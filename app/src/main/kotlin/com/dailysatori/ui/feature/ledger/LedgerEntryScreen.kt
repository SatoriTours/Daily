package com.dailysatori.ui.feature.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.bookkeeping.*
import com.dailysatori.core.navigation.LocalPhoneNavigation
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.feature.bookkeeping.LedgerEditorDialog
import com.dailysatori.ui.feature.phone.PhoneSourcePanel
import com.dailysatori.ui.feature.phone.phoneDisplayTime
import com.dailysatori.ui.feature.phone.phoneSignedAmount
import com.dailysatori.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Opens a stable entry ID independently of the current ledger period and asynchronous loading. */
@Composable
fun LedgerEntryScreen(entryId: String, onBack: () -> Unit, vm: LedgerViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    val navigation = LocalPhoneNavigation.current
    val entry = state.ledger.entries.firstOrNull { it.id == entryId && it.status != LedgerStatus.DELETED && it.status != LedgerStatus.IGNORED }
    var editing by remember(entryId) { mutableStateOf(false) }
    var settings by remember(entryId) { mutableStateOf(false) }
    SettingsScaffold(title = i18n.t("phone.ledger_detail"), onBack = onBack, actions = {
        if (entry != null) IconButton(onClick = { settings = true }) {
            Icon(Icons.Default.MoreHoriz, i18n.t("phone.ledger_settings"))
        }
    }) { modifier ->
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            when {
                state.failed -> item { Text(i18n.t("ledger.failed"), color = MaterialTheme.colorScheme.error) }
                !state.loaded -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                entry == null -> item { Text(i18n.t("phone.result_not_found"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> {
                    item { LedgerEntrySummary(entry) { settings = true } }
                    item { PhoneSourcePanel(onOpenSource = { navigation.openHistory(it) }, ledger = entry) }
                    item {
                        FilledTonalButton(onClick = { editing = true }, enabled = !state.busy,
                            shape = RoundedCornerShape(Radius.m), modifier = Modifier.fillMaxWidth()) { Text(i18n.t("phone.edit")) }
                    }
                }
            }
        }
    }
    if (entry != null && editing) LedgerEditorDialog(entry, state.busy, state.failed, { editing = false }) { amount, currency, kind, merchant ->
        vm.edit(entry.id, amount, currency, kind, merchant) { editing = false }
    }
    if (entry != null && settings) LedgerDetailSheet(entry, state.busy, onDismiss = { settings = false },
        onCategory = { category, remember -> vm.setCategory(entry.id, category, remember) },
        onNote = { vm.setNote(entry.id, it) }, onExcluded = { vm.setExcluded(entry.id, it) },
        onMoveToTodo = { settings = false; vm.moveToTodo(entry) },
        onDelete = { settings = false; vm.dismiss(entry.id, LedgerStatus.DELETED) })
}

@Composable
private fun LedgerEntrySummary(entry: LedgerEntry, onSettings: () -> Unit) {
    val i18n: I18nService = koinInject()
    Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(i18n.t("bookkeeping.kind.${entry.kind.name.lowercase()}") + " · " +
                i18n.t(if (entry.status == LedgerStatus.POSTED) "phone.ledger_posted" else "phone.pending"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (entry.amountMinor == null) i18n.t("phone.amount_unknown") else phoneSignedAmount(entry), style = MaterialTheme.typography.displayLarge)
            Text(entry.merchant.ifBlank { i18n.t("ledger.unknown_merchant") }, style = MaterialTheme.typography.headlineSmall)
            Text(phoneDisplayTime(entry.receivedAt, kotlinx.datetime.TimeZone.currentSystemDefault().id, full = true),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.s))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = onSettings, contentPadding = PaddingValues(), modifier = Modifier.fillMaxWidth()) {
                Text(i18n.t("ledger.detail.category"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(i18n.t("ledger.category.${entry.category.name.lowercase()}"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(IconSize.xs))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(i18n.t("ledger.detail.source"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (entry.source.startsWith("sms:")) i18n.t("phone.sms") else i18n.t("phone.notification"),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}
