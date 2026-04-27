package com.dailysatori.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ExtensionOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun PluginCenterScreen(onBack: () -> Unit = {}) {
    val viewModel: PluginCenterViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = "插件中心",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.loadPlugins() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
            IconButton(onClick = { viewModel.updateAllPlugins() }) {
                Icon(Icons.Default.CloudDownload, contentDescription = "全部更新")
            }
        },
    ) { modifier ->
        if (state.isLoading) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.plugins.isEmpty()) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Default.ExtensionOff,
                    title = "暂无插件",
                    subtitle = "配置插件服务器地址后刷新",
                    actionLabel = "刷新",
                    onAction = { viewModel.loadPlugins() },
                )
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(state.plugins, key = { it.fileName }) { plugin ->
                    val isUpdating = state.updatingPluginId == plugin.fileName
                    Card(
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.l),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(Spacing.m))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plugin.fileName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${plugin.content.length} 字符",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isUpdating) {
                                CircularProgressIndicator(modifier = Modifier.size(IconSize.m), strokeWidth = 2.dp)
                            } else {
                                OutlinedButton(onClick = { viewModel.updatePlugin(plugin.fileName) }) {
                                    Text("更新")
                                }
                            }
                        }
                        if (isUpdating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
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
