package com.dailysatori.core.diagnostics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.dailysatori.service.diagnostics.*

/** Free text is untrusted, even when the caller uses Kermit. Never persist it verbatim. */
class DiagnosticLogWriter(private val diagnostics: Diagnostics) : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val level = when (severity) {
            Severity.Verbose, Severity.Debug -> DiagnosticLevel.DEBUG
            Severity.Info -> DiagnosticLevel.INFO
            Severity.Warn -> DiagnosticLevel.WARNING
            else -> DiagnosticLevel.ERROR
        }
        val source = when (tag) {
            "AI", "AiService", "MCPAgent" -> DiagnosticSource.AI
            "DiaryThought" -> DiagnosticSource.DIARY
            "DBMigration" -> DiagnosticSource.MIGRATION
            "Backup" -> DiagnosticSource.BACKUP
            "WebpageParser", "WebViewLoader", "ArticlesVM" -> DiagnosticSource.PARSER
            else -> DiagnosticSource.LEGACY
        }
        diagnostics.emit(DiagnosticCode.LEGACY_LOG, source, level, error = throwable)
    }
}
