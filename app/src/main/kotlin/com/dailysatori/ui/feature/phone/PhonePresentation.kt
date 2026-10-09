package com.dailysatori.ui.feature.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.phone.PhoneChannel
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

internal fun phoneChannelStatus(state: PhoneUiState, channel: PhoneChannel): String {
    val options = state.preferences.forChannel(channel)
    return when {
        !options.enabled -> "off"
        channel == PhoneChannel.SMS && !state.smsGranted -> "permission"
        channel == PhoneChannel.NOTIFICATION && !state.bookkeeping.granted -> "permission"
        !options.todos && !options.ledger -> "purpose"
        channel == PhoneChannel.NOTIFICATION && state.preferences.sources.isEmpty() -> "sources"
        channel == PhoneChannel.NOTIFICATION && !state.bookkeeping.capture.connected -> "waiting"
        else -> "connected"
    }
}

@Composable internal fun PhoneStatusLabel(status: String) {
    val i18n: I18nService = koinInject()
    Surface(shape = RoundedCornerShape(Radius.circular), color = if (status == "connected")
        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(i18n.t("phone.access.$status"), Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelMedium, color = if (status == "connected")
                MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun PhoneEmptyState(kind: String, showSetup: Boolean, onSetup: () -> Unit) {
    val i18n: I18nService = koinInject()
    val icon = when (kind) {
        "pending" -> Icons.Default.CheckCircleOutline
        "todos" -> Icons.AutoMirrored.Filled.ListAlt
        "ledger" -> Icons.Default.AccountBalanceWallet
        else -> Icons.Default.History
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(Spacing.m).size(IconSize.xl), tint = MaterialTheme.colorScheme.primary)
            }
            Text(i18n.t("phone.empty_${kind}_title"), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(i18n.t(if (showSetup) "phone.empty_setup_hint" else "phone.empty_${kind}_hint"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            if (showSetup) Button(onClick = onSetup, shape = RoundedCornerShape(Radius.circular),
                modifier = Modifier.heightIn(min = Height.button),
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s)) {
                Text(i18n.t("phone.setup_sources"))
            }
        }
    }
}

@Composable internal fun PhoneInfoNote(text: String, icon: ImageVector = Icons.Default.PrivacyTip) {
    Row(Modifier.padding(horizontal = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(icon, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
