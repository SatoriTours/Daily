package com.dailysatori.ui.feature.settings.taskcenter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.asynctask.asyncTaskStatusDisplayName
import com.dailysatori.service.asynctask.asyncTaskTypeDisplayName
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.shared.db.Async_task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalSerializationApi::class)
private val taskCenterPrettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

@Composable
fun TaskCenterScreen(onBack: () -> Unit) {
    val viewModel: TaskCenterViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    BackHandler(enabled = state.selectedTask != null) {
        viewModel.closeTask()
    }

    AppScaffold(title = "任务", onBack = if (state.selectedTask == null) onBack else viewModel::closeTask) { modifier ->
        state.selectedTask?.let { task ->
            TaskCenterTaskDetail(
                task = task,
                taskLog = state.taskLog,
                failureSuperseded = state.selectedFailureSuperseded,
                modifier = modifier,
            )
            return@AppScaffold
        }
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            TaskCenterFilters(state = state, viewModel = viewModel)
            if (state.tasks.isNotEmpty()) {
                TaskCenterSummaryBand(tasks = state.tasks)
            }
            if (state.tasks.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Refresh,
                    title = "暂无任务",
                    subtitle = "没有匹配结果",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskCenterTaskCard(
                            task = task,
                            onOpen = { viewModel.openTask(task.id) },
                            onCancel = { viewModel.cancel(task.id) },
                        )
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
}

@Composable
private fun TaskCenterLoadMoreRow(loadedCount: Int, requestedLimit: Int, loadMore: () -> Unit) {
    LaunchedEffect(loadedCount, requestedLimit) {
        loadMore()
    }
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "已加载 $loadedCount / $requestedLimit 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("正在加载更多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TaskCenterSummaryBand(tasks: List<AsyncTaskListItem>) {
    val active = tasks.count { it.status == AsyncTaskStatus.queued.name || it.status == AsyncTaskStatus.running.name || it.status == AsyncTaskStatus.retrying.name }
    val failed = tasks.count { it.status == AsyncTaskStatus.failed.name }
    val succeeded = tasks.count { it.status == AsyncTaskStatus.succeeded.name }
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskCenterTaskMetric("当前", active.toString(), MaterialTheme.colorScheme.tertiary)
            TaskCenterTaskMetric("失败", failed.toString(), MaterialTheme.colorScheme.error)
            TaskCenterTaskMetric("完成", succeeded.toString(), MaterialTheme.colorScheme.primary)
            TaskCenterTaskMetric("总数", tasks.size.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskCenterTaskMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TaskCenterFilters(state: TaskCenterState, viewModel: TaskCenterViewModel) {
    TaskCenterFilterBar(
        state = state,
        viewModel = viewModel,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
    )
}

@Composable
private fun TaskCenterFilterBar(
    state: TaskCenterState,
    viewModel: TaskCenterViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("筛选", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TaskCenterMultiSelectDropdown(
            label = "任务类型",
            summary = taskCenterTypeFilterSummary(state.types),
            options = AsyncTaskType.entries.map { it.name to it.displayName },
            selected = state.types,
            onToggle = viewModel::toggleType,
            onClear = viewModel::clearTypes,
            modifier = Modifier.weight(1f).widthIn(min = 0.dp),
        )
        TaskCenterMultiSelectDropdown(
            label = "任务状态",
            summary = taskCenterStatusFilterSummary(state.statuses),
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
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：$summary", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("全部") },
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
private fun TaskCenterTaskCard(task: AsyncTaskListItem, onOpen: () -> Unit, onCancel: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(asyncTaskTypeDisplayName(task.type), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TaskCenterStatusPill(task.status)
                    if (task.status == AsyncTaskStatus.queued.name || task.status == AsyncTaskStatus.running.name || task.status == AsyncTaskStatus.retrying.name) {
                        OutlinedButton(onClick = onCancel) {
                            Text("取消")
                        }
                    }
                }
                val message = taskCenterListSummary(task)
                if (message.isNotBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("#${task.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(Spacing.xs))
                    Text(
                        "执行时间 ${taskCenterTimestampText(task.startedAt ?: task.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "耗时 ${taskCenterDurationText(task.startedAt, task.finishedAt ?: task.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCenterStatusPill(status: String) {
    val color = taskCenterStatusColor(status)
    Text(
        asyncTaskStatusDisplayName(status),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
}

@Composable
private fun taskCenterStatusColor(status: String): androidx.compose.ui.graphics.Color = when (status) {
    AsyncTaskStatus.succeeded.name -> MaterialTheme.colorScheme.primary
    AsyncTaskStatus.failed.name -> MaterialTheme.colorScheme.error
    AsyncTaskStatus.cancelled.name -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun TaskCenterTaskDetail(
    task: Async_task,
    taskLog: String,
    failureSuperseded: Boolean,
    modifier: Modifier = Modifier,
) {
    var pageIndex by remember(taskLog) { mutableStateOf(0) }
    var logEntries by remember(taskLog) { mutableStateOf(emptyList<TaskCenterHttpLogEntry>()) }
    var logHasNext by remember(taskLog) { mutableStateOf(false) }
    var logLoading by remember(taskLog) { mutableStateOf(false) }
    var logLoadedOnce by remember(taskLog) { mutableStateOf(false) }

    LaunchedEffect(taskLog, pageIndex) {
        logLoading = true
        val nextPage = withContext(Dispatchers.Default) {
            taskCenterHttpLogPage(taskLog, pageIndex)
        }
        logEntries = if (pageIndex == 0) nextPage.entries else logEntries + nextPage.entries
        logHasNext = nextPage.hasNext
        logLoadedOnce = true
        logLoading = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item(key = "task-header") {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(asyncTaskTypeDisplayName(task.type), style = MaterialTheme.typography.titleMedium)
                Text("#${task.id} · ${asyncTaskStatusDisplayName(task.status)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (failureSuperseded) {
            item(key = "task-superseded") {
                TaskCenterDetailSection("状态说明", "这是历史失败记录，后续同类任务已经成功完成。")
            }
        }
        item(key = "task-time") {
            TaskCenterDetailSection(
                "时间",
                listOf(
                    "创建：${taskCenterTimestampText(task.created_at)}",
                    "开始：${taskCenterTimestampText(task.started_at)}",
                    "完成：${taskCenterTimestampText(task.finished_at)}",
                    "更新：${taskCenterTimestampText(task.updated_at)}",
                    "耗时：${taskCenterDurationText(task.started_at, task.finished_at ?: task.updated_at)}",
                ).joinToString("\n"),
            )
        }
        item(key = "task-progress") {
            TaskCenterDetailSection("进度", "${task.progress_current} / ${task.progress_total}\n${task.progress_message}")
        }
        item(key = "task-payload") { TaskCenterJsonSection("Payload", task.payload_json) }
        item(key = "task-checkpoint") { TaskCenterJsonSection("Checkpoint", task.checkpoint_json) }
        item(key = "task-result") { TaskCenterJsonSection("Result", task.result_json) }
        item(key = "task-error") {
            TaskCenterDetailSection("错误", listOf(task.last_error_code, task.last_error_message).filter { it.isNotBlank() }.joinToString("\n"))
        }
        item(key = "task-http-log-title") {
            Text("诊断日志", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (logEntries.isEmpty() && logLoading && taskLog.isNotBlank()) {
            item(key = "task-http-log-loading") {
                TaskCenterDetailSection("诊断日志", "日志加载中")
            }
        } else if (logEntries.isEmpty() && logLoadedOnce) {
            item(key = "task-http-log-empty") {
                TaskCenterDetailSection("诊断日志", taskLog.filterNot { it == '\u0000' }.ifBlank { "暂无日志" })
            }
        }
        itemsIndexed(logEntries, key = { index, _ -> "task-http-log-entry-$index" }) { index, entry ->
            TaskCenterHttpLogCard(index = index, entry = entry)
        }
        if (logHasNext || logLoading) {
            item(key = "task-http-log-load-more") {
                TaskCenterHttpLogLoadMoreRow(
                    loading = logLoading,
                    onLoadMore = {
                        if (!logLoading && logHasNext) pageIndex += 1
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskCenterJsonSection(title: String, body: String) {
    if (body.isBlank()) return
    var formatted by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val displayBody = if (formatted) taskCenterFormatJson(body) else body
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { formatted = !formatted }) {
                    Text(if (formatted) "原文" else "格式化")
                }
                if (displayBody.length > 1_200) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "展开")
                    }
                }
            }
            SelectionContainer {
                Text(
                    displayBody,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (expanded) Int.MAX_VALUE else 16,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TaskCenterHttpLogLoadMoreRow(
    loading: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect("task-center-http-log-load-more", loading) {
        if (!loading) onLoadMore()
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (loading) "日志加载中" else "加载更多日志",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskCenterHttpLogCard(index: Int, entry: TaskCenterHttpLogEntry) {
    var bodyViewerOpen by remember { mutableStateOf(false) }
    if (bodyViewerOpen) {
        TaskCenterHttpBodyViewer(
            title = "HTTP #${index + 1} · ${entry.label} Body",
            body = entry.responseBody,
            onClose = { bodyViewerOpen = false },
        )
    }
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("HTTP #${index + 1} · ${entry.label}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (entry.responseBody.isNotBlank()) {
                    TextButton(onClick = { bodyViewerOpen = true }) {
                        Text("查看 Body")
                    }
                }
            }
            if (entry.request.isNotBlank()) {
                TaskCenterHttpMetaGrid(
                    title = "HTTP 请求",
                    rows = listOf(
                        "Method" to entry.requestMethod,
                        "URL" to entry.requestUrl,
                    ).filter { it.second.isNotBlank() },
                )
                TaskCenterHttpParamsGrid(params = entry.requestParamRows)
            }
            if (entry.response.isNotBlank()) {
                TaskCenterHttpMetaGrid(
                    title = "HTTP 响应",
                    rows = listOf(
                        "Status" to entry.responseStatus,
                        "耗时" to entry.durationText,
                        "Headers" to entry.responseHeaders,
                    ).filter { it.second.isNotBlank() },
                )
                TaskCenterHttpBodySummary(body = entry.responseBody, onOpen = { bodyViewerOpen = true })
            }
        }
    }
}

@Composable
private fun TaskCenterHttpMetaGrid(title: String, rows: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 56.dp))
                SelectionContainer {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (label == "URL" || label == "Params") FontFamily.Monospace else null,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCenterHttpParamsGrid(params: List<Pair<String, String>>) {
    if (params.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Params", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        params.forEach { (key, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                SelectionContainer {
                    Text(
                        key,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.widthIn(min = 96.dp, max = 132.dp),
                    )
                }
                SelectionContainer {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCenterHttpBodySummary(body: String, onOpen: () -> Unit) {
    if (body.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Body · ${body.length} 字符",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpen) {
            Text("查看 Body")
        }
    }
}

@Composable
private fun TaskCenterHttpBodyViewer(title: String, body: String, onClose: () -> Unit) {
    val displayBody = remember(body) { taskCenterFormatJson(body) }
    val lines = remember(displayBody) { bodyDisplayLines(displayBody) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text("${body.length} 字符 · 默认格式化显示", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onClose) {
                        Text("关闭")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(lines) { line ->
                        SelectionContainer {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                softWrap = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun bodyDisplayLines(body: String): List<String> {
    val rawLines = body.lineSequence().toList().ifEmpty { listOf("") }
    return rawLines.flatMap { line ->
        if (line.length <= BODY_VIEWER_LINE_CHARS) {
            listOf(line)
        } else {
            line.chunked(BODY_VIEWER_LINE_CHARS)
        }
    }
}

private const val BODY_VIEWER_LINE_CHARS = 4_000
private const val HTTP_LOG_PAGE_SIZE = 50

@Composable
private fun TaskCenterDetailSection(title: String, body: String) {
    if (body.isBlank()) return
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal data class TaskCenterHttpLogEntry(
    val label: String,
    val request: String,
    val response: String,
) {
    private val requestDetail: String
        get() = request.substringAfter("] ", missingDelimiterValue = "")

    private val responseMeta: String
        get() = response.substringBefore(" body=", missingDelimiterValue = response)

    val requestMethod: String
        get() = requestDetail.substringBefore(" ", missingDelimiterValue = "").trim()

    val requestUrl: String
        get() = requestDetail
            .substringAfter(" ", missingDelimiterValue = "")
            .substringBefore(" params=", missingDelimiterValue = requestDetail.substringAfter(" ", missingDelimiterValue = ""))
            .trim()

    val requestParams: String
        get() = request.substringAfter(" params=", missingDelimiterValue = "").trim()

    val requestParamRows: List<Pair<String, String>>
        get() = requestParams
            .split('&')
            .mapNotNull { param ->
                val text = param.trim()
                if (text.isBlank()) return@mapNotNull null
                val key = text.substringBefore("=", missingDelimiterValue = text).trim()
                val value = text.substringAfter("=", missingDelimiterValue = "").trim()
                key to value
            }

    val responseStatus: String
        get() = responseMeta.substringAfter("status=", missingDelimiterValue = "").substringBefore(" headers=").trim()

    val responseHeaders: String
        get() = responseMeta.substringAfter(" headers=", missingDelimiterValue = "").trim()

    val responseBody: String
        get() = response.substringAfter(" body=", missingDelimiterValue = "").trim()

    val durationText: String
        get() = taskCenterStepDurationText(request, response)

    fun formattedResponse(): String {
        val body = responseBody
        if (body.isBlank()) return response
        val formatted = taskCenterFormatJson(body)
        return if (formatted == body) response else response.substringBefore(" body=") + " body=\n" + formatted
    }

    fun formattedResponseBody(): String = taskCenterFormatJson(responseBody)
}

internal data class TaskCenterHttpLogPage(
    val entries: List<TaskCenterHttpLogEntry>,
    val pageIndex: Int,
    val pageSize: Int,
    val totalEntries: Int?,
    val hasNextPage: Boolean,
) {
    val startIndex: Int
        get() = if (entries.isEmpty()) 0 else pageIndex * pageSize

    val endIndex: Int
        get() = startIndex + entries.size

    val displayRangeText: String
        get() = if (totalEntries != null) {
            "第 ${startIndex + 1}-${endIndex} / 共 $totalEntries 条"
        } else if (hasNext) {
            "第 ${startIndex + 1}-${endIndex} 条，后面还有"
        } else {
            "第 ${startIndex + 1}-${endIndex} 条"
        }

    val hasPrevious: Boolean
        get() = pageIndex > 0

    val hasNext: Boolean
        get() = hasNextPage
}

internal fun taskCenterHttpLogEntries(taskLog: String): List<TaskCenterHttpLogEntry> {
    val pending = mutableListOf<MutableTaskCenterHttpLogEntry>()
    val completed = mutableListOf<MutableTaskCenterHttpLogEntry>()
    var activeResponse: MutableTaskCenterHttpLogEntry? = null

    taskLog.filterNot { it == '\u0000' }.lineSequence().forEach { line ->
        when {
            line.contains("HTTP request [") -> {
                activeResponse = null
                pending += MutableTaskCenterHttpLogEntry(
                    label = line.substringAfter("HTTP request [").substringBefore("]"),
                    request = line,
                    response = "",
                )
            }
            line.contains("HTTP response [") -> {
                val responseLabel = line.substringAfter("HTTP response [").substringBefore("]")
                val responseExternalId = taskCenterHttpExternalId(line)
                val matchIndex = pending.indexOfFirst { entry ->
                    entry.label == responseLabel &&
                        responseExternalId.isNotBlank() &&
                        taskCenterHttpExternalId(entry.request) == responseExternalId
                }.takeIf { it >= 0 } ?: pending.indexOfFirst { it.label == responseLabel }
                val entry = if (matchIndex >= 0) {
                    pending.removeAt(matchIndex)
                } else {
                    MutableTaskCenterHttpLogEntry(responseLabel, "", "")
                }
                entry.response = line
                completed += entry
                activeResponse = entry
            }
            activeResponse != null && !taskCenterIsLifecycleLogLine(line) -> {
                activeResponse?.response += "\n$line"
            }
            pending.isNotEmpty() -> {
                pending.last().request += "\n$line"
            }
        }
    }
    return (completed + pending)
        .map { TaskCenterHttpLogEntry(it.label.ifBlank { "request" }, it.request, it.response) }
        .sortedBy { entry ->
            taskCenterLogLineInstant(entry.request)
                ?: taskCenterLogLineInstant(entry.response)
                ?: Instant.DISTANT_FUTURE
        }
}

private data class MutableTaskCenterHttpLogEntry(
    val label: String,
    var request: String,
    var response: String,
)

private data class PendingTaskCenterHttpLogEntry(
    val label: String,
    val externalId: String,
    val pageEntry: MutableTaskCenterHttpLogEntry?,
)

private fun taskCenterHttpExternalId(line: String): String =
    line.substringAfter("externalId=", missingDelimiterValue = "")
        .substringBefore("&")
        .substringBefore(",")
        .substringBefore(" ")
        .substringBefore("\n")
        .trim()

internal fun taskCenterHttpLogPage(
    taskLog: String,
    pageIndex: Int,
    pageSize: Int = HTTP_LOG_PAGE_SIZE,
): TaskCenterHttpLogPage {
    val safePageSize = pageSize.coerceAtLeast(1)
    val requestedPageIndex = pageIndex.coerceAtLeast(0)
    return taskCenterHttpLogPageUnchecked(taskLog, requestedPageIndex, safePageSize)
}

private fun taskCenterHttpLogPageUnchecked(
    taskLog: String,
    pageIndex: Int,
    pageSize: Int,
): TaskCenterHttpLogPage {
    val startIndex = pageIndex * pageSize
    val endExclusive = startIndex + pageSize
    val pending = mutableListOf<PendingTaskCenterHttpLogEntry>()
    val pageEntries = mutableListOf<MutableTaskCenterHttpLogEntry>()
    var activeResponse: MutableTaskCenterHttpLogEntry? = null
    var activeRequest: MutableTaskCenterHttpLogEntry? = null
    var totalEntries = 0
    var hasNextPage = false

    run loop@{
        taskLog.filterNot { it == '\u0000' }.lineSequence().forEach { line ->
        when {
            line.contains("HTTP request [") -> {
                if (totalEntries >= endExclusive) {
                    hasNextPage = true
                    return@loop
                }
                activeResponse = null
                val selected = totalEntries in startIndex until endExclusive
                val pageEntry = if (selected) {
                    MutableTaskCenterHttpLogEntry(
                        label = line.substringAfter("HTTP request [").substringBefore("]"),
                        request = line,
                        response = "",
                    ).also { pageEntries += it }
                } else {
                    null
                }
                pending += PendingTaskCenterHttpLogEntry(
                    label = line.substringAfter("HTTP request [").substringBefore("]"),
                    externalId = taskCenterHttpExternalId(line),
                    pageEntry = pageEntry,
                )
                activeRequest = pageEntry
                totalEntries += 1
            }
            line.contains("HTTP response [") -> {
                activeRequest = null
                val responseLabel = line.substringAfter("HTTP response [").substringBefore("]")
                val responseExternalId = taskCenterHttpExternalId(line)
                val matchIndex = pending.indexOfFirst { entry ->
                    entry.label == responseLabel &&
                        responseExternalId.isNotBlank() &&
                        entry.externalId == responseExternalId
                }.takeIf { it >= 0 } ?: pending.indexOfFirst { it.label == responseLabel }
                val entry = if (matchIndex >= 0) pending.removeAt(matchIndex).pageEntry else null
                entry?.response = line
                activeResponse = entry
            }
            activeResponse != null && !taskCenterIsLifecycleLogLine(line) -> {
                activeResponse?.response += "\n$line"
            }
            activeRequest != null -> {
                activeRequest?.request += "\n$line"
            }
        }
        }
    }

    return TaskCenterHttpLogPage(
        entries = pageEntries.map { TaskCenterHttpLogEntry(it.label.ifBlank { "request" }, it.request, it.response) },
        pageIndex = pageIndex,
        pageSize = pageSize,
        totalEntries = null,
        hasNextPage = hasNextPage,
    )
}

private fun taskCenterIsLifecycleLogLine(line: String): Boolean =
    Regex("""^\S+(?:\s+\S+)?\s+TASK\s+""").containsMatchIn(line)

internal fun taskCenterStepDurationText(startLine: String, endLine: String): String {
    val startedAt = taskCenterLogLineInstant(startLine) ?: return "未完成"
    val endedAt = taskCenterLogLineInstant(endLine) ?: return "未完成"
    val millis = endedAt.toEpochMilliseconds() - startedAt.toEpochMilliseconds()
    if (millis < 0L) return "未完成"
    return taskCenterDurationTextFromMillis(millis)
}

private fun taskCenterLogLineInstant(line: String): Instant? =
    line.substringBefore(" ", missingDelimiterValue = "").trim().takeIf { it.isNotBlank() }?.let { token ->
        runCatching { Instant.parse(token) }.getOrNull()
    }

private fun taskCenterDurationTextFromMillis(millis: Long): String {
    if (millis < 1_000L) return "${millis}ms"
    if (millis < 60_000L) return "${(millis / 100L) / 10.0}s"
    val seconds = millis / 1_000L
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    if (minutes < 60L) return "${minutes}m ${remainingSeconds}s"
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return "${hours}h ${remainingMinutes}m"
}

private fun taskCenterFormatJson(body: String): String =
    runCatching {
        taskCenterPrettyJson.encodeToString(Json.parseToJsonElement(body))
    }.getOrDefault(body)

private fun taskCenterListSummary(task: AsyncTaskListItem): String {
    task.lastErrorMessage.takeIf { task.status == AsyncTaskStatus.failed.name && it.isNotBlank() }?.let { return it }
    val base = task.progressMessage.ifBlank { asyncTaskStatusDisplayName(task.status) }
    val itemsSeen = taskCenterCheckpointLong(task.checkpointJson, "itemsSeen")
    if (task.type == AsyncTaskType.external_favorite_sync.name && itemsSeen != null && base.contains("完成")) {
        return "$base，读取到 ${itemsSeen} 条"
    }
    return base
}

private fun taskCenterCheckpointLong(json: String, key: String): Long? =
    Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)""")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

private fun taskCenterTypeFilterSummary(selected: Set<String>): String =
    if (selected.isEmpty()) {
        "全部"
    } else {
        selected.joinToString("、") { asyncTaskTypeDisplayName(it) }
    }

private fun taskCenterStatusFilterSummary(selected: Set<String>): String =
    if (selected.isEmpty()) {
        "全部"
    } else {
        selected.joinToString("、") { asyncTaskStatusDisplayName(it) }
    }

private fun taskCenterTimestampText(value: Long?): String {
    if (value == null || value <= 0L) return "未开始"
    val time = Instant.fromEpochMilliseconds(value).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${time.monthNumber.toString().padStart(2, '0')}-${time.dayOfMonth.toString().padStart(2, '0')} " +
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

private fun taskCenterDurationText(startedAt: Long?, endedAt: Long?): String {
    if (startedAt == null || startedAt <= 0L || endedAt == null || endedAt < startedAt) return "未开始"
    val seconds = ((endedAt - startedAt) / 1000L).coerceAtLeast(0L)
    if (seconds < 60L) return "${seconds}s"
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    if (minutes < 60L) return "${minutes}m ${remainingSeconds}s"
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return "${hours}h ${remainingMinutes}m"
}
