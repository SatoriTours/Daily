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
)
