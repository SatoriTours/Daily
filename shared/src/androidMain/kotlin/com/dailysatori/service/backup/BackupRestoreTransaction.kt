package com.dailysatori.service.backup

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Runs before any database, repository or worker is opened in the new process. */
internal class BackupRestoreTransaction(
    private val root: File,
    private val appData: File,
    private val database: File,
    private val lifeArchive: File,
    private val password: File,
    private val move: (File, File) -> Unit = { source, target ->
        target.parentFile?.mkdirs()
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    },
) {
    private val incoming get() = File(root, "incoming")
    private val rollback get() = File(root, "rollback")
    private val journal get() = File(root, "journal.json")
    private val ready get() = File(root, "ready")
    private val committed get() = File(root, "committed")
    private val rolledBack get() = File(root, "rolled-back")

    fun stage(prepared: File) {
        check(!ready.exists() && !journal.exists()) { "已有待恢复数据，请重启应用" }
        check(File(prepared, "database.db").isFile && File(prepared, "app_data").isDirectory)
        root.deleteRecursively()
        check(root.mkdirs())
        try {
            move(prepared, incoming)
            // Flush all payloads before publishing a durable ready marker.
            incoming.walkTopDown().filter(File::isFile).forEach { file ->
                java.io.RandomAccessFile(file, "rw").use { it.fd.sync() }
            }
            durableWrite(ready, "ready")
        } catch (failure: Exception) {
            root.deleteRecursively()
            throw failure
        }
    }

    fun applyPending(): String? {
        if (!root.exists()) return null
        if (committed.exists()) {
            val message = completedMessage()
            discardFinished()
            return message
        }
        if (rolledBack.exists()) {
            val message = rolledBack.readText()
            discardFinished()
            return message
        }
        if (journal.exists()) {
            val message = finishRollback(Json.decodeFromString<RestoreJournal>(journal.readText()), "恢复中断，已还原原有数据")
            discardFinished()
            return message
        }
        if (!ready.exists()) {
            root.deleteRecursively()
            return null
        }
        val plan = RestoreJournal(targets().map { (key, file) -> RestoreItem(key, file.exists()) })
        durableWrite(journal, Json.encodeToString(plan))
        val result = try {
            plan.items.forEach { item -> replace(item) }
            durableWrite(committed, "committed")
            completedMessage()
        } catch (_: Exception) {
            // Leave the journal intact if rollback itself fails so the next launch can retry.
            finishRollback(plan, "恢复失败，已还原原有数据")
        }
        discardFinished()
        return result
    }

    private fun completedMessage(): String {
        val message = if (File(incoming, "needs-backup-directory").exists()) "恢复完成，请选择自动备份目录" else "恢复完成"
        return message + if (File(incoming, "preserve-life-archive").exists())
            "；旧备份不含生活资料，已保留本机原有资料" else ""
    }

    private fun finishRollback(plan: RestoreJournal, message: String): String {
        recover(plan)
        // A completed rollback is terminal even if retiring its journal fails. Replaying
        // that journal later could delete files the user created after the rollback.
        durableWrite(rolledBack, message)
        return message
    }

    private fun targets(): List<Pair<String, File>> {
        val names = (appData.listFiles().orEmpty().map { it.name } +
            File(incoming, "app_data").listFiles().orEmpty().map { it.name })
            .filter { isBackupUserFile(it) }.distinct().sorted()
        return listOf("database.db" to database,
            "database.db-wal" to File("${database.path}-wal"),
            "database.db-shm" to File("${database.path}-shm"),
            "database.db-journal" to File("${database.path}-journal")) +
            names.map { "app_data/$it" to File(appData, it) } +
            listOfNotNull(
                ("life_archive" to lifeArchive).takeIf { File(incoming, "life_archive").exists() },
                ("backup_password.sec" to password).takeIf { File(incoming, "backup_password.sec").exists() },
            )
    }

    private fun destination(key: String): File = when (key) {
        "database.db" -> database
        "database.db-wal" -> File("${database.path}-wal")
        "database.db-shm" -> File("${database.path}-shm")
        "database.db-journal" -> File("${database.path}-journal")
        "life_archive" -> lifeArchive
        "backup_password.sec" -> password
        else -> {
            require(key.startsWith("app_data/") && isBackupUserFile(key.removePrefix("app_data/")) &&
                '/' !in key.removePrefix("app_data/"))
            File(appData, key.removePrefix("app_data/"))
        }
    }

    private fun replace(item: RestoreItem) {
        val target = destination(item.key)
        if (item.existed) move(target, File(rollback, item.key))
        val source = File(incoming, item.key)
        if (source.exists()) move(source, target)
    }

    private fun recover(plan: RestoreJournal) {
        plan.items.asReversed().forEach { item ->
            val target = destination(item.key)
            val old = File(rollback, item.key)
            if (old.exists()) {
                check(target.deleteRecursively())
                move(old, target)
            } else if (!item.existed && !File(incoming, item.key).exists()) {
                check(target.deleteRecursively())
            }
        }
    }

    private fun durableWrite(file: File, value: String) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { stream -> stream.write(value.toByteArray()); stream.fd.sync() }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun discardFinished() {
        // Retire the entire journal atomically before deleting anything in it. A crash
        // during cleanup must never turn a committed restore into a partial rollback.
        try {
            val retired = File(root.parentFile, "${root.name}.finished-${java.util.UUID.randomUUID()}")
            move(root, retired)
            retired.deleteRecursively()
        } catch (_: Exception) { /* Retain the journal for a safe cleanup retry on next launch. */ }
    }
}

@Serializable
private data class RestoreJournal(val items: List<RestoreItem>)

@Serializable
private data class RestoreItem(val key: String, val existed: Boolean)
