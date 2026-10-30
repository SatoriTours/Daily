package com.dailysatori.service.security

import com.dailysatori.platform.FileManager
import com.dailysatori.platform.PlatformContext
import java.io.File
import java.io.RandomAccessFile

/** Sole initialization owner, before DI, repositories and workers. */
object DatabaseBootstrap {
    fun prepare(context: PlatformContext, onPreparationRequired: () -> Unit = {}): String? {
        val directory = context.context.noBackupFilesDir
        directory.mkdirs()
        return RandomAccessFile(File(directory, "database-init.lock"), "rw").use { file ->
            file.channel.lock().use {
                if (requireSinglePendingOperation(directory)) onPreparationRequired()
                val migration = DatabaseEncryptionMigration(context)
                val schema = DatabaseSchemaUpgrade.forContext(context)
                migration.transaction().applyPending()
                schema.recoverPending()
                directory.listFiles().orEmpty().filter {
                    it.name.startsWith("database-install.finished-") || it.name.startsWith("database-schema-install.finished-")
                }
                    .forEach { check(it.deleteRecursively()) }
                cleanBackupTemporaries(context.context.cacheDir)
                val message = FileManager().apply { init(context.context) }.applyPendingRestore()
                migration.ensureEncrypted(onPreparationRequired)
                schema.upgrade(onPreparationRequired)
                message
            }
        }
    }
}

/** Multiple live install intents cannot be ordered safely: keep all inputs and stop. */
private fun requireSinglePendingOperation(directory: File): Boolean {
    val pending = listOf("database-install", "database-schema-install", "pending-restore").count { name ->
        val root = File(directory, name)
        !File(root, "committed").exists() && !File(root, "rolled-back").exists() &&
            listOf("ready", "transaction.json", "journal.json").any { File(root, it).exists() }
    }
    check(pending <= 1) { "存在多个待处理数据库操作，原有数据已保留" }
    return pending == 1
}

/** Interrupted private plaintext archives/key descriptors must not survive a new business startup. */
internal fun cleanBackupTemporaries(cache: File) {
    val timestamp = "\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}"
    val temporaryDirectory = Regex("temp_$timestamp")
    val temporaryArchive = Regex("daily_satori_backup_$timestamp(?:_hint_[^./]{3})?\\.zip(?:\\.enc)?")
    cache.listFiles().orEmpty().filter { file ->
        file.name in setOf("verify_temp", "restore_temp") ||
            temporaryDirectory.matches(file.name) || temporaryArchive.matches(file.name)
    }.forEach { file ->
        // Files.walk does not follow links, unlike recursive java.io.File traversal.
        java.nio.file.Files.walk(file.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.delete(it) }
        }
    }
}
