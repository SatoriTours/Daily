package com.dailysatori.ui.feature.settings.taskcenter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.asynctask.asyncTaskStatusDisplayName
import com.dailysatori.service.asynctask.asyncTaskTypeDisplayName
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

@Composable
fun TaskCenterScreen(onBack: () -> Unit, recentFailures: Boolean = false) {
    val viewModel: TaskCenterViewModel = koinViewModel()
    LaunchedEffect(viewModel, recentFailures) { viewModel.applyEntryFilter(recentFailures) }
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val selected = state.selectedTask
    var logOpen by rememberSaveable(selected?.id) { mutableStateOf(false) }
    var logQuery by rememberSaveable(selected?.id) { mutableStateOf("") }
    var logFilter by rememberSaveable(selected?.id) { mutableStateOf(TaskCenterLogFilter.All) }
    val logCopy = taskCenterCopy()
    BackHandler(enabled = selected != null) {
        if (logOpen) logOpen = false else viewModel.closeTask()
    }

    AppScaffold(
        title = if (logOpen) {
            stringResource(R.string.task_center_log_title)
        } else {
            stringResource(R.string.task_center_title)
        },
        onBack = when {
            logOpen -> ({ logOpen = false })
            selected == null -> onBack
            else -> viewModel::closeTask
        },
        searchQuery = logQuery,
        onSearchQueryChange = if (logOpen) ({ logQuery = it }) else null,
        inlinePageActions = logOpen,
        searchActionLabel = if (logOpen) logCopy.logSearchAction else null,
        showAssistant = !logOpen,
        pageActions = if (logOpen) {
            listOf(
                taskCenterLogFilterAction(
                    filter = logFilter,
                    allLabel = logCopy.logFilterAll,
                    abnormalLabel = logCopy.logFilterAbnormal,
                    filterLabelFormat = logCopy.logFilter,
                ) { logFilter = it },
            )
        } else {
            emptyList()
        },
    ) { modifier ->
        val task = state.selectedTask
        when {
            task != null && logOpen -> TaskCenterTaskLogScreen(
                task = task,
                title = taskCenterTaskName(task.type),
                source = taskCenterTaskSource(task.type, task.payload_json, state.sourceNames),
                taskLog = state.taskLog,
                httpLog = state.httpLog,
                query = logQuery,
                filter = logFilter,
                modifier = modifier,
            )
            task != null -> TaskCenterTaskDetail(
                task = task,
                title = taskCenterTaskName(task.type),
                source = taskCenterTaskSource(task.type, task.payload_json, state.sourceNames),
                taskLog = state.taskLog,
                failureSuperseded = state.selectedFailureSuperseded,
                needsAttention = state.attentionTasks.any { it.id == task.id },
                onAcknowledge = { viewModel.acknowledgeFailure(task.id, task.updated_at) },
                onResumeAssistant = { viewModel.resumeAssistantBackground(task.id) },
                onCancel = { viewModel.cancel(task.id) },
                assistantResumeBlocked = state.assistantResumeBlocked,
                httpLog = state.httpLog,
                captureState = state.captureState,
                onArmCapture = { viewModel.armCapture(task.id, task.type) },
                onDisarmCapture = { viewModel.disarmCapture(task.id) },
                onOpenLog = { logOpen = true },
                modifier = modifier,
            )
            else -> TaskCenterTaskList(state = state, viewModel = viewModel, modifier = modifier)
        }
    }
}

