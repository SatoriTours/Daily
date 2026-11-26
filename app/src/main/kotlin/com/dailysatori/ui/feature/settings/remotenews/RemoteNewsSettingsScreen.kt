package com.dailysatori.ui.feature.settings.remotenews

import com.dailysatori.core.diagnostics.performanceContent
import com.dailysatori.ui.component.appbar.PageAction
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.dailysatori.ui.component.settings.rememberSettingsEditorBack
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.remotenews.RemoteFeedHistoryStatus
import com.dailysatori.service.remotenews.RemoteFeedHistoryStrategy
import com.dailysatori.shared.db.Remote_news_source
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.component.settings.matchesSettingsQuery
import com.dailysatori.ui.component.settings.SettingsSearchEmptyState
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun RemoteNewsSettingsScreen(
    onBack: () -> Unit,
    onOpenArticleAiTasks: (() -> Unit)? = null,
) {
    val viewModel: RemoteNewsSettingsViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    if (state.isEditing) {
        RemoteNewsSourceEditorPage(state = state, viewModel = viewModel)
    } else {
        RemoteNewsSourceListPage(
            state = state,
            viewModel = viewModel,
            onBack = onBack,
            onOpenArticleAiTasks = onOpenArticleAiTasks,
        )
    }
}

@Composable
private fun RemoteNewsSourceListPage(
    state: RemoteNewsSettingsState,
    viewModel: RemoteNewsSettingsViewModel,
    onBack: () -> Unit,
    onOpenArticleAiTasks: (() -> Unit)?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val i18n = koinInject<I18nService>()
    val visibleSources = state.sources.filter { matchesSettingsQuery(query, it.name, it.base_url) }
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    AppScaffold(
        useGroupNavigation = true,
        title = "远程新闻设置",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHost) },
        searchQuery = query, onSearchQueryChange = { query = it },
        pageActions = listOf(PageAction("新增远程新闻", Icons.Default.Add, onClick = viewModel::openAdd)),
    ) { modifier ->
        if (query.isNotBlank() && visibleSources.isEmpty()) SettingsSearchEmptyState(modifier)
        else if (state.sources.isEmpty()) {
            LazyColumn(
                modifier = modifier.fillMaxSize().performanceContent(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { RemoteNewsSyncSummary(state) }
                item {
                    EmptyState(
                        icon = Icons.Default.Add,
                        title = i18n.t("remote_news_settings.empty_title"),
                        subtitle = i18n.t("remote_news_settings.empty_subtitle"),
                        actionLabel = i18n.t("remote_news_settings.add_action"),
                        onAction = viewModel::openAdd,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize().performanceContent(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { RemoteNewsSyncSummary(state) }
                item { ArticleAiHistoryControls(onOpenTasks = onOpenArticleAiTasks) }
                item { RemoteNewsSourceListHeader(state.sources.size) }
                items(visibleSources, key = { it.id }) { source ->
                    RemoteNewsSourceRow(
                        source = source,
                        syncedCountText = remoteNewsSourceSyncedCountText(source, state),
                        syncWork = state.syncWorkBySourceId[source.id],
                        historyUi = state.historyBySourceId[source.id],
                        onClick = { viewModel.openEdit(source) },
                        onSync = { viewModel.syncSource(source.id) },
                        onCancelSync = { viewModel.cancelSync(source.id) },
                        onStartHistory = { viewModel.startHistory(source.id) },
                        onPauseHistory = { viewModel.pauseHistory(source.id) },
                        onResumeHistory = { viewModel.resumeHistory(source.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteNewsSyncSummary(state: RemoteNewsSettingsState) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
                        style = MaterialTheme.typography.titleMedium.editorial(),
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
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
    historyUi: RemoteNewsHistoryUi?,
    onClick: () -> Unit,
    onSync: () -> Unit,
    onCancelSync: () -> Unit,
    onStartHistory: () -> Unit,
    onPauseHistory: () -> Unit,
    onResumeHistory: () -> Unit,
) {
    val syncing = syncWork?.active == true
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                        remoteNewsDisplayUrl(source.base_url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RemoteNewsStatusPill(source = source, syncWork = syncWork)
            }
            if (historyUi != null && historyUi.status != RemoteFeedHistoryStatus.OFF) {
                RemoteNewsHistoryLine(historyUi)
            }
            remoteNewsHistoryErrorText(historyUi)?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (syncing) {
                RemoteNewsSyncProgressBox(syncWork)
            }
            if (syncWork?.active != true) {
                RemoteNewsSourceDetails(source = source, syncedCountText = syncedCountText)
            }
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
                val historyAction = remoteNewsHistoryAction(historyUi, source.enabled == 1L)
                if (historyAction != RemoteNewsHistoryAction.NONE) {
                    // Compact intrinsic width: a narrow screen must never break the label into two lines.
                    OutlinedButton(
                        onClick = when (historyAction) {
                            RemoteNewsHistoryAction.PAUSE -> onPauseHistory
                            RemoteNewsHistoryAction.RESUME -> onResumeHistory
                            else -> onStartHistory
                        },
                        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                    ) {
                        Text(
                            remoteNewsHistoryActionLabel(historyAction, historyUi),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteNewsSyncProgressBox(work: RemoteNewsSyncWorkUi) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            // A long stage message owns its own line; it must never compete with the short progress count.
            Text(
                remoteNewsSyncProgressTitle(work),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { remoteNewsSyncProgressFraction(work) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    remoteNewsSyncProgressText(work),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                remoteNewsProgressMetrics(work).forEach { metric ->
                    Column(horizontalAlignment = Alignment.Start) {
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

/** One compact line: walk state, imported count and page count; never an unknown-total percentage. */
@Composable
private fun RemoteNewsHistoryLine(ui: RemoteNewsHistoryUi) {
    val hasError = remoteNewsHistoryErrorText(ui) != null
    val color = when {
        hasError || ui.status == RemoteFeedHistoryStatus.BLOCKED -> MaterialTheme.colorScheme.error
        ui.status.open -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val progress = remoteNewsHistoryProgressText(ui)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(Radius.circular))
                .background(color),
        )
        Text(
            remoteNewsHistoryStatusText(ui),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
        )
        if (progress.isNotBlank()) {
            Text(
                progress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RemoteNewsSourceDetails(source: Remote_news_source, syncedCountText: String) {
    // While a sync runs, the progress card already carries the stage and source progress; this card stays idle-only.
    val lines = listOf(
        "同步状态" to if (source.enabled == 1L) "已启用" else "已停用",
        "同步记录" to syncedCountText,
        "首次导入范围" to remoteNewsSourceHistoryRangeText(source),
    )
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
    Surface(
        shape = RoundedCornerShape(Radius.circular),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
    ) {
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
    val requestBack = rememberSettingsEditorBack(state.hasChanges, state.busy, viewModel::closeEditor)
    val urlFocusRequester = remember { FocusRequester() }
    var confirmDelete by rememberSaveable(state.editingId) { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.settings_delete_source_title), style = MaterialTheme.typography.titleMedium.editorial()) },
        text = { Text(stringResource(R.string.settings_delete_source_message, state.savedDraft.name)) },
        confirmButton = {
            TextButton(enabled = !state.busy, onClick = {
                confirmDelete = false
                state.editingId?.let(viewModel::deleteSource)
            }) { Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
    AppScaffold(
        title = if (state.editingId == null) "新增远程新闻" else "编辑远程新闻",
        onBack = requestBack,
        pageActions = if (state.editingId == null) emptyList() else listOf(
            PageAction(
                label = "更多",
                icon = MoreActionsIcon,
                enabled = !state.busy,
                menuContent = { dismiss ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_delete_subscription)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            dismiss()
                            confirmDelete = true
                        },
                        enabled = !state.busy,
                    )
                },
                onClick = {},
            ),
        ),
        bottomBar = {
            SettingsEditorBottomBar(
                canTest = !state.busy && state.baseUrl.isNotBlank(),
                canSave = !state.busy && state.hasChanges && state.baseUrl.isNotBlank(),
                isTesting = state.isTesting,
                isSaving = state.isSaving,
                onTest = viewModel::testConnection,
                onSave = viewModel::save,
            )
        },
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            RemoteNewsSourceHint()
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("名称（可选）") },
                enabled = !state.busy,
                singleLine = true,
                supportingText = { Text("留空时使用订阅频道标题") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("完整 URL") },
                enabled = !state.busy,
                placeholder = { Text("https://example.com/feed.xml?token=…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                trailingIcon = {
                    if (state.baseUrl.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.updateBaseUrl("")
                                // Keep the field focused so the keyboard stays available for retyping.
                                urlFocusRequester.requestFocus()
                            },
                            enabled = !state.busy,
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "清空地址")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(urlFocusRequester),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启用", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.enabled, onCheckedChange = viewModel::updateEnabled, enabled = !state.busy)
            }
            RemoteNewsHistoryRangePicker(state = state, viewModel = viewModel)
            state.message?.let { message ->
                RemoteNewsTestResult(message = message, isError = state.isError)
            }
            Spacer(Modifier.height(Spacing.m))
        }
    }
}

@Composable
private fun RemoteNewsSourceHint() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "填写完整 RSS 地址，Token 参数直接写在 URL 里。测试只读取，不会保存。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** First-import range of a new subscription; an edit never restarts the history walk. */
@Composable
private fun RemoteNewsHistoryRangePicker(state: RemoteNewsSettingsState, viewModel: RemoteNewsSettingsViewModel) {
    if (state.editingId != null) {
        Text(
            "历史补全请在来源列表中使用“补全历史”，编辑来源不会重新抓取历史。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("首次导入范围", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            RemoteFeedHistoryStrategy.entries.forEach { strategy ->
                FilterChip(
                    selected = state.historyStrategy == strategy,
                    onClick = { viewModel.updateHistoryStrategy(strategy) },
                    enabled = !state.busy,
                    label = { Text(remoteNewsHistoryRangeText(strategy)) },
                )
            }
        }
        Text(
            "只影响首次导入；之后新增的文章会持续累计，不受此范围限制。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RemoteNewsTestResult(message: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}
