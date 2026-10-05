package com.dailysatori.ui.feature.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.bookkeeping.*
import com.dailysatori.service.phone.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.sms.*
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.feature.bookkeeping.*
import com.dailysatori.ui.feature.settings.sms.SmsSourceCard
import com.dailysatori.ui.theme.*
import kotlinx.datetime.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable fun PhoneAssistantScreen(onBack: () -> Unit, initialTab: Int = 0, vm: PhoneAssistantViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(initialTab) }
    var settings by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf<PhoneChannel?>(null) }
    var todoEdit by remember { mutableStateOf<PhoneTodoEdit?>(null) }
    var ledgerEdit by remember { mutableStateOf<LedgerEntry?>(null) }
    var ledgerAdd by remember { mutableStateOf<PhoneMessage?>(null) }
    var clear by remember { mutableStateOf<PhoneMessage?>(null) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.configure(PhoneChannel.SMS, state.preferences.sms.copy(enabled = it)); vm.refreshAccess()
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshAccess() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshAccess() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsScaffold(title = i18n.t(if (history) "phone.history" else "phone.title"), onBack = { if (history) history = false else onBack() }, actions = {
        IconButton(onClick = { history = !history }) { Icon(Icons.Default.History, i18n.t("phone.history")) }
        IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, i18n.t("phone.settings")) }
    }) { modifier ->
        Column(modifier.fillMaxSize()) {
            if (!history) TabRow(selectedTabIndex = tab) {
                listOf("pending", "todos", "ledger").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(i18n.t("phone.$label")) })
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                item {
                    Text(i18n.t("phone.overview", i18n.t(if (state.preferences.sms.enabled && state.smsGranted) "phone.on" else "phone.off"),
                        i18n.t(if (state.preferences.notification.enabled && state.bookkeeping.granted && state.bookkeeping.capture.connected) "phone.on" else "phone.off")), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { settings = true }) { Text(i18n.t("phone.settings")) }
                    if (state.failed || state.bookkeeping.capture.failed) Text(i18n.t("phone.operation_failed"), color = MaterialTheme.colorScheme.error)
                    if (!state.canNotify) TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { Text(i18n.t("phone.notify_permission")) }
                    if (!state.exactAlarms) TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }) { Text(i18n.t("phone.alarm_permission")) }
                }
                if (history) {
                    items(state.history, key = { it.id }) { row -> PhoneMessageCard(row, state, vm,
                        onAddTodo = { todoEdit = PhoneTodoEdit(row, null) }, onAddLedger = { ledgerAdd = row }, onClear = { clear = row }) }
                    if (state.historyHasMore) item {
                        TextButton(onClick = vm::loadMoreHistory) { Text(i18n.t("phone.load_more")) }
                    }
                } else {
                    if (tab != 2) {
                        val todos = state.messages.flatMap { row -> row.todos.filter {
                            if (tab == 1) it.state == PhoneResultState.DONE else it.state !in setOf(PhoneResultState.DONE, PhoneResultState.IGNORED)
                        }.map { row to it } }
                        items(todos, key = { it.second.id }) { (row, todo) -> PhoneTodoCard(row, todo, state, vm) { todoEdit = PhoneTodoEdit(row, todo) } }
                        val legacy = state.smsRecords.filter { !it.id.startsWith("ph_") && if (tab == 1) it.status == SmsSourceStatus.CREATED else it.status !in setOf(SmsSourceStatus.CREATED, SmsSourceStatus.IGNORED) }
                        items(legacy, key = { "legacy:${it.id}" }) { record -> SmsSourceCard(record, state.busy,
                            onRetry = { vm.retryLegacy(record) }, onIgnore = { vm.ignoreLegacy(record) }, onBlock = { vm.blockLegacy(record) }) }
                    }
                    if (tab != 1) {
                        if (tab == 2) item { BookkeepingMonth(state.bookkeeping, vm::changeMonth) }
                        val entries = state.bookkeeping.ledger.entries.filter { entry ->
                            if (tab == 0) entry.status == LedgerStatus.PENDING else {
                                val date = Instant.fromEpochMilliseconds(entry.receivedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
                                entry.status == LedgerStatus.POSTED && date.year == state.bookkeeping.month.year && date.monthNumber == state.bookkeeping.month.monthNumber
                            }
                        }.sortedByDescending { it.receivedAt }
                        items(entries, key = { "ledger:${it.id}" }) { entry -> LedgerEntryCard(entry, state.bookkeeping.copy(busy = state.busy),
                            onEdit = { ledgerEdit = entry }, onIgnore = { vm.dismissLedger(entry.id, LedgerStatus.IGNORED) },
                            onDelete = { vm.dismissLedger(entry.id, LedgerStatus.DELETED) }) }
                    }
                }
                item { Text(i18n.t("phone.coverage"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (settings) PhoneSettingsDialog(state, onDismiss = { settings = false }, onConfigure = vm::configure,
        onSmsEnable = { if (it && !state.smsGranted) smsPermission.launch(Manifest.permission.RECEIVE_SMS) else vm.configure(PhoneChannel.SMS, state.preferences.sms.copy(enabled = it)) },
        onGrant = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        onSources = { settings = false; sources = true }, onCloud = { consent = it })
    if (sources) BookkeepingSourcesDialog(state.bookkeeping, vm::selectSource) { sources = false }
    consent?.let { channel -> PhoneConsentDialog(channel, { consent = null }) {
        vm.configure(channel, state.preferences.forChannel(channel).copy(cloud = true)); consent = null
    } }
    todoEdit?.let { edit -> PhoneTodoDialog(edit, state.smsRecords.firstOrNull { it.id == edit.todo?.id }?.draft, state.busy, state.failed,
        { todoEdit = null }) { draft -> vm.confirmTodo(edit.row, edit.todo, draft) { todoEdit = null } } }
    ledgerEdit?.let { entry -> LedgerEditorDialog(entry, state.busy, state.failed, { ledgerEdit = null }) { amount, currency, kind, merchant ->
        vm.editLedger(entry, amount, currency, kind, merchant) { ledgerEdit = null }
    } }
    ledgerAdd?.let { row ->
        val entry = LedgerEntry("manual:${row.id}", listOf(row.id), row.event.origin, row.event.text, row.event.receivedAt)
        LedgerEditorDialog(entry, state.busy, state.failed, { ledgerAdd = null }) { amount, currency, kind, merchant ->
            vm.addLedger(row, amount, currency, kind, merchant) { ledgerAdd = null }
        }
    }
    clear?.let { row -> AlertDialog(onDismissRequest = { clear = null }, title = { Text(i18n.t("phone.clear_source")) },
        text = { Text(i18n.t("phone.clear_notice")) }, confirmButton = { TextButton(onClick = { vm.clearText(row); clear = null }) { Text(i18n.t("bookkeeping.confirm")) } },
        dismissButton = { TextButton(onClick = { clear = null }) { Text(i18n.t("bookkeeping.cancel")) } }) }
}
