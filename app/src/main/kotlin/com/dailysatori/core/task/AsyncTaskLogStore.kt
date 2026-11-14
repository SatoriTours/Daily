package com.dailysatori.core.task

import com.dailysatori.service.externalfavorites.FavoriteSyncHttpLogger
import com.dailysatori.service.diagnostics.DiagnosticRedactor
import com.dailysatori.service.diagnostics.TaskHttpCapture
import com.dailysatori.service.diagnostics.TaskHttpDirection
import com.dailysatori.service.diagnostics.TaskHttpRequestHandle
import com.dailysatori.service.asynctask.AsyncTaskLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock

class AsyncTaskLogStore(
    private val root: File,
    private val maxBytesPerTask: Int = DEFAULT_MAX_BYTES_PER_TASK,
    private val maxHttpBytesPerTask: Int = DEFAULT_MAX_HTTP_BYTES_PER_TASK,
) : AsyncTaskLogger {
    private val writeLock = Any()

    init {
        root.mkdirs()
    }

    override fun append(taskId: Long, message: String) {
        if (taskId <= 0L || maxBytesPerTask <= 0) return
        synchronized(writeLock) {
            root.mkdirs()
            val file = taskFile(taskId)
            val existing = if (file.exists()) file.readText() else ""
            val line = buildString {
                append(Clock.System.now())
                append(" ")
                append(message.trimEnd())
                append('\n')
            }
            // Drop the marker before appending so it never accumulates, and tell the reader when older lines
            // were cut instead of silently presenting a partial history.
            val body = if (existing.startsWith(TRIM_MARKER)) existing.removePrefix(TRIM_MARKER) else existing
            val combined = body + line
            val capped = combined.takeLastUtf8Bytes(maxBytesPerTask)
            // The marker is part of the cap so the file never exceeds its budget, and a cut can never leave a
            // broken multi-byte character behind.
            file.writeText(
                if (capped.length < combined.length) {
                    TRIM_MARKER + capped.takeLastUtf8Bytes(maxBytesPerTask - TRIM_MARKER.encodeToByteArray().size)
                } else {
                    capped
                },
            )
        }
    }

    /**
     * HTTP capture lines live in their own file with an independent cap. Trimming keeps whole records
     * only: a half JSON line must never be left behind, so the newest records win and the oldest whole
     * records are dropped.
     */
    fun appendHttp(taskId: Long, text: String) {
        if (taskId <= 0L || maxHttpBytesPerTask <= 0) return
        synchronized(writeLock) {
            root.mkdirs()
            val file = taskHttpFile(taskId)
            val existing = if (file.exists()) file.readText() else ""
            val lines = (existing + text.trimEnd() + "\n").lines().filter { it.isNotBlank() }
            val kept = ArrayDeque<String>()
            var bytes = 0
            for (line in lines.asReversed()) {
                val cost = line.encodeToByteArray().size + 1
                if (kept.isEmpty() || bytes + cost <= maxHttpBytesPerTask) {
                    kept.addFirst(line)
                    bytes += cost
                } else {
                    break
                }
            }
            file.writeText(kept.joinToString("\n", postfix = "\n"))
        }
    }

    fun readHttp(taskId: Long): String =
        if (taskId <= 0L) "" else taskHttpFile(taskId).takeIf { it.exists() }?.readText().orEmpty()

    fun read(taskId: Long): String =
        if (taskId <= 0L) "" else taskFile(taskId).takeIf { it.exists() }?.readText().orEmpty()

    fun delete(taskIds: Iterable<Long>) {
        synchronized(writeLock) {
            taskIds.forEach { taskId ->
                if (taskId > 0L) {
                    taskFile(taskId).delete()
                    taskHttpFile(taskId).delete()
                }
            }
        }
    }

    fun observe(taskId: Long, pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS): Flow<String> =
        flow {
            var last: String? = null
            while (currentCoroutineContext().isActive) {
                val value = read(taskId)
                if (value != last) {
                    emit(value)
                    last = value
                }
                delay(pollIntervalMs)
            }
        }.flowOn(Dispatchers.IO)

    /** Same bounded polling contract as [observe]; no extra background service. */
    fun observeHttp(taskId: Long, pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS): Flow<String> =
        flow {
            var last: String? = null
            while (currentCoroutineContext().isActive) {
                val value = readHttp(taskId)
                if (value != last) {
                    emit(value)
                    last = value
                }
                delay(pollIntervalMs)
            }
        }.flowOn(Dispatchers.IO)

    private fun taskFile(taskId: Long): File = File(root, "task-$taskId.log")

    private fun taskHttpFile(taskId: Long): File = File(root, "task-$taskId.http.log")

    /** Keeps whole UTF-8 characters: the cut starts at the first lead byte at or after the byte budget. */
    private fun String.takeLastUtf8Bytes(maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        val bytes = encodeToByteArray()
        if (bytes.size <= maxBytes) return this
        var start = bytes.size - maxBytes
        while (start < bytes.size && (bytes[start].toInt() and 0xC0) == 0x80) start++
        return bytes.copyOfRange(start, bytes.size).decodeToString()
    }

    private companion object {
        const val TRIM_MARKER = "[已裁剪较早日志]\n"
        const val DEFAULT_MAX_BYTES_PER_TASK = 64 * 1024 * 1024
        const val DEFAULT_MAX_HTTP_BYTES_PER_TASK = 2 * 1024 * 1024
        const val DEFAULT_POLL_INTERVAL_MS = 1_000L
    }
}

