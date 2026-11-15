package com.dailysatori.ui.feature.settings.diagnostics

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dailysatori.core.diagnostics.DiagnosticExportState
import com.dailysatori.core.storage.DiagnosticCreateDocument
import org.koin.androidx.compose.koinViewModel

/**
 * One export session for the whole settings module. The launcher and request collector live with
 * the settings screen, so the home row and the diagnostics page share one owner instead of
 * competing for the same picker request.
 */
data class DiagnosticExportAccess(
    val info: DiagnosticSettingsState,
    val export: DiagnosticExportState,
    val prepare: (Boolean) -> Unit,
    val clear: () -> Unit,
)

@Composable
fun rememberDiagnosticExportAccess(
    viewModel: DiagnosticSettingsViewModel = koinViewModel(),
): DiagnosticExportAccess {
    val info by viewModel.state.collectAsState()
    val export by viewModel.exportState.collectAsState()
    val context = LocalContext.current
    var pendingToken by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(remember(context) { DiagnosticCreateDocument(context) }) { uri ->
        pendingToken?.let { viewModel.onDestinationChosen(it, uri) }
        pendingToken = null
    }
    LaunchedEffect(viewModel) {
        viewModel.refresh()
        viewModel.requests.collect { request ->
            pendingToken = request.token
            try {
                picker.launch(request.fileName)
            } catch (_: Exception) {
                pendingToken = null
                viewModel.pickerFailed()
            }
        }
    }
    DisposableEffect(viewModel, context) {
        onDispose {
            if (context.settingsActivity()?.isChangingConfigurations != true) viewModel.releaseOnLeave()
        }
    }
    return DiagnosticExportAccess(
        info = info,
        export = export,
        prepare = viewModel::prepare,
        clear = viewModel::clear,
    )
}

private fun Context.settingsActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.settingsActivity()
    else -> null
}
