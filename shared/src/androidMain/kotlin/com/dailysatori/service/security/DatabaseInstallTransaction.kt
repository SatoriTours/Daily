package com.dailysatori.service.security

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Owns only database/key files. Runs under DatabaseBootstrap's initialization lock. */
internal class DatabaseInstallTransaction(
    private val root: File,
    private val database: File,
    private val keyFile: File,
    private val move: (File, File) -> Unit = { from, to ->
        to.parentFile?.mkdirs(); Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
    },
) {
    private val targets get() = linkedMapOf("database" to database, "key" to keyFile,
        "wal" to File("${database.path}-wal"), "shm" to File("${database.path}-shm"), "journal" to File("${database.path}-journal"))
    private val incoming get() = File(root, "incoming")
    private val rollback get() = File(root, "rollback")
    private val journal get() = File(root, "transaction.json")

    fun stage(candidate: File, wrappedKey: ByteArray) {
        check(!root.exists()) { "存在未完成数据库转换" }
        check(candidate.isFile)
        check(incoming.mkdirs())
        move(candidate, File(incoming, "database"))
        FileOutputStream(File(incoming, "key")).use { it.write(wrappedKey); it.fd.sync() }
        java.io.RandomAccessFile(File(incoming, "database"), "rw").use { it.fd.sync() }
        durableWrite(File(root, "ready"), "ready")
    }

    fun applyPending() {
        if (!root.exists()) return
        if (File(root, "committed").exists() || File(root, "rolled-back").exists()) { retire(); return }
        if (journal.exists()) {
            recover(Json.decodeFromString<InstallJournal>(journal.readText()))
            durableWrite(File(root, "rolled-back"), "done")
            retire(); return
        }
        if (!File(root, "ready").exists()) { check(root.deleteRecursively()); return }
        val plan = InstallJournal(targets.map { InstallItem(it.key, it.value.exists()) })
        durableWrite(journal, Json.encodeToString(plan))
        try {
            plan.items.forEach { item ->
                val target = targets.getValue(item.name)
                if (item.existed) move(target, File(rollback, item.name))
                val source = File(incoming, item.name)
                if (source.exists()) move(source, target)
            }
            durableWrite(File(root, "committed"), "done")
        } catch (_: Exception) {
            recover(plan)
            durableWrite(File(root, "rolled-back"), "done")
            retire()
            throw DatabaseSecurityException("数据库转换失败，原有数据已保留")
        }
        retire()
    }

    private fun recover(plan: InstallJournal) {
        plan.items.asReversed().forEach { item ->
            val target = targets.getValue(item.name)
            val old = File(rollback, item.name)
            if (old.exists()) {
                check(target.deleteRecursively()); move(old, target)
            } else if (!item.existed && !File(incoming, item.name).exists()) check(target.deleteRecursively())
        }
    }

    private fun durableWrite(file: File, value: String) {
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { it.write(value.toByteArray()); it.fd.sync() }
        move(temp, file)
    }

    private fun retire() {
        val retired = File(root.parentFile, root.name + ".finished-" + java.util.UUID.randomUUID())
        try { move(root, retired); retired.deleteRecursively() }
        catch (_: Exception) { /* A durable terminal marker makes retry safe. */ }
    }
}

@Serializable private data class InstallJournal(val items: List<InstallItem>)
@Serializable private data class InstallItem(val name: String, val existed: Boolean)
