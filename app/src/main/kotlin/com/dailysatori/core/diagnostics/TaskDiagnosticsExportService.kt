package com.dailysatori.core.diagnostics

import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.data.repository.AsyncTaskRepository
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.datetime.Clock

/**
 * Task-scoped diagnostics export. It assembles the task's safe metadata, its lifecycle log and its
 * explicitly captured HTTP file itself, so the caller never fabricates a global DiagnosticSnapshot and
 * never has to reach into the network core. Bodies are only present when the user armed capture for this
 * task; everything else stays as the ordinary sanitized log.
 */
class TaskDiagnosticsExportService(
    private val tasks: AsyncTaskRepository,
    private val store: AsyncTaskLogStore,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun write(taskId: Long, output: OutputStream): Boolean {
        val task = tasks.getById(taskId) ?: return false
        val timeFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS XXX", java.util.Locale.ROOT)
            .withZone(ZoneId.systemDefault())
        fun local(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms))
        val http = store.readHttp(taskId)
        val writer = output.bufferedWriter(Charsets.UTF_8)
        writer.appendLine("Daily Satori task diagnostics / schema=1")
        writer.appendLine("task=" + task.id + " type=" + task.type + " status=" + task.status)
        writer.appendLine("exportedAt=" + local(clock()) + " timeZone=" + ZoneId.systemDefault().id)
        writer.appendLine("progress=" + task.progress_current + "/" + task.progress_total + " attempt=" + task.attempt_count)
        writer.appendLine("createdAt=" + local(task.created_at) + " updatedAt=" + local(task.updated_at))
        task.started_at?.let { writer.appendLine("startedAt=" + local(it)) }
        task.finished_at?.let { writer.appendLine("finishedAt=" + local(it)) }
        if (task.last_error_code.isNotBlank() || task.last_error_message.isNotBlank()) {
            writer.appendLine("error=" + task.last_error_code + " " + task.last_error_message)
        }
        writer.appendLine("")
        writer.appendLine("Lifecycle log (sanitized):")
        val lifecycle = store.read(taskId)
        writer.append(if (lifecycle.isBlank()) "[未记录]" + System.lineSeparator() else lifecycle)
        if (!lifecycle.endsWith("\n") && lifecycle.isNotBlank()) writer.appendLine()
        writer.appendLine("")
        if (http.isBlank()) {
            writer.appendLine("Task HTTP capture: [未采集]（未对该任务开启正文采集）")
        } else {
            writer.appendLine("Task HTTP capture (sanitized, explicitly exported by the user; may contain private content):")
            writer.appendLine("http.schema=1")
            writer.append(http)
            if (!http.endsWith("\n")) writer.appendLine()
        }
        writer.flush()
        return true
    }
}
