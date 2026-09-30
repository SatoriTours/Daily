package com.dailysatori.ui.feature.settings.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.BuildConfig
import com.dailysatori.R
import com.dailysatori.core.diagnostics.*
import com.dailysatori.core.service.UpdateChannel
import com.dailysatori.ui.theme.*

@Composable
internal fun RecoveryUpdateSection(
    state: RecoveryUpdateState,
    enabled: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    HorizontalDivider()
    Text(stringResource(R.string.recovery_update_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.recovery_update_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
    val channel = stringResource(if (state.configuration.channel == UpdateChannel.COMMIT)
        R.string.recovery_update_commit else R.string.recovery_update_stable)
    Text(stringResource(R.string.recovery_update_channel, channel), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.configuration.usedDefaults) {
        Text(stringResource(R.string.recovery_update_defaults), style = MaterialTheme.typography.bodySmall)
    }
    OutlinedButton(onClick = onCheck, enabled = enabled && !state.busy,
        modifier = Modifier.fillMaxWidth().height(Height.button)) {
        Text(stringResource(if (state.phase == RecoveryUpdatePhase.CHECKING) R.string.recovery_update_checking else R.string.recovery_update_check))
    }
    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    state.release?.let { release ->
        Text(stringResource(R.string.recovery_update_available, release.version), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = if (state.download != null) onInstall else onDownload, enabled = enabled && !state.busy,
            modifier = Modifier.fillMaxWidth().height(Height.button)) {
            Text(stringResource(if (state.download != null) R.string.recovery_update_install else R.string.recovery_update_download))
        }
    }
    if (state.phase == RecoveryUpdatePhase.DOWNLOADING) {
        state.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.recovery_update_downloading), style = MaterialTheme.typography.bodySmall)
    }
    state.failure?.let { failure ->
        Text(stringResource(when (failure) {
            RecoveryUpdateFailure.CHECK -> R.string.recovery_update_check_failed
            RecoveryUpdateFailure.DOWNLOAD -> R.string.recovery_update_download_failed
            RecoveryUpdateFailure.INSTALL -> R.string.recovery_update_install_failed
        }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
