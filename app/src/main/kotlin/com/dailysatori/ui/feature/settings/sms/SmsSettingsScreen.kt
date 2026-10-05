package com.dailysatori.ui.feature.settings.sms

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dailysatori.core.reminder.ReminderOpenRequest
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.sms.*
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.theme.*
import kotlinx.datetime.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SmsSettingsScreen(onBack: () -> Unit, viewModel: SmsSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val i18n: I18nService = koinInject()
    val context = LocalContext.current
    var cloudConsent by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setMonitoring(granted); viewModel.refreshAccess()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshAccess() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    AppScaffold(title = i18n.t("sms.title"), onBack = onBack, actions = {
        Box {
            IconButton(onClick = { pageMenu = true }) { Icon(Icons.Default.MoreVert, i18n.t("sms.more")) }
            DropdownMenu(pageMenu, onDismissRequest = { pageMenu = false }) {
                DropdownMenuItem(text = { Text(i18n.t("sms.sync_pending")) }, enabled = !state.busy,
                    onClick = { pageMenu = false; viewModel.syncPending() })
                DropdownMenuItem(text = { Text(i18n.t("sms.clear_blocked")) }, enabled = !state.busy,
                    onClick = { pageMenu = false; viewModel.clearBlockedSenders() })
            }
        }
    }) { modifier ->
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.m),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column {
                            SmsToggle(i18n.t("sms.monitor"), state.enabled, state.busy) { enabled ->
                                if (!enabled) viewModel.setMonitoring(false)
                                else if (state.smsPermission) viewModel.setMonitoring(true)
                                else smsPermission.launch(Manifest.permission.RECEIVE_SMS)
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Spacing.m),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            SmsToggle(i18n.t("sms.cloud"), state.cloudAllowed, state.busy) {
                                if (it) cloudConsent = true else viewModel.setCloudAllowed(false)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Icon(Icons.Default.PrivacyTip, null, Modifier.size(IconSize.s),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(i18n.t("sms.privacy"), modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.enabled && !state.smsPermission) Text(
                        i18n.t("sms.permission_missing"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = Spacing.m),
                    )
                    if (!state.notifications) SmsPermissionButton(i18n.t("sms.enable_notifications"), Icons.Default.Notifications) {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                    if (!state.exactAlarms) SmsPermissionButton(i18n.t("sms.exact_alarm_hint"), Icons.Default.Alarm) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                    state.messageKey?.let {
                        Text(i18n.t(it), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.m))
                    }
                }
            }
            items(state.records.filter { it.status != SmsSourceStatus.IGNORED }, key = { it.id }) { record ->
                SmsSourceCard(record, state.busy, onRetry = { viewModel.retry(record) },
                    onIgnore = { viewModel.ignore(record) }, onBlock = { viewModel.block(record) })
            }
        }
    }
    if (cloudConsent) AlertDialog(onDismissRequest = { cloudConsent = false }, title = { Text(i18n.t("sms.cloud")) },
        text = { Text(i18n.t("sms.consent")) },
        confirmButton = { TextButton(onClick = { cloudConsent = false; viewModel.setCloudAllowed(true) }) { Text(i18n.t("sms.agree")) } },
        dismissButton = { TextButton(onClick = { cloudConsent = false }) { Text(i18n.t("sms.cancel")) } })
}

@Composable private fun SmsToggle(title: String, checked: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = !busy, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = Height.listItem + Spacing.s)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked, onCheckedChange = null, enabled = !busy)
    }
}

@Composable private fun SmsPermissionButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = Height.button),
        shape = RoundedCornerShape(Radius.m),
        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Icon(icon, null, Modifier.size(IconSize.m))
        Spacer(Modifier.width(Spacing.s))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable internal fun SmsSourceCard(record: SmsSourceRecord, busy: Boolean, onRetry: () -> Unit, onIgnore: () -> Unit, onBlock: () -> Unit) {
    val i18n: I18nService = koinInject()
    var expanded by remember(record.id) { mutableStateOf(false) }
    var menu by remember(record.id) { mutableStateOf(false) }
    val created = record.status == SmsSourceStatus.CREATED
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.l), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.Sms, null, Modifier.padding(Spacing.s).size(IconSize.m), tint = MaterialTheme.colorScheme.primary)
                }
                Text(record.source.sender.ifBlank { i18n.t("sms.local_candidate") }, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, i18n.t("sms.more")) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (created || record.status in setOf(SmsSourceStatus.FAILED, SmsSourceStatus.LOCAL_ONLY))
                            DropdownMenuItem(text = { Text(i18n.t(if (created) "sms.reschedule" else "sms.retry")) }, enabled = !busy, onClick = { menu = false; onRetry() })
                        if (!created) DropdownMenuItem(text = { Text(i18n.t("sms.ignore")) }, enabled = !busy, onClick = { menu = false; onIgnore() })
                        DropdownMenuItem(text = { Text(i18n.t("sms.block")) }, enabled = !busy, onClick = { menu = false; onBlock() })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(record.draft?.title ?: i18n.t("sms.local_candidate"), style = MaterialTheme.typography.titleMedium)
                Surface(shape = RoundedCornerShape(Radius.circular), color = if (created) AppColors.success.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (created) Icon(Icons.Default.Check, null, Modifier.size(IconSize.xs), tint = AppColors.success)
                        Text(i18n.t("sms.status_${record.status.name.lowercase()}"), style = MaterialTheme.typography.labelSmall,
                            color = if (created) AppColors.success else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            record.draft?.deadlineMs?.let { deadline ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(i18n.t("sms.deadline"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatSmsTime(Instant.fromEpochMilliseconds(deadline)), style = MaterialTheme.typography.titleSmall)
                        if (record.draft?.estimated == true) Text(i18n.t("sms.estimated"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } ?: if (created) Text(i18n.t("sms.unscheduled"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) else Unit
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { expanded = !expanded }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Icon(Icons.Default.Description, null, Modifier.size(IconSize.s))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(i18n.t("sms.source_short"))
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(IconSize.s))
                }
                if (created) FilledTonalButton(onClick = { record.reminderId?.let(ReminderOpenRequest.state::open) }) { Text(i18n.t("sms.view_short")) }
            }
            if (expanded) {
                Text(record.source.sender + " · " + formatSmsTime(record.receivedAt), style = MaterialTheme.typography.bodySmall)
                Text(record.source.body, style = MaterialTheme.typography.bodyMedium)
                SmsPrivacy.aiText(record.source.body)?.let { Text(i18n.t("sms.redacted_preview") + "\n" + it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

internal fun formatSmsTime(at: Instant): String = at.toLocalDateTime(TimeZone.currentSystemDefault()).toString().replace('T', ' ').take(16)
