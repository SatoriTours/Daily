package com.dailysatori.ui.feature.phone

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.dailysatori.ui.component.settings.*
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
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var sources by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf<PhoneChannel?>(null) }
    var todoEdit by remember { mutableStateOf<PhoneTodoEdit?>(null) }
    var ledgerEdit by remember { mutableStateOf<LedgerEntry?>(null) }
    var ledgerAdd by remember { mutableStateOf<PhoneMessage?>(null) }
    var clear by remember { mutableStateOf<PhoneMessage?>(null) }
    val smsPermission = rememberSmsPermissionAccess(state.busy) {
        vm.configure(PhoneChannel.SMS, state.preferences.sms.copy(enabled = true))
    }
    var settingsUnavailable by remember { mutableStateOf(false) }
    var restrictedHelp by remember { mutableStateOf(false) }
    val openSettings: (Intent) -> Unit = { intent ->
        if (runCatching { context.startActivity(intent) }.isFailure && !openAppPermissionSettings(context)) settingsUnavailable = true
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshAccess() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = history && !settings) { history = false }
    val todos = state.messages.flatMap { row -> row.todos.filter {
        if (tab == 1) it.state == PhoneResultState.DONE else it.state !in setOf(PhoneResultState.DONE, PhoneResultState.IGNORED)
    }.map { row to it } }
    val legacy = state.smsRecords.filter { !it.id.startsWith("ph_") && if (tab == 1) it.status == SmsSourceStatus.CREATED
        else it.status !in setOf(SmsSourceStatus.CREATED, SmsSourceStatus.IGNORED) }
    val pendingEntries = state.bookkeeping.ledger.entries.filter { it.status == LedgerStatus.PENDING }.sortedByDescending { it.receivedAt }
    val postedEntries = state.bookkeeping.ledger.entries.filter { entry ->
        val date = Instant.fromEpochMilliseconds(entry.receivedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
        entry.status == LedgerStatus.POSTED && date.year == state.bookkeeping.month.year && date.monthNumber == state.bookkeeping.month.monthNumber
    }.sortedByDescending { it.receivedAt }
    val entries = if (tab == 0) pendingEntries else postedEntries
    val pendingCount = state.messages.sumOf { row -> row.todos.count { it.state !in setOf(PhoneResultState.DONE, PhoneResultState.IGNORED) } } +
        state.smsRecords.count { !it.id.startsWith("ph_") && it.status !in setOf(SmsSourceStatus.CREATED, SmsSourceStatus.IGNORED) } + pendingEntries.size
    val todoCount = state.messages.sumOf { row -> row.todos.count { it.state == PhoneResultState.DONE } } +
        state.smsRecords.count { !it.id.startsWith("ph_") && it.status == SmsSourceStatus.CREATED }
    val needsSetup = PhoneChannel.entries.none { phoneChannelStatus(state, it) == "connected" }
    if (settings) PhoneSettingsScreen(state, onDismiss = { settings = false }, onConfigure = vm::configure,
        onSmsEnable = { if (it) smsPermission() else vm.configure(PhoneChannel.SMS, state.preferences.sms.copy(enabled = false)) },
        onGrant = { openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        onSources = { sources = true }, onCloud = { consent = it },
        onAppSettings = { if (!openAppPermissionSettings(context)) settingsUnavailable = true }, onRestrictedHelp = { restrictedHelp = true },
        onNotify = { openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
        onAlarm = {
            if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            else openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        })
    else SettingsScaffold(title = i18n.t(if (history) "phone.history" else "phone.title"), onBack = { if (history) history = false else onBack() }, actions = {
        IconButton(onClick = { history = !history }) { Icon(Icons.Default.History, i18n.t("phone.history")) }
        IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, i18n.t("phone.settings")) }
    }) { modifier ->
        Column(modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (!history) item { PhoneOverview(state) { settings = true } }
                if (!history) item { PhoneTabs(tab, listOf(pendingCount, todoCount, postedEntries.size)) { tab = it } }
                if (state.failed || state.bookkeeping.capture.failed) item {
                    Text(i18n.t("phone.operation_failed"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (history) {
                    if (state.history.isEmpty()) item { PhoneEmptyState("history", needsSetup) { settings = true } }
                    items(state.history, key = { it.id }) { row -> PhoneMessageCard(row, state, vm,
                        onAddTodo = { todoEdit = PhoneTodoEdit(row, null) }, onAddLedger = { ledgerAdd = row }, onClear = { clear = row }) }
                    if (state.historyHasMore) item {
                        TextButton(onClick = vm::loadMoreHistory) { Text(i18n.t("phone.load_more")) }
                    }
                } else {
                    if (tab != 2) {
                        if (todos.isNotEmpty() || legacy.isNotEmpty()) item {
                            Text(i18n.t("phone.todos"), style = MaterialTheme.typography.titleSmall)
                        }
                        items(todos, key = { it.second.id }) { (row, todo) -> PhoneTodoCard(row, todo, state, vm) { todoEdit = PhoneTodoEdit(row, todo) } }
                        items(legacy, key = { "legacy:${it.id}" }) { record -> SmsSourceCard(record, state.busy,
                            onRetry = { vm.retryLegacy(record) }, onIgnore = { vm.ignoreLegacy(record) }, onBlock = { vm.blockLegacy(record) }) }
                    }
                    if (tab != 1) {
                        if (tab == 2) item {
                            Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.padding(Spacing.m)) { BookkeepingMonth(state.bookkeeping, vm::changeMonth) }
                            }
                        }
                        if (tab == 0 && entries.isNotEmpty()) item { Text(i18n.t("phone.ledger"), style = MaterialTheme.typography.titleSmall) }
                        items(entries, key = { "ledger:${it.id}" }) { entry -> LedgerEntryCard(entry, state.bookkeeping.copy(busy = state.busy),
                            onEdit = { ledgerEdit = entry }, onIgnore = { vm.dismissLedger(entry.id, LedgerStatus.IGNORED) },
                            onDelete = { vm.dismissLedger(entry.id, LedgerStatus.DELETED) }) }
                    }
                    val empty = when (tab) {
                        0 -> todos.isEmpty() && legacy.isEmpty() && entries.isEmpty()
                        1 -> todos.isEmpty() && legacy.isEmpty()
                        else -> entries.isEmpty()
                    }
                    if (empty) item { PhoneEmptyState(listOf("pending", "todos", "ledger")[tab], needsSetup) { settings = true } }
                }
                item { PhoneInfoNote(i18n.t("phone.coverage")) }
            }
        }
    }
    if (restrictedHelp) AlertDialog(onDismissRequest = { restrictedHelp = false }, title = { Text(i18n.t("phone.restricted_help")) },
        text = { Text(i18n.t("phone.restricted_settings_help"), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { restrictedHelp = false; if (!openAppPermissionSettings(context)) settingsUnavailable = true }) { Text(i18n.t("phone.open_app_settings")) } },
        dismissButton = { TextButton(onClick = { restrictedHelp = false }) { Text(i18n.t("bookkeeping.cancel")) } })
    if (settingsUnavailable) AlertDialog(onDismissRequest = { settingsUnavailable = false }, title = { Text(i18n.t("phone.open_app_settings")) },
        text = { Text(i18n.t("phone.settings_unavailable")) }, confirmButton = {
            TextButton(onClick = { settingsUnavailable = false }) { Text(i18n.t("bookkeeping.done")) }
        })
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
