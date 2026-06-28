package com.dailysatori.ui.feature.settings.remotenews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.shared.db.Remote_news_source
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun RemoteNewsSettingsScreen(onBack: () -> Unit) {
    val viewModel: RemoteNewsSettingsViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    if (state.isEditing) {
        RemoteNewsSourceEditorPage(state = state, viewModel = viewModel)
    } else {
        RemoteNewsSourceListPage(state = state, viewModel = viewModel, onBack = onBack)
    }
}

@Composable
private fun RemoteNewsSourceListPage(
    state: RemoteNewsSettingsState,
    viewModel: RemoteNewsSettingsViewModel,
    onBack: () -> Unit,
) {
    AppScaffold(
        title = "远程新闻设置",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAdd) {
                Icon(Icons.Default.Add, contentDescription = "新增远程新闻")
            }
        },
    ) { modifier ->
        if (state.sources.isEmpty()) {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { RemoteNewsSyncSummary(state) }
                item {
                    EmptyState(
                        icon = Icons.Default.Add,
                        title = "暂无远程新闻",
                        subtitle = "点击右下角新增远程新闻源",
                        actionLabel = "新增远程新闻",
                        onAction = viewModel::openAdd,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { RemoteNewsSyncSummary(state) }
                item { RemoteNewsSourceListHeader(state.sources.size) }
                items(state.sources, key = { it.id }) { source ->
                    RemoteNewsSourceRow(
                        source = source,
                        syncedCountText = remoteNewsSourceSyncedCountText(source, state),
                        syncWork = state.syncWorkBySourceId[source.id],
                        onClick = { viewModel.openEdit(source) },
                        onSync = { viewModel.syncSource(source.id) },
                        onCancelSync = { viewModel.cancelSync(source.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteNewsSyncSummary(state: RemoteNewsSettingsState) {
    Surface(
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                RemoteNewsIconBox()
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        "同步概览",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        remoteNewsSummarySubtitle(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                remoteNewsSummaryMetrics(state).forEach { metric ->
                    RemoteNewsMetricTile(metric = metric, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RemoteNewsIconBox() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(Radius.l))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RemoteNewsMetricTile(metric: RemoteNewsSummaryMetric, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                metric.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RemoteNewsSourceListHeader(sourceCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "远程新闻源",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RemoteNewsChip("$sourceCount 个来源")
    }
}

@Composable
private fun RemoteNewsSourceRow(
    source: Remote_news_source,
    syncedCountText: String,
    syncWork: RemoteNewsSyncWorkUi?,
    onClick: () -> Unit,
    onSync: () -> Unit,
    onCancelSync: () -> Unit,
) {
    val syncing = syncWork?.active == true
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        source.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        source.base_url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RemoteNewsStatusPill(source = source, syncWork = syncWork)
            }
            if (syncing) {
                RemoteNewsSyncProgressBox(syncWork)
            }
            RemoteNewsSourceDetails(source = source, syncedCountText = syncedCountText, syncWork = syncWork)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = if (syncing) onCancelSync else onSync,
                    enabled = remoteNewsSyncActionEnabled(source, syncWork),
                    colors = if (syncing) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(remoteNewsSyncActionLabel(syncWork))
                }
            }
        }
    }
}

@Composable
private fun RemoteNewsStatusPill(source: Remote_news_source, syncWork: RemoteNewsSyncWorkUi?) {
    val active = syncWork?.active == true
    val enabled = source.enabled == 1L
    val color = when {
        active -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(Radius.circular),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.xs)
                    .clip(RoundedCornerShape(Radius.circular))
                    .background(color),
            )
            Text(
                remoteNewsEffectiveStatusLabel(source, syncWork),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RemoteNewsSyncProgressBox(work: RemoteNewsSyncWorkUi) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    remoteNewsSyncProgressTitle(work),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    remoteNewsSyncProgressText(work),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { remoteNewsSyncProgressFraction(work) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                remoteNewsProgressMetrics(work).forEach { metric ->
                    Surface(
                        shape = RoundedCornerShape(Radius.s),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.s)) {
                            Text(metric.value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                metric.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteNewsSourceDetails(source: Remote_news_source, syncedCountText: String, syncWork: RemoteNewsSyncWorkUi?) {
    val lines = if (syncWork?.active == true) {
        remoteNewsRunningDetailLines(syncWork).map { it.label to it.value }
    } else {
        listOf(
            "同步状态" to if (source.enabled == 1L) "已启用" else "已停用",
            "同步记录" to syncedCountText,
        )
    }
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            lines.forEach { (label, value) ->
                RemoteNewsDetailRow(label, value)
            }
        }
    }
}

@Composable
private fun RemoteNewsDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Spacing.s),
        )
    }
}

@Composable
private fun RemoteNewsChip(text: String) {
    Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RemoteNewsSourceEditorPage(
    state: RemoteNewsSettingsState,
    viewModel: RemoteNewsSettingsViewModel,
) {
    AppScaffold(
        title = if (state.editingId == null) "新增远程新闻" else "编辑远程新闻",
        onBack = viewModel::closeEditor,
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(Spacing.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            RemoteNewsSourceHelperCard()
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("完整 URL") },
                placeholder = { Text("http://host:3000/api/v1/external/top_articles_today") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            var tokenVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::updateToken,
                label = { Text("Token") },
                singleLine = true,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (tokenVisible) "隐藏 Token" else "显示 Token",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启用", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.enabled, onCheckedChange = viewModel::updateEnabled)
            }
            state.message?.let { message ->
                RemoteNewsMessageCard(message)
            }
            Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isSaving) "保存中..." else "保存")
            }
            OutlinedButton(onClick = viewModel::testConnection, enabled = !state.isTesting, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isTesting) "测试中..." else "测试连接")
            }
            if (state.editingId != null) {
                TextButton(onClick = { viewModel.deleteSource(state.editingId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun RemoteNewsSourceHelperCard() {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("填写完整接口", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "URL 应指向 top_articles_today 接口，测试连接会验证 Token 并读取文章列表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RemoteNewsMessageCard(message: String) {
    Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