@Composable
private fun TaskCenterTaskList(
    state: TaskCenterState,
    viewModel: TaskCenterViewModel,
    modifier: Modifier = Modifier,
) {
    val copy = taskCenterCopy()
    Column(modifier = modifier.fillMaxSize()) {
        TaskCenterTabRow(
            tab = state.tab,
            activeTotal = state.activeTotal,
            pendingTotal = state.pendingTotal,
            onSelect = viewModel::selectTab,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (state.tab) {
                    TaskCenterTab.Current -> copy.sectionCurrent
                    TaskCenterTab.Pending -> copy.sectionPending
                    TaskCenterTab.History -> copy.sectionHistory
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::toggleFilters) {
                Text(if (state.filtersExpanded) copy.filterCollapse else copy.filter)
            }
        }
        if (state.filtersExpanded) {
            TaskCenterFilterBar(state = state, viewModel = viewModel)
        }
        if (state.updatedSince != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.management_recent_tasks),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = viewModel::showAllTasks) {
                    Text(stringResource(R.string.management_all_tasks))
                }
            }
        }
        if (state.tasks.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Refresh,
                title = copy.emptyTitle,
                subtitle = when (state.tab) {
                    TaskCenterTab.Current -> copy.emptyCurrent
                    TaskCenterTab.Pending -> copy.emptyPending
                    TaskCenterTab.History -> copy.emptyHistory
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.l),
            ) {
                items(state.tasks, key = { it.id }) { task ->
                    TaskCenterTaskRow(
                        task = task,
                        title = taskCenterTaskTitle(task.type, task.payloadJson, state.sourceNames),
                        onOpen = { viewModel.openTask(task.id) },
                    )
                    TaskCenterRowDivider()
                }
                if (state.tab == TaskCenterTab.Current) {
                    item(key = "task-center-merge-hint") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.m),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            Text(
                                copy.mergeHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                copy.supersededHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.hasMore) {
                    item(key = "task-center-load-more") {
                        TaskCenterLoadMoreRow(
                            loadedCount = state.loadedCount,
                            requestedLimit = state.requestedLimit,
                            loadMore = { viewModel.loadMore() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCenterTabRow(
    tab: TaskCenterTab,
    activeTotal: Long?,
    pendingTotal: Long?,
    onSelect: (TaskCenterTab) -> Unit,
) {
    val copy = taskCenterCopy()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m, vertical = Spacing.s)
            .clip(RoundedCornerShape(Radius.m))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        TaskCenterTab.entries.forEach { value ->
            val total = when (value) {
                TaskCenterTab.Current -> activeTotal
                TaskCenterTab.Pending -> pendingTotal
                TaskCenterTab.History -> null
            }
            val count = taskCenterTabCountText(loaded = 0, total = total)
            val label = when (value) {
                TaskCenterTab.Current -> copy.tabCurrent
                TaskCenterTab.Pending -> copy.tabPending
                TaskCenterTab.History -> copy.tabHistory
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = value == tab, role = Role.Tab) { onSelect(value) },
                shape = RoundedCornerShape(Radius.s),
                color = if (value == tab) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Text(
                    if (count.isBlank()) label else "${label} ${count}",
                    modifier = Modifier.padding(vertical = Spacing.s),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (value == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun TaskCenterRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

@Composable
private fun TaskCenterTaskRow(task: AsyncTaskListItem, title: String, onOpen: () -> Unit) {
    val copy = taskCenterCopy()
    val row = taskCenterRowText(task, copy.strings)
    val progress = taskCenterProgress(task)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TaskCenterStatusPill(task.status)
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (row.headline.isNotBlank()) {
            Text(row.headline, style = MaterialTheme.typography.bodyMedium)
        }
        if (row.detail.isNotBlank()) {
            Text(
                row.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            taskCenterRowMetaText(task, row.retryAtMs, copy),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(Radius.xxs))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.fraction)
                            .height(4.dp)
                            .clip(RoundedCornerShape(Radius.xxs))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    progress.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun taskCenterRowMetaText(task: AsyncTaskListItem, retryAtMs: Long?, copy: TaskCenterCopy): String = buildList {
    add("#${task.id}")
    val created = taskCenterWhenText(task.createdAt, copy)
    val startedAt = task.startedAt
    if (startedAt == null) {
        add(String.format(copy.metaCreated, created))
        val waited = Clock.System.now().toEpochMilliseconds() - task.createdAt
        if (taskCenterIsActive(task.status) && waited > 0) {
            add(String.format(copy.metaWaited, taskCenterDurationText(waited, copy.strings)))
        }
    } else {
        add(String.format(copy.metaStarted, created))
        val elapsedEnd = task.finishedAt ?: task.updatedAt
        add(String.format(copy.metaElapsed, taskCenterDurationText((elapsedEnd - startedAt).coerceAtLeast(0L), copy.strings)))
    }
    retryAtMs?.let { add(String.format(copy.metaRetryAt, taskCenterTimestampText(it, copy.strings))) }
}.joinToString(" · ")

internal fun taskCenterWhenText(value: Long?, copy: TaskCenterCopy): String {
    if (value == null || value <= 0L) return copy.notStarted
    val zone = TimeZone.currentSystemDefault()
    val time = Instant.fromEpochMilliseconds(value).toLocalDateTime(zone)
    val now = Clock.System.now().toLocalDateTime(zone)
    val clock = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    return if (time.date == now.date) {
        String.format(copy.today, clock)
    } else {
        "${time.monthNumber.toString().padStart(2, '0')}-${time.dayOfMonth.toString().padStart(2, '0')} $clock"
    }
}

@Composable
private fun TaskCenterLoadMoreRow(loadedCount: Int, requestedLimit: Int, loadMore: () -> Unit) {
    val copy = taskCenterCopy()
    LaunchedEffect(loadedCount, requestedLimit) {
        loadMore()
    }
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                String.format(copy.loadedOf, loadedCount, requestedLimit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(copy.loadingMore, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TaskCenterFilterBar(
    state: TaskCenterState,
    viewModel: TaskCenterViewModel,
    modifier: Modifier = Modifier,
) {
    val copy = taskCenterCopy()
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        TaskCenterMultiSelectDropdown(
            label = copy.filterType,
            summary = taskCenterTypeFilterSummary(state.types, copy),
            options = AsyncTaskType.entries.map { it.name to it.displayName },
            selected = state.types,
            onToggle = viewModel::toggleType,
            onClear = viewModel::clearTypes,
            modifier = Modifier.weight(1f).widthIn(min = 0.dp),
        )
        TaskCenterMultiSelectDropdown(
            label = copy.filterStatus,
            summary = taskCenterStatusFilterSummary(state.statuses, copy),
            options = AsyncTaskStatus.entries.map { it.name to asyncTaskStatusDisplayName(it.name) },
            selected = state.statuses,
            onToggle = viewModel::toggleStatus,
            onClear = viewModel::clearStatuses,
            modifier = Modifier.weight(1f).widthIn(min = 0.dp),
        )
    }
}

@Composable
private fun TaskCenterMultiSelectDropdown(
    label: String,
    summary: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = taskCenterCopy()
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：$summary", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(copy.filterAll) },
                leadingIcon = { Checkbox(checked = selected.isEmpty(), onCheckedChange = null) },
                onClick = onClear,
            )
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    leadingIcon = { Checkbox(checked = value in selected, onCheckedChange = null) },
                    onClick = { onToggle(value) },
                )
            }
        }
    }
}

@Composable
internal fun TaskCenterStatusPill(status: String) {
    val color = taskCenterStatusColor(status)
    Text(
        asyncTaskStatusDisplayName(status),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
    )
}

@Composable
internal fun taskCenterStatusColor(status: String): androidx.compose.ui.graphics.Color = when (status) {
    AsyncTaskStatus.succeeded.name -> MaterialTheme.colorScheme.primary
    AsyncTaskStatus.failed.name -> MaterialTheme.colorScheme.error
    AsyncTaskStatus.cancelled.name -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.tertiary
}

private fun taskCenterTypeFilterSummary(selected: Set<String>, copy: TaskCenterCopy): String =
    if (selected.isEmpty()) copy.filterAll else selected.joinToString("、") { asyncTaskTypeDisplayName(it) }

private fun taskCenterStatusFilterSummary(selected: Set<String>, copy: TaskCenterCopy): String =
    if (selected.isEmpty()) copy.filterAll else selected.joinToString("、") { asyncTaskStatusDisplayName(it) }
