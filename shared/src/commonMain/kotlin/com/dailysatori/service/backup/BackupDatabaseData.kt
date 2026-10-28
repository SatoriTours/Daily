package com.dailysatori.service.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.config.SettingKeys
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.diary.isLegacyNullImagePath
import com.dailysatori.service.security.SecretFieldProcessor
import com.dailysatori.service.security.SecretValueCipher

/** Operates only on the backup copy, never on the running app's database. */
internal class BackupDatabaseData(private val driver: SqlDriver) {
    fun checkBackupDatabase() {
        check(strings("PRAGMA quick_check") == listOf("ok")) { "数据库完整性检查失败" }
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
            val found = actual[table] ?: throw BackupValidationException("Missing required table: $table")
            val missing = columns - found
            if (missing.isNotEmpty()) throw BackupValidationException("Missing required columns: $table.${missing.sorted().joinToString(",")}")
        }
        checkBackupDatabase()
        // Validate structure from metadata and read only aggregate counts used by the summary.
        val summaryTables = setOf("diary", "article", "book", "bookkeeping_entry", "reminder", "setting")
        return expectedSchema.keys.intersect(summaryTables).associateWith { table ->
            driver.executeQuery(null, "SELECT COUNT(*) FROM \"${table.replace("\"", "\"\"")}\"", { cursor ->
                check(cursor.next().value)
                QueryResult.Value(checkNotNull(cursor.getLong(0)))
            }, 0).value
        }
    }

    fun prepareSecrets(cipher: SecretValueCipher, legacyFields: Boolean = true) {
        val settings = SettingRepository(DailySatoriDatabase(driver))
        val version = settings.get(SettingKeys.schemaVersion)?.toLongOrNull() ?: 0
        check(version <= DatabaseConfig.currentSchemaVersion) { "备份版本较新，请先升级应用" }
        if (legacyFields) SecretFieldProcessor(driver, cipher).decryptLegacyFields()
        DatabaseMigration(driver, settings).runMigrations()
    }

    fun userFiles(appDataDir: String): List<String> = attachmentReferences(appDataDir).map { it.relativePath }.distinct()

    fun attachmentReferences(appDataDir: String): List<BackupAttachmentReference> {
        check(strings("PRAGMA quick_check") == listOf("ok")) { "数据库完整性检查失败" }
        check(strings("PRAGMA foreign_key_check").isEmpty()) { "数据库关联完整性检查失败" }
        if ("error_message" in strings("SELECT name FROM pragma_table_info('diary_attachment')")) {
            check(strings("SELECT error_message FROM diary_attachment WHERE error_message = 'recording_active'").isEmpty()) {
                "正在录音，请结束录音后再备份或恢复"
            }
        }
        return pathRows().flatMap { row ->
            row.paths().mapNotNull { path ->
                portablePath(path, appDataDir)?.let {
                    BackupAttachmentReference(row.source, row.id, row.recordId, path, it, row.isImage)
                }
            }
        }
    }

    fun prepareRestore(appDataDir: String, backupDirectory: String) {
        val rows = pathRows()
        // Relocate references without requiring every historical attachment to exist in the archive.
        rows.forEach { row ->
            val paths = row.paths().map { path ->
                val relative = portablePath(path, appDataDir)
                if (relative == null) path else {
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
        holdRestoredAssistantWork()
    }

    private fun holdRestoredAssistantWork() {
        SettingRepository(DailySatoriDatabase(driver)).upsert("assistant.history.restore_hold", "true")
        driver.execute(null, "UPDATE async_task SET checkpoint_json=\'\' WHERE type=\'assistant_turn\'", 0)
        // Only the staged restore copy is changed. Normal app restart keeps automatic recovery.
        driver.execute(null, """UPDATE assistant_turn SET requires_resume=1 WHERE id IN (
            SELECT a.turn_id FROM assistant_action a JOIN async_task t ON t.id=a.task_id
            WHERE t.status IN ('queued','running','retrying')
        )""".trimIndent(), 0)
        driver.execute(null, """UPDATE async_task SET status='failed',lease_owner=NULL,lease_until_ms=NULL,
            last_error_code='restore_requires_resume',last_error_message='备份中的助手动作等待明确继续'
            WHERE status IN ('queued','running','retrying') AND id IN (SELECT task_id FROM assistant_action)
        """.trimIndent(), 0)
        driver.execute(null, """UPDATE assistant_turn SET requires_resume = 1
            WHERE response_message_id IS NULL AND task_id IN (
                SELECT id FROM async_task WHERE type = 'assistant_turn'
                AND status IN ('queued', 'running', 'retrying')
            )""".trimIndent(), 0)
        driver.execute(null, """UPDATE async_task SET status = 'failed',
            lease_owner = NULL, lease_until_ms = NULL, run_after_ms = NULL,
            last_error_code = 'restore_requires_resume',
            last_error_message = '备份已恢复，请核对后手动继续助手任务'
            WHERE type = 'assistant_turn'
            AND id IN (SELECT task_id FROM assistant_turn WHERE requires_resume = 1)""".trimIndent(), 0)
        driver.execute(null, """UPDATE async_task SET status='failed',lease_owner=NULL,lease_until_ms=NULL,run_after_ms=NULL,
            last_error_code='restore_requires_resume',last_error_message='备份中的记忆索引等待明确继续'
            WHERE type IN ('assistant_index','assistant_summary','assistant_history_backfill') AND status IN ('queued','running','retrying')""".trimIndent(),0)
    }

    private fun pathRows(): List<PathRow> = BackupAttachmentSource.entries.flatMap { source ->
        val table = source.table
        val column = source.column
        // The retained legacy field is not an active file association after schema 40.
        if (source == BackupAttachmentSource.DIARY_IMAGE && (SettingRepository(DailySatoriDatabase(driver)).get(SettingKeys.schemaVersion)?.toLongOrNull() ?: 0) >= 40) return@flatMap emptyList()
        val columns = strings("SELECT name FROM pragma_table_info('$table')")
        if (column !in columns) emptyList() else {
            val owner = source.ownerColumn?.takeIf { it in columns } ?: "rowid"
            val kind = if (source == BackupAttachmentSource.DIARY_ATTACHMENT && "kind" in columns) "kind" else "NULL"
            driver.executeQuery(null, "SELECT rowid, $column, $owner, $kind FROM $table WHERE $column IS NOT NULL AND $column <> ''", { cursor ->
                val result = mutableListOf<PathRow>()
                while (cursor.next().value) result += PathRow(source, cursor.getLong(0)!!, cursor.getString(1)!!,
                    cursor.getLong(2), source != BackupAttachmentSource.DIARY_ATTACHMENT || cursor.getString(3) == "image")
                QueryResult.Value(result)
            }, 0).value
        }
    }

    private fun strings(sql: String): List<String> = driver.executeQuery(null, sql, { cursor ->
        val values = mutableListOf<String>()
        while (cursor.next().value) values += cursor.getString(0).orEmpty()
        QueryResult.Value(values)
    }, 0).value

    private data class PathRow(val source: BackupAttachmentSource, val id: Long, val value: String,
        val recordId: Long?, val isImage: Boolean) {
        val table: String get() = source.table
        val column: String get() = source.column
        fun paths(): List<String> {
            val paths = if (table == "diary") value.split(',').map(String::trim).filter(String::isNotEmpty) else listOf(value)
            // Old JSON imports turned absent images into these literal values. They are not files.
            return paths.filterNot { table != "diary_attachment" && isLegacyNullImagePath(it) }
        }
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
