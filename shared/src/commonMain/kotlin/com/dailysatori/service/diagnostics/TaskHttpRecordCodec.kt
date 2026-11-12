package com.dailysatori.service.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One capture record as JSONL. Producers (sinks) and consumers (UI) share this contract, so no side
 * maintains its own key/value syntax. Version 2 adds the full sanitized url and exact headers.
 */
@Serializable
data class TaskHttpRecordDto(
    val schema: Int = TaskHttpRecordCodec.SCHEMA_VERSION,
    val taskId: Long? = null,
    val label: String? = null,
    val requestId: String,
    val attempt: Int,
    val direction: String,
    val method: String? = null,
    val url: String? = null,
    val service: String? = null,
    val route: String? = null,
    val status: Int? = null,
    val durationMs: Long? = null,
    val bytes: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val bodyState: String,
    val droppedBytes: Long? = null,
)

/**
 * Single encode/decode contract for the per task HTTP capture. Version 2 is the only written format;
 * version 1 lines stay readable for older files but never invent fields they did not store (an absent
 * url stays null, headers stay empty, and NOT_CAPTURED stays explicit).
 */
object TaskHttpRecordCodec {
    const val SCHEMA_VERSION = 2
    // Defaults are written explicitly so a consumer never has to infer the schema or a missing field.
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = false }

    fun encode(record: TaskHttpRecord, taskId: Long? = null, label: String? = null): String =
        json.encodeToString(
            TaskHttpRecordDto.serializer(),
            TaskHttpRecordDto(
                taskId = taskId,
                label = label,
                requestId = record.requestId,
                attempt = record.attempt,
                direction = record.direction.name,
                method = record.method,
                url = record.url,
                service = record.service,
                route = record.route,
                status = record.status,
                durationMs = record.durationMs,
                bytes = record.bytes,
                headers = record.headers,
                body = record.body,
                bodyState = record.bodyState,
                droppedBytes = record.droppedBytes,
            ),
        )

    /** Decodes one line; null for anything that is not a readable record (never guessed). */
    fun decode(line: String): TaskHttpRecord? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("{")) return decodeLegacy(trimmed)
        val dto = runCatching {
            json.decodeFromString(TaskHttpRecordDto.serializer(), trimmed)
        }.getOrNull() ?: return null
        val direction = TaskHttpDirection.entries.firstOrNull { it.name == dto.direction } ?: return null
        return TaskHttpRecord(
            requestId = dto.requestId,
            attempt = dto.attempt,
            direction = direction,
            method = dto.method,
            url = dto.url,
            service = dto.service,
            route = dto.route,
            status = dto.status,
            durationMs = dto.durationMs,
            bytes = dto.bytes,
            headers = dto.headers,
            body = dto.body,
            bodyState = dto.bodyState,
            droppedBytes = dto.droppedBytes,
        )
    }

    fun decodeAll(text: String): List<TaskHttpRecord> =
        text.lines().mapNotNull(::decode)

    /**
     * Version 1 compatibility: key/value header line plus indented body lines. Fields that version 1 did
     * not store (url, exact headers) are returned as null/empty instead of being reconstructed.
     */
    fun decodeLegacy(text: String): TaskHttpRecord? {
        val header = text.substringBefore("\n").trim()
        if (!header.startsWith("# http")) return null
        val tokens = header.removePrefix("# http").trim().split(" ").filter { it.isNotBlank() }
        val fields = tokens.filter { it.contains("=") }.associate { it.substringBefore("=") to it.substringAfter("=") }
        val direction = TaskHttpDirection.entries.firstOrNull { it.name == fields["dir"] } ?: return null
        val bodyLines = text.lines().drop(1).filter { it.startsWith("  | ") }
            .map { it.removePrefix("  | ") }
        return TaskHttpRecord(
            requestId = fields["request"] ?: "unknown",
            attempt = fields["attempt"]?.toIntOrNull() ?: 1,
            direction = direction,
            method = fields["method"]?.takeIf { it != "-" },
            url = null,
            service = fields["service"]?.takeIf { it != "-" },
            route = fields["route"]?.takeIf { it != "-" },
            status = fields["status"]?.toIntOrNull(),
            durationMs = fields["durationMs"]?.toLongOrNull(),
            bytes = fields["bytes"]?.toLongOrNull(),
            headers = emptyMap(),
            body = bodyLines.joinToString("\n").ifBlank { null },
            bodyState = fields["state"] ?: TASK_HTTP_NOT_CAPTURED,
            droppedBytes = fields["droppedBytes"]?.toLongOrNull(),
        )
    }
}

