package com.dailysatori.ui.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.core.service.UpdateChannel
import com.dailysatori.ui.component.settings.SettingsRow
import com.dailysatori.ui.component.settings.SettingsSectionCard
import com.dailysatori.ui.theme.*

@Composable
internal fun UpdateSettingsSection(state: SettingsState, viewModel: SettingsViewModel) {
    var choosing by remember { mutableStateOf(false) }
    val busy = state.isCheckingUpdate || state.isDownloadingUpdate || !state.updateChannelLoaded
    SettingsSectionCard(stringResource(R.string.personal_settings_updates)) {
        Text(
            text = "当前安装：${state.installedChannel.label} · ${state.currentVersion}",
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Spacing.m),
            thickness = BorderWidth.xs,
            color = MaterialTheme.colorScheme.outline,
        )
        SettingsRow(
            icon = Icons.Default.Settings,
            title = "更新渠道",
            subtitle = "",
            value = state.updateChannel.label,
            onClick = { if (!busy) choosing = true },
            showDivider = state.updateStatus != null,
        )
        state.updateStatus?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (choosing) UpdateChannelDialog(state.updateChannel, { choosing = false }) {
        choosing = false
        viewModel.selectUpdateChannel(it)
    }
}

@Composable
private fun UpdateChannelDialog(selected: UpdateChannel, onDismiss: () -> Unit, onSelect: (UpdateChannel) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = Radius.none,
        title = { Text("更新渠道", style = MaterialTheme.typography.titleMedium.editorial()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UpdateChannel.entries.forEach { channel -> UpdateChannelOption(channel, selected, onSelect) }
                Text(
                    "切换渠道后立即检查更新；较旧的安装包需等待兼容版本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UpdateChannelOption(channel: UpdateChannel, selected: UpdateChannel, onSelect: (UpdateChannel) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(channel) }.padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = channel == selected, onClick = { onSelect(channel) })
        Text(channel.label, style = MaterialTheme.typography.bodyLarge)
    }
}
