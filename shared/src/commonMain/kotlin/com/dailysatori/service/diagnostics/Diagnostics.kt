package com.dailysatori.service.diagnostics

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class Diagnostics(
    private val sink: DiagnosticSink = DiagnosticSink { false },
    private val now: () -> Long = DiagnosticLog::now,
) {
    fun emit(
        code: DiagnosticCode, source: DiagnosticSource,
        level: DiagnosticLevel = DiagnosticLevel.INFO,
        fields: Map<String, String> = emptyMap(), error: Throwable? = null,
        trace: DiagnosticTrace? = DiagnosticLog.currentTrace(), requestId: String? = null,
    ) {
        // Diagnostics must not change business behavior, including during storage failure.
        runCatching { sink.tryEmit(SafeDiagnosticEvent.capture(code, source, level, trace, requestId, fields, error, now())) }
    }

    suspend fun <T> operation(
        source: DiagnosticSource, taskId: Long? = null, fields: Map<String, String> = emptyMap(),
        isFailure: (T) -> Boolean = { false }, block: suspend () -> T,
    ): T {
        val trace = DiagnosticLog.currentTrace() ?: DiagnosticTrace(DiagnosticLog.newId(), taskId)
        return withContext(DiagnosticLog.traceContext(trace)) {
            val start = DiagnosticLog.elapsed()
            emit(DiagnosticCode.OPERATION_START, source, fields = fields)
            try {
                block().also {
                    val failed = isFailure(it)
                    emit(if (failed) DiagnosticCode.OPERATION_FAILED else DiagnosticCode.OPERATION_END,
                        source, if (failed) DiagnosticLevel.WARNING else DiagnosticLevel.INFO,
                        fields = mapOf("durationMs" to (DiagnosticLog.elapsed() - start).toString()))
                }
            } catch (cancelled: CancellationException) {
                emit(DiagnosticCode.OPERATION_CANCELLED, source, error = cancelled)
                throw cancelled
            } catch (failure: Exception) {
                emit(DiagnosticCode.OPERATION_FAILED, source, DiagnosticLevel.ERROR, error = failure)
                throw failure
            }
        }
    }
}

enum class DiagnosticCoverage { TRANSPORT, LIFECYCLE_ONLY, NOT_OBSERVED }

expect object DiagnosticLog {
    var diagnostics: Diagnostics
    val sessionId: String
    fun currentTrace(): DiagnosticTrace?
    fun traceContext(trace: DiagnosticTrace): CoroutineContext
    fun newId(): String
    fun nextSequence(): Long
    fun now(): Long
    fun elapsed(): Long
    fun threadCategory(): String
    fun fingerprint(value: String): String
    fun safeStack(error: Throwable): List<String>
    fun registerCoverage(source: DiagnosticSource, coverage: DiagnosticCoverage)
    fun coverage(): Map<DiagnosticSource, DiagnosticCoverage>
}
