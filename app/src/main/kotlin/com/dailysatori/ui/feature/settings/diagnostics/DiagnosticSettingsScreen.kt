package com.dailysatori.ui.feature.settings.diagnostics

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dailysatori.core.diagnostics.DiagnosticExportPhase
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.theme.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun DiagnosticSettingsScreen(onBack: () -> Unit, viewModel: DiagnosticSettingsViewModel = koinViewModel()) {
    val info by viewModel.state.collectAsState()
    val export by viewModel.exportState.collectAsState()
    val i18n = koinInject<I18nService>()
    val context = LocalContext.current
    var pendingToken by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val busy = export.phase in setOf(DiagnosticExportPhase.PREPARING, DiagnosticExportPhase.AWAITING_DESTINATION, DiagnosticExportPhase.SAVING)
    val back: () -> Unit = { if (export.phase != DiagnosticExportPhase.SAVING) onBack() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        pendingToken?.let { viewModel.onDestinationChosen(it, uri) }
        pendingToken = null
    }
    BackHandler(onBack = back)
    LaunchedEffect(viewModel) {
        viewModel.refresh()
        viewModel.requests.collect { request ->
            pendingToken = request.token
            try { picker.launch(request.fileName) }
            catch (_: Exception) { pendingToken = null; viewModel.pickerFailed() }
        }
    }
    DisposableEffect(viewModel, context) {
        onDispose { if (context.activity()?.isChangingConfigurations != true) viewModel.releaseOnLeave() }
    }
    AppScaffold(title = i18n.t("diagnostics.title"), onBack = back) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(i18n.t("diagnostics.description"), style = MaterialTheme.typography.bodyMedium)
            Text(i18n.t("diagnostics.privacy"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(i18n.t("diagnostics.coverage"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            info.bytes?.let {
                Text(i18n.t("diagnostics.storage") + " " + String.format(Locale.ROOT, "%.2f MiB", it / (1024.0 * 1024)),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (info.health.droppedEvents + info.health.ioFailures + info.health.prunedFiles + info.health.corruptLines + info.health.truncatedEvents > 0) {
                Text(i18n.t("diagnostics.gaps"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { viewModel.prepare(false) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(Height.button)) { Text(i18n.t("diagnostics.exportRecent")) }
            OutlinedButton(onClick = { viewModel.prepare(true) }, enabled = !busy && info.lastCrashMs != null,
                modifier = Modifier.fillMaxWidth().height(Height.button)) { Text(i18n.t("diagnostics.exportCrash")) }
            Text(info.lastCrashMs?.let { i18n.t("diagnostics.lastCrash") + " " + DateFormat.getDateTimeInstance().format(Date(it)) }
                ?: i18n.t("diagnostics.noCrash"), style = MaterialTheme.typography.bodySmall)
            Text(i18n.t("diagnostics.destination"), style = MaterialTheme.typography.bodySmall)
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(i18n.t(when (export.phase) {
                    DiagnosticExportPhase.AWAITING_DESTINATION -> "diagnostics.chooseDestination"
                    DiagnosticExportPhase.SAVING -> "diagnostics.saving"
                    else -> "diagnostics.preparing"
                }), style = MaterialTheme.typography.bodySmall)
            }
            if (export.phase == DiagnosticExportPhase.SAVED) {
                Text(i18n.t("diagnostics.saved"), color = MaterialTheme.colorScheme.primary)
                info.savedName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (export.phase == DiagnosticExportPhase.FAILED) {
                Text(i18n.t(if (export.residualFile) "diagnostics.failedResidual" else "diagnostics.failed"),
                    color = MaterialTheme.colorScheme.error)
            }
            info.messageKey?.let { Text(i18n.t(it), color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = { confirmClear = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(Height.button)) { Text(i18n.t("diagnostics.clear")) }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(i18n.t("diagnostics.clear")) },
        text = { Text(i18n.t("diagnostics.clearConfirm")) },
        confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clear() }) { Text(i18n.t("diagnostics.confirm")) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(i18n.t("diagnostics.cancel")) } },
    )
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.activity()
    else -> null
}
