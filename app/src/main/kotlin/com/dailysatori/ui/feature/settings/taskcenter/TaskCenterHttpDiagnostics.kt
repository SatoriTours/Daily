package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.asynctask.asyncTaskStatusDisplayName
import com.dailysatori.shared.db.Async_task
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Stable protocol placeholder written by the redactor; the UI renders it through a resource. */
internal const val TASK_CENTER_REDACTED = "[已隐藏]"

/** Marker for "this chunk is not complete JSON"; the message itself comes from resources. */
internal const val TASK_CENTER_SSE_INVALID = "SSE_CHUNK_NOT_JSON"
private const val HTTP_LOG_PAGE_SIZE = 50

private val taskCenterSensitiveKeys = setOf(
    "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key", "apikey",
    "x-auth-token", "token", "access_token", "refresh_token", "id_token", "api_key", "secret",
    "client_secret", "password", "session", "credential", "credentials", "private_key", "bearer",
)
private val taskCenterHeaderLine = Regex("""^([A-Za-z0-9_.-]{1,64})\s*:\s*(.*)$""")
private val taskCenterBearerToken = Regex("""(?i)(bearer\s+)[A-Za-z0-9._+/=-]{8,}""")
private val taskCenterSecretLike = Regex("""sk-[A-Za-z0-9_-]{8,}""")
private val taskCenterKeyValueSecret = Regex(
    """(?i)\b(${taskCenterSensitiveKeys.joinToString("|") { Regex.escape(it) }})=([^&\s,}\]]+)""",
)
private val taskCenterJsonSecret = Regex(
    """(?i)("(?:${taskCenterSensitiveKeys.joinToString("|") { Regex.escape(it) }}")\s*:\s*)"([^"]*)"""",
)

internal fun taskCenterIsSensitiveKey(key: String): Boolean = key.trim().lowercase() in taskCenterSensitiveKeys

/** Display-layer safety net: captured plaintext must never reach the screen or the clipboard. */
internal fun taskCenterRedactSensitive(text: String): String {
    if (text.isEmpty()) return text
    var value = text
    value = taskCenterHeaderLine.replace(value) { match ->
        val name = match.groupValues[1]
        if (taskCenterIsSensitiveKey(name)) "$name: $TASK_CENTER_REDACTED" else match.value
    }
    value = taskCenterJsonSecret.replace(value) { match -> "${match.groupValues[1]}\"$TASK_CENTER_REDACTED\"" }
    value = taskCenterKeyValueSecret.replace(value) { match -> "${match.groupValues[1]}=$TASK_CENTER_REDACTED" }
    value = taskCenterBearerToken.replace(value) { match -> "${match.groupValues[1]}$TASK_CENTER_REDACTED" }
    value = taskCenterSecretLike.replace(value, TASK_CENTER_REDACTED)
    return value
}

internal fun taskCenterContainsCredential(text: String): Boolean =
    text.isNotEmpty() && (
        taskCenterBearerToken.containsMatchIn(text) ||
            taskCenterSecretLike.containsMatchIn(text) ||
            text.lineSequence().any { line ->
                taskCenterKeyValueSecret.find(line)?.groupValues?.getOrNull(2)
                    ?.let { it.isNotBlank() && it != TASK_CENTER_REDACTED } == true ||
                    taskCenterJsonSecret.find(line)?.groupValues?.getOrNull(2)
                        ?.let { it.isNotBlank() && it != TASK_CENTER_REDACTED } == true
            }
        )

internal data class TaskCenterLogField(
    val text: String,
    val collected: Boolean,
    val state: String = if (collected) "CAPTURED" else "NOT_CAPTURED",
) {
    val emptyByDesign: Boolean
        get() = state == "EMPTY"

    companion object {
        val Missing = TaskCenterLogField("", false)
    }
}

