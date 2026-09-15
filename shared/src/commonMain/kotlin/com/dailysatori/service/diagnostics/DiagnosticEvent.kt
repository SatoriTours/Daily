package com.dailysatori.service.diagnostics

import kotlinx.serialization.Serializable

@Serializable
enum class DiagnosticCode {
    APP_START, FOREGROUND, BACKGROUND, PAGE_VIEW, NETWORK_CHANGED, HTTP_START, HTTP_HEADERS, HTTP_END, HTTP_FAILED,
    HTTP_CANCELLED, OPERATION_START, OPERATION_PROGRESS, OPERATION_END, OPERATION_FAILED, OPERATION_CANCELLED,
    AI_FIRST_CHUNK, LEGACY_LOG, CRASH, PROCESS_EXIT, DIAGNOSTIC_GAP,
}

@Serializable
enum class DiagnosticSource { APP, HTTP, AI, TOOL, WEBVIEW, IMAGE, DOWNLOAD, TASK, BACKUP, IMPORT, PARSER, DIARY, MIGRATION, LEGACY, WEB_SERVER }

@Serializable
enum class DiagnosticLevel { DEBUG, INFO, WARNING, ERROR }

data class DiagnosticTrace(val id: String, val taskId: Long? = null)

@Serializable
class SafeDiagnosticEvent private constructor(
    val timestampMs: Long,
    val elapsedMs: Long,
    val sessionId: String,
    val sequence: Long,
    val eventCode: DiagnosticCode,
    val source: DiagnosticSource,
    val level: DiagnosticLevel,
    val traceId: String?,
    val taskId: Long?,
    val requestId: String?,
    val attributes: Map<String, String>,
    val exception: List<String>,
    val thread: String,
    val schemaVersion: Int = 1,
    val contentOmitted: Boolean = true,
) {
    companion object {
        internal fun capture(
            code: DiagnosticCode, source: DiagnosticSource, level: DiagnosticLevel,
            trace: DiagnosticTrace?, requestId: String?, fields: Map<String, String>, error: Throwable?, timestampMs: Long,
        ) = SafeDiagnosticEvent(
            timestampMs, DiagnosticLog.elapsed(), DiagnosticLog.sessionId, DiagnosticLog.nextSequence(),
            code, source, level, trace?.id?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }, trace?.taskId,
            requestId?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) },
            DiagnosticRedactor.fields(fields), error?.let(DiagnosticLog::safeStack).orEmpty(), DiagnosticLog.threadCategory(),
        )
    }
}

fun interface DiagnosticSink {
    fun tryEmit(event: SafeDiagnosticEvent): Boolean
}
