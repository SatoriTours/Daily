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
            val capped = (existing + line).takeLastBytes(maxBytesPerTask)
            file.writeText(capped)
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

    private fun String.takeLastBytes(maxBytes: Int): String {
        val bytes = encodeToByteArray()
        if (bytes.size <= maxBytes) return this
        return bytes.takeLast(maxBytes).toByteArray().decodeToString()
    }

    private companion object {
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
        val safe = DiagnosticRedactor.fields(mapOf("method" to method, "url" to url))
        store.append(id, "HTTP request " + safe.toString() + " params=[omitted]")
        val handle = captureHandle(id, requestId, attempt) ?: return
        TaskHttpCapture.request(taskId = id, handle = handle, method = method, url = url,
            headers = parameters, body = null)
    }

    override fun logResponse(
        taskId: Long?,
        label: String,
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
        requestId: String?,
        attempt: Int?,
    ) {
        val id = taskId ?: return
        store.append(id, "HTTP response status=" + statusCode + " headers=[omitted] body=[omitted]")
        val handle = captureHandle(id, requestId, attempt) ?: return
        TaskHttpCapture.body(id, handle, TaskHttpDirection.RESPONSE, body, status = statusCode)
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

    private companion object {
        val unpaired = java.util.concurrent.atomic.AtomicLong()
    }
}
