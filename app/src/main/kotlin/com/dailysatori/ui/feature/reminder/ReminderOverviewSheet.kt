package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.R
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.ui.feature.profile.localDayTicker
import com.dailysatori.ui.theme.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderOverviewSheet(
    reminder: Reminder,
    isBusy: Boolean,
    sourceKey: String?,
    viewModel: ReminderViewModel,
    latestProfile: ReminderProfileSnapshot,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val i18n: I18nService = koinInject()
    val navigation = com.dailysatori.core.navigation.LocalPhoneNavigation.current
    var confirmDelete by remember(reminder.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Text(i18n.t("phone.todo_detail"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                ReminderEditorMoreActions(reminder, enabled = !isBusy, showComplete = false) { action ->
                    when (action) {
                        ReminderAction.DELETE -> confirmDelete = true
                        ReminderAction.PAUSE -> { viewModel.pause(reminder.id); onDismiss() }
                        ReminderAction.RESUME -> { viewModel.resume(reminder.id); onDismiss() }
                        ReminderAction.APPLY_LATEST_PROFILE -> { viewModel.applyLatestProfile(reminder.id, latestProfile); onDismiss() }
                        else -> Unit
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.reminder_cancel)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                ReminderOverviewInformation(reminder, sourceKey)
                com.dailysatori.ui.feature.phone.PhoneSourcePanel(reminderId = reminder.id, onOpenSource = { onDismiss(); navigation.openHistory(it) })
            }
            ReminderPrimaryActions(reminder, !isBusy, onComplete, onEdit)
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.reminder_delete_title)) }, text = { Text(stringResource(R.string.reminder_delete_message)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(reminder.id); onDismiss() }) {
            Text(stringResource(R.string.reminder_action_delete), color = MaterialTheme.colorScheme.error)
        } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.reminder_cancel)) } })
}

@Composable
internal fun ReminderPrimaryActions(reminder: Reminder, enabled: Boolean, onComplete: () -> Unit, onEdit: () -> Unit) {
    val i18n: I18nService = koinInject()
    val actions = reminderActions(reminder)
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
        if (ReminderAction.COMPLETE in actions) {
            Button(onClick = onComplete, enabled = enabled && reminder.dataIssue == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.xxl), shape = RoundedCornerShape(Radius.m)) {
                Icon(Icons.Outlined.Check, null, Modifier.size(IconSize.m))
                Text(reminderCompletionLabel(reminder), Modifier.padding(start = Spacing.s))
            }
        }
        if (ReminderAction.EDIT in actions) {
            TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.xxl)) {
                Text(i18n.t("reminder_ui.edit_task"))
            }
        }
    }
}

@Composable
internal fun reminderCompletionLabel(reminder: Reminder): String {
    val i18n: I18nService = koinInject()
    return if (reminder.recurrence == ReminderRecurrence.Once) stringResource(R.string.reminder_detail_complete)
    else i18n.t("reminder_ui.complete_cycle")
}

@Composable
internal fun ReminderOverviewInformation(reminder: Reminder, sourceKey: String? = null) {
    val i18n: I18nService = koinInject()
    val today by remember { localDayTicker() }.collectAsState(initial = Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val state = buildReminderListState(listOf(reminder), today, ReminderListMode.RECENT, ReminderListFilter())
    val item = (state.sections.flatMap { it.items } + state.pausedItems).firstOrNull()
    val deadline = reminder.deadlineAt
    val time = when {
        reminder.id.startsWith("sms:") && reminder.deadlineAt == null -> i18n.t("sms.no_deadline")
        deadline != null -> com.dailysatori.ui.feature.settings.sms.formatSmsTime(deadline)
        else -> "${item?.occurrenceDate ?: reminder.endDate} ${reminder.firstReminderTime}"
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(reminder.content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(i18n.t("phone.reminder_status.${reminder.status.name.lowercase()}"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = Spacing.m)) {
                ReminderOverviewRow(Icons.Outlined.Schedule, i18n.t(if (reminder.deadlineAt != null) "sms.deadline" else "reminder_ui.reminder_time"), time)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReminderOverviewRow(Icons.Outlined.Repeat, i18n.t("reminder_ui.repeat"),
                    if (item == null || item.isSingleDate) i18n.t("reminder_ui.no_repeat") else item.repeatLabel().label())
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReminderOverviewRow(Icons.Outlined.Source, i18n.t("phone.source_info"), sourceKey?.let { i18n.t(it) }
                    ?: i18n.t(if (reminder.id.startsWith("sms:")) "reminder_ui.source_unknown" else "reminder_ui.manual_source"))
            }
        }
        if (reminder.notes.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.reminder_notes_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(reminder.notes, style = MaterialTheme.typography.bodyMedium)
        }
        if (reminder.dataIssue != null) Text(stringResource(R.string.reminder_corrupt_profile_warning),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ReminderOverviewRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = Height.listItem).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(icon, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
