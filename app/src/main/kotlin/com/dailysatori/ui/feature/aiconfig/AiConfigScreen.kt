package com.dailysatori.ui.feature.aiconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.misc.FeatureIcon
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

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

    AppScaffold(
        title = "AI 配置管理",
        onBack = onBack,
        actions = {
            IconButton(onClick = { /* show info */ }) {
                Icon(Icons.Default.Info, contentDescription = "Info")
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
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
