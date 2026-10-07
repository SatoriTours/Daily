package com.dailysatori.service.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.config.SettingKeys
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.security.SecretFieldProcessor
import com.dailysatori.service.security.SecretValueCipher

/** Operates only on the backup copy, never on the running app's database. */
internal class BackupDatabaseData(private val driver: SqlDriver) {
    fun checkBackupDatabase() {
        check(strings("PRAGMA integrity_check") == listOf("ok")) { "数据库完整性检查失败" }
        check(strings("PRAGMA foreign_key_check").isEmpty()) { "数据库关联完整性检查失败" }
        val tables = schemaColumns().keys
        check(tables.containsAll(listOf("setting", "diary", "article", "book", "image"))) { "备份不是完整的应用数据库" }
        val version = SettingRepository(DailySatoriDatabase(driver)).get(SettingKeys.schemaVersion)
        check(version == null || version.toLongOrNull()?.let { it in 0..DatabaseConfig.currentSchemaVersion } == true) {
            "备份数据库版本无效或较新"
        }
    }

    fun schemaColumns(): Map<String, Set<String>> =
        strings("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'")
            .associateWith { table -> strings("SELECT name FROM pragma_table_info('${table.replace("'", "''")}')").toSet() }

    fun verifiedSummary(expectedSchema: Map<String, Set<String>>): Map<String, Long> {
        val actual = schemaColumns()
        expectedSchema.forEach { (table, columns) ->
            check(actual[table]?.containsAll(columns) == true) { "备份数据库缺少应用所需的表或字段" }
        }
        checkBackupDatabase()
        // Read every application table, not merely the SQLite header or schema.
        val counts = expectedSchema.keys.associateWith { table ->
            driver.executeQuery(null, "SELECT COUNT(*) FROM \"${table.replace("\"", "\"\"")}\"", { cursor ->
                check(cursor.next().value)
                QueryResult.Value(checkNotNull(cursor.getLong(0)))
            }, 0).value
        }
        return counts.filterKeys { it in setOf("diary", "article", "book", "bookkeeping_entry", "reminder", "setting") }
    }

    fun prepareSecrets(cipher: SecretValueCipher) {
        val settings = SettingRepository(DailySatoriDatabase(driver))
        val version = settings.get(SettingKeys.schemaVersion)?.toLongOrNull() ?: 0
        check(version <= DatabaseConfig.currentSchemaVersion) { "备份版本较新，请先升级应用" }
        DatabaseMigration(driver, settings, cipher).runMigrations()
        SecretFieldProcessor(driver, cipher).prepareRestoredSecrets(strict = true)
    }

    fun userFiles(appDataDir: String): List<String> {
        check(strings("PRAGMA quick_check") == listOf("ok")) { "数据库完整性检查失败" }
        check(strings("PRAGMA foreign_key_check").isEmpty()) { "数据库关联完整性检查失败" }
        if ("error_message" in strings("SELECT name FROM pragma_table_info('diary_attachment')")) {
            check(strings("SELECT error_message FROM diary_attachment WHERE error_message = 'recording_active'").isEmpty()) {
                "正在录音，请结束录音后再备份或恢复"
            }
        }
        return pathRows().flatMap { row -> row.paths().mapNotNull { portablePath(it, appDataDir) } }.distinct()
    }

    fun prepareRestore(appDataDir: String, backupDirectory: String, availableFiles: Set<String>) {
        userFiles(appDataDir)
        val rows = pathRows()
        rows.forEach { row ->
            val paths = row.paths().map { path ->
                val relative = portablePath(path, appDataDir)
                if (relative == null) path else {
                    check(relative in availableFiles) { "备份缺少附件，已取消恢复" }
                    if (row.table == "diary_attachment") "$appDataDir/$relative" else relative
                }
            }
            val updated = if (row.table == "diary") paths.joinToString(",") else paths.singleOrNull().orEmpty()
            driver.execute(null, "UPDATE ${row.table} SET ${row.column} = ? WHERE rowid = ?", 2) {
                bindString(0, updated)
                bindLong(1, row.id)
            }
        }
        SettingRepository(DailySatoriDatabase(driver)).upsert(SettingKeys.backupDir, backupDirectory)
    }

    private fun pathRows(): List<PathRow> = listOf(
        "diary_attachment" to "local_path", "diary" to "images", "image" to "path",
        "article" to "cover_image", "book" to "cover_image",
    ).flatMap { (table, column) ->
        if (column !in strings("SELECT name FROM pragma_table_info('$table')")) emptyList() else {
            driver.executeQuery(null, "SELECT rowid, $column FROM $table WHERE $column IS NOT NULL AND $column <> ''", { cursor ->
                val result = mutableListOf<PathRow>()
                while (cursor.next().value) result += PathRow(table, column, cursor.getLong(0)!!, cursor.getString(1)!!)
                QueryResult.Value(result)
            }, 0).value
        }
    }

    private fun strings(sql: String): List<String> = driver.executeQuery(null, sql, { cursor ->
        val values = mutableListOf<String>()
        while (cursor.next().value) values += cursor.getString(0).orEmpty()
        QueryResult.Value(values)
    }, 0).value

    private data class PathRow(val table: String, val column: String, val id: Long, val value: String) {
        fun paths(): List<String> = if (table == "diary") value.split(',').map(String::trim).filter(String::isNotEmpty) else listOf(value)
    }
}

internal fun portablePath(path: String, appDataDir: String): String? {
    if (path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) return null
    val relative = when {
        path.startsWith("$appDataDir/") -> path.removePrefix("$appDataDir/")
        path.startsWith('/') && "/DailySatori/" in path -> path.substringAfter("/DailySatori/")
        path.startsWith('/') && "/app_flutter/" in path -> path.substringAfter("/app_flutter/")
        else -> path
    }
    require(isBackupUserFile(relative)) { "附件路径不属于应用数据，无法完整迁移" }
    return relative
}
