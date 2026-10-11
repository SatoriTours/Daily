package com.dailysatori.ui.feature.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(record?.draft?.title ?: i18n.t("phone.todo_candidate"), style = MaterialTheme.typography.titleMedium)
            Text(i18n.t("phone.${row.event.channel.name.lowercase()}") + " · " + row.event.origin,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val status = if (reminder?.status == ReminderStatus.COMPLETED) "completed" else if (stopped) "stopped" else todo.state.name.lowercase()
            Surface(shape = RoundedCornerShape(Radius.s), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(i18n.t("phone.state.$status"), Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (todo.reason.isNotEmpty()) Text(i18n.t("phone.reason.${todo.reason}"), style = MaterialTheme.typography.bodySmall)
            record?.draft?.deadlineMs?.let {
                Text(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.of(row.event.zone)).toString().replace('T', ' '), style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (todo.state == PhoneResultState.DONE) {
                    TextButton(onClick = { ReminderOpenRequest.state.open(todo.reminderId) }) { Text(i18n.t("phone.view_todo")) }
                    if (reminder?.status !in setOf(ReminderStatus.COMPLETED, ReminderStatus.EXPIRED))
                        TextButton(onClick = { vm.complete(todo.reminderId) }, enabled = !state.busy) { Text(i18n.t("phone.complete")) }
                } else {
                    FilledTonalButton(onClick = onEdit, enabled = !state.busy, shape = RoundedCornerShape(Radius.m)) { Text(i18n.t("bookkeeping.confirm")) }
                    TextButton(onClick = { vm.ignoreTodo(row, todo) }, enabled = !state.busy) { Text(i18n.t("bookkeeping.ignore")) }
                    if (todo.state == PhoneResultState.FAILED || stopped)
                        TextButton(onClick = { vm.retry(row) }, enabled = !state.busy) { Text(i18n.t("phone.retry")) }
                }
            }
            if (!row.textErased) TextButton(onClick = { source = !source }) { Text(i18n.t(if (source) "bookkeeping.hide_source" else "bookkeeping.show_source")) }
            if (source && !row.textErased) PhoneSourceText(todo.text.ifEmpty { row.event.text })
        }
    }
}

@Composable private fun PhoneSourceText(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(text, Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