internal data class TaskCenterHttpLogEntry(
    val label: String,
    val request: String,
    val response: String,
) {
    private val requestDetail: String
        get() = taskCenterLogLineBody(request.lines().firstOrNull().orEmpty())
            .substringAfter("] ", missingDelimiterValue = "")

    private val requestMap: Map<String, String>
        get() {
            val head = taskCenterLogLineBody(request.lines().firstOrNull().orEmpty())
            if (!head.contains("HTTP request {")) return emptyMap()
            return head.substringAfter("HTTP request {").substringBefore("}", "")
                .split(',')
                .mapNotNull { part ->
                    val key = part.substringBefore('=', "").trim()
                    if (key.isBlank()) null else key to part.substringAfter('=', "").trim()
                }
                .toMap()
        }

    private val responseMeta: String
        get() = response.substringBefore(" body=", missingDelimiterValue = response)

    val requestMethod: String
        get() = requestMap["method"] ?: requestDetail.substringBefore(" ", missingDelimiterValue = "").trim()

    val requestUrl: String
        get() = requestMap["url"] ?: requestDetail
            .substringAfter(" ", missingDelimiterValue = "")
            .substringBefore(" params=", missingDelimiterValue = requestDetail.substringAfter(" ", missingDelimiterValue = ""))
            .trim()

    val requestParams: String
        get() = request.substringAfter(" params=", missingDelimiterValue = "")
            .substringBefore('\n')
            .trim()

    val requestParamRows: List<Pair<String, String>>
        get() = taskCenterKeyValueRows(requestParams)

    val responseStatus: String
        get() = responseMeta.substringAfter("status=", missingDelimiterValue = "").substringBefore(" headers=").trim()

    val responseHeaders: String
        get() = responseMeta.substringAfter(" headers=", missingDelimiterValue = "").trim()

    val responseBody: String
        get() = response.substringAfter(" body=", missingDelimiterValue = "").trim()

    fun durationLabel(strings: TaskCenterStrings): String = taskCenterStepDurationText(request, response, strings)

    fun formattedResponse(): String {
        val body = responseBody
        if (body.isBlank()) return response
        val formatted = taskCenterFormatJson(body)
        return if (formatted == body) response else response.substringBefore(" body=") + " body=\n" + formatted
    }

    fun formattedResponseBody(): String = taskCenterFormatJson(responseBody)
}

private fun taskCenterKeyValueRows(text: String): List<Pair<String, String>> {
    val trimmed = text.trim()
    if (trimmed.isBlank() || trimmed == "[omitted]") return emptyList()
    return trimmed.split(' ')
        .mapNotNull { part ->
            val key = part.substringBefore('=', "").trim()
            if (key.isBlank()) null else key to part.substringAfter('=', "").trim()
        }
}

internal data class TaskCenterRequestDetail(
    val label: String,
    val attemptNumber: Int,
    val requestId: String = "",
    val method: String,
    val url: String,
    val queryRows: List<Pair<String, String>>,
    val requestHeaders: List<Pair<String, String>>,
    val requestBody: TaskCenterLogField,
    val responseStatus: String,
    val responseHeaders: List<Pair<String, String>>,
    val responseBody: TaskCenterLogField,
    val errorLines: List<String>,
    val requestedAtMs: Long?,
    val respondedAtMs: Long?,
    val durationMsOverride: Long? = null,
) {
    val baseUrl: String
        get() = url.substringBefore('?')

    val fullUrl: String
        get() = if (queryRows.isEmpty()) {
            url
        } else {
            baseUrl + "?" + queryRows.joinToString("&") { "${it.first}=${it.second}" }
        }

    val durationMs: Long?
        get() {
            durationMsOverride?.let { return it }
            val start = requestedAtMs ?: return null
            val end = respondedAtMs ?: return null
            return (end - start).takeIf { it >= 0L }
        }

    fun durationLabel(strings: TaskCenterStrings): String = taskCenterDurationText(durationMs, strings)

    val httpOk: Boolean?
        get() = responseStatus.toIntOrNull()?.let { it in 200..299 }

    val stream: TaskCenterSseView?
        get() = taskCenterSseView(responseBody.text).takeIf { it.chunks.isNotEmpty() }

    val responseIncomplete: Boolean
        get() = stream?.incomplete == true

    val collectsSensitivePlaintext: Boolean
        get() = taskCenterContainsCredential(
            (requestHeaders + responseHeaders).joinToString("\n") { "${it.first}: ${it.second}" } +
                "\n" + requestBody.text + "\n" + responseBody.text + "\n" + fullUrl,
        )
}

