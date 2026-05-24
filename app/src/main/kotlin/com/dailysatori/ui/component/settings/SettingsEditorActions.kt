package com.dailysatori.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.Spacing

@Composable
fun SettingsEditorBottomBar(
    modifier: Modifier = Modifier,
    canTest: Boolean,
    canSave: Boolean,
    isTesting: Boolean,
    isSaving: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
    testText: String = settingsEditorTestActionText(isTesting),
    saveText: String = settingsEditorSaveActionText(isSaving),
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        OutlinedButton(
            onClick = onTest,
            modifier = Modifier.weight(1f),
            enabled = canTest && !isTesting && !isSaving,
        ) { Text(testText) }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            enabled = canSave && !isSaving && !isTesting,
        ) { Text(saveText) }
    }
}

@Composable
fun SettingsEditorMessage(message: String, isError: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

fun settingsEditorTestActionText(isTesting: Boolean): String = if (isTesting) "测试中..." else "测试连接"

fun settingsEditorSaveActionText(isSaving: Boolean): String = if (isSaving) "保存中..." else "保存"

fun settingsEditorActionsUseTestAndSave(): Boolean = true
