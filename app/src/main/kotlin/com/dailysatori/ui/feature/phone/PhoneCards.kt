package com.dailysatori.ui.feature.phone

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dailysatori.core.reminder.ReminderOpenRequest
import com.dailysatori.service.phone.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.ui.theme.*
import kotlinx.datetime.*
import org.koin.compose.koinInject

@Composable internal fun PhoneTodoCard(row: PhoneMessage, todo: PhoneTodo, state: PhoneUiState, vm: PhoneAssistantViewModel,
    onEdit: () -> Unit) {
    val i18n: I18nService = koinInject()
    var source by remember(todo.id) { mutableStateOf(false) }
    val record = state.smsRecords.firstOrNull { it.id == todo.id }
    val reminder = state.reminders.firstOrNull { it.id == todo.reminderId }
    val stopped = row.generation != state.preferences.forChannel(row.event.channel).generation && todo.state == PhoneResultState.QUEUED
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(record?.draft?.title ?: i18n.t("phone.todo_candidate"), style = MaterialTheme.typography.titleMedium)
            Text(i18n.t("phone.${row.event.channel.name.lowercase()}") + " · " + row.event.origin, style = MaterialTheme.typography.bodySmall)
            val status = if (reminder?.status == ReminderStatus.COMPLETED) "completed" else if (stopped) "stopped" else todo.state.name.lowercase()
            Text(i18n.t("phone.state.$status"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (todo.reason.isNotEmpty()) Text(i18n.t("phone.reason.${todo.reason}"), style = MaterialTheme.typography.bodySmall)
            record?.draft?.deadlineMs?.let {
                Text(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.of(row.event.zone)).toString().replace('T', ' '), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (todo.state == PhoneResultState.DONE) {
                    TextButton(onClick = { ReminderOpenRequest.state.open(todo.reminderId) }) { Text(i18n.t("phone.view_todo")) }
                    if (reminder?.status !in setOf(ReminderStatus.COMPLETED, ReminderStatus.EXPIRED))
                        TextButton(onClick = { vm.complete(todo.reminderId) }, enabled = !state.busy) { Text(i18n.t("phone.complete")) }
                } else {
                    TextButton(onClick = onEdit, enabled = !state.busy) { Text(i18n.t("bookkeeping.confirm")) }
                    TextButton(onClick = { vm.ignoreTodo(row, todo) }, enabled = !state.busy) { Text(i18n.t("bookkeeping.ignore")) }
                    if (todo.state == PhoneResultState.FAILED || stopped)
                        TextButton(onClick = { vm.retry(row) }, enabled = !state.busy) { Text(i18n.t("phone.retry")) }
                }
            }
            if (!row.textErased) TextButton(onClick = { source = !source }) { Text(i18n.t("bookkeeping.show_source")) }
            if (source && !row.textErased) Text(todo.text.ifEmpty { row.event.text }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun PhoneMessageCard(row: PhoneMessage, state: PhoneUiState, vm: PhoneAssistantViewModel,
    onAddTodo: () -> Unit, onAddLedger: () -> Unit, onClear: () -> Unit) {
    val i18n: I18nService = koinInject()
    var expanded by remember(row.id) { mutableStateOf(false) }
    val entry = state.bookkeeping.ledger.entries.firstOrNull { it.id == row.ledgerId }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(i18n.t("phone.${row.event.channel.name.lowercase()}") + " · " + row.event.origin, style = MaterialTheme.typography.titleSmall)
            Text(Instant.fromEpochMilliseconds(row.event.receivedAt).toLocalDateTime(TimeZone.of(row.event.zone)).toString().replace('T', ' '), style = MaterialTheme.typography.bodySmall)
            Text(i18n.t("phone.result_counts", row.todos.count { it.state == PhoneResultState.DONE },
                if (entry?.status in setOf(com.dailysatori.bookkeeping.LedgerStatus.POSTED, com.dailysatori.bookkeeping.LedgerStatus.PENDING)) 1 else 0))
            if (row.ledgerState == PhoneResultState.FAILED) Text(i18n.t("phone.reason.processing_failed"), color = MaterialTheme.colorScheme.error)
            if (row.todos.any { it.state in setOf(PhoneResultState.FAILED, PhoneResultState.QUEUED) } || row.ledgerState in setOf(PhoneResultState.FAILED, PhoneResultState.QUEUED))
                TextButton(onClick = { vm.retry(row) }, enabled = !state.busy) { Text(i18n.t("phone.retry")) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = onAddTodo, enabled = !state.busy) { Text(i18n.t("phone.add_todo")) }
                if (row.ledgerId.isEmpty()) TextButton(onClick = onAddLedger, enabled = !state.busy) { Text(i18n.t("phone.add_ledger")) }
            }
            if (row.ledgerId.isNotEmpty() && row.todos.any { it.state == PhoneResultState.DONE }) {
                Text(i18n.t("phone.related_notice"), style = MaterialTheme.typography.bodySmall)
                row.todos.filter { it.reminderId.isNotEmpty() }.forEach { todo ->
                    TextButton(onClick = { ReminderOpenRequest.state.open(todo.reminderId) }) { Text(i18n.t("phone.view_todo")) }
                }
            }
            if (row.textErased) Text(i18n.t("phone.source_erased"), style = MaterialTheme.typography.bodySmall)
            else {
                Row {
                    TextButton(onClick = { expanded = !expanded }) { Text(i18n.t("bookkeeping.show_source")) }
                    TextButton(onClick = onClear, enabled = !state.busy) { Text(i18n.t("phone.clear_source")) }
                }
                if (expanded) Text(row.event.text, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { vm.blockSource(row) }, enabled = !state.busy) { Text(i18n.t("phone.block_source")) }
        }
    }
}