internal fun taskCenterRequestDetails(taskLog: String): List<TaskCenterRequestDetail> {
    val attempts = mutableMapOf<String, Int>()
    return taskCenterHttpLogEntries(taskLog).map { entry ->
        val key = entry.label + "|" + entry.requestParams + "|" + entry.requestUrl.substringBefore('?')
        val attempt = (attempts[key] ?: 0) + 1
        attempts[key] = attempt
        entry.toRequestDetail(attempt)
    }
}

private fun TaskCenterHttpLogEntry.toRequestDetail(attemptNumber: Int): TaskCenterRequestDetail {
    val requestLines = request.lines()
    val requestHeaderRows = ArrayList<Pair<String, String>>()
    val requestBodyLines = ArrayList<String>()
    var bodyStarted = false
    requestLines.drop(1).forEach { raw ->
        val text = taskCenterLogLineBody(raw)
        if (text.isBlank()) return@forEach
        val header = if (bodyStarted) null else taskCenterHeaderLine.matchEntire(text)?.let { match ->
            match.groupValues[1] to match.groupValues[2]
        }
        if (header != null) {
            requestHeaderRows += header.first to if (taskCenterIsSensitiveKey(header.first)) {
                TASK_CENTER_REDACTED
            } else {
                taskCenterRedactSensitive(header.second)
            }
        } else {
            bodyStarted = true
            requestBodyLines += text
        }
    }
    val requestBodyText = requestBodyLines.joinToString("\n")
    val requestBody = when {
        requestBodyText.isBlank() -> TaskCenterLogField.Missing
        requestBodyText.trim() == "[omitted]" -> TaskCenterLogField.Missing
        else -> TaskCenterLogField(taskCenterRedactSensitive(requestBodyText), collected = true)
    }
    val urlQueryRows = taskCenterUrlQueryRows(requestUrl)
    val queryRows = if (urlQueryRows.isNotEmpty()) urlQueryRows else requestParamRows
    val headersText = responseHeaders
    val headersCollected = headersText.isNotBlank() && headersText != "[omitted]"
    val bodyText = responseBody
    val bodyCollected = bodyText.isNotBlank() && bodyText != "[omitted]"
    return TaskCenterRequestDetail(
        label = label.ifBlank { "request" },
        attemptNumber = attemptNumber,
        method = requestMethod.uppercase(),
        url = requestUrl,
        queryRows = queryRows,
        requestHeaders = requestHeaderRows,
        requestBody = requestBody,
        responseStatus = responseStatus,
        responseHeaders = if (headersCollected) {
            taskCenterKeyValueRows(headersText).map { (key, value) -> key to taskCenterRedactSensitive(value) }
        } else {
            emptyList()
        },
        responseBody = if (bodyCollected) {
            TaskCenterLogField(taskCenterRedactSensitive(bodyText), collected = true)
        } else {
            TaskCenterLogField.Missing
        },
        errorLines = if (bodyCollected) {
            bodyText.lines().filter { taskCenterLooksLikeError(it) }.take(20).map { taskCenterRedactSensitive(it) }
        } else {
            emptyList()
        },
        requestedAtMs = taskCenterLogLineInstant(request)?.toEpochMilliseconds(),
        respondedAtMs = taskCenterLogLineInstant(response)?.toEpochMilliseconds(),
    )
}

private fun taskCenterUrlQueryRows(url: String): List<Pair<String, String>> {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isBlank()) return emptyList()
    return query.split('&').mapNotNull { part ->
        val key = part.substringBefore('=', "").trim()
        if (key.isBlank()) null else key to part.substringAfter('=', "").trim()
    }
}

private fun taskCenterLooksLikeError(line: String): Boolean =
    line.contains("Exception") || line.contains("Caused by:") || line.startsWith("\tat ") ||
        line.contains("Error:") || line.contains("\"error\"")

internal data class TaskCenterSseChunk(
    val index: Int,
    val raw: String,
    val pretty: String?,
    val parseErrorMessage: String?,
    val done: Boolean,
)

