package com.dailysatori.ui.feature.settings.remotenews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
            EmptyState(
                icon = Icons.Default.Add,
                title = "暂无远程新闻",
                subtitle = "点击右下角新增远程新闻源",
                modifier = modifier.fillMaxSize(),
                actionLabel = "新增远程新闻",
                onAction = viewModel::openAdd,
            )
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(state.sources, key = { it.id }) { source ->
                    RemoteNewsSourceRow(source = source, onClick = { viewModel.openEdit(source) })
                }
            }
        }
    }
}

@Composable
private fun RemoteNewsSourceRow(source: Remote_news_source, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium)
                Text(source.base_url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    RemoteNewsSourceStatusDot(enabled = source.enabled == 1L)
                    RemoteNewsChip(if (source.enabled == 1L) "已启用" else "已停用")
                    RemoteNewsChip("完整接口")
                }
            }
        }
    }
}

@Composable
private fun RemoteNewsSourceStatusDot(enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(IconSize.xs)
            .clip(RoundedCornerShape(Radius.circular))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    )
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
                Text("启用", style = MaterialTheme.typography.bodyLarge)
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
