package com.dailysatori.core.startup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.ui.theme.*

@Composable
internal fun StartupScreen(state: StartupState, onRetry: () -> Unit, onDiagnostics: () -> Unit) {
    val failed = state == StartupState.Failed
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding().padding(Spacing.l), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = Breakpoint.mobile).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text("Daily Satori", style = AppTypography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(if (failed) R.string.startup_failed else R.string.startup_preparing),
                    style = AppTypography.headlineSmall)
                Text(stringResource(if (failed) R.string.startup_failed_description else R.string.startup_preparing_description),
                    style = AppTypography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (failed) {
                    Button(onClick = onRetry) { Text(stringResource(R.string.startup_retry)) }
                    TextButton(onClick = onDiagnostics) { Text(stringResource(R.string.startup_diagnostics)) }
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
