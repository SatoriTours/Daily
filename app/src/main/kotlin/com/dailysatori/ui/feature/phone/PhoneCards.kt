package com.dailysatori.ui.feature.phone

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs)) {
            Text(record?.draft?.title ?: i18n.t("phone.todo_candidate"), style = MaterialTheme.typography.titleMedium.editorial())
            Text(i18n.t("phone.${row.event.channel.name.lowercase()}") + " · " + row.event.origin,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val status = if (reminder?.status == ReminderStatus.COMPLETED) "completed" else if (stopped) "stopped" else todo.state.name.lowercase()
            Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline)) {
                Text(i18n.t("phone.state.$status"), Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (todo.reason.isNotEmpty()) Text(i18n.t("phone.reason.${todo.reason}"), style = MaterialTheme.typography.bodySmall)
            record?.draft?.deadlineMs?.let {
                Text(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.of(row.event.zone)).toString().replace('T', ' '), style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (todo.state == PhoneResultState.DONE) {
                    TextButton(onClick = { ReminderOpenRequest.state.open(todo.reminderId) }, contentPadding = PaddingValues(horizontal = Spacing.s)) { Text(i18n.t("phone.view_todo")) }
                    if (reminder?.status !in setOf(ReminderStatus.COMPLETED, ReminderStatus.EXPIRED))
                        TextButton(onClick = { vm.complete(todo.reminderId) }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = Spacing.s)) { Text(i18n.t("phone.complete")) }
                } else {
                    Button(onClick = onEdit, enabled = !state.busy, shape = RoundedCornerShape(Radius.m)) { Text(i18n.t("bookkeeping.confirm")) }
                    OutlinedButton(onClick = { vm.ignoreTodo(row, todo) }, enabled = !state.busy, shape = RoundedCornerShape(Radius.m),
                        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline)) { Text(i18n.t("bookkeeping.ignore")) }
                    if (todo.state == PhoneResultState.FAILED || stopped)
                        TextButton(onClick = { vm.retry(row) }, enabled = !state.busy) { Text(i18n.t("phone.retry")) }
                }
            }
            if (!row.textErased) TextButton(onClick = { source = !source }, contentPadding = PaddingValues()) { Text(i18n.t(if (source) "bookkeeping.hide_source" else "bookkeeping.show_source")) }
            if (source && !row.textErased) PhoneSourceText(todo.text.ifEmpty { row.event.text })
        }
    }
}

@Composable private fun PhoneSourceText(text: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
    ) {
        Text(text, Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
