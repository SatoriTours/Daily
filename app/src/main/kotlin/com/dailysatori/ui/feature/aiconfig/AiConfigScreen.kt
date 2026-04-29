package com.dailysatori.ui.feature.aiconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.misc.FeatureIcon
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiConfigScreen(
    onBack: () -> Unit = {},
    onEditConfig: (Long?) -> Unit = {},
) {
    val viewModel: AiConfigViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = "AI 配置管理",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEditConfig(null) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建配置")
            }
        },
    ) { modifier ->
        if (state.configs.isEmpty()) {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("暂无配置", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.padding(Spacing.s))
                Text("点击右下角 + 添加配置", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(state.configs, key = { it.id }) { config ->
                    Card(
                        onClick = { onEditConfig(config.id) },
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(viewModel.getFunctionTypeIcon(config.function_type ?: 0L), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(Spacing.m))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(config.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${config.model_name} · ${config.api_address}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            if (config.is_default == 1L) {
                                Text("默认", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
