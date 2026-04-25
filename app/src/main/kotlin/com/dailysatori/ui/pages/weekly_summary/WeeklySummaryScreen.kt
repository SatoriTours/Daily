package com.dailysatori.ui.pages.weekly_summary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.ui.components.EmptyState
import com.dailysatori.ui.components.SAppBar
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Spacing

@Composable
fun WeeklySummaryScreen(
    onSettings: () -> Unit = {},
) {
    var hasContent by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SAppBar(
                title = "周报",
                onBack = null,
                showBack = false,
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { /* history */ }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { /* generate */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Generate")
                    }
                },
            )
        },
    ) { padding ->
        if (!hasContent) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Default.Person,
                    title = "暂无周报",
                    subtitle = "点击右上角刷新按钮生成",
                    actionLabel = "立即生成",
                    onAction = { hasContent = true },
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.m)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.m),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("本周总结", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(Spacing.m))
                Text(
                    "Summary content will be rendered here as Markdown...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
