package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiChatScreen() {
    val viewModel: AiChatViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "AI 助手",
                showBack = false,
                actions = {
                    IconButton(onClick = { viewModel.clearMessages() }, enabled = state.messages.isNotEmpty()) {
                        Icon(Icons.Default.Refresh, contentDescription = "新对话")
                    }
                    IconButton(onClick = { viewModel.clearMessages() }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI 助手")
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.s).imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("问我任何问题...") },
                        shape = RoundedCornerShape(Radius.xl),
                        maxLines = 3,
                        enabled = !state.isProcessing,
                    )
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !state.isProcessing,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        },
    ) { padding ->
        if (state.isProcessing && state.currentStep.isNotBlank()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = Spacing.m),
            )
        }

        if (state.messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(Spacing.m))
                    Text("AI 助手", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(Spacing.s))
                    Text("基于你的知识库回答问题", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(vertical = Spacing.m),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(role = message.role, content = message.content, isError = message.isError)
                }
                if (state.isProcessing) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = Radius.m,
                                    topEnd = Radius.m,
                                    bottomStart = Radius.xs,
                                    bottomEnd = Radius.m,
                                ),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(0.8f),
                            ) {
                                Text(
                                    text = state.currentStep.ifBlank { "思考中..." },
                                    modifier = Modifier.padding(Spacing.m),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(role: String, content: String, isError: Boolean = false) {
    val isUser = role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = Radius.m,
                topEnd = Radius.m,
                bottomStart = if (isUser) Radius.m else Radius.xs,
                bottomEnd = if (isUser) Radius.xs else Radius.m,
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary
                     else if (isError) MaterialTheme.colorScheme.errorContainer
                     else MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Text(
                text = content,
                modifier = Modifier.padding(Spacing.m),
                color = when {
                    isUser -> MaterialTheme.colorScheme.onPrimary
                    isError -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