internal data class TaskCenterSseView(
    val chunks: List<TaskCenterSseChunk>,
    val doneReceived: Boolean,
    val incomplete: Boolean,
)

/**
 * Splits captured SSE output on real event boundaries. Each chunk keeps its original bytes, valid
 * JSON is pretty printed per chunk, and incomplete streams are flagged instead of completed.
 */
internal fun taskCenterSseView(body: String): TaskCenterSseView {
    val text = body.trim('\n')
    if (text.isBlank() || !text.lineSequence().any { it.startsWith("data:") }) {
        return TaskCenterSseView(emptyList(), doneReceived = false, incomplete = false)
    }
    val chunks = taskCenterSplitSseBlocks(text).mapIndexed { index, raw ->
        val payload = raw.lineSequence()
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trim() }
        if (payload.trim() == "[DONE]") {
            TaskCenterSseChunk(index, raw, pretty = null, parseErrorMessage = null, done = true)
        } else {
            val document = taskCenterJsonDocument(payload)
            TaskCenterSseChunk(
                index = index,
                raw = raw,
                pretty = document.text.takeIf { document.formatted },
                parseErrorMessage = if (document.formatted) null else document.errorMessage ?: TASK_CENTER_SSE_INVALID,
                done = false,
            )
        }
    }
    val doneReceived = chunks.any { it.done }
    return TaskCenterSseView(chunks, doneReceived, incomplete = !doneReceived)
}

private fun taskCenterSplitSseBlocks(text: String): List<String> {
    val blocks = ArrayList<String>()
    val current = StringBuilder()
    fun flush() {
        val block = current.toString().trim('\n')
        if (block.isNotBlank()) blocks += block
        current.setLength(0)
    }
    text.lineSequence().forEach { line ->
        when {
            line.isBlank() -> flush()
            line.startsWith("data:") && current.isNotBlank() -> {
                flush()
                current.append(line)
            }
            else -> {
                if (current.isNotEmpty()) current.append('\n')
                current.append(line)
            }
        }
    }
    flush()
    return blocks
}

internal fun taskCenterCurlCommand(detail: TaskCenterRequestDetail): String {
    val lines = mutableListOf("curl -X ${detail.method.ifBlank { "GET" }} '${taskCenterRedactSensitive(detail.fullUrl)}'")
    detail.requestHeaders.forEach { (key, value) ->
        lines += "  -H '${taskCenterRedactSensitive(key)}: ${taskCenterRedactSensitive(value)}'"
    }
    if (detail.requestBody.collected && detail.requestBody.text.isNotBlank()) {
        val body = taskCenterRedactSensitive(detail.requestBody.text).replace("'", "'\\''")
        lines += "  --data-raw '$body'"
    }
    return lines.joinToString(" \\\n")
}

internal data class TaskCenterHttpLogPage(
    val entries: List<TaskCenterHttpLogEntry>,
    val pageIndex: Int,
    val pageSize: Int,
    val totalEntries: Int?,
    val hasNextPage: Boolean,
) {
    val startIndex: Int
        get() = if (entries.isEmpty()) 0 else pageIndex * pageSize

    val endIndex: Int
        get() = startIndex + entries.size

    val hasPrevious: Boolean
        get() = pageIndex > 0

    val hasNext: Boolean
        get() = hasNextPage
}

