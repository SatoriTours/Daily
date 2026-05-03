package com.dailysatori

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.dailysatori.core.navigation.DailySatoriNavHost
import com.dailysatori.ui.feature.settings.SettingsState
import com.dailysatori.ui.feature.settings.SettingsViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun DailySatoriApp(
    sharedTextState: StateFlow<String?>? = null,
    launchedFromShareState: StateFlow<Boolean>? = null,
    onSharedTextHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val viewModel: AppUrlIntakeViewModel = koinViewModel()
    val upgradeViewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val upgradeState by upgradeViewModel.state.collectAsState()
    val sharedText by sharedTextState?.collectAsState(initial = null) ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }
    val launchedFromShare by launchedFromShareState?.collectAsState(initial = false) ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        upgradeViewModel.checkUpdateAutomatically()
    }

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            viewModel.handleSharedText(sharedText)
            onSharedTextHandled()
        }
    }

    DisposableEffect(lifecycleOwner, launchedFromShare) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && shouldCheckClipboardOnForeground(launchedFromShare)) {
                coroutineScope.launch {
                    delay(500)
                    viewModel.checkClipboard()
                }
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                installPendingUpgradeIfAllowed(context, upgradeViewModel)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.duplicateUrl) {
        if (state.duplicateUrl != null) {
            snackbarHostState.showSnackbar(duplicateUrlSnackbarMessage())
            viewModel.dismissDuplicateUrl()
        }
    }

    LaunchedEffect(upgradeState.updateMessage) {
        upgradeState.updateMessage?.let {
            snackbarHostState.showSnackbar(it)
            upgradeViewModel.clearUpdateMessage()
        }
    }

    UpgradeDownloadCompleteReceiver(upgradeViewModel, context)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DailySatoriNavHost(navController)
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.clipboardUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissClipboardUrl() },
            title = { Text(clipboardPromptTitle()) },
            text = { Text("是否保存为文章？\n$url") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmClipboardUrl() },
                    enabled = !state.isSavingUrl,
                ) {
                    Text(if (state.isSavingUrl) "保存中..." else "保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClipboardUrl() }) {
                    Text("取消")
                }
            },
        )
    }

    UpgradeDialog(upgradeState, upgradeViewModel, context)

}

@Composable
private fun UpgradeDialog(
    state: SettingsState,
    viewModel: SettingsViewModel,
    context: Context,
) {
    val release = state.availableRelease ?: return
    if (!state.showUpdateDialog) return
    AlertDialog(
        onDismissRequest = { viewModel.dismissUpdateDialog() },
        title = { Text("发现新版本") },
        text = { Text("当前版本 v${state.currentVersion}\n最新版本 ${release.version}\n是否立即更新？") },
        dismissButton = { TextButton(onClick = { viewModel.dismissUpdateDialog() }) { Text("稍后") } },
        confirmButton = { TextButton(onClick = { viewModel.startUpdateDownload(context) }) { Text("立即更新") } },
    )
}

@Composable
private fun UpgradeDownloadCompleteReceiver(
    viewModel: SettingsViewModel,
    context: Context,
) {
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                val installIntent = viewModel.createInstallIntentForDownload(context, completedId) ?: return
                if (canInstallPackages(context)) {
                    context.startActivity(installIntent)
                    viewModel.markInstallLaunched()
                } else {
                    context.startActivity(unknownAppSourcesIntent(context))
                    viewModel.notifyInstallPermissionRequired()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
}

private fun installPendingUpgradeIfAllowed(context: Context, viewModel: SettingsViewModel) {
    if (!canInstallPackages(context)) return
    val installIntent = viewModel.createPendingInstallIntent(context) ?: return
    context.startActivity(installIntent)
    viewModel.markInstallLaunched()
}

private fun canInstallPackages(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

private fun unknownAppSourcesIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
