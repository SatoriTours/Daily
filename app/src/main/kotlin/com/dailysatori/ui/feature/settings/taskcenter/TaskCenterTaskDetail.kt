package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.core.diagnostics.performanceTab
import com.dailysatori.service.diagnostics.PerformanceResult
import com.dailysatori.service.diagnostics.PerformanceScreen
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextAlign
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
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.ui.theme.editorial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val ROUND_EVENT_LIMIT = 8

@Composable
internal fun TaskCenterTaskDetail(
    task: Async_task,
    title: String,
    source: String?,
    taskLog: String,
    failureSuperseded: Boolean,
    needsAttention: Boolean,
    onAcknowledge: () -> Unit,
    onResumeAssistant: () -> Unit,
    onCancel: () -> Unit,
    canRetry: Boolean = false,
    onRetry: () -> Unit = {},
    sourceTarget: TaskCenterSourceTarget? = null,
    onOpenSourceTarget: ((TaskCenterSourceTarget) -> Unit)? = null,
    assistantResumeBlocked: Boolean,
    httpLog: String,
    captureState: TaskHttpCaptureState,
    onArmCapture: () -> Unit,
    onDisarmCapture: () -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val i18n: I18nService = koinInject()
    val copy = taskCenterCopy()
    var showHistory by remember(task.id) { mutableStateOf(false) }
    var showDiagnostics by remember(task.id) { mutableStateOf(false) }
    val events = remember(taskLog, copy) { taskCenterLifecycleEvents(taskLog, copy.strings) }
    val rounds = remember(events) { taskCenterLifecycleRounds(events) }
    val earlierRounds = rounds.dropLast(1)
    val currentEvents = rounds.lastOrNull()?.events.orEmpty().asReversed().take(ROUND_EVENT_LIMIT)
    val counts = remember(task.type, task.checkpoint_json) {
        taskCenterOutcomeCounts(task.type, task.checkpoint_json)
    }
    val recordCount = remember(httpLog) { taskCenterCaptureRead(httpLog).requests.size }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item(key = "task-header") { TaskCenterDetailHeader(task, title, source, counts) }
        item(key = "task-round-title") { TaskCenterSectionHeader(copy.detailRoundTitle, copy.detailRoundNewest) }
        if (currentEvents.isEmpty()) {
            item(key = "task-round-empty") { TaskCenterNote(copy.detailRoundEmpty) }
        } else {
            itemsIndexed(currentEvents, key = { index, _ -> "task-round-event-$index" }) { index, event ->
                TaskCenterRoundEventRow(event, newest = index == 0)
            }
        }
        item(key = "task-history") {
            TaskCenterHistoryCard(earlierRounds, expanded = showHistory) { showHistory = !showHistory }
        }
        item(key = "task-open-log") { TaskCenterNavRow(copy.detailOpenLog, copy.detailOpenLogHint, onOpenLog) }
        if (sourceTarget != null && onOpenSourceTarget != null) {
            item(key = "task-open-source") {
                TaskCenterNavRow(
                    title = copy.detailOpenSource,
                    hint = source?.takeIf { it.isNotBlank() } ?: title,
                    onClick = { onOpenSourceTarget(sourceTarget) },
                )
            }
        }
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
        item(key = "task-capture") {
            TaskCenterCaptureCard(
                task = task,
                captureState = captureState,
                recordCount = recordCount,
                onArm = onArmCapture,
                onDisarm = onDisarmCapture,
            )
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
            item(key = "task-timing") {
                Column(Modifier.padding(horizontal = Spacing.m)) { TaskCenterTimingRows(task) }
            }
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
        if (canRetry) {
            item(key = "task-retry-action") {
                FilledTonalButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                ) {
                    Text(copy.detailRetry)
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
private fun TaskCenterDetailHeader(
    task: Async_task,
    title: String,
    source: String?,
    counts: TaskCenterOutcomeCounts?,
) {
    val copy = taskCenterCopy()
    val meta = listOfNotNull(source?.takeIf { it.isNotBlank() }, "#${task.id}").joinToString(" · ")
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall.editorial(), softWrap = true)
        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TaskCenterStatusPill(task.status)
            Text(
                String.format(copy.detailUpdated, taskCenterWhenText(task.updated_at, copy)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val total = task.progress_total
        if (total > 0L) {
            val current = task.progress_current.coerceIn(0L, total)
            val percent = (current * 100L / total).toInt()
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("$current", style = MaterialTheme.typography.headlineMedium)
                Text(
                    String.format(copy.detailProgressProcessed, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xxs),
                )
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            TaskCenterProgressBar(fraction = current.toFloat() / total.toFloat())
        }
        if (counts != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(
                    String.format(copy.detailStatsSucceeded, counts.succeeded),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    String.format(copy.detailStatsFailed, counts.failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (counts.failed > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    String.format(copy.detailStatsDeferred, counts.deferred),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskCenterProgressBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radius.xxs))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp)
                .clip(RoundedCornerShape(Radius.xxs)).background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun TaskCenterSectionHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TaskCenterRoundEventRow(event: TaskCenterLifecycleEvent, newest: Boolean) {
    val copy = taskCenterCopy()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xxs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(
            Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape)
                .background(if (newest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            event.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = if (newest) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun TaskCenterHistoryCard(
    rounds: List<TaskCenterLifecycleRound>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val copy = taskCenterCopy()
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m).clickable(onClick = onToggle),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(copy.detailHistoryTitle, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (rounds.isEmpty()) copy.detailHistoryNone else String.format(copy.detailHistoryCount, rounds.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (rounds.isNotEmpty()) {
                    Text(
                        if (expanded) "⌃" else "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded && rounds.isNotEmpty()) {
                rounds.asReversed().forEach { round -> TaskCenterHistoryRoundRow(round) }
                Text(
                    copy.detailHistoryNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskCenterHistoryRoundRow(round: TaskCenterLifecycleRound) {
    val copy = taskCenterCopy()
    val start = taskCenterTimestampText(round.startMs, copy.strings, includeSeconds = false)
    val end = taskCenterTimestampText(round.endMs, copy.strings, includeSeconds = false)
    val range = if (round.endMs != null && round.endMs != round.startMs) {
        String.format(copy.detailRoundRange, start, end)
    } else {
        start
    }
    val outcome = round.events.lastOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            outcome?.summary ?: copy.unknown,
            style = MaterialTheme.typography.bodySmall,
            color = if (outcome?.abnormal == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TaskCenterNavRow(title: String, hint: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun TaskCenterNote(text: String) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
internal fun TaskCenterRequestOutcomePill(detail: TaskCenterRequestDetail) {
    val copy = taskCenterCopy()
    val sourceCopy = taskCenterSourceCopy()
    val view = taskCenterSourceView(detail, sourceCopy)
    val (label, color) = when (detail.source) {
        TaskCenterRecordSource.BusinessResult ->
            if (detail.httpOk == true) sourceCopy.businessSucceeded to MaterialTheme.colorScheme.primary
            else sourceCopy.businessFailed to MaterialTheme.colorScheme.error
        TaskCenterRecordSource.Unknown -> sourceCopy.unknownBadge to MaterialTheme.colorScheme.onSurfaceVariant
        TaskCenterRecordSource.Http -> when {
            view.statusIsError -> view.status to MaterialTheme.colorScheme.error
            detail.httpOk == true -> view.status to MaterialTheme.colorScheme.primary
            else -> view.status to MaterialTheme.colorScheme.onSurfaceVariant
        }
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
internal fun TaskCenterRequestScreen(
    task: Async_task,
    detail: TaskCenterRequestDetail,
    attempts: List<TaskCenterRequestDetail>,
    onOpenAttempt: (TaskCenterRequestDetail) -> Unit,
    onClose: () -> Unit,
) {
    val copy = taskCenterCopy()
    val strings = copy.strings
    val sourceCopy = taskCenterSourceCopy()
    val sourceView = remember(detail, sourceCopy) { taskCenterSourceView(detail, sourceCopy) }
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
                            taskCenterRequestTitle(detail, copy.requestFallbackTitle),
                            style = MaterialTheme.typography.titleMedium.editorial(),
                        )
                        Text(
                            "#" + task.id + " · " + listOf(detail.method, detail.endpointLabel)
                                .filter { it.isNotBlank() }
                                .joinToString(" "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                sourceView.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Radius.s))
                                    .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                            )
                            Text(
                                buildString {
                                    append(sourceView.status)
                                    sourceView.attempt?.let { append(" · " + it) }
                                    detail.durationMs?.let { append(" · " + taskCenterDurationText(it, strings)) }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sourceView.statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                            RequestTab.Response -> sourceView.responseTab
                            RequestTab.ErrorTiming -> sourceView.errorTab
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
                    modifier = Modifier.fillMaxSize().performanceTab(when (tab) {
                        RequestTab.Request -> PerformanceScreen.TASK_REQUEST
                        RequestTab.Response -> PerformanceScreen.TASK_RESPONSE
                        RequestTab.ErrorTiming -> PerformanceScreen.TASK_ERROR_TIMING
                    }, PerformanceResult.OK),
                    contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    when (tab) {
                        RequestTab.Request -> {
                            item { TaskCenterRequestInfoCard(task, detail, sourceCopy, sourceView) }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(copy.requestAddress, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        TextButton(
                                            enabled = detail.fullUrl.isNotBlank(),
                                            onClick = { clipboard.setText(AnnotatedString(taskCenterRedactSensitive(detail.fullUrl))) },
                                        ) {
                                            Text(copy.requestCopyUrl)
                                        }
                                    }
                                    TaskCenterCodeBlock(
                                        when {
                                            detail.fullUrl.isNotBlank() ->
                                                listOf(detail.method, detail.fullUrl).filter { it.isNotBlank() }.joinToString(" ")
                                            detail.endpointLabel.isNotBlank() ->
                                                listOf(detail.method, detail.endpointLabel).filter { it.isNotBlank() }.joinToString(" ") +
                                                    " · " + copy.requestUrlUnrecorded
                                            else -> listOf(detail.method, copy.missingUrl).filter { it.isNotBlank() }.joinToString(" · ")
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
                                    title = sourceView.requestBodyTitle,
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
                            item { TaskCenterFullJsonSection(detail) }
                        }
                        RequestTab.Response -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(
                                        sourceView.status,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (sourceView.statusIsError) {
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
                                        title = sourceView.responseBodyTitle,
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
                                            clipboard.setText(
                                                AnnotatedString(
                                                    "# source=" + taskCenterSourceKey(detail.source) + "\n" +
                                                        taskCenterRedactSensitive(detail.responseBody.text),
                                                ),
                                            )
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
                                    Text(sourceView.errorTitle, style = MaterialTheme.typography.titleSmall)
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
                                    TaskCenterKeyValueRow(sourceView.startedLabel, taskCenterTimestampText(detail.requestedAtMs, strings, includeSeconds = true))
                                    TaskCenterKeyValueRow(sourceView.finishedLabel, taskCenterTimestampText(detail.respondedAtMs, strings, includeSeconds = true))
                                    TaskCenterKeyValueRow(sourceView.durationLabel, detail.durationLabel(strings))
                                    if (detail.source == TaskCenterRecordSource.Http) {
                                        val queued = detail.requestedAtMs?.let { it - task.created_at }
                                        TaskCenterKeyValueRow(
                                            copy.errorQueueTime,
                                            if (queued != null && queued >= 0) taskCenterDurationText(queued, strings) else strings.unknown,
                                        )
                                    }
                                }
                            }
                            // Retry links require the same explicit request id; a blank label/id is never a link.
                            val others = taskCenterRelatedAttempts(detail, attempts)
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(copy.errorOtherAttempts, style = MaterialTheme.typography.titleSmall)
                                    if (others.isEmpty()) {
                                        Text(
                                            if (detail.requestId.isBlank()) copy.errorOtherAttemptsNoId else copy.errorOtherAttemptsEmpty,
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
                                                    taskCenterRequestTitle(other, copy.requestFallbackTitle) + " · " +
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
private fun TaskCenterRequestInfoCard(
    task: Async_task,
    detail: TaskCenterRequestDetail,
    sourceCopy: TaskCenterSourceCopy,
    view: TaskCenterSourceView,
) {
    val copy = taskCenterCopy()
    val strings = copy.strings
    val started = detail.requestedAtMs?.let { taskCenterTimestampText(it, strings, includeSeconds = true) }
        ?: copy.requestNotRecorded
    val finished = detail.respondedAtMs?.let { taskCenterTimestampText(it, strings, includeSeconds = true) }
        ?: copy.requestNotRecorded
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(copy.requestCallInfo, style = MaterialTheme.typography.titleSmall)
            TaskCenterKeyValueRow(sourceCopy.sourceLabel, view.badge)
            TaskCenterKeyValueRow(copy.requestStatus, view.status)
            TaskCenterKeyValueRow(
                view.durationLabel,
                detail.durationMs?.let { taskCenterDurationText(it, strings) } ?: copy.requestNotRecorded,
            )
            TaskCenterKeyValueRow(view.startedLabel, started)
            TaskCenterKeyValueRow(view.finishedLabel, finished)
            TaskCenterKeyValueRow(
                copy.requestEndpoint,
                listOf(detail.method, detail.endpointLabel.ifBlank { copy.missingUrl })
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
            )
            TaskCenterKeyValueRow(copy.requestModel, taskCenterRequestModel(detail) ?: copy.requestNotRecorded)
            TaskCenterKeyValueRow(copy.requestTask, "#${task.id} · ${taskCenterTaskName(task.type)}")
            Text(
                view.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskCenterFullJsonSection(detail: TaskCenterRequestDetail) {
    val copy = taskCenterCopy()
    var expanded by remember(detail) { mutableStateOf(false) }
    val body = remember(detail) { taskCenterRequestFullJson(detail) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(copy.requestFullJson, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) copy.detailCollapse else copy.detailExpand)
            }
        }
        if (expanded) {
            TaskCenterPayloadViewer(
                title = taskCenterRequestTitle(detail, copy.requestFallbackTitle),
                body = body,
                emptyText = copy.requestHeadersAbsent,
                uncollectedText = copy.viewerNotCollectedNote,
                maxHeight = 360.dp,
            )
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
                shape = RoundedCornerShape(Radius.m),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
    append("# source=" + taskCenterSourceKey(detail.source) + "\n")
    append(listOf(detail.method, taskCenterRedactSensitive(detail.fullUrl)).filter { it.isNotBlank() }.joinToString(" ") + "\n")
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
    appendLine("source=" + taskCenterSourceKey(detail.source) + " fromCapture=" + detail.fromCapture)
    appendLine(listOf(detail.method, taskCenterRedactSensitive(detail.fullUrl)).filter { it.isNotBlank() }.joinToString(" "))
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
            shape = RoundedCornerShape(Radius.xl),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = Radius.none,
            title = { Text(stringResource(R.string.task_center_capture_confirm_title), style = MaterialTheme.typography.titleMedium.editorial()) },
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
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
            shape = RoundedCornerShape(Radius.xl),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = Radius.none,
            title = { Text(copy.exportConfirmTitle, style = MaterialTheme.typography.titleMedium.editorial()) },
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