internal fun taskCenterHttpLogEntries(taskLog: String): List<TaskCenterHttpLogEntry> {
    val pending = mutableListOf<MutableTaskCenterHttpLogEntry>()
    val completed = mutableListOf<MutableTaskCenterHttpLogEntry>()
    var activeResponse: MutableTaskCenterHttpLogEntry? = null

    taskLog.filterNot { it == '\u0000' }.lineSequence().forEach { line ->
        when {
            line.contains("HTTP request [") || line.contains("HTTP request {") -> {
                activeResponse = null
                pending += MutableTaskCenterHttpLogEntry(
                    label = line.substringAfter("HTTP request [", "").substringBefore("]"),
                    request = line,
                    response = "",
                )
            }
            line.contains("HTTP response") -> {
                val responseLabel = line.substringAfter("HTTP response [", "").substringBefore("]")
                val responseExternalId = taskCenterHttpExternalId(line)
                val matchIndex = pending.indexOfFirst { entry ->
                    entry.label == responseLabel &&
                        responseExternalId.isNotBlank() &&
                        taskCenterHttpExternalId(entry.request) == responseExternalId
                }.takeIf { it >= 0 } ?: pending.indexOfFirst { it.label == responseLabel }
                val entry = if (matchIndex >= 0) {
                    pending.removeAt(matchIndex)
                } else {
                    MutableTaskCenterHttpLogEntry(responseLabel, "", "")
                }
                entry.response = line
                completed += entry
                activeResponse = entry
            }
            activeResponse != null && !taskCenterIsLifecycleLogLine(line) -> {
                activeResponse?.response += "\n$line"
            }
            pending.isNotEmpty() -> {
                pending.last().request += "\n$line"
            }
        }
    }
    return (completed + pending)
        .map { TaskCenterHttpLogEntry(it.label.ifBlank { "request" }, it.request, it.response) }
        .sortedBy { entry ->
            taskCenterLogLineInstant(entry.request)
                ?: taskCenterLogLineInstant(entry.response)
                ?: Instant.DISTANT_FUTURE
        }
}

private data class MutableTaskCenterHttpLogEntry(
    val label: String,
    var request: String,
    var response: String,
)

private data class PendingTaskCenterHttpLogEntry(
    val label: String,
    val externalId: String,
    val pageEntry: MutableTaskCenterHttpLogEntry?,
)

private fun taskCenterHttpExternalId(line: String): String =
    line.substringAfter("externalId=", missingDelimiterValue = "")
        .substringBefore("&")
        .substringBefore(",")
        .substringBefore(" ")
        .substringBefore("\n")
        .trim()

internal fun taskCenterHttpLogPage(
    taskLog: String,
    pageIndex: Int,
    pageSize: Int = HTTP_LOG_PAGE_SIZE,
): TaskCenterHttpLogPage {
    val safePageSize = pageSize.coerceAtLeast(1)
    val requestedPageIndex = pageIndex.coerceAtLeast(0)
    return taskCenterHttpLogPageUnchecked(taskLog, requestedPageIndex, safePageSize)
}

private fun taskCenterHttpLogPageUnchecked(
    taskLog: String,
    pageIndex: Int,
    pageSize: Int,
): TaskCenterHttpLogPage {
    val startIndex = pageIndex * pageSize
    val endExclusive = startIndex + pageSize
    val pending = mutableListOf<PendingTaskCenterHttpLogEntry>()
    val pageEntries = mutableListOf<MutableTaskCenterHttpLogEntry>()
    var activeResponse: MutableTaskCenterHttpLogEntry? = null
    var activeRequest: MutableTaskCenterHttpLogEntry? = null
    var totalEntries = 0
    var hasNextPage = false

    run loop@{
        taskLog.filterNot { it == '\u0000' }.lineSequence().forEach { line ->
            when {
                line.contains("HTTP request [") || line.contains("HTTP request {") -> {
                    if (totalEntries >= endExclusive) {
                        hasNextPage = true
                        return@loop
                    }
                    activeResponse = null
                    val selected = totalEntries in startIndex until endExclusive
                    val pageEntry = if (selected) {
                        MutableTaskCenterHttpLogEntry(
                            label = line.substringAfter("HTTP request [", "").substringBefore("]"),
                            request = line,
                            response = "",
                        ).also { pageEntries += it }
                    } else {
                        null
                    }
                    pending += PendingTaskCenterHttpLogEntry(
                        label = line.substringAfter("HTTP request [", "").substringBefore("]"),
                        externalId = taskCenterHttpExternalId(line),
                        pageEntry = pageEntry,
                    )
                    activeRequest = pageEntry
                    totalEntries += 1
                }
                line.contains("HTTP response") -> {
                    activeRequest = null
                    val responseLabel = line.substringAfter("HTTP response [", "").substringBefore("]")
                    val responseExternalId = taskCenterHttpExternalId(line)
                    val matchIndex = pending.indexOfFirst { entry ->
                        entry.label == responseLabel &&
                            responseExternalId.isNotBlank() &&
                            entry.externalId == responseExternalId
                    }.takeIf { it >= 0 } ?: pending.indexOfFirst { it.label == responseLabel }
                    val entry = if (matchIndex >= 0) pending.removeAt(matchIndex).pageEntry else null
                    entry?.response = line
                    activeResponse = entry
                }
                activeResponse != null && !taskCenterIsLifecycleLogLine(line) -> {
                    activeResponse?.response += "\n$line"
                }
                activeRequest != null -> {
                    activeRequest?.request += "\n$line"
                }
            }
        }
    }

    return TaskCenterHttpLogPage(
        entries = pageEntries.map { TaskCenterHttpLogEntry(it.label.ifBlank { "request" }, it.request, it.response) },
        pageIndex = pageIndex,
        pageSize = pageSize,
        totalEntries = null,
        hasNextPage = hasNextPage,
    )
}

