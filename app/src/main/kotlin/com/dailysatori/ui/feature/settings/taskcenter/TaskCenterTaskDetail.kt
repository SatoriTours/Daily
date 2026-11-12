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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.core.content.FileProvider
import com.dailysatori.core.diagnostics.TaskDiagnosticsExportService
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dailysatori.R
import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.asyncTaskStatusDisplayName
import com.dailysatori.service.diagnostics.TaskHttpCaptureState
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.shared.db.Async_task
import com.dailysatori.ui.component.indicator.AttentionReason
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val REQUEST_PAGE_SIZE = 20

@Composable
internal fun TaskCenterTaskDetail(
    task: Async_task,
    title: String,
    taskLog: String,
    failureSuperseded: Boolean,
    needsAttention: Boolean,
    onAcknowledge: () -> Unit,
    onResumeAssistant: () -> Unit,
    onCancel: () -> Unit,
    assistantResumeBlocked: Boolean,
    httpLog: String,
    captureState: TaskHttpCaptureState,
    onArmCapture: () -> Unit,
    onDisarmCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val i18n: I18nService = koinInject()
    val copy = taskCenterCopy()
    val strings = copy.strings
    var showDiagnostics by remember(task.id) { mutableStateOf(false) }
    var requestLimit by remember(task.id) { mutableStateOf(REQUEST_PAGE_SIZE) }
    var details by remember(task.id) { mutableStateOf(emptyList<TaskCenterRequestDetail>()) }
    var detailsLoading by remember(task.id) { mutableStateOf(true) }
    var unreadableLines by remember(task.id) { mutableStateOf(0) }
    var openRequest by remember(task.id) { mutableStateOf<TaskCenterRequestDetail?>(null) }
    val events = remember(taskLog) { taskCenterLifecycleEvents(taskLog, strings) }
    LaunchedEffect(taskLog, httpLog, task.id) {
        detailsLoading = true
        val loaded = withContext(Dispatchers.Default) {
            val capture = taskCenterCaptureRead(httpLog)
            val projected = if (capture.requests.isNotEmpty()) {
                taskCenterCapturedRequestDetails(capture.requests)
            } else {
                taskCenterRequestDetails(taskLog)
            }
            projected to capture.unreadableLines
        }
        details = loaded.first
        unreadableLines = loaded.second
        detailsLoading = false
    }

    openRequest?.let { detail ->
        TaskCenterRequestScreen(
            task = task,
            detail = detail,
            attempts = details,
            onOpenAttempt = { openRequest = it },
            onClose = { openRequest = null },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item(key = "task-header") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    TaskCenterStatusPill(task.status)
                    Text(
                        "#${task.id} · ${asyncTaskStatusDisplayName(task.status)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "#${task.id} · ${taskCenterWhenText(task.created_at, copy)} · " +
                        copy.detailLastUpdate + " " + taskCenterWhenText(task.updated_at, copy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "task-status-card") { TaskCenterStatusCard(task) }
        if (needsAttention && task.status == AsyncTaskStatus.failed.name) {
            item(key = "task-attention") {
                Column(Modifier.padding(horizontal = Spacing.m)) {
                    AttentionReason(
                        taskCenterFailureText(task.last_error_code, task.last_error_message) { i18n.t(it) },
                        i18n.t("attention.acknowledge"),
                        onAcknowledge,
                    )
                }
            }
        }
        if (failureSuperseded) {
            item(key = "task-superseded") { TaskCenterNote(copy.detailSuperseded) }
        }
        if (!failureSuperseded && task.type in setOf("assistant_index", "assistant_summary", "assistant_history_backfill") &&
            task.status in setOf("failed", "cancelled")
        ) {
            item(key = "assistant-background-continue") {
                Column(Modifier.padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        stringResource(R.string.assistant_background_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (assistantResumeBlocked) {
                        Text(
                            stringResource(R.string.assistant_background_blocked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = onResumeAssistant) {
                        Text(stringResource(R.string.assistant_background_continue))
                    }
                }
            }
        }
        item(key = "task-timeline-title") { TaskCenterSectionTitle(copy.detailTimeline) }
        if (events.isEmpty()) {
            item(key = "task-timeline-empty") { TaskCenterNote(copy.detailTimelineEmpty) }
        } else {
            itemsIndexed(events, key = { index, _ -> "task-event-${index}" }) { index, event ->
                TaskCenterTimelineRow(event = event, last = index == events.lastIndex)
            }
        }
        item(key = "task-timing") { TaskCenterTimingRows(task) }
        item(key = "task-capture") {
            TaskCenterCaptureCard(
                task = task,
                captureState = captureState,
                recordCount = taskCenterCaptureRead(httpLog).requests.size,
                onArm = onArmCapture,
                onDisarm = onDisarmCapture,
            )
        }
        item(key = "task-requests-title") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskCenterSectionTitle(copy.detailRequests, modifier = Modifier.weight(1f))
                if (unreadableLines > 0) {
                    Text(
                        String.format(copy.unreadableLines, unreadableLines),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (details.isNotEmpty()) {
                    Text(
                        String.format(copy.detailRequestsCount, details.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (detailsLoading) {
            item(key = "task-requests-loading") { TaskCenterNote(copy.detailRequestsLoading) }
        } else if (details.isEmpty()) {
            item(key = "task-requests-empty") {
                TaskCenterNote(
                    when (taskCenterRequestEmptyKind(taskLog)) {
                        TaskCenterRequestEmptyKind.NoLog -> copy.requestsEmptyLog
                        TaskCenterRequestEmptyKind.NoHttpLines -> copy.requestsEmptyNoCapture
                    },
                )
            }
        } else {
            val visible = details.take(requestLimit)
            itemsIndexed(visible, key = { index, _ -> "task-request-${index}" }) { _, detail ->
                TaskCenterRequestRow(detail) { openRequest = detail }
            }
            if (details.size > visible.size) {
                item(key = "task-requests-more") {
                    TextButton(
                        onClick = { requestLimit += REQUEST_PAGE_SIZE },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
                    ) {
                        Text(String.format(copy.detailRequestsMore, visible.size, details.size))
                    }
                }
            }
        }
        item(key = "task-export") { TaskCenterExportAction(task) }
        item(key = "task-diagnostics-toggle") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskCenterSectionTitle(copy.detailDiagnostics, modifier = Modifier.weight(1f))
                TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                    Text(if (showDiagnostics) copy.detailCollapse else copy.detailExpand)
                }
            }
        }
        if (showDiagnostics) {
            item(key = "task-payload") {
                Column(Modifier.padding(horizontal = Spacing.m)) {
                    TaskCenterPayloadViewer(
                        title = copy.detailPayload,
                        body = task.payload_json,
                        emptyText = copy.detailPayloadEmpty,
                        uncollectedText = copy.viewerNotCollectedNote,
                    )
                }
            }
            item(key = "task-checkpoint") {
                Column(Modifier.padding(horizontal = Spacing.m)) {
                    TaskCenterPayloadViewer(
                        title = copy.detailCheckpoint,
                        body = task.checkpoint_json,
                        emptyText = copy.detailCheckpointEmpty,
                        uncollectedText = copy.viewerNotCollectedNote,
                    )
                }
            }
            item(key = "task-result") {
                Column(Modifier.padding(horizontal = Spacing.m)) {
                    TaskCenterPayloadViewer(
                        title = copy.detailResult,
                        body = task.result_json,
                        emptyText = copy.detailResultEmpty,
                        uncollectedText = copy.viewerNotCollectedNote,
                    )
                }
            }
            item(key = "task-error") {
                Column(Modifier.padding(horizontal = Spacing.m)) {
                    val error = listOf(task.last_error_code, task.last_error_message)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    TaskCenterFieldSection(copy.detailError, error.ifBlank { copy.detailErrorEmpty })
                }
            }
            item(key = "task-diagnostics-log") {
                Column(Modifier.padding(horizontal = Spacing.m)) {
                    TaskCenterPayloadViewer(
                        title = copy.detailLog,
                        body = taskLog.filterNot { it == '\u0000' },
                        emptyText = copy.detailLogEmpty,
                        uncollectedText = copy.viewerNotCollectedNote,
                        maxHeight = 420.dp,
                    )
                }
            }
        }
        if (taskCenterIsActive(task.status)) {
            item(key = "task-cancel-action") {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                ) {
                    Text(copy.detailCancel)
                }
            }
        }
    }
}

@Composable
private fun TaskCenterStatusCard(task: Async_task) {
    val copy = taskCenterCopy()
    val text = taskCenterRowText(
        AsyncTaskListItem(
            id = task.id,
            type = task.type,
            status = task.status,
            progressCurrent = task.progress_current,
            progressTotal = task.progress_total,
            progressMessage = task.progress_message,
            checkpointJson = task.checkpoint_json,
            createdAt = task.created_at,
            startedAt = task.started_at,
            finishedAt = task.finished_at,
            updatedAt = task.updated_at,
            lastErrorMessage = task.last_error_message,
            runAfterMs = task.run_after_ms,
            attemptCount = task.attempt_count,
            payloadJson = task.payload_json,
        ),
        copy.strings,
    )
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            // Header carries the task name; the status card leads with the state so the current
            // stage is not printed twice (headline + summary both showing the progress message).
            Text(
                when (task.status) {
                    AsyncTaskStatus.failed.name, AsyncTaskStatus.retrying.name ->
                        text.headline.ifBlank { asyncTaskStatusDisplayName(task.status) }
                    else -> asyncTaskStatusDisplayName(task.status)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            val detail = taskCenterExecutionSummary(task, copy.strings)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val extra = text.detail.takeIf { it.isNotBlank() && it != detail }
            if (extra != null) {
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TaskCenterTimelineRow(event: TaskCenterLifecycleEvent, last: Boolean) {
    val copy = taskCenterCopy()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
        )
        Text(
            event.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            event.atMs?.let { taskCenterTimestampText(it, copy.strings, includeSeconds = true) } ?: copy.unknown,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskCenterTimingRows(task: Async_task) {
    val copy = taskCenterCopy()
    val strings = copy.strings
    val finishedAt = task.finished_at
    val queued = (task.started_at ?: task.updated_at) - task.created_at
    val elapsed = (finishedAt ?: task.updated_at) - (task.started_at ?: task.created_at)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        TaskCenterKeyValueRow(copy.detailQueuedAt, taskCenterTimestampText(task.created_at, strings, includeSeconds = true))
        TaskCenterKeyValueRow(copy.detailStartedAt, taskCenterTimestampText(task.started_at, strings, includeSeconds = true))
        TaskCenterKeyValueRow(
            copy.detailFinishedAt,
            if (finishedAt != null && finishedAt > 0L) {
                taskCenterTimestampText(finishedAt, strings, includeSeconds = true)
            } else {
                copy.detailNotFinished
            },
        )
        TaskCenterKeyValueRow(copy.detailQueueTime, if (queued >= 0) taskCenterDurationText(queued, strings) else strings.unknown)
        // updated_at is the last progress write, not "now": the label must not imply live elapsed time.
        TaskCenterKeyValueRow(
            if (task.finished_at == null) copy.detailRunningProgress else copy.detailTotalTime,
            if (elapsed >= 0) taskCenterDurationText(elapsed, strings) else strings.unknown,
        )
        TaskCenterKeyValueRow(copy.detailLastUpdate, taskCenterWhenText(task.updated_at, copy))
    }
}

@Composable
private fun TaskCenterKeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TaskCenterSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(horizontal = Spacing.m, vertical = Spacing.xxs),
    )
}

@Composable
private fun TaskCenterNote(text: String) {
    Surface(
        shape = RoundedCornerShape(Radius.s),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
    }
}

@Composable
private fun TaskCenterFieldSection(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TaskCenterCodeBlock(body)
    }
}

@Composable
private fun TaskCenterCodeBlock(text: String) {
    Surface(
        shape = RoundedCornerShape(Radius.s),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                softWrap = true,
                modifier = Modifier.fillMaxWidth().padding(Spacing.s),
            )
        }
    }
}

@Composable
private fun TaskCenterRequestRow(detail: TaskCenterRequestDetail, onOpen: () -> Unit) {
    val copy = taskCenterCopy()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                taskCenterRequestTitle(detail),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TaskCenterResponseBadge(detail)
        }
        Text(
            listOfNotNull(
                detail.method.takeIf { it.isNotBlank() },
                detail.baseUrl.takeIf { it.isNotBlank() },
            ).joinToString(" "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildString {
                append(detail.durationLabel(copy.strings))
                detail.requestedAtMs?.let { append(" · " + taskCenterTimestampText(it, copy.strings, includeSeconds = true)) }
                if (detail.responseIncomplete) append(" · " + copy.badgeInterrupted)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (detail.responseIncomplete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskCenterResponseBadge(detail: TaskCenterRequestDetail) {
    val copy = taskCenterCopy()
    val (label, color) = when {
        detail.httpOk == false -> String.format(copy.responseFailed, detail.responseStatus) to MaterialTheme.colorScheme.error
        detail.responseIncomplete -> copy.badgeInterrupted to MaterialTheme.colorScheme.error
        detail.httpOk == true -> String.format(copy.responseOk, detail.responseStatus) to MaterialTheme.colorScheme.primary
        else -> copy.badgeNoResponse to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
    )
}

private enum class RequestTab { Request, Response, ErrorTiming }

@Composable
private fun TaskCenterRequestScreen(
    task: Async_task,
    detail: TaskCenterRequestDetail,
    attempts: List<TaskCenterRequestDetail>,
    onOpenAttempt: (TaskCenterRequestDetail) -> Unit,
    onClose: () -> Unit,
) {
    val copy = taskCenterCopy()
    val strings = copy.strings
    var tab by remember(detail) { mutableStateOf(RequestTab.Request) }
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            taskCenterRequestTitle(detail),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "#${task.id} · ${detail.method} ${detail.baseUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(
                                    if (detail.responseIncomplete) {
                                        String.format(copy.responseIncomplete, detail.responseStatus.ifBlank { copy.responseMissing })
                                    } else if (detail.httpOk == false) {
                                        String.format(copy.responseFailed, detail.responseStatus)
                                    } else {
                                        String.format(copy.responseOk, detail.responseStatus.ifBlank { copy.responseMissing })
                                    },
                                )
                                append(" · " + detail.durationLabel(strings))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (detail.httpOk == false || detail.responseIncomplete) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    TextButton(onClick = onClose) { Text(copy.close) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    RequestTab.entries.forEach { value ->
                        val label = when (value) {
                            RequestTab.Request -> copy.requestTabRequest
                            RequestTab.Response -> copy.requestTabResponse
                            RequestTab.ErrorTiming -> copy.requestTabError
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { tab = value }
                                .padding(vertical = Spacing.s),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (tab == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(
                                        if (tab == value) MaterialTheme.colorScheme.primary
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                    ),
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    when (tab) {
                        RequestTab.Request -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(copy.requestAddress, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { clipboard.setText(AnnotatedString(detail.fullUrl)) }) {
                                            Text(copy.requestCopyUrl)
                                        }
                                    }
                                    TaskCenterCodeBlock(
                                        if (detail.fullUrl.isBlank()) {
                                            detail.method.ifBlank { "GET" } + " · " + copy.missingUrl
                                        } else {
                                            "${detail.method.ifBlank { "GET" }} ${detail.fullUrl}"
                                        },
                                    )
                                }
                            }
                            item { TaskCenterPairSection(copy.requestQuery, detail.queryRows, copy.requestNoQuery) }
                            item {
                                TaskCenterPairSection(
                                    title = String.format(copy.requestHeaders, detail.requestHeaders.size),
                                    rows = detail.requestHeaders,
                                    emptyText = copy.requestHeadersAbsent,
                                )
                            }
                            item {
                                TaskCenterPayloadViewer(
                                    title = copy.requestBody,
                                    body = detail.requestBody.text,
                                    emptyText = copy.requestHeadersAbsent,
                                    uncollectedText = copy.viewerNotCollectedNote,
                                    maxHeight = 360.dp,
                                )
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(taskCenterRequestCopyText(detail))) }) {
                                        Text(copy.requestCopy)
                                    }
                                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(taskCenterCurlCommand(detail))) }) {
                                        Text(copy.requestCopyCurl)
                                    }
                                }
                            }
                        }
                        RequestTab.Response -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(
                                        when {
                                            detail.responseIncomplete -> copy.responseIncompleteTitle
                                            detail.httpOk == false -> String.format(copy.responseFailed, detail.responseStatus)
                                            detail.httpOk == true -> String.format(copy.responseOk, detail.responseStatus)
                                            else -> copy.responseMissing
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (detail.httpOk == false || detail.responseIncomplete) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        String.format(copy.responseTiming, detail.durationLabel(strings)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            item {
                                TaskCenterPairSection(
                                    title = String.format(copy.responseHeaders, detail.responseHeaders.size),
                                    rows = detail.responseHeaders,
                                    emptyText = copy.responseHeadersAbsent,
                                )
                            }
                            val stream = detail.stream
                            if (stream != null) {
                                if (stream.incomplete) {
                                    item { TaskCenterNote(copy.responseIncompleteNote) }
                                }
                                itemsIndexed(stream.chunks, key = { index, _ -> "sse-${index}" }) { index, chunk ->
                                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Text(
                                            String.format(copy.streamChunk, index + 1, stream.chunks.size) +
                                                if (chunk.done) " · " + copy.streamDone else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (chunk.pretty != null) {
                                            TaskCenterCodeBlock(chunk.pretty)
                                        } else {
                                            TaskCenterCodeBlock(chunk.raw)
                                            chunk.parseErrorMessage?.let { message ->
                                                Text(
                                                    if (message == TASK_CENTER_SSE_INVALID) {
                                                        copy.streamInvalidGeneric
                                                    } else {
                                                        String.format(copy.streamInvalid, message)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    TaskCenterPayloadViewer(
                                        title = copy.responseBody,
                                        body = detail.responseBody.text,
                                        emptyText = copy.responseHeadersAbsent,
                                        uncollectedText = copy.viewerNotCollectedNote,
                                        maxHeight = 360.dp,
                                    )
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(taskCenterRedactSensitive(detail.responseBody.text)))
                                        },
                                    ) {
                                        Text(copy.responseCopy)
                                    }
                                    TextButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(taskCenterDiagnosticText(task, detail, copy)))
                                        },
                                    ) {
                                        Text(copy.responseCopyDiagnostic)
                                    }
                                }
                            }
                        }
                        RequestTab.ErrorTiming -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(copy.detailError, style = MaterialTheme.typography.titleSmall)
                                    val error = listOf(task.last_error_code, task.last_error_message)
                                        .filter { it.isNotBlank() }
                                        .joinToString("\n")
                                    TaskCenterCodeBlock(error.ifBlank { copy.errorEmpty })
                                }
                            }
                            if (detail.errorLines.isNotEmpty()) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Text(copy.errorCaptured, style = MaterialTheme.typography.titleSmall)
                                        TaskCenterCodeBlock(detail.errorLines.joinToString("\n"))
                                    }
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(copy.errorTiming, style = MaterialTheme.typography.titleSmall)
                                    TaskCenterKeyValueRow(copy.detailQueuedAt, taskCenterTimestampText(task.created_at, strings, includeSeconds = true))
                                    TaskCenterKeyValueRow(copy.errorRequestedAt, taskCenterTimestampText(detail.requestedAtMs, strings, includeSeconds = true))
                                    TaskCenterKeyValueRow(copy.errorRespondedAt, taskCenterTimestampText(detail.respondedAtMs, strings, includeSeconds = true))
                                    TaskCenterKeyValueRow(copy.errorNetworkTime, detail.durationLabel(strings))
                                    val queued = detail.requestedAtMs?.let { it - task.created_at }
                                    TaskCenterKeyValueRow(
                                        copy.errorQueueTime,
                                        if (queued != null && queued >= 0) taskCenterDurationText(queued, strings) else strings.unknown,
                                    )
                                }
                            }
                            val others = attempts.filter { it !== detail && it.label == detail.label }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(copy.errorOtherAttempts, style = MaterialTheme.typography.titleSmall)
                                    if (others.isEmpty()) {
                                        Text(
                                            copy.errorOtherAttemptsEmpty,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        others.forEach { other ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onOpenAttempt(other) }
                                                    .padding(vertical = Spacing.xs),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    taskCenterRequestTitle(other) + " · " +
                                                        taskCenterTimestampText(other.requestedAtMs, strings, includeSeconds = true) +
                                                        " · " + other.responseStatus.ifBlank { copy.responseMissing } +
                                                        " · " + other.durationLabel(strings),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (other.httpOk == false) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Text(copy.view + " ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(taskCenterDiagnosticText(task, detail, copy))) },
                                ) {
                                    Text(copy.responseCopyDiagnostic)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCenterPairSection(title: String, rows: List<Pair<String, String>>, emptyText: String) {
    val copy = taskCenterCopy()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (rows.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Surface(
                shape = RoundedCornerShape(Radius.s),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    rows.forEach { (key, value) ->
                        SelectionContainer {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(if (key.length > 18) 132.dp else 96.dp),
                                )
                                Text(
                                    // The mask value is a protocol placeholder; show localized copy.
                                    if (value == TASK_CENTER_REDACTED) copy.redacted else value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    softWrap = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun taskCenterRequestCopyText(detail: TaskCenterRequestDetail): String = buildString {
    append("${detail.method.ifBlank { "GET" }} ${taskCenterRedactSensitive(detail.fullUrl)}\n")
    detail.requestHeaders.forEach { (key, value) ->
        append("${taskCenterRedactSensitive(key)}: ${taskCenterRedactSensitive(value)}\n")
    }
    if (detail.requestBody.collected) {
        append('\n')
        append(taskCenterRedactSensitive(detail.requestBody.text))
    }
}

internal fun taskCenterDiagnosticText(task: Async_task, detail: TaskCenterRequestDetail, copy: TaskCenterCopy): String = buildString {
    appendLine("#${task.id} ${task.type} ${task.status}")
    appendLine("${detail.method} ${taskCenterRedactSensitive(detail.fullUrl)}")
    appendLine("${detail.responseStatus.ifBlank { copy.responseMissing }} · ${detail.durationLabel(copy.strings)}")
    if (task.last_error_code.isNotBlank() || task.last_error_message.isNotBlank()) {
        appendLine("${task.last_error_code} ${task.last_error_message}")
    }
    if (detail.responseIncomplete) appendLine(copy.responseIncompleteTitle)
    val body = taskCenterRedactSensitive(detail.responseBody.text)
    if (body.isNotBlank()) {
        appendLine()
        appendLine(body.take(8_000))
        if (body.length > 8_000) appendLine("…")
    }
}

@Composable
private fun TaskCenterCaptureCard(
    task: Async_task,
    captureState: TaskHttpCaptureState,
    recordCount: Int,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
) {
    val copy = taskCenterCopy()
    var confirming by remember { mutableStateOf(false) }
    val terminal = !taskCenterIsActive(task.status)
    val armedState = captureState as? TaskHttpCaptureState.Armed

    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.task_center_capture_confirm_title)) },
            text = { Text(stringResource(R.string.task_center_capture_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onArm()
                }) { Text(stringResource(R.string.task_center_capture_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.task_center_capture_cancel))
                }
            },
        )
    }

    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.task_center_capture_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        armedState != null && terminal -> String.format(
                            copy.captureSessionArmedTerminal,
                            taskCenterDurationText(armedState.remainingMs, copy.strings),
                        )
                        armedState != null -> String.format(
                            stringResource(R.string.task_center_capture_armed),
                            taskCenterDurationText(armedState.remainingMs, copy.strings),
                        )
                        recordCount > 0 -> String.format(copy.captureRecordsCount, recordCount)
                        else -> copy.captureNoRecords
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (armedState != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (terminal) {
                Text(
                    stringResource(R.string.task_center_capture_terminal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.task_center_capture_off_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    copy.captureScope,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                armedState != null -> OutlinedButton(onClick = onDisarm) {
                    Text(stringResource(R.string.task_center_capture_stop))
                }
                !terminal -> OutlinedButton(onClick = { confirming = true }) {
                    Text(stringResource(R.string.task_center_capture_confirm_ok))
                }
            }
        }
    }
}

@Composable
private fun TaskCenterExportAction(task: Async_task) {
    val copy = taskCenterCopy()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportService: TaskDiagnosticsExportService = koinInject()
    var confirming by remember(task.id) { mutableStateOf(false) }
    var exporting by remember(task.id) { mutableStateOf(false) }
    var failed by remember(task.id) { mutableStateOf(false) }

    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(copy.exportConfirmTitle) },
            text = { Text(copy.exportConfirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    exporting = true
                    failed = false
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            runCatching {
                                val dir = File(context.filesDir, "DailySatori/task_diagnostics").apply { mkdirs() }
                                val target = File(dir, "task-${task.id}-${System.currentTimeMillis()}.txt")
                                val ok = target.outputStream().use { output -> exportService.write(task.id, output) }
                                if (!ok) {
                                    target.delete()
                                    null
                                } else {
                                    target
                                }
                            }.getOrNull()
                        }
                        exporting = false
                        if (file == null) {
                            failed = true
                            return@launch
                        }
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, copy.exportConfirmTitle))
                        }.onFailure { failed = true }
                    }
                }) { Text(copy.exportConfirmOk) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.task_center_capture_cancel))
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        OutlinedButton(
            onClick = { confirming = true },
            enabled = !exporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (exporting) copy.exporting else copy.exportAction)
        }
        if (failed) {
            Text(
                copy.exportFailed,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

