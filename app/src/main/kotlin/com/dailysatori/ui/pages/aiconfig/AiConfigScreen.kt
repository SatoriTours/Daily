package com.dailysatori.ui.pages.aiconfig

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.components.FeatureIcon
import com.dailysatori.ui.components.SAppBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(
    onBack: () -> Unit = {},
    onEditConfig: (Long) -> Unit = {},
) {
    val configItems = listOf("通用配置", "文章分析", "书籍解读", "日记总结")
    val configIcons = listOf(
        Icons.Default.Settings,
        Icons.Default.Settings,
        Icons.Default.Settings,
        Icons.Default.Settings,
    )

    Scaffold(
        topBar = {
            SAppBar(
                title = "AI 配置管理",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { /* show info */ }) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            contentPadding = PaddingValues(vertical = Spacing.m),
        ) {
            items(configItems.indices.toList()) { index ->
                Card(
                    onClick = { /* navigate to edit */ },
                    shape = RoundedCornerShape(Radius.m),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(enabled = true),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FeatureIcon(icon = configIcons[index])
                        Spacer(modifier = Modifier.width(Spacing.m))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(configItems[index], style = MaterialTheme.typography.titleSmall)
                            Text("未配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
