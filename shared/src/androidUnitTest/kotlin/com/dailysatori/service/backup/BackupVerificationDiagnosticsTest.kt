package com.dailysatori.service.backup

import java.sql.SQLException
import kotlin.test.*

class BackupVerificationDiagnosticsTest {
    @Test
    fun reportCopiesAllFramesCausesAndSuppressedErrorsWithoutMessagesOrPasswordHints() {
        var failure: Throwable = IllegalArgumentException("secret root token")
        repeat(10) { failure = IllegalStateException("private diary text", failure) }
        failure.stackTrace = Array(45) { StackTraceElement("com.dailysatori.Check", "check", "Check.kt", it + 1) }
        failure.addSuppressed(SQLException("private SQL parameters"))
        val result = BackupVerificationResult(BackupVerificationStatus.FAILED, BackupVerificationStage.PREPARING,
            "2026-10-09T02:16:17Z", "daily_satori_backup_2026-10-05-14-35-12_hint_abc.zip.enc",
            diagnosticLog = backupVerificationFailureLog(failure))

        val report = result.diagnosticReport()
        assertTrue(report.contains("Check.kt:45"))
        assertTrue(report.contains("IllegalArgumentException"))
        assertTrue(report.contains("Suppressed: java.sql.SQLException"))
        assertTrue(report.contains("2026-10-05-14-35-12.zip.enc"))
        listOf("secret root token", "private diary text", "private SQL parameters", "hint_abc").forEach {
            assertFalse(report.contains(it))
        }
    }

    @Test
    fun databaseFailureHasAnActionableCategoryWithoutCopyingRawSql() {
        val log = backupVerificationFailureLog(SQLException("no such table: private_payload while compiling: SELECT token FROM private_payload"))
        assertTrue(log.contains("databaseFailure=missing_table"))
        assertFalse(log.contains("private_payload"))
        assertFalse(log.contains("SELECT token"))
    }

    @Test
    fun schemaFailureReportsMissingApplicationIdentifiersButNotRowContents() {
        val log = backupVerificationFailureLog(BackupValidationException("Missing required columns: diary.parent_diary_id"))
        assertTrue(log.contains("Missing required columns: diary.parent_diary_id"))
    }

    @Test
    fun cyclicCauseChainStopsWithoutDiscardingItsFrames() {
        val first = IllegalStateException("private")
        val second = IllegalArgumentException("secret", first)
        first.initCause(second)
        val log = backupVerificationFailureLog(first)
        assertTrue(log.contains("[cycle omitted]"))
        assertTrue(log.contains("IllegalStateException"))
        assertTrue(log.contains("IllegalArgumentException"))
        assertFalse(log.contains("private"))
    }
}
