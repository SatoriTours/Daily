package com.dailysatori.ui.feature.settings.weekly

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun WeeklySummaryScreen(
    onSettings: () -> Unit = {},
) {
    val viewModel: WeeklySummaryViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = "周报",
        showBack = false,
        actions = {
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
            IconButton(onClick = { /* history */ }) {
                Icon(Icons.Default.History, contentDescription = "历史")
            }
            IconButton(onClick = { viewModel.checkAndGenerate() }, enabled = !state.isGenerating) {
                if (state.isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(IconSize.s), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "生成")
                }
            }
        },
    ) { modifier ->
        if (state.currentSummary == null) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    EmptyState(
                        icon = Icons.Default.Person,
                        title = "暂无周报",
                        subtitle = "点击右上角刷新按钮生成",
                        actionLabel = "立即生成",
                        onAction = { viewModel.checkAndGenerate() },
                    )
                }
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
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
                    state.currentSummary!!.content ?: "暂无内容",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (state.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Spacing.m),
                )
            }
        }
    }
}
