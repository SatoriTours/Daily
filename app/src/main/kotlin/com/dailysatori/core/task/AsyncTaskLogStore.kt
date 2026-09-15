package com.dailysatori.core.task

import com.dailysatori.service.externalfavorites.FavoriteSyncHttpLogger
import com.dailysatori.service.diagnostics.DiagnosticRedactor
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

    fun read(taskId: Long): String =
        if (taskId <= 0L) "" else taskFile(taskId).takeIf { it.exists() }?.readText().orEmpty()

    fun delete(taskIds: Iterable<Long>) {
        synchronized(writeLock) {
            taskIds.forEach { taskId ->
                if (taskId > 0L) taskFile(taskId).delete()
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

    private fun taskFile(taskId: Long): File = File(root, "task-$taskId.log")

    private fun String.takeLastBytes(maxBytes: Int): String {
        val bytes = encodeToByteArray()
        if (bytes.size <= maxBytes) return this
        return bytes.takeLast(maxBytes).toByteArray().decodeToString()
    }

    private companion object {
        const val DEFAULT_MAX_BYTES_PER_TASK = 64 * 1024 * 1024
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
    ) {
        val id = taskId ?: return
        val safe = DiagnosticRedactor.fields(mapOf("method" to method, "url" to url))
        store.append(id, "HTTP request $safe params=[omitted]")
    }

    override fun logResponse(
        taskId: Long?,
        label: String,
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
    ) {
        val id = taskId ?: return
        store.append(id, "HTTP response status=$statusCode headers=[omitted] body=[omitted]")
    }
}
