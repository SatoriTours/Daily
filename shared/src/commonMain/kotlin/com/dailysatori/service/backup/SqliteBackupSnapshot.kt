package com.dailysatori.service.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

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

private fun sqliteValue(driver: SqlDriver, sql: String): String = driver.executeQuery(null, sql,
    { cursor -> cursor.next(); QueryResult.Value(cursor.getString(0).orEmpty()) }, 0).value
