package com.dailysatori.ui.feature.settings.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

@Composable
internal fun BackupPasswordDialog(
    title: String,
    description: String,
    label: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    minimumLength: Int = 1,
) {
    val i18n: I18nService = koinInject()
    // Keep passwords only for this dialog; never restore them after recreation.
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.l),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(description, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(label) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password); password = "" },
                enabled = password.isNotBlank() && password.length >= minimumLength) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t("button.cancel")) } },
    )
}
