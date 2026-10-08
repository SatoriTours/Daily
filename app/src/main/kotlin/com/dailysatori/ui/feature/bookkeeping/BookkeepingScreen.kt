package com.dailysatori.ui.feature.bookkeeping

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.bookkeeping.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.theme.*
import kotlinx.datetime.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun BookkeepingScreen(onBack: () -> Unit, viewModel: BookkeepingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var showSources by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val pending = state.ledger.entries.filter { it.status == LedgerStatus.PENDING }.sortedByDescending { it.receivedAt }
    SettingsScaffold(title = i18n.t("bookkeeping.title"), onBack = onBack) { modifier ->
        LazyColumn(modifier.padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m)) {
            item {
                BookkeepingAccessCard(state, onEnabled = viewModel::setEnabled, onSources = { showSources = true },
                    onGrant = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
            }
            if (state.error || state.capture.failed) item {
                Text(i18n.t(if (state.capture.failed) "bookkeeping.capture_failed" else "bookkeeping.operation_failed"),
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.refreshAccess() }, enabled = !state.busy) { Text(i18n.t("bookkeeping.refresh")) }
            }
            item {
                LedgerSummary(state, viewModel::changePeriod, viewModel::changeAnchor,
                    onEdit = { editing = it }, onDismiss = { entry, status -> viewModel.dismiss(entry.id, status) })
            }
            if (pending.isNotEmpty()) item { Text(i18n.t("bookkeeping.pending", pending.size), style = MaterialTheme.typography.titleMedium) }
            items(pending, key = { it.id }) { entry -> LedgerEntryCard(entry, state, onEdit = { editing = entry }, onIgnore = {
                viewModel.dismiss(entry.id, LedgerStatus.IGNORED)
            }, onDelete = { viewModel.dismiss(entry.id, LedgerStatus.DELETED) }) }
        }
    }
    if (showSources) BookkeepingSourcesDialog(state, onSelect = viewModel::selectSource, onDismiss = { showSources = false })
    editing?.let { entry -> LedgerEditorDialog(entry, state.busy, state.error, onDismiss = { editing = null }, onSave = { amount, currency, kind, merchant ->
        viewModel.edit(entry.id, amount, currency, kind, merchant) { editing = null }
    }) }
}

@Composable
private fun BookkeepingAccessCard(state: BookkeepingUiState, onEnabled: (Boolean) -> Unit, onSources: () -> Unit, onGrant: () -> Unit) {
    val i18n: I18nService = koinInject()
    val status = when {
        !state.enabled -> "off"
        !state.granted -> "grant_required"
        state.selectedSources.isEmpty() -> "select_required"
        state.capture.connected -> "connected"
        else -> "disconnected"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i18n.t("bookkeeping.enabled"), Modifier.weight(1f))
                Switch(state.enabled, onCheckedChange = onEnabled, enabled = !state.busy)
            }
            Text(i18n.t("bookkeeping.status.$status"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(i18n.t("bookkeeping.local_notice"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onGrant) { Text(i18n.t(if (state.granted) "bookkeeping.manage_access" else "bookkeeping.grant")) }
            TextButton(onClick = onSources, enabled = !state.busy) { Text(i18n.t("bookkeeping.sources", state.selectedSources.size)) }
        }
    }
}

