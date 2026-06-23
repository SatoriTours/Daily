package com.dailysatori.core.task

import com.dailysatori.service.externalfavorites.FavoriteSyncHttpLogger
import java.io.File
import kotlinx.datetime.Clock

class AsyncTaskLogStore(
    private val root: File,
    private val maxBytesPerTask: Int = DEFAULT_MAX_BYTES_PER_TASK,
) {
    init {
        root.mkdirs()
    }

    fun append(taskId: Long, message: String) {
        if (taskId <= 0L || maxBytesPerTask <= 0) return
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

    fun read(taskId: Long): String =
        if (taskId <= 0L) "" else taskFile(taskId).takeIf { it.exists() }?.readText().orEmpty()

    private fun taskFile(taskId: Long): File = File(root, "task-$taskId.log")

    private fun String.takeLastBytes(maxBytes: Int): String {
        val bytes = encodeToByteArray()
        if (bytes.size <= maxBytes) return this
        return bytes.takeLast(maxBytes).toByteArray().decodeToString()
    }

    private companion object {
        const val DEFAULT_MAX_BYTES_PER_TASK = 64 * 1024 * 1024
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
        store.append(
            id,
            buildString {
                append("HTTP request [")
                append(label)
                append("] ")
                append(method)
                append(' ')
                append(url)
                if (parameters.isNotEmpty()) {
                    append(" params=")
                    append(parameters.entries.joinToString("&") { "${it.key}=${it.value}" })
                }
            },
        )
    }

    override fun logResponse(
        taskId: Long?,
        label: String,
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
    ) {
        val id = taskId ?: return
        store.append(
            id,
            buildString {
                append("HTTP response [")
                append(label)
                append("] status=")
                append(statusCode)
                if (headers.isNotEmpty()) {
                    append(" headers=")
                    append(headers.entries.joinToString(",") { "${it.key}=${it.value}" })
                }
                append(" body=")
                append(body)
            },
        )
    }
}
