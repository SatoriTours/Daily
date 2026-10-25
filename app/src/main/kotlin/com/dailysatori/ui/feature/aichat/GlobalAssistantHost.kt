package com.dailysatori.ui.feature.aichat

import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.dailysatori.core.navigation.*
import com.dailysatori.ui.component.appbar.LocalAssistantLauncher
import com.dailysatori.ui.feature.myspace.PersonalChatViewModel
import org.koin.androidx.compose.koinViewModel

data class AssistantOpenRequest(val kind: String = "", val key: String = "", val sessionId: String? = null)

internal enum class AssistantBackAction { HideKeyboard, ReturnToChat, Close }
internal fun assistantBackAction(imeVisible: Boolean, sessionsVisible: Boolean): AssistantBackAction = when {
    imeVisible -> AssistantBackAction.HideKeyboard
    sessionsVisible -> AssistantBackAction.ReturnToChat
    else -> AssistantBackAction.Close
}

private class AssistantDialogWindow {
    var window: Window? = null
    fun imeVisible(): Boolean = window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    fun hideIme() { window?.let { WindowCompat.getInsetsController(it, it.decorView).hide(WindowInsetsCompat.Type.ime()) } }
}

/** A separate full-window dialog keeps the existing navigation tree, scroll and editing state mounted. */
@Composable
fun GlobalAssistantHost(navController: NavHostController, content: @Composable ((AssistantOpenRequest) -> Unit) -> Unit) {
    // Resolve above NavHost, never against an individual destination's ViewModelStoreOwner.
    val viewModel: AiChatViewModel = koinViewModel(key = "global-assistant")
    val contextViewModel: PersonalChatViewModel = koinViewModel(key = "global-assistant-context")
    val state by viewModel.state.collectAsState()
    val context by contextViewModel.state.collectAsState()
    var visible by rememberSaveable { mutableStateOf(false) }
    var contextSession by rememberSaveable { mutableStateOf("") }
    var pendingReference by remember { mutableStateOf<Pair<String, AssistantOpenRequest>?>(null) }
    var pageReferenceAtOpen by remember { mutableStateOf<AssistantOpenRequest?>(null) }
    val savedUi = rememberSaveableStateHolder()
    val dialogWindow = remember { AssistantDialogWindow() }
    val underlyingKeyboard = LocalSoftwareKeyboardController.current
    val entry by navController.currentBackStackEntryAsState()
    val pageReference = assistantPageReference(entry)
    val open: (AssistantOpenRequest) -> Unit = { request ->
        request.sessionId?.let(viewModel::switchSession)
        if (request.kind.isNotBlank() || request.sessionId != null) viewModel.setSessionsVisible(false)
        if (request.kind.isNotBlank()) pendingReference = viewModel.state.value.sessionId to request
        pageReferenceAtOpen = pageReference
        underlyingKeyboard?.hide()
        visible = true
    }
    CompositionLocalProvider(LocalAssistantLauncher provides { open(AssistantOpenRequest()) }) { content(open) }
    LaunchedEffect(visible, state.sessionId, pendingReference) {
        if (!visible) return@LaunchedEffect
        contextViewModel.initialize()
        if (contextSession != state.sessionId) {
            contextViewModel.removeContext()
            contextViewModel.load("", "")
            contextSession = state.sessionId
        }
        pendingReference?.let { (owner, request) ->
            if (owner == state.sessionId) contextViewModel.load(request.kind, request.key)
            pendingReference = null
        }
    }
    if (visible) Dialog(
        onDismissRequest = {
            when (assistantBackAction(dialogWindow.imeVisible(), viewModel.state.value.showSessions)) {
                AssistantBackAction.HideKeyboard -> dialogWindow.hideIme()
                AssistantBackAction.ReturnToChat -> viewModel.setSessionsVisible(false)
                AssistantBackAction.Close -> visible = false
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnClickOutside = false),
    ) {
        val keyboard = LocalSoftwareKeyboardController.current
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
        DisposableEffect(window, lightSystemBars) {
            dialogWindow.window = window
            window?.setWindowAnimations(0)
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
            // DialogProperties owns API-specific IME resize modes; do not override them on every edit.
            onDispose { if (dialogWindow.window == window) dialogWindow.window = null }
        }
        val close = { keyboard?.hide(); visible = false }
        CompositionLocalProvider(LocalAssistantLauncher provides null) {
            savedUi.SaveableStateProvider(state.sessionId) {
                val contextMatches = contextSession == state.sessionId
                AiChatScreen(
                    viewModel = viewModel,
                    onBack = close,
                    onArticleClick = { close(); navController.navigate(ArticleDetailRoute(it)) },
                    onReminderClick = { close(); navController.navigate(ReminderDetailRoute(it)) },
                    onTaskCenterClick = { close(); navController.navigate(TaskCenterRoute()) },
                    explicitContext = context.context.takeIf { contextMatches },
                    contextLabel = context.label,
                    includeThoughts = contextMatches && context.includeThoughts,
                    onRemoveContext = { pendingReference = null; contextViewModel.removeContext() },
                    canAttachCurrentPage = pageReferenceAtOpen != null,
                    onAttachCurrentPage = { pageReferenceAtOpen?.let { pendingReference = state.sessionId to it } },
                    contextLoading = !contextMatches || context.loading,
                    contextError = context.error.takeIf { contextMatches },
                )
            }
        }
    }
}

/** Only concrete, already-saved public content has an explicit reference action; never infer page data. */
private fun assistantPageReference(entry: NavBackStackEntry?): AssistantOpenRequest? = when {
    entry == null -> null
    entry.destination.hasRoute<ArticleDetailRoute>() -> AssistantOpenRequest("article", entry.toRoute<ArticleDetailRoute>().articleId.toString())
    entry.destination.hasRoute<MyOpportunityRoute>() -> AssistantOpenRequest("opportunity", entry.toRoute<MyOpportunityRoute>().id)
    entry.destination.hasRoute<MyThoughtsRoute>() -> entry.toRoute<MyThoughtsRoute>().thoughtKey?.let { AssistantOpenRequest("thought", it) }
    else -> null
}
