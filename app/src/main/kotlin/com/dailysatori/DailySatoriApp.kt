package com.dailysatori

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.dailysatori.core.navigation.DailySatoriNavHost
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
    val state by viewModel.state.collectAsState()
    val sharedText by sharedTextState?.collectAsState(initial = null) ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }
    val launchedFromShare by launchedFromShareState?.collectAsState(initial = false) ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

}
