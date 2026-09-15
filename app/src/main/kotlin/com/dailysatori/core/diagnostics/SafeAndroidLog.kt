package com.dailysatori.core.diagnostics

import co.touchlab.kermit.Severity
import com.dailysatori.service.diagnostics.DiagnosticLog

/** Compatibility at existing Android Log call sites; arguments are never persisted as free text. */
object SafeAndroidLog {
    fun d(tag: String, message: String): Int = record(Severity.Debug, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null): Int = record(Severity.Warn, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null): Int = record(Severity.Error, tag, message, error)
    private fun record(level: Severity, tag: String, message: String, error: Throwable?): Int {
        DiagnosticLogWriter(DiagnosticLog.diagnostics).log(level, message, tag, error)
        return 0
    }
}
