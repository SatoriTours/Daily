package com.dailysatori.ui.feature.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import com.dailysatori.core.navigation.LocalPhoneNavigation
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.bookkeeping.LedgerEntry
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.phone.*
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.feature.bookkeeping.LedgerEditorDialog
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Processing history of SMS and notifications: source text, results, retry and rebuild actions. */
@Composable
fun PhoneHistoryScreen(onBack: () -> Unit, vm: PhoneAssistantViewModel = koinViewModel(), messageId: String? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    var todoEdit by remember { mutableStateOf<PhoneTodoEdit?>(null) }
    var ledgerAdd by remember { mutableStateOf<PhoneMessage?>(null) }
    var clear by remember { mutableStateOf<PhoneMessage?>(null) }
    val navigation = LocalPhoneNavigation.current
    val rows = remember(state.history, state.messages, messageId) { phoneHistoryRows(state.history, state.messages, messageId) }
    val listState = rememberLazyListState()
    var located by rememberSaveable(messageId) { mutableStateOf(false) }
    LaunchedEffect(messageId, state.messagesLoaded, state.historyLoaded, rows) {
        if (messageId != null && !located && state.messagesLoaded && state.historyLoaded) {
            val index = rows.indexOfFirst { it.id == messageId }
            if (index >= 0) { listState.scrollToItem(index + 1); located = true }
        }
    }
    SettingsScaffold(title = i18n.t("auth.records"), onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m - Spacing.xs)) {
            item(key = "source-info") {
                PhoneInfoNote(i18n.t("phone.local_original"), androidx.compose.material.icons.Icons.Default.Lock)
            }
            items(rows, key = { it.id }) { row ->
                PhoneMessageCard(row, state, vm, onAddTodo = { todoEdit = PhoneTodoEdit(row, null) },
                    onAddLedger = { ledgerAdd = row }, onClear = { clear = row },
                    onOpenLedger = navigation.openLedger, onOpenTodo = navigation.openTodo, focused = row.id == messageId)
            }
            if (!state.historyLoaded && !state.failed) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.historyLoaded && rows.isEmpty() && !state.failed) item { PhoneEmptyState("history", false) {} }
            if (messageId != null) {
                val target = phoneHistoryTarget(messageId, state.messagesLoaded, state.messages)
                if (target == PhoneTargetStatus.MISSING) item {
                    Text(i18n.t("phone.source_not_found"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.historyHasMore) item {
                TextButton(onClick = vm::loadMoreHistory) { Text(i18n.t("phone.load_more")) }
            }
            if (state.failed) item {
                Text(i18n.t("phone.operation_failed"), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            item { PhoneInfoNote(i18n.t("phone.clear_preserves_results")) }
        }
    }
    todoEdit?.let { edit ->
        PhoneTodoDialog(edit, state.smsRecords.firstOrNull { it.id == edit.todo?.id }?.draft, state.busy, state.failed,
            { todoEdit = null }) { draft -> vm.confirmTodo(edit.row, edit.todo, draft) { todoEdit = null } }
    }
    ledgerAdd?.let { row ->
        val entry = LedgerEntry("manual:${row.id}", listOf(row.id), row.event.origin, row.event.text, row.event.receivedAt)
        LedgerEditorDialog(entry, state.busy, state.failed, { ledgerAdd = null }) { amount, currency, kind, merchant ->
            vm.addLedger(row, amount, currency, kind, merchant) { ledgerAdd = null }
        }
    }
    clear?.let { row -> AlertDialog(onDismissRequest = { clear = null }, title = { Text(i18n.t("phone.clear_source")) },
        text = { Text(i18n.t("phone.clear_notice")) },
        confirmButton = { TextButton(onClick = { vm.clearText(row); clear = null }) { Text(i18n.t("bookkeeping.confirm")) } },
        dismissButton = { TextButton(onClick = { clear = null }) { Text(i18n.t("bookkeeping.cancel")) } }) }
}
