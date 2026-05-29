package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

enum class ChatInputAction { Send, Stop }

private val ChatInputButtonSize = 34.dp
private val ChatInputContentMinHeight = Height.input

fun chatInputAction(isProcessing: Boolean): ChatInputAction =
    if (isProcessing) ChatInputAction.Stop else ChatInputAction.Send

fun chatInputActionDescription(action: ChatInputAction): String = when (action) {
    ChatInputAction.Send -> "发送"
    ChatInputAction.Stop -> "停止生成"
}

fun chatInputSuggestionLabels(): List<String> = listOf("整理今天", "提炼主题", "搜索记忆")

fun chatInputPlaceholderText(): String = "继续追问今天的新闻、日记或文章..."

fun chatInputShowsSuggestions(inputText: String, isProcessing: Boolean): Boolean = inputText.isBlank() && !isProcessing

fun chatInputTextAfterSuggestion(currentText: String, suggestion: String): String =
    listOf(currentText.trim(), suggestion).filter { it.isNotBlank() }.joinToString(" ")

fun chatInputUsesImePadding(): Boolean = true

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isProcessing: Boolean,
    onSuggestionClick: (String) -> Unit = { suggestion -> onInputChange(chatInputTextAfterSuggestion(inputText, suggestion)) },
) {
    var isFocused by remember { mutableStateOf(false) }
    val inputShape = RoundedCornerShape(Radius.circular)
    val contentPadding = PaddingValues(
        top = Spacing.xs,
        bottom = Spacing.xs,
        start = Spacing.xs,
        end = Spacing.xs,
    )
    val action = chatInputAction(isProcessing)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.m, vertical = Spacing.xs)
                .imePadding(),
        ) {
            if (chatInputShowsSuggestions(inputText, isProcessing)) {
                ChatInputSuggestions(onSuggestionClick)
            }
            Surface(
                shape = inputShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        },
                        shape = inputShape,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(start = Spacing.s, end = Spacing.xs, top = Spacing.xxs, bottom = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isFocused = it.isFocused },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        minLines = 1,
                        maxLines = 3,
                        enabled = true,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.heightIn(min = ChatInputContentMinHeight)
                                    .padding(contentPadding),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        chatInputPlaceholderText(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                innerTextField()
                            }
                        },
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

@Composable
private fun ChatInputSuggestions(onSuggestionClick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        chatInputSuggestionLabels().forEach { label ->
            Surface(
                shape = RoundedCornerShape(Radius.circular),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.clickable { onSuggestionClick(label) },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
