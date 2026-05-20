package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

enum class ChatInputAction { Send, Stop }

private val ChatInputButtonSize = 34.dp
private val ChatInputMinHeight = Height.input

fun chatInputAction(isProcessing: Boolean): ChatInputAction =
    if (isProcessing) ChatInputAction.Stop else ChatInputAction.Send

fun chatInputActionDescription(action: ChatInputAction): String = when (action) {
    ChatInputAction.Send -> "发送"
    ChatInputAction.Stop -> "停止生成"
}

fun chatInputUsesImePadding(): Boolean = true

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isProcessing: Boolean,
) {
    var isFocused by remember { mutableStateOf(false) }
    val inputShape = RoundedCornerShape(Radius.circular)
    val action = chatInputAction(isProcessing)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.m, vertical = Spacing.xs)
                .imePadding(),
        ) {
            Surface(
                shape = inputShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = inputShape,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(start = Spacing.s, end = Spacing.xs, top = Spacing.xxs, bottom = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = ChatInputMinHeight)
                            .onFocusChanged { isFocused = it.isFocused },
                        placeholder = {
                            Text(
                                "问我任何问题...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        minLines = 1,
                        maxLines = 3,
                        enabled = true,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    FilledIconButton(
                        onClick = {
                            when (action) {
                                ChatInputAction.Send -> onSend()
                                ChatInputAction.Stop -> onStop()
                            }
                        },
                        enabled = action == ChatInputAction.Stop || inputText.isNotBlank(),
                        modifier = Modifier.size(ChatInputButtonSize),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = contentColorFor(MaterialTheme.colorScheme.primary),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Icon(
                            imageVector = when (action) {
                                ChatInputAction.Send -> Icons.AutoMirrored.Filled.Send
                                ChatInputAction.Stop -> Icons.Default.Stop
                            },
                            contentDescription = chatInputActionDescription(action),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
