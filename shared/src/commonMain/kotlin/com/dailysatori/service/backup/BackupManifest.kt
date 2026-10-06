package com.dailysatori.service.backup

import kotlinx.serialization.Serializable

/** Plaintext only inside the password-encrypted backup or its private temporary directory. */
interface LifeArchiveBackup {
    suspend fun exportSnapshot(): String
    suspend fun prepareRestore(snapshot: String): ByteArray
}

@Serializable
internal data class BackupManifest(
    val formatVersion: Int = 1,
    val schemaVersion: Long,
    val sourceAppDataDir: String,
    val files: List<BackupManifestFile>,
)

@Serializable
internal data class BackupManifestFile(val path: String, val size: Long, val sha256: String)

internal const val BackupManifestName = "manifest.json"
internal const val LifeArchiveBackupName = "life_archive.json"

internal fun isBackupUserFile(path: String): Boolean =
    path.isNotBlank() && !path.startsWith('/') && '\\' !in path &&
        path.split('/').none { it.isBlank() || it == "." || it == ".." } &&
        path.substringBefore('/') !in setOf("backups", "restore_temp", BackupManifestName,
            LifeArchiveBackupName, "daily_satori.db", "_restore")
