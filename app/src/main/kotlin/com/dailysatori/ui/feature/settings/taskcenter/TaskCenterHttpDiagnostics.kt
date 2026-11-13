package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.asynctask.asyncTaskStatusDisplayName
import com.dailysatori.service.diagnostics.TASK_HTTP_KIND_BUSINESS_RESULT
import com.dailysatori.service.diagnostics.TASK_HTTP_KIND_HTTP
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

internal const val TASK_HTTP_ROUTE_OMITTED = "[dynamic-path-omitted]"

/**
 * One text-log HTTP record. The real writer stores `HTTP request {method=…, service=…, route=…}`;
 * older logs used `[label] METHOD url`. A response is paired only by an explicit requestId + attempt,
 * never by label or by list order.
 */
internal data class TaskCenterHttpLogEntry(
    val label: String,
    val request: String,
    val response: String,
    val paired: Boolean = false,
) {
    private val requestHead: String
        get() = taskCenterLogLineBody(request.lines().firstOrNull().orEmpty())

    private val responseHead: String
        get() = taskCenterLogLineBody(response.lines().firstOrNull().orEmpty())

    private val requestMap: Map<String, String>
        get() = taskCenterHttpRequestMap(requestHead)

    private val requestLegacy: String
        get() = if (requestHead.startsWith("HTTP request [")) requestHead.substringAfter("] ", "") else ""

    private val requestParamsText: String
        get() = request.substringAfter(" params=", missingDelimiterValue = "")
            .substringBefore('\n')
            .substringBefore(" label=")
            .substringBefore(" requestId=")
            .substringBefore(" attempt=")
            .trim()

    val requestMethod: String
        get() = requestMap["method"] ?: requestLegacy.substringBefore(" ", missingDelimiterValue = "").trim()

    val requestUrl: String
        get() = requestMap["url"] ?: requestLegacy
            .substringAfter(" ", missingDelimiterValue = "")
            .substringBefore(" params=", missingDelimiterValue = requestLegacy.substringAfter(" ", missingDelimiterValue = ""))
            .trim()

    val service: String
        get() = requestMap["service"] ?: ""

    val route: String
        get() = requestMap["route"] ?: ""

    val requestParams: String
        get() = requestParamsText

    val requestParamRows: List<Pair<String, String>>
        get() = taskCenterKeyValueRows(requestParamsText)

    /** Explicit identity written next to the line; empty for older logs, which are never guessed. */
    val requestId: String
        get() = taskCenterHttpMetaValue(requestHead, "requestId")
            .ifBlank { taskCenterHttpMetaValue(responseHead, "requestId") }

    val attempt: Int?
        get() = taskCenterHttpMetaValue(requestHead, "attempt").toIntOrNull()
            ?: taskCenterHttpMetaValue(responseHead, "attempt").toIntOrNull()

    /** Explicit source marker; old lines have none and stay unknown. */
    val kind: String?
        get() = taskCenterHttpMetaValue(requestHead, "kind").ifBlank {
            taskCenterHttpMetaValue(responseHead, "kind")
        }.ifBlank { null }

    val source: TaskCenterRecordSource
        get() = taskCenterRecordSource(kind)

    val identity: String
        get() = if (requestId.isNotBlank() && attempt != null) requestId + "#" + attempt else ""

    val requestAtMs: Long?
        get() = taskCenterLogLineInstant(request)?.toEpochMilliseconds()

    val responseAtMs: Long?
        get() = taskCenterLogLineInstant(response)?.toEpochMilliseconds()

    val responseStatus: String
        get() = responseHead.substringAfter("status=", missingDelimiterValue = "")
            .substringBefore(" headers=")
            .substringBefore(" body=")
            .trim()

    val responseHeaders: String
        get() = responseHead.substringAfter(" headers=", missingDelimiterValue = "")
            .substringBefore(" body=")
            .trim()

    val responseBody: String
        get() = response.substringAfter(" body=", missingDelimiterValue = "")
            .substringBefore('\n')
            .substringBefore(" label=")
            .substringBefore(" requestId=")
            .substringBefore(" attempt=")
            .trim()

    fun durationLabel(strings: TaskCenterStrings): String =
        if (paired) taskCenterStepDurationText(request, response, strings) else strings.unknown

    fun formattedResponse(): String {
        val body = responseBody
        if (body.isBlank()) return response
        val formatted = taskCenterFormatJson(body)
        return if (formatted == body) response else response.substringBefore(" body=") + " body=\n" + formatted
    }

    fun formattedResponseBody(): String = taskCenterFormatJson(responseBody)
}

