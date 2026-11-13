package com.dailysatori.service.diagnostics

import kotlinx.datetime.Clock

/** Marker written instead of content that was never captured. */
const val TASK_HTTP_NOT_CAPTURED = "NOT_CAPTURED"

/** The call genuinely has no body (for example a GET); this is not a capture failure. */
const val TASK_HTTP_EMPTY = "EMPTY"
const val TASK_HTTP_OMITTED = "OMITTED"
const val TASK_HTTP_TRUNCATED = "TRUNCATED"
const val TASK_HTTP_INCOMPLETE = "INCOMPLETE"
const val TASK_HTTP_CAPTURED = "CAPTURED"

/** Explicit record source. Old files have neither value, so consumers must not assume raw HTTP. */
const val TASK_HTTP_KIND_HTTP = "http"
const val TASK_HTTP_KIND_BUSINESS_RESULT = "business_result"

/** Where the capture text came from; decoded text is not the raw wire bytes. */
enum class TaskHttpDirection { REQUEST, RESPONSE, SSE_EVENT }

/**
 * One line-oriented record. Records carry the existing correlation keys: task, trace and the request
 * id/attempt the transport layer already produced.
 */
data class TaskHttpRecord(
    val requestId: String,
    val attempt: Int,
    val direction: TaskHttpDirection,
    val method: String? = null,
    /** Sanitized full URL: keeps path and query names for debugging, masks credential values. */
    val url: String? = null,
    val service: String? = null,
    val route: String? = null,
    val status: Int? = null,
    val durationMs: Long? = null,
    val bytes: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val bodyState: String = TASK_HTTP_NOT_CAPTURED,
    val droppedBytes: Long? = null,
    /**
     * Real wall-clock time of the capture event, not a derived network timing: the request time is when
     * the request was written, the response time when the response arrived. Older records have null.
     */
    val requestAtMs: Long? = null,
    val responseAtMs: Long? = null,
    /** Optional source contract: raw HTTP transport vs a business-result record. */
    val kind: String? = null,
)

/**
 * Correlation handle for exactly one HTTP call. Bodies and SSE fragments bind to the handle of the call
 * that produced them, so concurrent calls inside one task can never swap payloads.
 */
data class TaskHttpRequestHandle(val requestId: String, val attempt: Int = 1) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<TaskHttpRequestHandle>
}

/** Sink for already-sanitized records; the app binds one, everything else stays a no-op. */
fun interface TaskHttpSink {
    fun write(taskId: Long, label: String, record: TaskHttpRecord)
}

/**
 * Explicit, process-scoped capture session. It is never armed by default: the UI must ask the user,
 * explain that bodies may contain private content, and call [arm]. A session expires after [ttlMs], is
 * lost on process restart, and [disarm] stops in-flight requests immediately because every write checks
 * the session again.
 */