@Composable
internal fun LedgerSummary(state: BookkeepingUiState, onPeriod: (LedgerPeriod) -> Unit, onAnchor: (Int) -> Unit,
    onEdit: (LedgerEntry) -> Unit, onDismiss: (LedgerEntry, LedgerStatus) -> Unit) {
    val i18n: I18nService = koinInject()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LedgerPeriod.entries.forEach { period ->
                Surface(modifier = Modifier.weight(1f).selectable(period == state.period, role = Role.Tab, onClick = { onPeriod(period) }),
                    shape = RoundedCornerShape(Radius.m),
                    color = if (period == state.period) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                    Text(i18n.t("bookkeeping.period.${period.name.lowercase()}"), Modifier.padding(vertical = Spacing.s),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onAnchor(-1) }) { Text(i18n.t("bookkeeping.previous_period")) }
            Text(state.buckets.firstOrNull()?.let { periodLabel(state.period, it) } ?: state.anchor.toString(),
                style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onAnchor(1) }) { Text(i18n.t("bookkeeping.next_period")) }
        }
        state.totals.forEach { Text(totalLine(i18n, it)) }
        Text(i18n.t("bookkeeping.totals_notice"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.buckets.forEach { bucket ->
            Text(periodLabel(state.period, bucket), style = MaterialTheme.typography.titleSmall)
            if (bucket.entries.isEmpty()) Text(i18n.t("bookkeeping.bucket_empty"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            bucket.totals.forEach { Text(totalLine(i18n, it), style = MaterialTheme.typography.bodySmall) }
            bucket.entries.forEach { entry -> LedgerEntryCard(entry, state, onEdit = { onEdit(entry) },
                onIgnore = { onDismiss(entry, LedgerStatus.IGNORED) }, onDelete = { onDismiss(entry, LedgerStatus.DELETED) }) }
        }
    }
}

private fun totalLine(i18n: I18nService, total: LedgerTotal): String = i18n.t("bookkeeping.month_totals", total.currency,
    LedgerMoney.format(total.income, total.currency), LedgerMoney.format(total.expense, total.currency),
    LedgerMoney.format(total.refund, total.currency))

internal fun periodLabel(period: LedgerPeriod, bucket: LedgerBucket): String = when (period) {
    LedgerPeriod.MONTH -> "${bucket.start.year}-${bucket.start.monthNumber.toString().padStart(2, '0')}"
    LedgerPeriod.WEEK -> "${bucket.start} ~ ${bucket.end.monthNumber.toString().padStart(2, '0')}-${bucket.end.dayOfMonth.toString().padStart(2, '0')}"
    LedgerPeriod.DAY -> bucket.start.toString()
    else -> bucket.start.toString()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerEntryCard(entry: LedgerEntry, state: BookkeepingUiState, onEdit: () -> Unit, onIgnore: () -> Unit, onDelete: () -> Unit) {
    val i18n: I18nService = koinInject()
    var showText by remember(entry.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val currency = if (entry.reason == "ambiguous_currency") i18n.t("bookkeeping.unknown_currency") else entry.currency
            Text("$currency ${entry.amountMinor?.let { LedgerMoney.format(it, entry.currency) } ?: i18n.t("bookkeeping.unknown_amount")}",
                style = MaterialTheme.typography.titleMedium)
            Text(listOf(i18n.t("bookkeeping.kind.${entry.kind.name.lowercase()}"), entry.merchant,
                entry.accountTail.takeIf { it.isNotEmpty() }?.let { i18n.t("bookkeeping.account_tail", *arrayOf(it)) }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · "))
            val source = state.sources.firstOrNull { it.packageName == entry.source }?.label ?: entry.source
            val time = Instant.fromEpochMilliseconds(entry.receivedAt).toLocalDateTime(TimeZone.currentSystemDefault())
                .toString().replace('T', ' ').take(16)
            Text(i18n.t("bookkeeping.received_time", source, time), style = MaterialTheme.typography.bodySmall)
            if (entry.status == LedgerStatus.PENDING) Text(i18n.t("bookkeeping.reason.${entry.reason}"), color = MaterialTheme.colorScheme.error)
            if (showText) Text(entry.text, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = onEdit, enabled = !state.busy) { Text(i18n.t(if (entry.status == LedgerStatus.PENDING) "bookkeeping.confirm" else "bookkeeping.edit")) }
                if (entry.status == LedgerStatus.PENDING) TextButton(onClick = onIgnore, enabled = !state.busy) { Text(i18n.t("bookkeeping.ignore")) }
                TextButton(onClick = onDelete, enabled = !state.busy) { Text(i18n.t("bookkeeping.delete")) }
                TextButton(onClick = { showText = !showText }) { Text(i18n.t(if (showText) "bookkeeping.hide_source" else "bookkeeping.show_source")) }
            }
        }
    }
}