private val TASK_HTTP_META_REQUEST_ID =
    Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
private val TASK_HTTP_META_LABEL = Regex("""^[a-z][a-z0-9_]{0,31}""")
private val TASK_HTTP_META_PATTERNS = mapOf(
    "requestId" to Regex("""(?:^|\s)requestId=([^\s]+)"""),
    "label" to Regex("""(?:^|\s)label=([^\s]+)"""),
    "attempt" to Regex("""(?:^|\s)attempt=([^\s]+)"""),
    "kind" to Regex("""(?:^|\s)kind=([^\s]+)"""),
)

private fun taskCenterHttpMetaValue(line: String, key: String): String {
    val value = TASK_HTTP_META_PATTERNS[key]?.find(line)?.groupValues?.getOrNull(1) ?: return ""
    return when (key) {
        "requestId" -> value.takeIf { TASK_HTTP_META_REQUEST_ID.matches(it) } ?: ""
        "label" -> value.takeIf { TASK_HTTP_META_LABEL.matches(it) } ?: ""
        "attempt" -> value.takeIf { it.toIntOrNull() != null } ?: ""
        "kind" -> value.takeIf { it == TASK_HTTP_KIND_HTTP || it == TASK_HTTP_KIND_BUSINESS_RESULT } ?: ""
        else -> value
    }
}

private fun taskCenterHttpRequestMap(head: String): Map<String, String> {
    if (!head.contains("HTTP request {")) return emptyMap()
    val body = head.substringAfter("HTTP request {").substringBefore("}", "")
    return buildMap {
        Regex("""method=([A-Z]+)""").find(body)?.groupValues?.getOrNull(1)?.let { put("method", it) }
        Regex("""url=([^,}]+)""").find(body)?.groupValues?.getOrNull(1)?.trim()?.let { put("url", it) }
        Regex("""service=([^,}\s]+)""").find(body)?.groupValues?.getOrNull(1)?.trim()?.let { put("service", it) }
        Regex("""route=([^,}]+)""").find(body)?.groupValues?.getOrNull(1)?.trim()?.let { put("route", it) }
    }
}

private fun taskCenterHttpEntryLabel(request: String, response: String): String {
    val requestHead = taskCenterLogLineBody(request.lines().firstOrNull().orEmpty())
    val bracket = requestHead.substringAfter("HTTP request [", "").substringBefore("]")
    if (bracket.isNotBlank()) return bracket
    return taskCenterHttpMetaValue(requestHead, "label").ifBlank {
        taskCenterHttpMetaValue(taskCenterLogLineBody(response.lines().firstOrNull().orEmpty()), "label")
    }
}

private fun taskCenterHttpIdentity(line: String): String {
    val id = taskCenterHttpMetaValue(line, "requestId")
    val attempt = taskCenterHttpMetaValue(line, "attempt").toIntOrNull()
    return if (id.isNotBlank() && attempt != null) id + "#" + attempt else ""
}

/** Header, JSON or stack lines continue the active HTTP block; a new timed line is its own record. */
private val TASK_HTTP_HEADER_LINE = Regex("""^[A-Za-z0-9_.-]{1,64}\s*:\s*.*""")

