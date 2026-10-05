package com.dailysatori.ui.feature.phone

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.dailysatori.service.phone.*
import com.dailysatori.service.sms.SmsReminderDraft
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.theme.*
import kotlinx.datetime.*
import org.koin.compose.koinInject

internal data class PhoneTodoEdit(val row: PhoneMessage, val todo: PhoneTodo?)

@Composable internal fun PhoneTodoDialog(edit: PhoneTodoEdit, initial: SmsReminderDraft?, busy: Boolean, failed: Boolean,
    onDismiss: () -> Unit, onSave: (SmsReminderDraft) -> Unit) {
    val i18n: I18nService = koinInject()
    var title by remember(edit) { mutableStateOf(initial?.title.orEmpty()) }
    val zone = TimeZone.of(edit.row.event.zone)
    var deadline by remember(edit) { mutableStateOf(initial?.deadlineMs?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).toString().take(16).replace('T', ' ') }.orEmpty()) }
    val parsed = remember(deadline) { runCatching { LocalDateTime.parse(deadline.trim().replace(' ', 'T')).toInstant(zone) }.getOrNull() }
    val valid = title.isNotBlank() && title.length <= 300 && (deadline.isBlank() || parsed != null && parsed > Clock.System.now())
    AlertDialog(onDismissRequest = onDismiss, title = { Text(i18n.t("phone.edit_todo")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            OutlinedTextField(title, onValueChange = { title = it }, label = { Text(i18n.t("phone.todo_title")) }, singleLine = true)
            OutlinedTextField(deadline, onValueChange = { deadline = it }, label = { Text(i18n.t("phone.deadline")) },
                supportingText = { Text(i18n.t("phone.deadline_hint")) }, isError = deadline.isNotBlank() && !valid, singleLine = true)
            Text(i18n.t("phone.unscheduled_hint"), style = MaterialTheme.typography.bodySmall)
            if (failed) Text(i18n.t("phone.operation_failed"), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = valid && !busy, onClick = {
        onSave(SmsReminderDraft(title.trim(), deadlineMs = parsed?.toEpochMilliseconds(), category = initial?.category ?: "other"))
    }) { Text(i18n.t("bookkeeping.save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t("bookkeeping.cancel")) } })
}

@Composable internal fun PhoneConsentDialog(channel: PhoneChannel, onDismiss: () -> Unit, onAgree: () -> Unit) {
    val i18n: I18nService = koinInject()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(i18n.t("phone.cloud")) },
        text = { Text(i18n.t("phone.cloud_notice", *arrayOf(i18n.t("phone.${channel.name.lowercase()}")))) },
        confirmButton = { TextButton(onClick = onAgree) { Text(i18n.t("sms.agree")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t("bookkeeping.cancel")) } })
}
