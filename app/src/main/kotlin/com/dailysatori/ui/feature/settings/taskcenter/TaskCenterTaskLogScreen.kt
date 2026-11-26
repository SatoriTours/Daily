package com.dailysatori.ui.feature.settings.taskcenter

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.shared.db.Async_task
import com.dailysatori.ui.component.appbar.PageAction
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.ui.theme.editorial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val INFO_PREVIEW_CHARS = 140

/**
 * Filter action for the page app bar: the filter lives next to search in the navigation bar
 * (SettingsScaffold merges it with the built-in search action), never in its own content row.
 */
internal fun taskCenterLogFilterAction(
    filter: TaskCenterLogFilter,
    allLabel: String,
    abnormalLabel: String,
    filterLabelFormat: String,
    onSelect: (TaskCenterLogFilter) -> Unit,
): PageAction = PageAction(
    label = String.format(filterLabelFormat, if (filter == TaskCenterLogFilter.All) allLabel else abnormalLabel),
    icon = Icons.Default.FilterList,
    alwaysVisible = true,
    menuContent = { dismiss ->
        TaskCenterLogFilter.entries.forEach { value ->
            DropdownMenuItem(
                text = { Text(if (value == TaskCenterLogFilter.All) allLabel else abnormalLabel) },
                leadingIcon = { if (value == filter) Icon(Icons.Default.Check, contentDescription = null) },
                onClick = {
                    dismiss()
                    onSelect(value)
                },
            )
        }
    },
    onClick = {},
)

/**
 * Full log page: lifecycle events, real request rows, standalone responses and any other timed log
 * line on one timeline, grouped by date and newest first by default. Only request rows open a detail.
 */
