package com.dailysatori.ui.feature.settings.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.core.diagnostics.DiagnosticExportPhase
import com.dailysatori.ui.theme.*

@Composable
fun DiagnosticRecoveryScreen(viewModel: DiagnosticRecoveryViewModel, onInstall: () -> Unit, onContinue: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val export by viewModel.exportState.collectAsState()
    val update by viewModel.updateState.collectAsState()
    var token by rememberSaveable { mutableStateOf<String?>(null) }
    val exporting = export.phase in setOf(DiagnosticExportPhase.PREPARING,
        DiagnosticExportPhase.AWAITING_DESTINATION, DiagnosticExportPhase.SAVING)
    val busy = state.checking || exporting || update.busy
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        token?.let { viewModel.save(it, uri) }
        token = null
    }
    LaunchedEffect(viewModel) {
        viewModel.requests.collect { request ->
            token = request.token
            try { picker.launch(request.fileName) }
            catch (_: Exception) { token = null; viewModel.pickerFailed() }
        }
    }
    LaunchedEffect(viewModel) { viewModel.startup.collect { onContinue() } }
    LaunchedEffect(viewModel) { viewModel.installRequests.collect { onInstall() } }
    // Keep the routing check invisible; normal launches never draw the recovery page.
    if (!state.showRecovery) return
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Spacer(Modifier.height(Spacing.l))
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(if (state.needsRecovery) R.string.recovery_interrupted else R.string.recovery_description),
                style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.recovery_privacy), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.recovery_destination), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { viewModel.prepare(true) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(Height.button)) { Text(stringResource(R.string.recovery_export_crash)) }
            OutlinedButton(onClick = { viewModel.prepare(false) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(Height.button)) { Text(stringResource(R.string.recovery_export_recent)) }
            if (state.checking || exporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(if (export.phase == DiagnosticExportPhase.AWAITING_DESTINATION)
                    R.string.recovery_choose_destination else R.string.recovery_working), style = MaterialTheme.typography.bodySmall)
            }
            if (export.phase == DiagnosticExportPhase.SAVED) {
                Text(stringResource(R.string.recovery_saved), color = MaterialTheme.colorScheme.primary)
            }
            if (export.phase == DiagnosticExportPhase.FAILED) {
                Text(stringResource(if (export.residualFile) R.string.recovery_export_residual else R.string.recovery_export_failed),
                    color = MaterialTheme.colorScheme.error)
            }
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            RecoveryUpdateSection(update, enabled = !state.checking && !exporting,
                onCheck = viewModel::checkUpdate, onDownload = viewModel::downloadUpdate, onInstall = onInstall)
            TextButton(onClick = viewModel::continueStartup, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recovery_continue))
            }
        }
    }
}
