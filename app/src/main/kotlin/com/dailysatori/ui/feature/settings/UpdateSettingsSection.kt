package com.dailysatori.ui.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import com.dailysatori.core.service.UpdateChannel
import com.dailysatori.ui.component.settings.SettingsRow
import com.dailysatori.ui.component.settings.SettingsSectionCard
import com.dailysatori.ui.theme.*

@Composable
internal fun UpdateSettingsSection(state: SettingsState, viewModel: SettingsViewModel) {
    var choosing by remember { mutableStateOf(false) }
    val busy = state.isCheckingUpdate || state.isDownloadingUpdate || !state.updateChannelLoaded
    SettingsSectionCard("应用更新") {
        Text(
            text = "当前安装：${state.installedChannel.label} · ${state.currentVersion}",
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            style = MaterialTheme.typography.bodyMedium,
        )
        SettingsRow(Icons.Default.Settings, "更新渠道", state.updateChannel.label,
            onClick = { if (!busy) choosing = true })
        SettingsRow(Icons.Default.Refresh, "检查更新", if (state.isCheckingUpdate) "检查中…" else "启动时自动检查所选渠道",
            onClick = { if (!busy) viewModel.checkUpdate() })
        state.updateStatus?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth().padding(Spacing.m),
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
        title = { Text("更新渠道") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UpdateChannel.entries.forEach { channel -> UpdateChannelOption(channel, selected, onSelect) }
                Text("切换渠道后立即检查更新；较旧的安装包需等待兼容版本。", style = MaterialTheme.typography.bodySmall)
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
