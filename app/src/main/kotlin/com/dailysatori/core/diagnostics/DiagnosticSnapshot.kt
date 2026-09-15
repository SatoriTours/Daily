package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.SafeDiagnosticEvent
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val diagnosticJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class DiagnosticHealth(
    val droppedEvents: Long = 0, val ioFailures: Long = 0, val prunedFiles: Long = 0,
    val corruptLines: Long = 0, val truncatedEvents: Long = 0,
    val lastProcessMayHaveLostBufferedEvents: Boolean = true,
)

data class DiagnosticStorePolicy(
    val retentionMs: Long = 24 * 60 * 60 * 1000L,
    val maxBytes: Long = 30 * 1024 * 1024L,
    val segmentBytes: Long = 1024 * 1024L,
    val queueCapacity: Int = 512,
    val crashBytes: Long = 10 * 1024 * 1024L,
    val crashRetentionMs: Long = 7 * 24 * 60 * 60 * 1000L,
) {
    init {
        require(retentionMs > 0 && maxBytes > 0 && segmentBytes > 0 && queueCapacity > 0 && crashBytes > 0)
    }
}

class DiagnosticSnapshot internal constructor(
    val file: File, val startMs: Long, val endMs: Long, val health: DiagnosticHealth,
    val crash: Boolean = false,
) : AutoCloseable {
    fun events(): Sequence<SafeDiagnosticEvent> = sequence {
        file.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                yield(diagnosticJson.decodeFromString(SafeDiagnosticEvent.serializer(), line))
            }
        }
    }

    override fun close() { file.delete() }
}
