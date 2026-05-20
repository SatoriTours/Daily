package com.dailysatori.ui.feature.aiconfig

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.service.ai.aiConfigDisplayName
import com.dailysatori.service.ai.canDeleteAiConfig
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

internal const val aiConfigDeleteActionSizeDp = 32
internal const val aiConfigDeleteIconSizeDp = 18
internal const val aiConfigDefaultCardBorderAlpha = 0.28f
internal const val aiConfigDefaultIconAlpha = 0.82f
internal const val aiConfigDeleteIconAlpha = 0.62f

@Composable
fun AiConfigScreen(
    onBack: () -> Unit = {},
    onEditConfig: ((Long?) -> Unit)? = null,
) {
    val viewModel: AiConfigViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    var isEditing by remember { mutableStateOf(false) }
    var editingConfigId by remember { mutableStateOf<Long?>(null) }
    var deletingConfigId by remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = isEditing) {
        isEditing = false
        viewModel.loadConfigs()
    }

    if (isEditing) {
        AiConfigEditScreen(
            configId = editingConfigId,
            onBack = {
                isEditing = false
                viewModel.loadConfigs()
            },
        )
        return
    }

        AppScaffold(
        title = "AI 配置",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (onEditConfig != null) {
                        onEditConfig(null)
                    } else {
                        isEditing = true
                        editingConfigId = null
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加配置")
            }
        },
    ) { modifier ->
        if (state.configs.isEmpty()) {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("暂无 AI 配置", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(Spacing.s))
                Text(
                    "点击右下角 + 添加模型配置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                item {
                    Text(
                        "${state.configs.size} 个配置",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                }
                items(state.configs, key = { it.id }) { config ->
                    val isDefault = config.is_default == 1L
                    val canDelete = canDeleteAiConfig(config.is_default)
                    Card(
                        onClick = {
                            if (onEditConfig != null) {
                                onEditConfig(config.id)
                            } else {
                                isEditing = true
                                editingConfigId = config.id
                            }
                        },
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDefault)
                                MaterialTheme.colorScheme.surfaceContainerLow
                            else
                                MaterialTheme.colorScheme.surface,
                        ),
                        border = if (isDefault) {
                            BorderStroke(
                                BorderWidth.s,
                                MaterialTheme.colorScheme.primary.copy(alpha = aiConfigDefaultCardBorderAlpha),
                            )
                        } else {
                            null
                        },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    aiConfigDisplayName(config.provider, config.model_name),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isDefault) {
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "默认",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = aiConfigDefaultIconAlpha),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                if (canDelete) {
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    IconButton(
                                        onClick = { deletingConfigId = config.id },
                                        modifier = Modifier.size(aiConfigDeleteActionSizeDp.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除配置",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = aiConfigDeleteIconAlpha),
                                            modifier = Modifier.size(aiConfigDeleteIconSizeDp.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                if (isDefault) "默认模型" else "备用模型",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    config.provider.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(Spacing.s))
                                Text(
                                    config.api_address,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val targetDeleteId = deletingConfigId
    if (targetDeleteId != null) {
        AlertDialog(
            onDismissRequest = { deletingConfigId = null },
            shape = RoundedCornerShape(Radius.xl),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            iconContentColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("删除 AI 配置") },
            text = { Text("确定删除这个非默认模型配置吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConfig(targetDeleteId)
                        deletingConfigId = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingConfigId = null }) { Text("取消") }
            },
        )
    }
}
