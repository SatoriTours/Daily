package com.dailysatori.ui.feature.settings.externalfavorites

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.component.settings.SettingsSectionCard
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExternalFavoritesSettingsScreen(onBack: () -> Unit) {
    val viewModel: ExternalFavoritesSettingsViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    var showAddPage by remember { mutableStateOf(false) }
    val connectX = {
        viewModel.createXAuthorizationUrl()?.let { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }.onFailure {
                viewModel.showMessage("无法打开授权页面，请确认设备已安装浏览器")
            }
        }
        Unit
    }
    val openAddPage = { showAddPage = true }

    if (showAddPage) {
        ExternalFavoriteAddServicePage(
            state = state,
            viewModel = viewModel,
            onBack = { showAddPage = false },
            onConnectX = {
                if (viewModel.saveXOAuthClientIdForConnect()) {
                    showAddPage = false
                    connectX()
                }
            },
        )
    } else {
        ExternalFavoriteSourceListPage(
            state = state,
            viewModel = viewModel,
            onBack = onBack,
            openAddPage = openAddPage,
        )
    }
}

@Composable
private fun ExternalFavoriteSourceListPage(
    state: ExternalFavoritesSettingsState,
    viewModel: ExternalFavoritesSettingsViewModel,
    onBack: () -> Unit,
    openAddPage: () -> Unit,
) {
    AppScaffold(
        title = "外部收藏同步",
        onBack = onBack,
        actions = {
            IconButton(onClick = viewModel::markRestoredSourcesAuthCheckRequired) {
                Icon(Icons.Default.Refresh, contentDescription = "重新验证授权")
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = openAddPage) {
                Icon(Icons.Default.Add, contentDescription = externalFavoriteAddServiceActionLabel())
            }
        },
    ) { modifier ->
        if (state.sources.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Bookmark,
                title = externalFavoriteEmptyStateTitle(),
                subtitle = externalFavoriteEmptyStateSubtitle(state.message),
                modifier = modifier.fillMaxSize(),
                actionLabel = externalFavoriteAddServiceActionLabel(),
                onAction = openAddPage,
            )
        } else {
            ExternalFavoriteSourceList(state = state, viewModel = viewModel, modifier = modifier)
        }
    }
}

@Composable
private fun ExternalFavoriteAddServicePage(
    state: ExternalFavoritesSettingsState,
    viewModel: ExternalFavoritesSettingsViewModel,
    onBack: () -> Unit,
    onConnectX: () -> Unit,
) {
    var displayName by remember { mutableStateOf(externalFavoriteDefaultDisplayName()) }
    AppScaffold(
        title = externalFavoriteAddPageTitle(),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(Spacing.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ExternalFavoriteAddHelperCard()
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.xOAuthClientId,
                onValueChange = viewModel::updateXOAuthClientId,
                label = { Text(externalFavoriteXClientIdLabel()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ExternalFavoriteAddSyncNote()
            state.message?.takeIf { it.isNotBlank() }?.let { ExternalFavoriteMessage(it) }
            Button(onClick = onConnectX, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bookmark, contentDescription = null)
                Text(externalFavoriteConnectXActionLabel())
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun ExternalFavoriteAddSyncNote() {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                externalFavoriteAddPageSyncNoteTitle(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                externalFavoriteAddPageSyncNoteText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExternalFavoriteAddHelperCard() {
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
                Text(
                    externalFavoriteAddPageHelperTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    externalFavoriteAddPageHelperText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ExternalFavoriteSourceList(
    state: ExternalFavoritesSettingsState,
    viewModel: ExternalFavoritesSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        state.message?.let { message ->
            item {
                ExternalFavoriteMessage(message)
            }
        }
        item {
            SettingsSectionCard("同步来源") {
                Text(
                    "外部平台收藏会先写入本地收藏，再由 AI 整理内容。当前版本使用定期同步，不承诺实时导入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                )
            }
        }
        items(state.sources, key = { it.id }) { source ->
            ExternalFavoriteSourceCard(
                item = source,
                syncing = state.syncingSourceId == source.id,
                onSyncNow = { viewModel.syncNow(source.id) },
                onImportOlder = { viewModel.importOlder(source.id) },
                onToggleEnabled = { viewModel.toggleEnabled(source.id, it) },
                onDelete = { viewModel.deleteSource(source.id) },
            )
        }
    }
}

@Composable
private fun ExternalFavoriteSourceCard(
    item: ExternalFavoriteSourceUi,
    syncing: Boolean,
    onSyncNow: () -> Unit,
    onImportOlder: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val source = item.source
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(source.display_name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(source.provider.uppercase(), source.account_name.ifBlank { source.account_id }).joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = item.enabled, onCheckedChange = onToggleEnabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                ExternalFavoriteChip(externalFavoriteHealthLabel(item.health))
                ExternalFavoriteChip(externalFavoritePeriodicSyncSubtitle(item.health))
            }
            Text(
                externalFavoriteLastSyncText(source.last_sync_started_at, source.last_success_at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (source.last_error_message.isNotBlank()) {
                Text(
                    source.last_error_message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onSyncNow, enabled = item.enabled && !syncing) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(if (syncing) "同步中" else "同步")
                }
                OutlinedButton(onClick = onImportOlder, enabled = item.enabled && !syncing) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Text("历史")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun ExternalFavoriteChip(text: String) {
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
private fun ExternalFavoriteMessage(message: String) {
    Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun externalFavoriteLastSyncText(lastAttemptAt: Long?, lastSuccessAt: Long?): String = when {
    lastSuccessAt != null -> "上次成功：$lastSuccessAt"
    lastAttemptAt != null -> "上次尝试：$lastAttemptAt"
    else -> "尚未同步"
}