class TaskHttpCaptureSession(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private class Session(val taskId: Long, val label: String, val armedAtMs: Long) {
        var attempts = 0
    }

    private val sessions = mutableMapOf<Long, Session>()
    private var sink: TaskHttpSink? = null

    fun bind(sink: TaskHttpSink?) { this.sink = sink }

    fun arm(taskId: Long, label: String) {
        if (taskId <= 0L) return
        synchronized(sessions) { sessions[taskId] = Session(taskId, label, nowMs()) }
    }

    fun disarm(taskId: Long) {
        synchronized(sessions) { sessions.remove(taskId) }
    }

    fun disarmAll() {
        synchronized(sessions) { sessions.clear() }
    }

    fun isArmed(taskId: Long?): Boolean = session(taskId) != null

    fun labelOf(taskId: Long): String? = session(taskId)?.label

    fun expiresAtMs(taskId: Long): Long? = session(taskId)?.let { it.armedAtMs + ttlMs }

    /** Derived state for UI: reading it already drops an expired session. */
    fun state(taskId: Long): TaskHttpCaptureState {
        val session = session(taskId) ?: return TaskHttpCaptureState.Off
        val expiresAt = session.armedAtMs + ttlMs
        return TaskHttpCaptureState.Armed(session.label, expiresAt, (expiresAt - nowMs()).coerceAtLeast(0L))
    }

    /**
     * Opens one explicit correlation handle; null when the task is not armed. The attempt counter is
     * monotonic per task, so retries never share an identity.
     */
    fun openRequest(taskId: Long?): TaskHttpRequestHandle? {
        val session = session(taskId) ?: return null
        val attempt = synchronized(sessions) { ++session.attempts }
        return TaskHttpRequestHandle(DiagnosticLog.newId(), attempt)
    }

    /**
     * Arms a follow-up execution of the same business task (for example a manual retry that gets a new
     * task id). It never arms anything globally and only inherits a still-armed, unexpired session.
     */
    fun armFollowUp(taskId: Long, previousTaskId: Long): Boolean {
        val previous = session(previousTaskId) ?: return false
        arm(taskId, previous.label)
        return true
    }

    fun record(taskId: Long?, record: TaskHttpRecord): Boolean {
        val currentSink = sink
        val session = session(taskId) ?: return false
        if (currentSink == null) return false
        currentSink.write(session.taskId, session.label, record)
        return true
    }

    /** Request side: sanitized full URL, allow-listed headers and the redacted request body. */
    fun request(
        taskId: Long?,
        handle: TaskHttpRequestHandle?,
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        kind: String = TASK_HTTP_KIND_HTTP,
    ): Boolean {
        val session = session(taskId) ?: return false
        val owner = handle ?: return false
        val currentSink = sink ?: return false
        val (redactedBody, bodyState, dropped) = if (body == null) {
            Triple(null, TASK_HTTP_EMPTY, null)
        } else {
            TaskHttpRedaction.truncateUtf8(TaskHttpRedaction.redactBody(body), DEFAULT_MAX_BODY_BYTES)
        }
        currentSink.write(session.taskId, session.label, TaskHttpRecord(
            requestId = owner.requestId,
            attempt = owner.attempt,
            direction = TaskHttpDirection.REQUEST,
            method = method,
            url = TaskHttpRedaction.url(url),
            headers = TaskHttpRedaction.headers(headers),
            body = redactedBody,
            bodyState = bodyState,
            droppedBytes = dropped,
            requestAtMs = nowMs(),
            kind = kind,
        ))
        return true
    }

    /** Body records are attributed to the handle of the call that produced them, never to "latest". */
    fun bodyRecord(
        taskId: Long?,
        handle: TaskHttpRequestHandle?,
        direction: TaskHttpDirection,
        text: String,
        maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES,
        status: Int? = null,
        kind: String = TASK_HTTP_KIND_HTTP,
    ): Boolean {
        val session = session(taskId) ?: return false
        val owner = handle ?: return false
        val (body, state, dropped) = TaskHttpRedaction.redactBody(text, direction == TaskHttpDirection.SSE_EVENT)
            .let { redacted -> TaskHttpRedaction.truncateUtf8(redacted, maxBodyBytes) }
        return record(taskId, TaskHttpRecord(
            requestId = owner.requestId,
            attempt = owner.attempt,
            direction = direction,
            status = status,
            body = if (state == TASK_HTTP_OMITTED) null else body,
            bodyState = state,
            droppedBytes = dropped,
            responseAtMs = if (direction == TaskHttpDirection.RESPONSE) nowMs() else null,
            kind = kind,
        ))
    }

    internal fun sseEvent(
        taskId: Long?,
        handle: TaskHttpRequestHandle?,
        direction: TaskHttpDirection,
        text: String,
        state: String,
        kind: String = TASK_HTTP_KIND_HTTP,
    ): Boolean {
        val session = session(taskId) ?: return false
        val currentSink = sink ?: return false
        val owner = handle ?: return false
        currentSink.write(session.taskId, session.label, TaskHttpRecord(
            requestId = owner.requestId,
            attempt = owner.attempt,
            direction = direction,
            body = text.ifBlank { null },
            bodyState = state,
            responseAtMs = if (direction == TaskHttpDirection.RESPONSE) nowMs() else null,
            kind = kind,
        ))
        return true
    }

    private fun session(taskId: Long?): Session? {
        val id = taskId ?: return null
        if (id <= 0L) return null
        return synchronized(sessions) {
            val session = sessions[id] ?: return null
            if (nowMs() - session.armedAtMs >= ttlMs) {
                sessions.remove(id)
                return null
            }
            session
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 30L * 60_000L
        const val DEFAULT_MAX_BODY_BYTES = 64 * 1024
        const val MAX_TASK_HTTP_BYTES = 2L * 1024 * 1024
        const val MAX_REQUEST_BODY_BYTES = 256 * 1024
    }
}

/** UI-observable capture state; expiry is derived on read, so no background timer is needed. */
sealed interface TaskHttpCaptureState {
    data object Off : TaskHttpCaptureState
    data class Armed(val label: String, val expiresAtMs: Long, val remainingMs: Long) : TaskHttpCaptureState
}

/** Process-wide handle; the app binds the sink, tests bind a recording one. */
object TaskHttpCapture {
    val session = TaskHttpCaptureSession()

    fun isArmed(taskId: Long?): Boolean = session.isArmed(taskId)
    fun state(taskId: Long): TaskHttpCaptureState = session.state(taskId)
    fun arm(taskId: Long, label: String) = session.arm(taskId, label)
    fun disarm(taskId: Long) = session.disarm(taskId)
    fun expiresAtMs(taskId: Long): Long? = session.expiresAtMs(taskId)
    fun armFollowUp(taskId: Long, previousTaskId: Long): Boolean = session.armFollowUp(taskId, previousTaskId)
    fun body(
        taskId: Long?,
        handle: TaskHttpRequestHandle?,
        direction: TaskHttpDirection,
        text: String,
        status: Int? = null,
        kind: String = TASK_HTTP_KIND_HTTP,
    ) = session.bodyRecord(taskId, handle, direction, text, status = status, kind = kind)
    fun request(
        taskId: Long?,
        handle: TaskHttpRequestHandle?,
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        kind: String = TASK_HTTP_KIND_HTTP,
    ) = session.request(taskId, handle, method, url, headers, body, kind = kind)
    fun record(taskId: Long?, record: TaskHttpRecord) = session.record(taskId, record)
}

internal fun currentCaptureTaskId(): Long? = DiagnosticLog.currentTrace()?.taskId

/** The handle of the HTTP call this coroutine belongs to; null when the task is not armed. */
suspend fun currentCaptureHandle(): TaskHttpRequestHandle? =
    kotlin.coroutines.coroutineContext[TaskHttpRequestHandle]

/**
 * Collects SSE at event boundaries: a credential split across chunks can never leak, an unterminated
 * tail is omitted instead of written, and budget exhaustion stops writing further content without
 * touching the business stream.
 */
class TaskHttpSseCapture(
    private val taskId: Long?,
    private val handle: TaskHttpRequestHandle?,
    private val maxBytes: Int = TaskHttpCaptureSession.MAX_REQUEST_BODY_BYTES,
    private val write: (TaskHttpDirection, String, String) -> Unit = { direction, text, state ->
        TaskHttpCapture.session.sseEvent(taskId, handle, direction, text, state)
    },
) {
    private val pending = StringBuilder()
    private var written = 0
    private var exhausted = false

    fun accept(line: String) {
        if (line.isBlank()) {
            flushEvent()
            return
        }
        if (pending.length + line.length > MAX_EVENT_CHARS) {
            // Oversized single event: never keep a partial credential-bearing fragment.
            pending.clear()
            return
        }
        pending.append(line).append('\n')
    }

    fun finish() {
        if (pending.isNotBlank() && !exhausted) write(TaskHttpDirection.SSE_EVENT, "", TASK_HTTP_INCOMPLETE)
        pending.clear()
    }

    private fun flushEvent() {
        if (pending.isBlank()) return
        val event = pending.toString()
        pending.clear()
        if (exhausted) return
        val redacted = TaskHttpRedaction.redactBody(event, isSseEvent = true).trim()
        if (redacted.isEmpty()) return
        val bytes = redacted.encodeToByteArray().size
        if (written + bytes > maxBytes) {
            exhausted = true
            write(TaskHttpDirection.SSE_EVENT, "", TASK_HTTP_OMITTED)
            return
        }
        written += bytes
        write(TaskHttpDirection.SSE_EVENT, redacted, TASK_HTTP_CAPTURED)
    }

    private companion object {
        const val MAX_EVENT_CHARS = 32 * 1024
    }
}

/** Redaction happens before anything is written; stored text is the sanitized original, never formatted. */
object TaskHttpRedaction {
    private val safeHeaders = setOf("host", "content-type", "accept", "user-agent", "content-length", "content-encoding")
    private val sensitiveKeys = setOf(
        "authorization", "cookie", "set-cookie", "token", "access_token", "refresh_token", "api_key", "apikey",
        "key", "secret", "client_secret", "password", "passwd", "signature", "sig", "auth", "code", "session",
        "x-api-key", "credential", "bearer",
    )
    private val sensitivePattern = Regex(
        "(?i)(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}|(authorization|cookie|set-cookie|x-api-key|api[_-]?key|token|secret|password)\\s*[:=]\\s*\\S+",
    )
    /** Quoted JSON keys survive malformed payloads, so they get their own mask pass. */
    private val sensitiveQuoted = Regex(
        "(?i)(\\\"(?:authorization|cookie|set-cookie|x-api-key|api[_-]?key|access_token|refresh_token|token|secret|password|passwd|signature|sig|session|credential)\\\"\\s*:\\s*)\\\"[^\\\"]*\\\"",
    )
    private const val MASK = "已脱敏"
    private const val OMITTED = "无法安全脱敏，已省略"
    private const val INCOMPLETE = "事件不完整，已省略"

    fun isSensitiveKey(key: String): Boolean =
        sensitiveKeys.any { key.lowercase().contains(it) }

    fun headers(values: Map<String, String>): Map<String, String> = values.mapValues { (name, value) ->
        if (name.lowercase() in safeHeaders) value else MASK
    }

    /** URL userinfo and query credentials never leave the device. */
    fun url(raw: String): String {
        val withoutFragment = raw.substringBefore('#')
        val schemeSplit = withoutFragment.indexOf("://")
        if (schemeSplit < 0) return redactText(withoutFragment)
        val scheme = withoutFragment.substring(0, schemeSplit + 3)
        val rest = withoutFragment.substring(schemeSplit + 3)
        val authority = rest.substringBefore('/').substringBefore('?')
        val host = authority.substringAfter('@')
        val pathAndQuery = rest.removePrefix(authority)
        val path = pathAndQuery.substringBefore('?')
        val query = pathAndQuery.substringAfter('?', "")
        val safeQuery = query.split('&').filter { it.isNotBlank() }.joinToString("&") { pair ->
            val name = pair.substringBefore('=')
            if (isSensitiveKey(name)) name + "=" + MASK else pair
        }
        return scheme + host + path + if (safeQuery.isEmpty()) "" else "?" + safeQuery
    }

    /** Recursively masks sensitive keys and free-form secrets in parsed JSON; unparseable input fails closed. */
    fun redactJson(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return redactText(text)
        return runCatching { redactElement(kotlinx.serialization.json.Json.parseToJsonElement(trimmed)).toString() }
            .getOrElse { redactText(text) }
    }

    private fun redactElement(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement =
        when (element) {
            is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(
                element.mapValues { (key, value) ->
                    if (isSensitiveKey(key)) kotlinx.serialization.json.JsonPrimitive(MASK)
                    else redactElement(value)
                },
            )
            is kotlinx.serialization.json.JsonArray ->
                kotlinx.serialization.json.JsonArray(element.map(::redactElement))
            is kotlinx.serialization.json.JsonPrimitive ->
                if (element.isString) kotlinx.serialization.json.JsonPrimitive(redactText(element.content))
                else element
        }

    /** Free-form text, cURL and "raw" views all go through this; unmasked secrets never pass through. */
    fun redactText(text: String): String {
        val withoutUrls = Regex("(?i)(https?://)[^\\s'\"\\\\]+").replace(text) { match ->
            url(match.value)
        }
        val maskedQuoted = sensitiveQuoted.replace(withoutUrls) { match -> match.groupValues[1] + '\"' + MASK + '\"' }
        return sensitivePattern.replace(maskedQuoted) { match ->
            val key = match.value.substringBefore(':').substringBefore('=').trim()
            if (key.isBlank()) MASK else key + ": " + MASK
        }
    }

    /**
     * Body redaction for one complete payload. SSE lines keep only their data payload for the mask pass;
     * masking is applied once and the result never falls back to the unmasked text.
     */
    fun redactBody(text: String, isSseEvent: Boolean = false): String {
        if (text.isBlank()) return ""
        val payload = if (isSseEvent) {
            text.lineSequence().filter { it.trimStart().startsWith("data:") }
                .joinToString("\n") { it.trimStart().removePrefix("data:").trim() }
        } else text
        if (payload.isBlank()) return ""
        val trimmed = payload.trimStart()
        val isJson = trimmed.startsWith("{") || trimmed.startsWith("[")
        val redacted = if (isJson) redactJson(payload) else redactText(payload)
        // Fail closed only when masking could not be applied at all.
        if (!isJson && !redacted.contains(MASK) && looksSensitive(redacted)) return OMITTED
        return redacted
    }

    private fun looksSensitive(text: String): Boolean = sensitivePattern.containsMatchIn(text)

    /** UTF-8 safe cut; dropped bytes are reported only when the full text is known. */
    fun truncateUtf8(text: String, maxBytes: Int): Triple<String, String, Long?> {
        if (maxBytes <= 0) return Triple("", TASK_HTTP_OMITTED, null)
        val bytes = text.encodeToByteArray()
        if (bytes.size <= maxBytes) return Triple(text, TASK_HTTP_CAPTURED, null)
        var end = maxBytes
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        val kept = bytes.copyOfRange(0, end).decodeToString()
        return Triple(kept + "\n[截断]", TASK_HTTP_TRUNCATED, (bytes.size - end).toLong())
    }

    /** A sanitized cURL projection; the stored body already went through redaction. */
    fun asCurl(method: String?, url: String?, headers: Map<String, String>, body: String?): String {
        val parts = mutableListOf("curl")
        if (method != null) parts += "-X " + method
        url?.let { parts += "'" + url(it) + "'" }
        headers(haircut(headers)).forEach { (name, value) -> parts += "-H '" + name + ": " + value + "'" }
        body?.takeIf { it.isNotBlank() }?.let { parts += "--data '" + redactBody(it) + "'" }
        return parts.joinToString(" ")
    }

    private fun haircut(values: Map<String, String>) = values
}

