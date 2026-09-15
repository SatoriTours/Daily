package com.dailysatori.service.diagnostics

import java.io.IOException
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import okhttp3.Response

/** OkHttp events observe consumption without wrapping, buffering or pre-reading a response. */
fun diagnosticEventListenerFactory(
    source: DiagnosticSource,
    diagnostics: Diagnostics = DiagnosticLog.diagnostics,
): EventListener.Factory {
    DiagnosticLog.registerCoverage(source, DiagnosticCoverage.TRANSPORT)
    return EventListener.Factory { DiagnosticNetworkListener(source, diagnostics, DiagnosticLog.currentTrace()) }
}

private class DiagnosticNetworkListener(
    private val source: DiagnosticSource,
    private val diagnostics: Diagnostics,
    private val trace: DiagnosticTrace?,
) : EventListener() {
    private var requestId = DiagnosticLog.newId()
    private var attempt = 0
    private var started = DiagnosticLog.elapsed()
    private var status: Int? = null
    private var bytes: Long? = null
    private var finished = false

    override fun callStart(call: Call) { start(call.request()) }

    @Synchronized
    override fun requestHeadersEnd(call: Call, request: Request) {
        if (attempt++ > 0) {
            finish(DiagnosticCode.HTTP_END)
            requestId = DiagnosticLog.newId()
            finished = false
            status = null
            bytes = null
            start(request)
        }
    }

    private fun start(request: Request) {
        started = DiagnosticLog.elapsed()
        diagnostics.emit(DiagnosticCode.HTTP_START, source,
            fields = mapOf("method" to request.method, "url" to request.url.toString(), "attempt" to maxOf(1, attempt).toString()),
            trace = trace, requestId = requestId)
    }

    @Synchronized
    override fun responseHeadersEnd(call: Call, response: Response) {
        status = response.code
        diagnostics.emit(DiagnosticCode.HTTP_HEADERS, source,
            fields = mapOf("status" to response.code.toString()), trace = trace, requestId = requestId)
    }

    @Synchronized
    override fun responseBodyEnd(call: Call, byteCount: Long) { bytes = byteCount }

    override fun callEnd(call: Call) { finish(DiagnosticCode.HTTP_END) }

    override fun callFailed(call: Call, ioe: IOException) {
        finish(if (call.isCanceled()) DiagnosticCode.HTTP_CANCELLED else DiagnosticCode.HTTP_FAILED, ioe)
    }

    @Synchronized
    private fun finish(code: DiagnosticCode, error: Throwable? = null) {
        if (finished) return
        finished = true
        val fields = buildMap {
            put("durationMs", (DiagnosticLog.elapsed() - started).toString())
            status?.let { put("status", it.toString()) }
            bytes?.let { put("bytes", it.toString()) }
        }
        val level = when {
            code == DiagnosticCode.HTTP_FAILED -> DiagnosticLevel.ERROR
            status != null && status!! >= 400 -> DiagnosticLevel.WARNING
            else -> DiagnosticLevel.INFO
        }
        diagnostics.emit(code, source, level, fields, error, trace, requestId)
    }
}
