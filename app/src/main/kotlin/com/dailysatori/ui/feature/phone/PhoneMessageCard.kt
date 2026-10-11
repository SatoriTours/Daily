package com.dailysatori.ui.feature.phone

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.bookkeeping.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.phone.*
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

@Composable
internal fun PhoneMessageCard(row: PhoneMessage, state: PhoneUiState, vm: PhoneAssistantViewModel,
    onAddTodo: () -> Unit, onAddLedger: () -> Unit, onClear: () -> Unit,
    onOpenLedger: (String) -> Unit, onOpenTodo: (String) -> Unit, focused: Boolean = false) {
    val i18n: I18nService = koinInject()
    val entries = phoneLinkedLedger(row, state.bookkeeping.ledger.entries)
    val reminders = phoneLinkedReminders(row, state.reminders)
    val actions = phoneMessageActions(row, state.preferences.forChannel(row.event.channel).cloud)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surface,
        border = if (focused) BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(horizontal = Spacing.m).padding(top = Spacing.s, bottom = Spacing.xs)) {
            PhoneMessageHeader(row, state.sourceLabels[row.event.origin] ?: row.event.origin) {
                PhoneMessageMenu(row, actions, state.busy, vm, onClear)
            }
            Spacer(Modifier.height(Spacing.s))
            if (row.textErased) Text(i18n.t("phone.source_erased"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else PhoneTextPreview(row.event.text)
            if (entries.isNotEmpty() || reminders.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.m))
                PhoneLinkedResults(entries, reminders, onOpenLedger, onOpenTodo)
            }
            if (row.ledgerState == PhoneResultState.FAILED) Text(i18n.t("phone.reason.processing_failed"),
                Modifier.padding(top = Spacing.s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(Spacing.xs))
            PhoneMessageFooter(reminders.size, entries.size, actions.addLedger && entries.isEmpty(), state.busy,
                !state.remindersLoaded || !state.ledgerLoaded, onAddTodo, onAddLedger)
        }
    }
}

@Composable
private fun PhoneMessageHeader(row: PhoneMessage, label: String, menu: @Composable () -> Unit) {
    val i18n: I18nService = koinInject()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(if (row.event.channel == PhoneChannel.SMS) Icons.AutoMirrored.Filled.Message else Icons.Default.NotificationsNone,
            null, Modifier.size(IconSize.xs), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(i18n.t("phone.${row.event.channel.name.lowercase()}") + " · " + label, Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(phoneDisplayTime(row.event.receivedAt, row.event.zone), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        menu()
    }
}

@Composable
private fun PhoneMessageMenu(row: PhoneMessage, actions: PhoneMessageActions, busy: Boolean,
    vm: PhoneAssistantViewModel, onClear: () -> Unit) {
    val i18n: I18nService = koinInject()
    var opened by remember(row.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { opened = true }, modifier = Modifier.size(Height.buttonSmall)) {
            Icon(Icons.Default.MoreHoriz, i18n.t("phone.more_actions"), Modifier.size(IconSize.m),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = opened, onDismissRequest = { opened = false }) {
            @Composable fun item(key: String, icon: ImageVector, destructive: Boolean = false, action: () -> Unit) {
                DropdownMenuItem(text = { Text(i18n.t(key), color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                    enabled = !busy, onClick = { opened = false; action() })
            }
            if (actions.retry) item("phone.retry", Icons.Default.Refresh) { vm.retry(row) }
            if (actions.reprocess) item("phone.reprocess", Icons.Default.Replay) { vm.reprocess(row) }
            if (actions.aiReprocess) item("phone.ai_reprocess", Icons.Default.AutoAwesome) { vm.aiReprocess(row) }
            if (actions.retry || actions.reprocess) HorizontalDivider()
            if (!row.textErased) item("phone.clear_source", Icons.Default.DeleteOutline, destructive = true, action = onClear)
            item("phone.block_source", Icons.Default.Block) { vm.blockSource(row) }
        }
    }
}

@Composable
private fun PhoneLinkedResults(entries: List<LedgerEntry>, reminders: List<Reminder>,
    onOpenLedger: (String) -> Unit, onOpenTodo: (String) -> Unit) {
    val i18n: I18nService = koinInject()
    Surface(shape = RoundedCornerShape(Radius.inset), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PhoneLinkedRow(Icons.Default.AccountBalanceWallet,
                    entry.merchant.ifBlank { i18n.t("phone.ledger") },
                    i18n.t("phone.ledger") + " · " + i18n.t("bookkeeping.kind.${entry.kind.name.lowercase()}") + " · " +
                        i18n.t(if (entry.status == LedgerStatus.POSTED) "phone.ledger_posted" else "phone.pending"),
                    entry.amountMinor?.let { phoneSignedAmount(entry) }.orEmpty()) { onOpenLedger(entry.id) }
            }
            reminders.forEachIndexed { index, reminder ->
                if (index > 0 || entries.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val date = phoneLinkedReminderDate(reminder) ?: i18n.t("phone.deadline_unset")
                PhoneLinkedRow(Icons.Default.EventAvailable, reminder.content,
                    i18n.t("phone.todos") + " · " + date + " · " + i18n.t("phone.reminder_status.${reminder.status.name.lowercase()}")) {
                    onOpenTodo(reminder.id)
                }
            }
        }
    }
}

internal fun phoneSignedAmount(entry: LedgerEntry): String {
    val sign = if (entry.kind in setOf(LedgerKind.INCOME, LedgerKind.REFUND)) "+" else "−"
    val currency = when (entry.currency) { "CNY", "JPY" -> "¥"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> entry.currency + " " }
    return sign + currency + entry.amountMinor?.let { LedgerMoney.format(it, entry.currency) }.orEmpty()
}

@Composable
private fun PhoneLinkedRow(icon: ImageVector, title: String, subtitle: String, value: String = "", onOpen: () -> Unit) {
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s).heightIn(min = Height.button),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(icon, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.bodySmall)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(IconSize.xs),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhoneMessageFooter(todos: Int, ledger: Int, addLedger: Boolean, busy: Boolean, loading: Boolean,
    onAddTodo: () -> Unit, onAddLedger: () -> Unit) {
    val i18n: I18nService = koinInject()
    // Two weighted rows would truncate status or squeeze buttons. FlowRow retains touch targets and wraps safely.
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.heightIn(min = Height.button), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(if (todos + ledger > 0) Icons.Default.CheckCircleOutline else Icons.Default.RemoveCircleOutline,
                null, Modifier.size(IconSize.xs), tint = if (todos + ledger > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            val key = when { loading -> "phone.loading_results"; todos > 0 && ledger > 0 -> "phone.result_counts"; todos > 0 -> "phone.todo_count"; ledger > 0 -> "phone.ledger_count"; else -> "phone.no_results" }
            val text = when (key) { "phone.result_counts" -> i18n.t(key, todos, ledger); "phone.todo_count" -> i18n.t(key, todos); "phone.ledger_count" -> i18n.t(key, ledger); else -> i18n.t(key) }
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (todos + ledger > 0)
                MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PhoneAddButton("phone.add_todo", !busy, onAddTodo)
            if (addLedger) PhoneAddButton("phone.add_ledger", !busy, onAddLedger)
        }
    }
}

@Composable
private fun PhoneAddButton(key: String, enabled: Boolean, onClick: () -> Unit) {
    val i18n: I18nService = koinInject()
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues()) {
        Icon(Icons.Default.Add, null, Modifier.size(IconSize.xs))
        Spacer(Modifier.width(Spacing.xxs))
        Text(i18n.t(key), style = MaterialTheme.typography.bodySmall)
    }
}
