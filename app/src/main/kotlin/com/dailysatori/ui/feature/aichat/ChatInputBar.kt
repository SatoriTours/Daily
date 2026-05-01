package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .imePadding(),
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.circular),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = Spacing.m, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
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
                        maxLines = 6,
                        enabled = enabled,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    FilledIconButton(
                        onClick = onSend,
                        enabled = inputText.isNotBlank() && enabled,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = contentColorFor(MaterialTheme.colorScheme.primary),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                "基于你的知识库和记忆回答",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.s),
            )
        }
    }
}
