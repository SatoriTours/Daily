package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.*
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import java.util.concurrent.atomic.AtomicBoolean

private class ServerDiagnosticCall {
    val requestId = DiagnosticLog.newId()
    val trace = DiagnosticTrace(DiagnosticLog.newId())
    val started = DiagnosticLog.elapsed()
    val finished = AtomicBoolean()
}
private val serverDiagnosticCall = AttributeKey<ServerDiagnosticCall>("ServerDiagnosticCall")

val DiagnosticServerPlugin = createApplicationPlugin("LocalDiagnostics") {
    onCall { call ->
        DiagnosticLog.registerCoverage(DiagnosticSource.WEB_SERVER, DiagnosticCoverage.LIFECYCLE_ONLY)
        val record = ServerDiagnosticCall()
        call.attributes.put(serverDiagnosticCall, record)
        DiagnosticLog.diagnostics.emit(DiagnosticCode.HTTP_START, DiagnosticSource.WEB_SERVER,
            fields = mapOf("method" to call.request.httpMethod.value, "url" to "http://localhost${call.request.path()}"),
            trace = record.trace, requestId = record.requestId)
    }
    on(ResponseSent) { call ->
        val record = call.attributes.getOrNull(serverDiagnosticCall)
        if (record != null && record.finished.compareAndSet(false, true)) {
            val fields = buildMap {
                put("durationMs", (DiagnosticLog.elapsed() - record.started).toString())
                call.response.status()?.value?.let { put("status", it.toString()) }
            }
            DiagnosticLog.diagnostics.emit(DiagnosticCode.HTTP_END, DiagnosticSource.WEB_SERVER,
                fields = fields, trace = record.trace, requestId = record.requestId)
        }
    }
    on(CallFailed) { call, error ->
        val record = call.attributes.getOrNull(serverDiagnosticCall)
        if (record != null && record.finished.compareAndSet(false, true)) {
            DiagnosticLog.diagnostics.emit(DiagnosticCode.HTTP_FAILED, DiagnosticSource.WEB_SERVER,
                DiagnosticLevel.ERROR, error = error, trace = record.trace, requestId = record.requestId)
        }
        throw error
    }
}