class AsyncTaskHttpLogWriter(
    private val store: AsyncTaskLogStore,
) : FavoriteSyncHttpLogger {
    override fun logRequest(
        taskId: Long?,
        label: String,
        method: String,
        url: String,
        parameters: Map<String, String>,
        requestId: String?,
        attempt: Int?,
        kind: String?,
    ) {
        val id = taskId ?: return
        if (label == "x_content_fetch_summary" && method == "SUMMARY" && url == "local://x-content-fetch") {
            val fields = listOf("fx_success", "fx_backoff", "fx_errors", "official_requests", "official_success",
                "body_missing", "budget_deferred", "cache_hit")
            val counts = fields.mapNotNull { key ->
                parameters[key]?.toIntOrNull()?.takeIf { it >= 0 }?.let { key to it }
            }.toMap()
            store.append(id, "X content fetch summary " + counts.toString())
            return
        }
        val recordKind = taskHttpRecordKind(kind)
        val safe = DiagnosticRedactor.fields(mapOf("method" to method, "url" to url))
        store.append(id, "HTTP request " + safe.toString() + " params=[omitted]" +
            taskHttpLogMeta(label, requestId, attempt, recordKind))
        val handle = captureHandle(id, requestId, attempt) ?: return
        TaskHttpCapture.request(taskId = id, handle = handle, method = method, url = url,
            headers = parameters, body = null, kind = recordKind)
    }

    override fun logResponse(
        taskId: Long?,
        label: String,
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
        requestId: String?,
        attempt: Int?,
        kind: String?,
    ) {
        val id = taskId ?: return
        val recordKind = taskHttpRecordKind(kind)
        store.append(id, "HTTP response status=" + statusCode + " headers=[omitted] body=[omitted]" +
            taskHttpLogMeta(label, requestId, attempt, recordKind))
        val handle = captureHandle(id, requestId, attempt) ?: return
        TaskHttpCapture.body(id, handle, TaskHttpDirection.RESPONSE, body, status = statusCode, kind = recordKind)
    }

    /**
     * Pairing comes only from the explicit identity the caller passed. A missing identity is recorded as
     * an unpaired request so UI can flag it, and is never inferred from the task or from ordering.
     */
    private fun captureHandle(taskId: Long, requestId: String?, attempt: Int?): TaskHttpRequestHandle? {
        if (!TaskHttpCapture.isArmed(taskId)) return null
        // An unknown identity is flagged per event instead of collapsing every unpaired call into one.
        val id = requestId ?: "unpaired-" + unpaired.incrementAndGet()
        return TaskHttpRequestHandle(id, attempt ?: 1)
    }

    /**
     * Correlation metadata for the text log. Only well-formed opaque identifiers are written; anything
     * else is dropped so a free-form label or id can never leak user content into the task log.
     */
    private fun taskHttpRecordKind(kind: String?): String =
        if (kind == com.dailysatori.service.diagnostics.TASK_HTTP_KIND_BUSINESS_RESULT) {
            com.dailysatori.service.diagnostics.TASK_HTTP_KIND_BUSINESS_RESULT
        } else {
            com.dailysatori.service.diagnostics.TASK_HTTP_KIND_HTTP
        }

    /** Explicit source marker; old lines have none, so consumers must not assume raw HTTP. */
    private fun taskHttpLogMeta(label: String, requestId: String?, attempt: Int?, kind: String): String = buildString {
        append(" kind=").append(kind)
        if (TASK_HTTP_SAFE_LABEL.matches(label)) append(" label=").append(label)
        if (requestId != null && TASK_HTTP_SAFE_REQUEST_ID.matches(requestId)) {
            append(" requestId=").append(requestId)
        }
        if (attempt != null && attempt in 1..99) append(" attempt=").append(attempt)
    }

    private companion object {
        val unpaired = java.util.concurrent.atomic.AtomicLong()
        val TASK_HTTP_SAFE_LABEL = Regex("[a-z][a-z0-9_]{0,31}")
        val TASK_HTTP_SAFE_REQUEST_ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
