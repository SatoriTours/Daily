package com.dailysatori.service.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.service.security.DatabaseKey

internal fun createSqliteBackupSnapshot(driver: SqlDriver, source: String, destination: String, copyFile: (String, String) -> Unit) {
    val version = sqliteValue(driver, "SELECT sqlite_version()").split('.').map(String::toInt)
    if (version[0] > 3 || version[0] == 3 && version[1] >= 27) {
        driver.execute(null, "VACUUM INTO '${destination.replace("'", "''")}'", 0)
        return
    }
    // Older Android SQLite has no VACUUM INTO. An exclusive rollback-journal transaction
    // blocks all writers while copying; WAL must never be copied as a lone database file.
    driver.execute(null, "BEGIN EXCLUSIVE", 0)
    try {
        check(sqliteValue(driver, "PRAGMA journal_mode").lowercase() != "wal") {
            "当前系统无法安全备份 WAL 数据库，请升级系统"
        }
        copyFile(source, destination)
    } finally {
        driver.execute(null, "ROLLBACK", 0)
    }
}

/** Explicitly keyed target: VACUUM INTO must not accidentally create a plaintext backup. */
internal fun exportEncryptedDatabase(driver: SqlDriver, destination: String, key: DatabaseKey) {
    try { exportKeyedDatabase(driver, destination, key) }
    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { throw com.dailysatori.service.security.DatabaseSecurityException("加密数据库导出失败") }
}

internal expect fun prepareEncryptedSnapshotFile(path: String)

private fun exportKeyedDatabase(driver: SqlDriver, destination: String, key: DatabaseKey) {
    prepareEncryptedSnapshotFile(destination)
    val password = key.sqlCipherPassword()
    try {
        driver.execute(null, "ATTACH DATABASE ? AS encrypted KEY ?", 2) {
            bindString(0, destination); bindString(1, password.decodeToString())
        }
    } finally { password.fill(0) }
    try {
        driver.execute(null, "BEGIN IMMEDIATE", 0)
        try {
            val version = sqliteValue(driver, "PRAGMA user_version").toLong()
            driver.executeQuery(null, "SELECT sqlcipher_export('encrypted')", { cursor -> cursor.next(); QueryResult.Value(Unit) }, 0)
            driver.execute(null, "PRAGMA encrypted.user_version = $version", 0)
            driver.execute(null, "COMMIT", 0)
        } catch (failure: Exception) {
            driver.execute(null, "ROLLBACK", 0)
            throw failure
        }
    } finally { driver.execute(null, "DETACH DATABASE encrypted", 0) }
}

private fun sqliteValue(driver: SqlDriver, sql: String): String = driver.executeQuery(null, sql,
    { cursor -> cursor.next(); QueryResult.Value(cursor.getString(0).orEmpty()) }, 0).value
