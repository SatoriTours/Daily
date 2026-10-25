package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.R
import com.dailysatori.ui.theme.*

enum class ChatInputAction { Send, Stop }
fun chatInputAction(isProcessing: Boolean): ChatInputAction = if (isProcessing) ChatInputAction.Stop else ChatInputAction.Send
fun chatInputActionDescription(action: ChatInputAction): String = if (action == ChatInputAction.Stop) "停止生成" else "发送"
fun chatInputPlaceholderText(): String = "问点什么"

@Composable
fun ChatInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isProcessing: Boolean,
    canSend: Boolean = true,
    canEdit: Boolean = true,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        ChatInputField(value, onValueChange, onSend, onStop, isProcessing,
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs), canSend = canSend, canEdit = canEdit)
    }
}

/** One stable editor: never flatten IME composition/selection to String and feed it back from a different host. */
@Composable
fun ChatInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    canSend: Boolean = true,
    canEdit: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val enabled = isProcessing || (canEdit && canSend && value.text.isNotBlank())
    val shape = RoundedCornerShape(Radius.m)
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier.weight(1f).heightIn(min = Height.input)
                .border(BorderWidth.s, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
                .padding(horizontal = Spacing.s + Spacing.xs, vertical = Spacing.s),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 1,
                maxLines = if (compact) 1 else 5,
                singleLine = compact,
                decorationBox = { editor ->
                    if (value.text.isEmpty()) Text(stringResource(R.string.assistant_input_placeholder),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    editor()
                },
            )
        }
        IconButton(onClick = if (isProcessing) onStop else onSend, enabled = enabled, modifier = Modifier.size(Spacing.xxl)) {
            Box(
                Modifier.size(IconSize.xl).background(if (enabled) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(Radius.inset)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isProcessing) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    stringResource(if (isProcessing) R.string.assistant_stop else R.string.assistant_send),
                    Modifier.size(IconSize.s),
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Compatibility for the book-reflection editor; local rich state survives text-only parent recompositions. */
@Composable
fun ChatInputField(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(inputText)) }
    LaunchedEffect(inputText) {
        if (value.text != inputText) value = TextFieldValue(inputText, TextRange(inputText.length))
    }
    ChatInputField(value, { value = it; onInputChange(it.text) }, onSend, onStop, isProcessing, modifier, compact)
}