private fun taskCenterIsLifecycleLogLine(line: String): Boolean =
    Regex("""^\S+(?:\s+\S+)?\s+TASK\s+""").containsMatchIn(line)

internal fun taskCenterLogLineBody(line: String): String {
    val first = line.substringBefore(' ', missingDelimiterValue = line)
    return if (runCatching { Instant.parse(first) }.isSuccess) {
        line.substringAfter(' ', missingDelimiterValue = "")
    } else {
        line
    }
}

internal fun taskCenterStepDurationText(startLine: String, endLine: String, strings: TaskCenterStrings): String {
    val startedAt = taskCenterLogLineInstant(startLine) ?: return strings.notStarted
    val endedAt = taskCenterLogLineInstant(endLine) ?: return strings.notStarted
    val millis = endedAt.toEpochMilliseconds() - startedAt.toEpochMilliseconds()
    if (millis < 0L) return strings.notStarted
    return taskCenterDurationText(millis, strings)
}

internal fun taskCenterLogLineInstant(line: String): Instant? =
    line.substringBefore(" ", missingDelimiterValue = "").trim().takeIf { it.isNotBlank() }?.let { token ->
        runCatching { Instant.parse(token) }.getOrNull()
    }

internal fun taskCenterDurationText(millis: Long?, strings: TaskCenterStrings): String {
    if (millis == null || millis < 0L) return strings.unknown
    if (millis < 1_000L) return "${millis}ms"
    if (millis < 60_000L) return "${(millis / 100L) / 10.0}s"
    val seconds = millis / 1_000L
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    if (minutes < 60L) return "${minutes}m ${remainingSeconds}s"
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return "${hours}h ${remainingMinutes}m"
}

internal data class TaskCenterLifecycleEvent(val atMs: Long?, val summary: String)

internal fun taskCenterLifecycleEvents(log: String, strings: TaskCenterStrings): List<TaskCenterLifecycleEvent> {
    val events = log.lineSequence()
        .filter { it.substringAfter(' ').startsWith("TASK ") }
        .map { line ->
            val event = line.substringAfter("TASK ")
            val summary = when {
                event.startsWith("started") -> strings.evStarted
                event.startsWith("progress") -> event.substringAfter("message=", strings.evProgress).substringBefore(" checkpoint=")
                event.startsWith("succeeded") -> strings.evSucceeded
                event.startsWith("failed") -> String.format(strings.evFailed, taskCenterEventReason(event, strings))
                event.startsWith("retry") -> buildString {
                    append(String.format(strings.evRetry, taskCenterEventReason(event, strings)))
                    event.substringAfter("runAfterMs=", "").toLongOrNull()?.let {
                        append(String.format(strings.evRetryAt, taskCenterTimestampText(it, strings)))
                    }
                }
                event.startsWith("cancelled") -> strings.evCancelled
                event.startsWith("interrupted") -> strings.evInterrupted
                else -> strings.evUnknown
            }
            TaskCenterLifecycleEvent(
                atMs = runCatching { Instant.parse(line.substringBefore(' ')).toEpochMilliseconds() }.getOrNull(),
                summary = summary,
            )
        }
        .toList()
        .fold(mutableListOf<TaskCenterLifecycleEvent>()) { entries, event ->
            if (entries.lastOrNull()?.summary != event.summary) entries.add(event)
            entries
        }
    return events.takeLast(40)
}

