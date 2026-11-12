package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.service.diagnostics.TaskHttpDirection
import com.dailysatori.service.diagnostics.TaskHttpRecord
import com.dailysatori.service.diagnostics.TaskHttpRecordCodec

/**
 * One captured HTTP call, projected for display. Records come only from the shared codec, so the UI
 * never maintains its own key/value syntax; fields a record genuinely lacks (for example a schema1
 * url) stay empty and are shown as "not recorded" instead of being guessed.
 */
internal data class TaskCenterCapturedRequest(
    val requestId: String,
    val attempt: Int,
    val label: String,
    val method: String,
    val url: String,
    val requestHeaders: List<Pair<String, String>>,
    val requestBody: TaskCenterLogField,
    val requestState: String,
    val status: String,
    val responseHeaders: List<Pair<String, String>>,
    val responseBody: TaskCenterLogField,
    val responseState: String,
    val durationMs: Long?,
    val droppedBytes: Long?,
    val sseEvents: List<String>,
)

/** Decoded capture plus the number of lines the codec refused to read as records. */
internal data class TaskCenterCaptureRead(
    val requests: List<TaskCenterCapturedRequest>,
    val unreadableLines: Int,
)

internal fun taskCenterCaptureRead(httpLog: String): TaskCenterCaptureRead {
    val cleaned = httpLog.filterNot { it == '\u0000' }
    if (cleaned.isBlank()) return TaskCenterCaptureRead(emptyList(), 0)
    val records = TaskHttpRecordCodec.decodeAll(cleaned)
    val nonBlankLines = cleaned.lineSequence().count { it.isNotBlank() }
    return TaskCenterCaptureRead(
        requests = taskCenterProject(records),
        unreadableLines = (nonBlankLines - records.size).coerceAtLeast(0),
    )
}

internal fun taskCenterCapturedRequests(httpLog: String): List<TaskCenterCapturedRequest> =
    taskCenterCaptureRead(httpLog).requests

private fun taskCenterProject(records: List<TaskHttpRecord>): List<TaskCenterCapturedRequest> {
    val grouped = LinkedHashMap<String, MutableList<TaskHttpRecord>>()
    records.forEachIndexed { index, record ->
        val id = record.requestId.ifBlank { "unpaired" }
        val key = if (id == "unpaired") "unpaired#${index}" else "${id}#${record.attempt}"
        grouped.getOrPut(key) { mutableListOf() }.add(record)
    }
    return grouped.values.map { group ->
        val request = group.firstOrNull { it.direction == TaskHttpDirection.REQUEST }
        val response = group.firstOrNull { it.direction == TaskHttpDirection.RESPONSE }
        val sse = group.filter { it.direction == TaskHttpDirection.SSE_EVENT }
            .mapNotNull { it.body?.takeIf(String::isNotBlank) }
        TaskCenterCapturedRequest(
            requestId = request?.requestId ?: response?.requestId ?: "",
            attempt = (request?.attempt ?: response?.attempt ?: 1).takeIf { it > 0 } ?: 1,
            label = "",
            method = request?.method.orEmpty(),
            url = request?.url.orEmpty(),
            requestHeaders = request?.headers.orEmpty().map { (key, value) -> key to value },
            requestBody = taskCenterCaptureField(request?.bodyState, request?.body),
            requestState = request?.bodyState ?: "NOT_CAPTURED",
            status = response?.status?.toString().orEmpty(),
            responseHeaders = response?.headers.orEmpty().map { (key, value) -> key to value },
            responseBody = taskCenterCaptureField(response?.bodyState, response?.body),
            responseState = response?.bodyState ?: "NOT_CAPTURED",
            durationMs = response?.durationMs,
            droppedBytes = request?.droppedBytes ?: response?.droppedBytes,
            sseEvents = sse,
        )
    }
}

private fun taskCenterCaptureField(state: String?, body: String?): TaskCenterLogField = when {
    state == null -> TaskCenterLogField.Missing
    state == "EMPTY" -> TaskCenterLogField("", collected = true, state = state)
    state == "CAPTURED" || state == "TRUNCATED" || state == "INCOMPLETE" ->
        TaskCenterLogField(taskCenterRedactSensitive(body.orEmpty()), collected = true, state = state)
    else -> TaskCenterLogField("", collected = false, state = state)
}

/** Request identity for display: the captured request id when known, never a guessed retry number. */
internal fun taskCenterRequestTitle(detail: TaskCenterRequestDetail): String =
    if (detail.requestId.isNotBlank()) "${detail.label} · ${detail.requestId}" else detail.label

/** Maps captured calls onto the request detail model used by the debug screens. */
internal fun taskCenterCapturedRequestDetails(httpLog: String): List<TaskCenterRequestDetail> =
    taskCenterCapturedRequestDetails(taskCenterCapturedRequests(httpLog))

internal fun taskCenterCapturedRequestDetails(
    captured: List<TaskCenterCapturedRequest>,
): List<TaskCenterRequestDetail> = captured.map { capturedRequest ->
        val queryRows = capturedRequest.url.substringAfter('?', "").substringBefore('#')
            .takeIf { it.isNotBlank() }
            ?.split('&')
            ?.mapNotNull { part ->
                val key = part.substringBefore('=', "").trim()
                if (key.isBlank()) null else key to part.substringAfter('=', "")
            }
            .orEmpty()
        TaskCenterRequestDetail(
            label = capturedRequest.label.ifBlank { capturedRequest.requestId },
            attemptNumber = capturedRequest.attempt,
            requestId = capturedRequest.requestId.takeIf { it != "unpaired" }.orEmpty(),
            method = capturedRequest.method,
            url = capturedRequest.url,
            queryRows = queryRows,
            requestHeaders = capturedRequest.requestHeaders,
            requestBody = capturedRequest.requestBody,
            responseStatus = capturedRequest.status,
            responseHeaders = capturedRequest.responseHeaders,
            responseBody = if (capturedRequest.sseEvents.isNotEmpty()) {
                TaskCenterLogField(
                    capturedRequest.sseEvents.joinToString("\n\n"),
                    collected = true,
                    state = capturedRequest.responseState,
                )
            } else {
                capturedRequest.responseBody
            },
            errorLines = emptyList(),
            requestedAtMs = null,
            respondedAtMs = null,
            durationMsOverride = capturedRequest.durationMs,
        )
    }
