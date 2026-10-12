package com.dailysatori.ui.feature.reminder

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.reminder.ReminderRecurrence
import org.koin.compose.koinInject

@Composable
internal fun ReminderCompletionSnackbar(ui: ReminderUiState, viewModel: ReminderViewModel, modifier: Modifier = Modifier) {
    val i18n: I18nService = koinInject()
    val host = remember { SnackbarHostState() }
    val undo = ui.completionUndo
    val message = when (ui.completionError) {
        ReminderCompletionError.COMPLETE -> i18n.t("reminder_ui.complete_failed")
        ReminderCompletionError.UNDO -> i18n.t("reminder_ui.undo_failed")
        null -> undo?.reminder?.let {
            if (it.recurrence == ReminderRecurrence.Once) i18n.t("reminder_ui.completed_message", *arrayOf(it.content))
            else i18n.t("reminder_ui.cycle_completed")
        }
    }
    val actionLabel = i18n.t("reminder_ui.undo")
    LaunchedEffect(undo, ui.completionError) {
        if (message == null) return@LaunchedEffect
        val canUndo = undo != null && ui.completionError == null
        val result = host.showSnackbar(message, actionLabel = actionLabel.takeIf { canUndo },
            withDismissAction = true, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed && undo != null) viewModel.undoCompletion(undo)
        else viewModel.dismissCompletion(undo.takeIf { canUndo })
    }
    SnackbarHost(hostState = host, modifier = modifier)
}