private fun taskCenterIsHttpContinuation(body: String): Boolean {
    val trimmed = body.trimStart()
    return TASK_HTTP_HEADER_LINE.matches(body) ||
        trimmed.startsWith("{") || trimmed.startsWith("[") ||
        trimmed.startsWith("\tat ") || trimmed.startsWith("Caused by:") || trimmed.contains("Exception")
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
    /** Sanitized service/route written by the real text logger; the full url may be unknown. */
    val service: String = "",
    val route: String = "",
    /** True only when the attempt number was explicitly recorded, never derived from ordering. */
    val attemptKnown: Boolean = false,
    /** A request whose response could not be matched by an explicit id + attempt. */
    val associationUnknown: Boolean = false,
    /** Explicit record source; unknown for old files without a marker. */
    val source: TaskCenterRecordSource = TaskCenterRecordSource.Unknown,
    /** True when this detail was projected from the capture records rather than the task log text. */
    val fromCapture: Boolean = false,
) {
    val baseUrl: String
        get() = url.substringBefore('?')

    val endpointLabel: String
        get() = when {
            url.isNotBlank() -> baseUrl
            route.isNotBlank() && route != TASK_HTTP_ROUTE_OMITTED ->
                listOf(service, route).filter { it.isNotBlank() }.joinToString(" ")
            service.isNotBlank() -> service
            else -> ""
        }

    val fullUrl: String
        get() = if (queryRows.isEmpty()) {
            url
        } else {
            baseUrl + "?" + queryRows.joinToString("&") { it.first + "=" + it.second }
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
            (requestHeaders + responseHeaders).joinToString("\n") { it.first + ": " + it.second } +
                "\n" + requestBody.text + "\n" + responseBody.text + "\n" + fullUrl,
        )
}

/**
 * Request rows come only from real request lines. A response joins its request only when both carry
 * the same explicit requestId + attempt; everything else stays a standalone record with unknown
 * association, and no time or status is inferred from ordering.
 */
internal fun taskCenterRequestDetails(taskLog: String): List<TaskCenterRequestDetail> =
    taskCenterHttpLogEntries(taskLog)
        .filter { it.request.isNotBlank() }
        .map { it.toRequestDetail() }

