package com.dailysatori.service.backup

/** Results describe a specific archive, never the running application's data. */
enum class BackupVerificationStatus { PASSED, LIMITED, FAILED, INCOMPLETE }
enum class BackupVerificationStage { SELECTING, READING, DECRYPTING, EXTRACTING, FILES, DATABASE, PREPARING, LIFE_ARCHIVE, CLEANUP, COMPLETE }
enum class BackupVerificationIssue { NO_DIRECTORY, NO_BACKUP, NO_PASSWORD, BUSY, BACKUP_CHANGED, CHECK_FAILED, CANCELLED }

data class BackupVerificationResult(
    val status: BackupVerificationStatus,
    val stage: BackupVerificationStage,
    val checkedAt: String,
    val fileName: String? = null,
    val backupTime: String? = null,
    val issue: BackupVerificationIssue? = null,
    val summary: Map<String, Long> = emptyMap(),
    val diagnosticLog: String = "",
) {
    fun diagnosticReport(): String = buildString {
        appendLine("Daily Satori backup verification")
        appendLine("status=$status stage=$stage issue=${issue ?: "none"}")
        appendLine("checkedAt=$checkedAt backupTime=${backupTime ?: "unknown"}")
        // Legacy archive names can contain password hints. Never copy these into diagnostics.
        val timestamp = Regex("""^daily_satori_backup_(\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2})(?:_hint_[^./]{3})?\.zip\.enc$""")
            .matchEntire(fileName.orEmpty())?.groupValues?.get(1)
        appendLine("file=${timestamp?.let { "daily_satori_backup_$it.zip.enc" } ?: "[name omitted]"}")
        appendLine("currentSchemaVersion=${com.dailysatori.config.DatabaseConfig.currentSchemaVersion}")
        appendLine("passwordSource=manual; liveData=untouched; backup=untouched")
        append(diagnosticLog)
    }
}

/** Details here are produced from application-controlled schema identifiers, never row values. */
internal class BackupValidationException(message: String) : IllegalStateException(message)

internal class BackupVerificationLog {
    private val lines = mutableListOf<String>()
    private var activeStep = "select_backup"

    fun start(step: String) {
        activeStep = step
        lines += "START $step"
    }

    fun ok(step: String = activeStep) { lines += "OK $step" }
    fun note(value: String) { lines += value }

    fun failure(error: Throwable) {
        lines += "FAIL $activeStep"
        lines += backupVerificationSafeStack(error)
    }

    fun text(): String = lines.joinToString("\n")
}

fun backupVerificationFailureLog(error: Throwable): String =
    BackupVerificationLog().apply { start("verification_client"); failure(error) }.text()

internal expect fun backupVerificationSafeStack(error: Throwable): List<String>
