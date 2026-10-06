package com.dailysatori.ui.feature.settings.speech

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.ContentResolver
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.diary.modelHint
import com.dailysatori.service.diary.speechModelDisplayName
import com.dailysatori.service.diary.speechSettingsProviders
import com.dailysatori.ui.component.settings.SettingsEditorMessage
import com.dailysatori.ui.component.settings.rememberSettingsEditorBack
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import com.dailysatori.service.i18n.I18nService

private enum class SpeechPicker { PROVIDER, MODEL }
private data class SpeechChoice(val id: String, val title: String, val subtitle: String = "")

@Composable
fun SpeechSettingsScreen(onBack: () -> Unit) {
    val pageBackground = MaterialTheme.colorScheme.surfaceContainerLowest
    val viewModel: SpeechSettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val i18n: I18nService = koinInject()
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.testConfiguration { readSpeechTestAudio(context.contentResolver, it) } }
    }
    val savedMessage = stringResource(R.string.settings_saved)
    val provider = speechSettingsProviders.firstOrNull { it.id == state.config.provider } ?: speechSettingsProviders.first()
    var picker by remember { mutableStateOf<SpeechPicker?>(null) }
    val requestBack = rememberSettingsEditorBack(state.hasChanges, state.saving || state.testing, onBack, viewModel::discardChanges)
    SettingsScaffold(
        useGroupNavigation = true,
        hasUnsavedChanges = state.hasChanges,
        navigationBusy = state.saving || state.testing,
        onDiscardChanges = viewModel::discardChanges,
        title = "语音模型", onBack = requestBack,
        bottomBar = {
            Button(
                onClick = { viewModel.save {
                    Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                    onBack()
                } }, enabled = state.canSave,
                shape = RoundedCornerShape(Radius.l),
                modifier = Modifier.fillMaxWidth().padding(Spacing.m).heightIn(min = Height.button),
            ) { Text(if (state.saving) "保存中…" else "保存") }
        },
    ) { modifier ->
        Column(
            modifier.fillMaxSize().background(pageBackground)
                .verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text("用于语音日记转文字", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.xs))
            Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
                Column {
                    SpeechSelectionRow("提供商", provider.name, state.editable) { picker = SpeechPicker.PROVIDER }
                    HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
                    SpeechSelectionRow("模型", speechModelDisplayName(state.config.model), state.editable) { picker = SpeechPicker.MODEL }
                }
            }
            Text(provider.modelHint(state.config.model), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.xs))
            SpeechKeyCard(state.config.apiKey, state.editable, provider.id, viewModel::setApiKey)
            OutlinedButton(
                onClick = { audioPicker.launch("audio/*") },
                enabled = state.canTest,
                modifier = Modifier.fillMaxWidth().heightIn(min = Height.button),
                shape = RoundedCornerShape(Radius.l),
            ) { Text(i18n.t(if (state.testing) "speech_test.testing" else "speech_test.button")) }
            Text(i18n.t("speech_test.hint"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.xs))
            state.message?.let { SettingsEditorMessage(it, state.isError, Modifier.padding(horizontal = Spacing.xs)) }
            Spacer(Modifier.height(Spacing.s))
        }
    }
    picker?.let { selection ->
        val options = if (selection == SpeechPicker.PROVIDER) speechSettingsProviders.map { SpeechChoice(it.id, it.name) }
        else provider.models.map { SpeechChoice(it, speechModelDisplayName(it), it) }
        SpeechChoiceSheet(
            title = if (selection == SpeechPicker.PROVIDER) "选择提供商" else "选择模型",
            options = options,
            selected = if (selection == SpeechPicker.PROVIDER) provider.id else state.config.model,
            onDismiss = { picker = null },
        ) { value ->
            if (selection == SpeechPicker.PROVIDER) viewModel.selectProvider(value) else viewModel.setModel(value)
            picker = null
        }
    }
}

private fun readSpeechTestAudio(resolver: ContentResolver, uri: Uri): Pair<ByteArray, String> {
    val extension = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.substringAfterLast('.', "")?.lowercase() else null
    }
    require(extension in setOf("wav", "mp3", "m4a", "mp4", "aac", "ogg", "flac", "webm"))
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(SPEECH_TEST_MAX_BYTES + 1)
        var size = 0
        while (size < buffer.size) {
            val count = input.read(buffer, size, buffer.size - size)
            if (count < 0) break
            if (count == 0) continue
            size += count
        }
        require(size in 1..SPEECH_TEST_MAX_BYTES)
        buffer.copyOf(size)
    } ?: throw java.io.IOException("Audio unavailable")
    return bytes to "test.$extension"
}

@Composable
private fun SpeechSelectionRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Height.listItem + Spacing.s).padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(IconSize.m),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpeechKeyCard(value: String, enabled: Boolean, providerId: String, onChange: (String) -> Unit) {
    var keyVisible by remember(providerId) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column {
            Text("API Key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.m, top = Spacing.m))
            TextField(
                value = value, onValueChange = onChange, enabled = enabled, singleLine = true,
                placeholder = { Text("粘贴 API Key") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                    disabledIndicatorColor = MaterialTheme.colorScheme.surface,
                ),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }, enabled = enabled) {
                        Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key", modifier = Modifier.size(IconSize.l))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeechChoiceSheet(
    title: String, options: List<SpeechChoice>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
            options.forEach { choice ->
                Surface(onClick = { onSelect(choice.id) }, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Height.listItem + Spacing.s).padding(horizontal = Spacing.l, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(choice.title, style = MaterialTheme.typography.bodyLarge,
                                color = if (choice.id == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (choice.subtitle.isNotEmpty()) Text(choice.subtitle, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (choice.id == selected) Icon(Icons.Default.Check, "已选择", Modifier.size(IconSize.l), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