internal fun TaskCenterHttpLogEntry.toRequestDetail(): TaskCenterRequestDetail {
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
    val responseText = if (paired) response else ""
    val headersText = if (paired) responseHeaders else ""
    val headersCollected = headersText.isNotBlank() && headersText != "[omitted]"
    val bodyText = if (paired) responseBody else ""
    val bodyCollected = bodyText.isNotBlank() && bodyText != "[omitted]"
    // The elapsed value is the span between the two real log writes of a reliably paired record;
    // it is never derived for unpaired/unknown records.
    val startedAt = if (paired) requestAtMs else null
    val finishedAt = if (paired) responseAtMs else null
    val elapsed = if (startedAt != null && finishedAt != null) (finishedAt - startedAt).takeIf { it >= 0L } else null
    return TaskCenterRequestDetail(
        label = label.ifBlank { taskCenterHttpEntryLabel(request, response) },
        attemptNumber = attempt ?: 1,
        requestId = requestId,
        method = requestMethod.uppercase(),
        url = requestUrl,
        queryRows = queryRows,
        requestHeaders = requestHeaderRows,
        requestBody = requestBody,
        responseStatus = if (paired) responseStatus else "",
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
        requestedAtMs = requestAtMs,
        respondedAtMs = finishedAt,
        durationMsOverride = elapsed,
        service = service,
        route = route,
        attemptKnown = attempt != null,
        associationUnknown = !paired,
        source = source,
        fromCapture = false,
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
    val lines = mutableListOf(
        "# source=" + taskCenterSourceKey(detail.source),
        if (detail.method.isBlank()) {
            "curl '" + taskCenterRedactSensitive(detail.fullUrl) + "'"
        } else {
            "curl -X " + detail.method + " '" + taskCenterRedactSensitive(detail.fullUrl) + "'"
        },
    )
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

/**
 * Other attempts of the same logical call. Only an explicit request id links records; an empty id
 * (old logs, unpaired business events) never links by label or ordering.
 */
internal fun taskCenterRelatedAttempts(
    selected: TaskCenterRequestDetail,
    attempts: List<TaskCenterRequestDetail>,
): List<TaskCenterRequestDetail> = if (selected.requestId.isBlank()) {
    emptyList()
} else {
    attempts.filter { it !== selected && it.requestId == selected.requestId }
}

/** A timed log line that is neither a TASK event nor an HTTP record (for example a fetch summary). */
internal data class TaskCenterLogInfoLine(val atMs: Long?, val text: String)

internal data class TaskCenterHttpLogParse(
    val entries: List<TaskCenterHttpLogEntry>,
    val infoLines: List<TaskCenterLogInfoLine>,
)

private class MutableTaskCenterHttpEntry(var request: String) {
    var response: String = ""
    var paired: Boolean = false
}

internal fun taskCenterHttpLogParse(taskLog: String): TaskCenterHttpLogParse {
    val entries = mutableListOf<MutableTaskCenterHttpEntry>()
    val pending = mutableMapOf<String, MutableTaskCenterHttpEntry>()
    val infoLines = mutableListOf<TaskCenterLogInfoLine>()
    var active: MutableTaskCenterHttpEntry? = null
    var activeIsResponse = false

    taskLog.filterNot { it == '\u0000' }.lineSequence().forEach { raw ->
        val line = raw.trimEnd()
        if (line.isBlank()) return@forEach
        val body = taskCenterLogLineBody(line)
        when {
            taskCenterIsLifecycleLogLine(line) -> {
                active = null
                activeIsResponse = false
            }
            body.contains("HTTP request [") || body.contains("HTTP request {") -> {
                val entry = MutableTaskCenterHttpEntry(line)
                entries += entry
                val identity = taskCenterHttpIdentity(line)
                if (identity.isNotBlank()) pending[identity] = entry
                active = entry
                activeIsResponse = false
            }
            body.contains("HTTP response") -> {
                val identity = taskCenterHttpIdentity(line)
                val match = identity.takeIf { it.isNotBlank() }?.let { pending.remove(it) }
                if (match != null) {
                    match.response = if (match.response.isBlank()) line else match.response + "\n" + line
                    match.paired = true
                    active = match
                } else {
                    val standalone = MutableTaskCenterHttpEntry("")
                    standalone.response = line
                    entries += standalone
                    active = standalone
                }
                activeIsResponse = true
            }
            active != null && taskCenterIsHttpContinuation(body) -> {
                val entry = active
                if (activeIsResponse) entry.response += "\n" + line else entry.request += "\n" + line
            }
            else -> {
                active = null
                activeIsResponse = false
                // Only timed lines are standalone records; an untimed orphan belongs to a body.
                if (taskCenterLogLineInstant(line) != null) {
                    infoLines += TaskCenterLogInfoLine(taskCenterLogLineInstant(line)?.toEpochMilliseconds(), body)
                }
            }
        }
    }

    val projected = entries.map { entry ->
        TaskCenterHttpLogEntry(
            label = taskCenterHttpEntryLabel(entry.request, entry.response),
            request = entry.request,
            response = entry.response,
            paired = entry.paired,
        )
    }.sortedBy { it.requestAtMs ?: it.responseAtMs ?: Long.MAX_VALUE }
    return TaskCenterHttpLogParse(projected, infoLines)
}

internal fun taskCenterHttpLogEntries(taskLog: String): List<TaskCenterHttpLogEntry> =
    taskCenterHttpLogParse(taskLog).entries
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
    val all = taskCenterHttpLogEntries(taskLog)
    val startIndex = pageIndex * pageSize
    val endExclusive = (startIndex + pageSize).coerceAtMost(all.size)
    val entries = if (startIndex >= all.size) emptyList() else all.subList(startIndex, endExclusive)
    return TaskCenterHttpLogPage(
        entries = entries,
        pageIndex = pageIndex,
        pageSize = pageSize,
        totalEntries = null,
        hasNextPage = endExclusive < all.size,
    )
}
private fun taskCenterIsLifecycleLogLine(line: String): Boolean =
    Regex("""^\S+(?:\s+\S+)?\s+TASK\s+""").containsMatchIn(line)

internal fun taskCenterLogLineBody(line: String): String {
    val first = line.substringBefore(' ', missingDelimiterValue = line)
    if (runCatching { Instant.parse(first) }.isSuccess) {
        return line.substringAfter(' ', missingDelimiterValue = "")
    }
    // Older writers produced "yyyy-MM-dd HH:mm:ss"; strip both tokens instead of keeping them in the body.
    val parts = line.split(' ', limit = 3)
    return if (parts.size == 3 && TASK_LOG_LEGACY_DATE.matches(parts[0]) && TASK_LOG_LEGACY_TIME.matches(parts[1])) {
        parts[2]
    } else {
        line
    }
}

private val TASK_LOG_LEGACY_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
private val TASK_LOG_LEGACY_TIME = Regex("""\d{2}:\d{2}:\d{2}(?:\.\d+)?""")

internal fun taskCenterStepDurationText(startLine: String, endLine: String, strings: TaskCenterStrings): String {
    val startedAt = taskCenterLogLineInstant(startLine) ?: return strings.notStarted
    val endedAt = taskCenterLogLineInstant(endLine) ?: return strings.notStarted
    val millis = endedAt.toEpochMilliseconds() - startedAt.toEpochMilliseconds()
    if (millis < 0L) return strings.notStarted
    return taskCenterDurationText(millis, strings)
}

internal fun taskCenterLogLineInstant(line: String): Instant? {
    val token = line.substringBefore(" ", missingDelimiterValue = "").trim()
    if (token.isNotBlank()) {
        runCatching { Instant.parse(token) }.getOrNull()?.let { return it }
    }
    // Legacy "yyyy-MM-dd HH:mm:ss" lines keep their real time instead of becoming unknown.
    val parts = line.trimStart().split(' ', limit = 3)
    if (parts.size == 3 && TASK_LOG_LEGACY_DATE.matches(parts[0]) && TASK_LOG_LEGACY_TIME.matches(parts[1])) {
        return runCatching { Instant.parse(parts[0] + "T" + parts[1] + "Z") }.getOrNull()
    }
    return null
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

internal enum class TaskCenterLifecycleEventKind { Started, Progress, Succeeded, Failed, Retry, Cancelled, Interrupted, Unknown }

internal data class TaskCenterLifecycleEvent(
    val atMs: Long?,
    val summary: String,
    val kind: TaskCenterLifecycleEventKind = TaskCenterLifecycleEventKind.Unknown,
) {
    /** Rows the log page treats as abnormal when the user picks "只看异常". */
    val abnormal: Boolean
        get() = kind == TaskCenterLifecycleEventKind.Failed ||
            kind == TaskCenterLifecycleEventKind.Retry ||
            kind == TaskCenterLifecycleEventKind.Cancelled ||
            kind == TaskCenterLifecycleEventKind.Interrupted
}

internal fun taskCenterLifecycleEvents(log: String, strings: TaskCenterStrings): List<TaskCenterLifecycleEvent> {
    val events = log.lineSequence()
        .filter { it.substringAfter(' ').startsWith("TASK ") }
        .map { line ->
            val event = line.substringAfter("TASK ")
            val kind = when {
                event.startsWith("started") -> TaskCenterLifecycleEventKind.Started
                event.startsWith("progress") -> TaskCenterLifecycleEventKind.Progress
                event.startsWith("succeeded") -> TaskCenterLifecycleEventKind.Succeeded
                event.startsWith("failed") -> TaskCenterLifecycleEventKind.Failed
                event.startsWith("retry") -> TaskCenterLifecycleEventKind.Retry
                event.startsWith("cancelled") -> TaskCenterLifecycleEventKind.Cancelled
                event.startsWith("interrupted") -> TaskCenterLifecycleEventKind.Interrupted
                else -> TaskCenterLifecycleEventKind.Unknown
            }
            val summary = when (kind) {
                TaskCenterLifecycleEventKind.Started -> strings.evStarted
                TaskCenterLifecycleEventKind.Progress ->
                    event.substringAfter("message=", strings.evProgress).substringBefore(" checkpoint=")
                TaskCenterLifecycleEventKind.Succeeded -> strings.evSucceeded
                TaskCenterLifecycleEventKind.Failed -> String.format(strings.evFailed, taskCenterEventReason(event, strings))
                TaskCenterLifecycleEventKind.Retry -> buildString {
                    append(String.format(strings.evRetry, taskCenterEventReason(event, strings)))
                    event.substringAfter("runAfterMs=", "").toLongOrNull()?.let {
                        append(String.format(strings.evRetryAt, taskCenterTimestampText(it, strings)))
                    }
                }
                TaskCenterLifecycleEventKind.Cancelled -> strings.evCancelled
                TaskCenterLifecycleEventKind.Interrupted -> strings.evInterrupted
                TaskCenterLifecycleEventKind.Unknown -> strings.evUnknown
            }
            TaskCenterLifecycleEvent(
                atMs = runCatching { Instant.parse(line.substringBefore(' ')).toEpochMilliseconds() }.getOrNull(),
                summary = summary,
                kind = kind,
            )
        }
        .toList()
    // Consecutive identical events are real log lines; they are kept, never silently folded away.
    return events.takeLast(60)
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