internal fun taskCenterLifecycleText(log: String, strings: TaskCenterStrings): String = taskCenterLifecycleEvents(log, strings)
    .joinToString("\n") { event ->
        "${event.atMs?.let { taskCenterTimestampText(it, strings, includeSeconds = true) } ?: strings.unknown} · ${event.summary}"
    }
    .ifBlank { strings.logEmpty }

private fun taskCenterEventReason(event: String, strings: TaskCenterStrings): String = event.substringAfter("message=", "")
    .substringBefore(" runAfterMs=").ifBlank {
        event.substringAfter("code=", "").substringBefore(' ').ifBlank { strings.evReasonFallback }
    }

internal fun taskCenterExecutionSummary(task: Async_task, strings: TaskCenterStrings): String = buildList {
    taskCenterSubject(task.payload_json, strings).takeIf { it.isNotBlank() }?.let { add(it) }
    add(
        when (task.status) {
            "queued" -> strings.summaryQueued
            "retrying" -> String.format(strings.summaryRetrying, task.attempt_count)
            "running" -> task.progress_message.ifBlank { strings.summaryRunning }
            else -> asyncTaskStatusDisplayName(task.status)
        },
    )
    if (task.type == AsyncTaskType.external_favorite_sync.name) {
        add(String.format(strings.summaryFavoritePages, task.progress_current, task.progress_total))
        taskCenterCheckpointLong(task.checkpoint_json, "itemsSeen")?.let {
            add(String.format(strings.summaryFavoriteItems, it))
        }
    } else if (task.progress_total > 0) {
        add(String.format(strings.summaryProgress, task.progress_current, task.progress_total))
    }
    if (task.type == AsyncTaskType.external_favorite_organize.name && task.status != "running" && task.progress_message.isNotBlank()) {
        add(task.progress_message)
    }
    if (task.status == "retrying") {
        task.run_after_ms?.let {
            add(String.format(strings.summaryRetryAt, taskCenterTimestampText(it, strings)))
        }
    }
    if (task.last_error_message.isNotBlank()) {
        add(String.format(strings.summaryLastReason, task.last_error_message))
    }
}.joinToString("\n")

internal fun taskCenterSubject(payload: String, strings: TaskCenterStrings): String = runCatching {
    val values = Json.parseToJsonElement(payload).jsonObject
    val labels = listOf(
        "articleId" to strings.subjectArticle,
        "sourceId" to strings.subjectSource,
        "diaryId" to strings.subjectDiary,
        "attachmentId" to strings.subjectAttachment,
        "batchId" to strings.subjectBatch,
    )
    labels.firstNotNullOfOrNull { (key, template) ->
        values[key]?.jsonPrimitive?.content?.let { String.format(template, it) }
    } ?: values["url"]?.jsonPrimitive?.content?.substringBefore('?')?.substringBefore('#').orEmpty()
}.getOrDefault("")

internal fun taskCenterCheckpointLong(json: String, key: String): Long? =
    Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)""")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

internal fun taskCenterTimestampText(value: Long?, strings: TaskCenterStrings, includeSeconds: Boolean = false): String {
    if (value == null || value <= 0L) return strings.notStarted
    val time = Instant.fromEpochMilliseconds(value).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${time.monthNumber.toString().padStart(2, '0')}-${time.dayOfMonth.toString().padStart(2, '0')} " +
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}" +
        if (includeSeconds) ":${time.second.toString().padStart(2, '0')}" else ""
}

/** Why the request panel has nothing to show: a missing log is a different fact from a log without HTTP lines. */
internal enum class TaskCenterRequestEmptyKind { NoLog, NoHttpLines }

internal fun taskCenterRequestEmptyKind(taskLog: String): TaskCenterRequestEmptyKind {
    val text = taskLog.filterNot { it == '\u0000' }
    return if (text.isBlank()) TaskCenterRequestEmptyKind.NoLog else TaskCenterRequestEmptyKind.NoHttpLines
}
