package com.dailysatori.ui.feature.settings.externalfavorites

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dailysatori.service.externalfavorites.ExternalSourceHealth
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExternalFavoritesSettingsScreen(onBack: () -> Unit) {
    val viewModel: ExternalFavoritesSettingsViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAddPage by remember { mutableStateOf(false) }
    val connectX = {
        viewModel.createXAuthorizationUrl()?.let { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }.onFailure {
                viewModel.showMessage("无法打开授权页面，请确认设备已安装浏览器")
            }.isSuccess
        } ?: false
    }
    val openAddPage = { showAddPage = true }

    BackHandler(enabled = showAddPage) {
        showAddPage = false
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showAddPage) {
        ExternalFavoriteAddServicePage(
            state = state,
            viewModel = viewModel,
            onBack = { showAddPage = false },
            onConnectX = {
                val clientIdSaved = viewModel.saveXOAuthClientIdForConnect()
                val authorizationLaunched = if (clientIdSaved) connectX() else false
                if (externalFavoriteShouldCloseAddPageAfterConnect(clientIdSaved, authorizationLaunched)) {
                    showAddPage = false
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
        floatingActionButton = {
            FloatingActionButton(onClick = openAddPage) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = externalFavoriteAddServiceActionLabel(hasSources = state.sources.isNotEmpty()),
                )
            }
        },
    ) { modifier ->
        if (state.sources.isEmpty()) {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { ExternalFavoriteManagementSummary(state) }
                state.message?.takeIf { it.isNotBlank() }?.let { message ->
                    item { ExternalFavoriteMessage(message) }
                }
                item {
                    ExternalFavoriteEmptyConnectionCard(
                        message = null,
                        onAction = openAddPage,
                    )
                }
                item { ExternalFavoriteConnectionSteps() }
            }
        } else {
            ExternalFavoriteSourceList(
                state = state,
                viewModel = viewModel,
                openAddPage = openAddPage,
                modifier = modifier,
            )
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
    AppScaffold(
        title = externalFavoriteAddPageTitle(),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(Spacing.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ExternalFavoriteAddHelperCard()
            OutlinedTextField(
                value = state.xOAuthClientId,
                onValueChange = viewModel::updateXOAuthClientId,
                label = { Text(externalFavoriteXClientIdLabel()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ExternalFavoriteAddNotes()
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
private fun ExternalFavoriteAddHelperCard() {
    Surface(
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            ExternalFavoriteIconBox {
                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    externalFavoriteAddPageHelperTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
private fun ExternalFavoriteAddNotes() {
    ExternalFavoriteStepList(
        steps = listOf(
            externalFavoriteReadOnlyStepLabel() to "Daily Satori 只请求读取收藏所需权限，不会修改或删除平台收藏。",
            externalFavoriteXOAuthRedirectUriLabel() to externalFavoriteXOAuthRedirectUri(),
            "同步" to externalFavoriteAddPageSyncNoteText(),
            "整理" to externalFavoriteAddPageOrganizeNoteText(),
        ),
    )
}

@Composable
private fun ExternalFavoriteManagementSummary(state: ExternalFavoritesSettingsState) {
    Surface(
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                ExternalFavoriteIconBox {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        externalFavoriteManagementSummaryTitle(state.sources),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        externalFavoriteManagementSummarySubtitle(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ExternalFavoriteSummaryMetrics(externalFavoriteSummaryMetrics(state))
            if (externalFavoriteShouldShowAuthCheckNotice(state.sources)) {
                ExternalFavoriteInlineNotice(
                    text = externalFavoriteAuthCheckNoticeText(),
                    warning = true,
                )
            }
        }
    }
}

@Composable
private fun ExternalFavoriteIconBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(Radius.l))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ExternalFavoriteSummaryMetrics(metrics: List<ExternalFavoriteSummaryMetric>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
        metrics.forEach { metric ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Radius.m),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            ) {
                Column(modifier = Modifier.padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalFavoriteSourceList(
    state: ExternalFavoritesSettingsState,
    viewModel: ExternalFavoritesSettingsViewModel,
    openAddPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteSourceId by remember { mutableStateOf<Long?>(null) }
    val pendingDeleteSource = externalFavoritePendingDeleteSource(pendingDeleteSourceId, state.sources)

    LaunchedEffect(pendingDeleteSourceId, pendingDeleteSource) {
        if (pendingDeleteSourceId != null && pendingDeleteSource == null) {
            pendingDeleteSourceId = null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item { ExternalFavoriteManagementSummary(state) }
        state.message?.let { message ->
            item { ExternalFavoriteMessage(message) }
        }
        item {
            ExternalFavoriteSourceSectionHeader(openAddPage = openAddPage, hasSources = state.sources.isNotEmpty())
        }
        items(state.sources, key = { it.id }) { source ->
            ExternalFavoriteSourceCard(
                item = source,
                syncWork = state.syncWorkBySourceId[source.id],
                onSyncNow = { viewModel.syncNow(source.id) },
                onFullSync = { viewModel.fullSyncNow(source.id) },
                onCancelSync = { viewModel.cancelSync(source.id) },
                onToggleEnabled = { viewModel.toggleEnabled(source.id, it) },
                onReconnect = openAddPage,
                onDelete = { pendingDeleteSourceId = source.id },
            )
        }
    }

    pendingDeleteSource?.let { pendingSource ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSourceId = null },
            title = { Text(externalFavoriteDeleteDialogTitle()) },
            text = { Text(externalFavoriteDeleteDialogText()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteSourceId = null
                        viewModel.deleteSource(pendingSource.id)
                    },
                ) {
                    Text(externalFavoriteDeleteConfirmLabel(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSourceId = null }) {
                    Text(externalFavoriteDeleteCancelLabel())
                }
            },
        )
    }
}

@Composable
private fun ExternalFavoriteSourceSectionHeader(openAddPage: () -> Unit, hasSources: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "同步来源",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = openAddPage) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(externalFavoriteAddServiceActionLabel(hasSources))
        }
    }
}

@Composable
private fun ExternalFavoriteSourceCard(
    item: ExternalFavoriteSourceUi,
    syncWork: ExternalFavoriteSyncWorkUi?,
    onSyncNow: () -> Unit,
    onFullSync: () -> Unit,
    onCancelSync: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onReconnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val source = item.source
    var menuExpanded by remember { mutableStateOf(false) }
    val syncing = syncWork?.active == true

    Card(
        shape = RoundedCornerShape(Radius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExternalFavoriteProviderBadge(source.provider)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        source.display_name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        externalFavoriteSourceSubtitle(
                            identity = externalFavoriteAccountIdentity(source.account_name, source.account_id),
                            lastSuccessAt = if (syncing) null else source.last_success_at,
                            syncIntervalMinutes = source.sync_interval_minutes,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExternalFavoriteHealthPill(item.health, syncWork)
            }

            if (syncWork != null && syncWork.active) {
                ExternalFavoriteSyncProgressBox(
                    work = syncWork,
                    historyComplete = source.config_json.contains(""""history_complete":true"""),
                )
            }
            ExternalFavoriteSourceDetails(item, syncWork)

            if (item.health == ExternalSourceHealth.limited) {
                ExternalFavoriteInlineNotice(
                    text = externalFavoriteRateLimitText(source.rate_limit_reset_at),
                    warning = true,
                )
            }
            if (source.last_error_message.isNotBlank()) {
                ExternalFavoriteInlineNotice(
                    text = source.last_error_message,
                    error = true,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = if (syncing) {
                        onCancelSync
                    } else when (item.health) {
                        ExternalSourceHealth.paused -> {
                            { onToggleEnabled(true) }
                        }
                        ExternalSourceHealth.needs_auth -> onReconnect
                        else -> onSyncNow
                    },
                    enabled = externalFavoriteSyncActionEnabled(item.health, item.enabled, syncWork),
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
                    Icon(
                        if (item.health == ExternalSourceHealth.needs_auth && !syncing) Icons.Default.Link else Icons.Default.Refresh,
                        contentDescription = null,
                    )
                    Text(externalFavoriteSyncActionLabel(item.health, syncWork))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(externalFavoriteFullSyncMenuLabel()) },
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            },
                            enabled = externalFavoriteCanRunSyncAction(item.health, item.enabled) && !syncing,
                            onClick = {
                                menuExpanded = false
                                onFullSync()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(externalFavoriteToggleSyncMenuLabel(item.enabled)) },
                            leadingIcon = {
                                Icon(
                                    if (item.enabled) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleEnabled(!item.enabled)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(externalFavoriteDeleteMenuLabel(), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalFavoriteProviderBadge(provider: String) {
    Surface(
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                externalFavoriteProviderBadge(provider),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.surface,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ExternalFavoriteHealthPill(health: ExternalSourceHealth, syncWork: ExternalFavoriteSyncWorkUi? = null) {
    val color = when {
        syncWork?.active == true -> MaterialTheme.colorScheme.primary
        health == ExternalSourceHealth.healthy -> MaterialTheme.colorScheme.primary
        health == ExternalSourceHealth.never_synced || health == ExternalSourceHealth.limited -> MaterialTheme.colorScheme.tertiary
        health == ExternalSourceHealth.needs_auth || health == ExternalSourceHealth.failing -> MaterialTheme.colorScheme.error
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
            Text(externalFavoriteEffectiveHealthLabel(health, syncWork), style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun ExternalFavoriteSyncProgressBox(work: ExternalFavoriteSyncWorkUi, historyComplete: Boolean) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    externalFavoriteSyncProgressTitle(work),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    externalFavoriteSyncProgressPageText(work),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { externalFavoriteSyncProgressFraction(work) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                externalFavoriteProgressMetrics(work, historyComplete).forEach { metric ->
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
private fun ExternalFavoriteSourceDetails(item: ExternalFavoriteSourceUi, syncWork: ExternalFavoriteSyncWorkUi?) {
    val detailLines = if (syncWork?.active == true) {
        externalFavoriteRunningDetailLines(syncWork)
    } else {
        externalFavoriteIdleDetailLines(item)
    }
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            detailLines.forEach { line ->
                ExternalFavoriteDetailRow(line.label, line.value)
            }
        }
    }
}

@Composable
private fun ExternalFavoriteDetailRow(label: String, value: String) {
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
private fun ExternalFavoriteInlineNotice(
    text: String,
    warning: Boolean = false,
    error: Boolean = false,
) {
    val color = when {
        error -> MaterialTheme.colorScheme.error
        warning -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = color.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = color)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ExternalFavoriteEmptyConnectionCard(message: String?, onAction: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ExternalFavoriteIconBox {
                Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(externalFavoriteEmptyStateTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                externalFavoriteEmptyStateSubtitle(message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(externalFavoriteAddServiceActionLabel(hasSources = false))
            }
        }
    }
}

@Composable
private fun ExternalFavoriteConnectionSteps() {
    ExternalFavoriteStepList(
        steps = listOf(
            "1" to "在 X Developer Portal 配置回调地址 dailysatori://oauth/x。",
            "2" to "填写 X OAuth Client ID，打开浏览器完成授权。",
            "3" to "同步结果进入本地文章库，后续由 AI 整理标题、摘要和标签。",
        ),
    )
}

@Composable
private fun ExternalFavoriteStepList(steps: List<Pair<String, String>>) {
    Surface(
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            steps.forEach { (label, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = RoundedCornerShape(Radius.s),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
