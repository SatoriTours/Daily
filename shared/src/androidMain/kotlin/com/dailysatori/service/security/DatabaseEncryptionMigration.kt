package com.dailysatori.service.security

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.platform.*
import com.dailysatori.service.backup.exportEncryptedDatabase
import java.io.File

internal class DatabaseEncryptionMigration(private val context: PlatformContext) {
    private val factory = DatabaseDriverFactory(context)
    private val keys = DatabaseKeyStore(context)
    private val database get() = context.context.getDatabasePath("daily_satori.db")
    private val root get() = File(context.context.noBackupFilesDir, "database-install")
    internal fun transaction() = DatabaseInstallTransaction(root, database, File(keys.storagePath()))

    fun ensureEncrypted() {
        val existingKey = keys.readExisting()
        if (database.exists() && !isPlaintextDatabase(database)) {
            val key = existingKey ?: throw DatabaseSecurityException()
            factory.createBackupDriver(database.path, key).useDriver { sqlStrings(it, "SELECT count(*) FROM setting") }
            return
        }
        val candidate = File(database.parentFile, "daily_satori.encrypted-candidate")
        if (!database.exists() && (existingKey != null || candidate.exists()))
            throw DatabaseSecurityException("数据库文件缺失，密钥和待转换数据已保留")
        candidate.parentFile?.mkdirs()
        cleanCandidate(candidate)
        val key = existingKey ?: DatabaseKey.generate()
        try {
            if (database.exists()) convertLegacy(database.path, candidate.path, key)
            else factory.createEncryptedDriver(candidate.path, key).useDriver { validate(it) }
            factory.createBackupDriver(candidate.path, key).useDriver { validate(it) }
            transaction().stage(candidate, keys.wrap(key))
            transaction().applyPending()
        } finally { cleanCandidate(candidate) }
    }

    fun convertLegacy(source: String, destination: String, key: DatabaseKey) {
        check(!File(destination).exists())
        val original = factory.createLegacyDriver(source)
        val before = try {
            val signature = schemaAndCounts(original)
            exportEncryptedDatabase(original, destination, key)
            signature
        } catch (_: Exception) { throw DatabaseSecurityException("历史数据库转换失败，原数据已保留") }
        finally { original.close() }
        factory.createBackupDriver(destination, key).useDriver { target ->
            check(before == schemaAndCounts(target)) { "数据库转换校验失败" }
            SecretFieldProcessor(target, SecretCipher(context)).decryptLegacyFields()
            validate(target)
        }
    }

    private fun cleanCandidate(file: File) {
        listOf(file, File("${file.path}-wal"), File("${file.path}-shm"), File("${file.path}-journal")).forEach {
            if (it.exists()) check(it.delete())
        }
    }
}

internal inline fun <T> SqlDriver.useDriver(block: (SqlDriver) -> T): T = try { block(this) } finally { close() }

internal fun validate(driver: SqlDriver) {
    check(sqlStrings(driver, "PRAGMA integrity_check") == listOf("ok")) { "数据库完整性检查失败" }
    check(sqlStrings(driver, "PRAGMA foreign_key_check").isEmpty()) { "数据库关联检查失败" }
    // No rows means all encrypted pages authenticated successfully.
    check(sqlStrings(driver, "PRAGMA cipher_integrity_check").isEmpty()) { "数据库加密完整性检查失败" }
}

private fun schemaAndCounts(driver: SqlDriver): Map<String, String> {
    val schema = sqlStrings(driver, "SELECT type || ':' || name || ':' || coalesce(sql,'') FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' ORDER BY name")
    return schema.associateWith { record ->
        if (!record.startsWith("table:")) "" else {
            val name = record.substringAfter(':').substringBefore(':').replace("\"", "\"\"")
            sqlStrings(driver, "SELECT count(*) FROM \"$name\"").single()
        }
    }
}

internal fun sqlStrings(driver: SqlDriver, sql: String): List<String> = driver.executeQuery(null, sql, { cursor ->
    val rows = mutableListOf<String>()
    while (cursor.next().value) rows += cursor.getString(0).orEmpty()
    QueryResult.Value(rows)
}, 0).value
