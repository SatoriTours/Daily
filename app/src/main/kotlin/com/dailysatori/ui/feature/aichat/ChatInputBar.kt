package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.R
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

enum class ChatInputAction { Send, Stop }

enum class ChatInputStyle { Standard, AssistantPaper }

enum class ChatInputActionVisualState { Stop, SendEnabled, SendMuted }

fun chatInputAction(isProcessing: Boolean): ChatInputAction = if (isProcessing) ChatInputAction.Stop else ChatInputAction.Send
fun chatInputActionDescription(action: ChatInputAction): String = if (action == ChatInputAction.Stop) "停止生成" else "发送"
fun chatInputPlaceholderText(): String = "问点什么"

fun chatInputActionVisualState(
    isProcessing: Boolean,
    canEdit: Boolean,
    canSend: Boolean,
    text: String,
): ChatInputActionVisualState = when {
    isProcessing -> ChatInputActionVisualState.Stop
    canEdit && canSend && text.isNotBlank() -> ChatInputActionVisualState.SendEnabled
    else -> ChatInputActionVisualState.SendMuted
}

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
        ChatInputField(
            value = value,
            onValueChange = onValueChange,
            onSend = onSend,
            onStop = onStop,
            isProcessing = isProcessing,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            canSend = canSend,
            canEdit = canEdit,
            style = ChatInputStyle.AssistantPaper,
        )
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
    style: ChatInputStyle = ChatInputStyle.Standard,
) {
    var focused by remember { mutableStateOf(false) }
    val actionState = chatInputActionVisualState(isProcessing, canEdit, canSend, value.text)
    val enabled = actionState != ChatInputActionVisualState.SendMuted
    val editor: @Composable () -> Unit = {
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
            decorationBox = { innerTextField ->
                if (value.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.assistant_input_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }

    if (style == ChatInputStyle.AssistantPaper) {
        AssistantPaperComposer(
            modifier = modifier,
            focused = focused,
            actionState = actionState,
            enabled = enabled,
            onActionClick = if (isProcessing) onStop else onSend,
            editor = editor,
        )
        return
    }

    val shape = RoundedCornerShape(Radius.m)
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier.weight(1f).heightIn(min = Height.input)
                .border(
                    if (focused) BorderWidth.s else BorderWidth.xs,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape,
                )
                .background(MaterialTheme.colorScheme.surface, shape)
                .padding(horizontal = Spacing.s + Spacing.xs, vertical = Spacing.s),
            contentAlignment = Alignment.CenterStart,
        ) {
            editor()
        }
        IconButton(onClick = if (isProcessing) onStop else onSend, enabled = enabled, modifier = Modifier.size(Spacing.xxl)) {
            Box(
                Modifier.size(IconSize.xl).background(
                    if (enabled) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(Radius.inset),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isProcessing) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    stringResource(if (isProcessing) R.string.assistant_stop else R.string.assistant_send),
                    Modifier.size(IconSize.s),
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AssistantPaperComposer(
    modifier: Modifier,
    focused: Boolean,
    actionState: ChatInputActionVisualState,
    enabled: Boolean,
    onActionClick: () -> Unit,
    editor: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xl)
    val containerColor = if (focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    val focusHint = if (focused) {
        Modifier.border(BorderWidth.xs, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .heightIn(min = Height.touchTarget)
            .then(focusHint)
            .background(containerColor, shape)
            .padding(end = Spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Height.touchTarget)
                .padding(start = Spacing.m, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
            contentAlignment = Alignment.CenterStart,
        ) {
            editor()
        }
        IconButton(
            onClick = onActionClick,
            enabled = enabled,
            modifier = Modifier.size(Height.touchTarget),
        ) {
            when (actionState) {
                ChatInputActionVisualState.Stop -> Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = stringResource(R.string.assistant_stop),
                    modifier = Modifier.size(IconSize.m),
                    tint = MaterialTheme.colorScheme.primary,
                )
                ChatInputActionVisualState.SendEnabled -> Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(R.string.assistant_send),
                    modifier = Modifier.size(IconSize.m),
                    tint = MaterialTheme.colorScheme.primary,
                )
                ChatInputActionVisualState.SendMuted -> Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(R.string.assistant_send),
                    modifier = Modifier.size(IconSize.m),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
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
