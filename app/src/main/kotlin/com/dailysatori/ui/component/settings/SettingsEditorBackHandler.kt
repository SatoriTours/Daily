package com.dailysatori.ui.component.settings

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.dailysatori.R

@Composable
fun rememberSettingsEditorBack(
    hasChanges: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onDiscard: () -> Unit = {},
): () -> Unit {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (!busy) {
            if (hasChanges) showDiscard = true else onBack()
        }
    }
    BackHandler(onBack = requestBack)
    if (showDiscard) AlertDialog(
        onDismissRequest = { if (!busy) showDiscard = false },
        title = { Text(stringResource(R.string.settings_unsaved_title)) },
        text = { Text(stringResource(R.string.settings_unsaved_message)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                showDiscard = false
                onDiscard()
                onBack()
            }) { Text(stringResource(R.string.settings_discard_changes)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { showDiscard = false }) {
                Text(stringResource(R.string.settings_continue_editing))
            }
        },
    )
    return requestBack
}