@Composable
internal fun TaskCenterTaskLogScreen(
    task: Async_task,
    title: String,
    source: String?,
    taskLog: String,
    httpLog: String,
    query: String,
    filter: TaskCenterLogFilter,
    modifier: Modifier = Modifier,
) {
    val copy = taskCenterCopy()
    val strings = copy.strings
    var sort by rememberSaveable(task.id) { mutableStateOf(TaskCenterLogSort.Newest) }
    var parsed by remember(task.id) { mutableStateOf<TaskCenterHttpLogParse?>(null) }
    var captured by remember(task.id) { mutableStateOf(emptyList<TaskCenterRequestDetail>()) }
    var loading by remember(task.id) { mutableStateOf(true) }
    var unreadableLines by remember(task.id) { mutableStateOf(0) }
    var openRequest by remember(task.id) { mutableStateOf<TaskCenterRequestDetail?>(null) }
    val events = remember(taskLog, copy) { taskCenterLifecycleEvents(taskLog, strings) }
    LaunchedEffect(taskLog, httpLog, task.id) {
        loading = true
        val loaded = withContext(Dispatchers.Default) {
            val capture = taskCenterCaptureRead(httpLog)
            val projected = if (capture.requests.isNotEmpty()) {
                taskCenterCapturedRequestDetails(capture.requests)
            } else {
                emptyList()
            }
            Triple(taskCenterHttpLogParse(taskLog), projected, capture.unreadableLines)
        }
        parsed = loaded.first
        captured = loaded.second
        unreadableLines = loaded.third
        loading = false
    }
    val entries = remember(parsed, captured, copy.requestFallbackTitle) {
        parsed?.let { taskCenterLogEntries(events, it, copy.requestFallbackTitle, captured) }.orEmpty()
    }
    val groups = remember(entries, filter, sort, query) {
        taskCenterLogGroups(entries, filter, sort, query)
    }
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date

    openRequest?.let { detail ->
        TaskCenterRequestScreen(
            task = task,
            detail = detail,
            attempts = if (captured.isNotEmpty()) captured else entries.mapNotNull { it.request },
            onOpenAttempt = { openRequest = it },
            onClose = { openRequest = null },
        )
    }

    Column(modifier.fillMaxSize()) {
        TaskCenterLogIntro(title = title, source = source, taskId = task.id, sort = sort, onSort = { sort = it })
        when {
            loading -> Text(
                copy.logLoading,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
            groups.isEmpty() -> TaskCenterNote(
                if (query.isBlank() && filter == TaskCenterLogFilter.All) copy.logEmpty else copy.logEmptyFiltered,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.l),
            ) {
                groups.forEach { group ->
                    item(key = "task-log-date-" + (group.date ?: "untimed")) {
                        TaskCenterLogDateHeader(group, today)
                    }
                    items(group.entries, key = { it.key }) { entry ->
                        TaskCenterLogRow(entry) { request -> openRequest = request }
                    }
                }
                item(key = "task-log-hint") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        if (entries.any { it.request != null }) {
                            Text(
                                copy.logHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (unreadableLines > 0) {
                            Text(
                                String.format(copy.unreadableLines, unreadableLines),
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
private fun TaskCenterLogIntro(
    title: String,
    source: String?,
    taskId: Long,
    sort: TaskCenterLogSort,
    onSort: (TaskCenterLogSort) -> Unit,
) {
    val copy = taskCenterCopy()
    val meta = listOfNotNull(source?.takeIf { it.isNotBlank() }, "#" + taskId).joinToString(" · ")
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(start = Spacing.m, end = Spacing.s, top = Spacing.xs, bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.editorial(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TaskCenterLogSortMenu(sort, onSort)
        }
        Text(
            meta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.xs),
        )
    }
}

@Composable
private fun TaskCenterLogSortMenu(sort: TaskCenterLogSort, onSelect: (TaskCenterLogSort) -> Unit) {
    val copy = taskCenterCopy()
    var expanded by remember { mutableStateOf(false) }
    val arrow = if (sort == TaskCenterLogSort.Newest) " ↓" else " ↑"
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                (if (sort == TaskCenterLogSort.Newest) copy.logSortNewest else copy.logSortOldest) + arrow,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TaskCenterLogSort.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(if (value == TaskCenterLogSort.Newest) copy.logSortNewest else copy.logSortOldest) },
                    leadingIcon = { if (value == sort) Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskCenterLogDateHeader(group: TaskCenterLogGroup, today: LocalDate) {
    val copy = taskCenterCopy()
    val label = group.date?.let { taskCenterLogDateText(it, today, copy.logDateToday, copy.logDate) } ?: copy.logNoTime
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            String.format(copy.logCount, group.entries.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskCenterLogRow(entry: TaskCenterLogEntry, onOpenRequest: (TaskCenterRequestDetail) -> Unit) {
    val copy = taskCenterCopy()
    val request = entry.request
    val responseOnly = request == null && entry.responseStatus.isNotBlank()
    val expandable = request == null && !responseOnly && entry.title.length > INFO_PREVIEW_CHARS
    var expanded by remember(entry.key) { mutableStateOf(false) }
    val title = when {
        request != null -> entry.title
        responseOnly -> when (entry.source) {
            TaskCenterRecordSource.BusinessResult ->
                String.format(copy.logResponseOnlyBusiness, entry.responseStatus.ifBlank { copy.responseMissing })
            TaskCenterRecordSource.Unknown ->
                String.format(copy.logResponseOnlyUnknown, entry.responseStatus.ifBlank { copy.responseMissing })
            TaskCenterRecordSource.Http ->
                String.format(copy.logResponseOnlyUnlinked, entry.responseStatus.ifBlank { copy.responseMissing })
        }
        else -> entry.title
    }
    val rowModifier = if (request == null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().clickable { onOpenRequest(request) }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = rowModifier.padding(start = Spacing.m, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                taskCenterLogClockText(entry.atMs).ifBlank { "—" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(Spacing.xxl).padding(top = Spacing.xxs),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expandable && !expanded) 4 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
                if (request != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        TaskCenterRequestOutcomePill(request)
                        Text(
                            requestSubtitle(request, copy),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.abnormal) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                if (expandable) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = Spacing.xs),
                    ) {
                        Text(
                            if (expanded) copy.detailCollapse else copy.detailExpand,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (request != null) {
                Text(
                    "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.m + Spacing.xxl + Spacing.s, end = Spacing.m)
                .height(BorderWidth.xs)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

private fun requestSubtitle(request: TaskCenterRequestDetail, copy: TaskCenterCopy): String = buildList {
    request.durationMs?.let { add(taskCenterDurationText(it, copy.strings)) }
    add(
        when (request.source) {
            TaskCenterRecordSource.BusinessResult -> copy.logBusinessHint
            TaskCenterRecordSource.Unknown -> copy.logUnknownHint
            TaskCenterRecordSource.Http -> copy.logRequestHint
        },
    )
}.joinToString(" · ")
