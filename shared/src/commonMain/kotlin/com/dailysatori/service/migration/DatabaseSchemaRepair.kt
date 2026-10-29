package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver

/** Candidate-only recovery of known secondary DDL, never authoritative tables or history rows. */
internal object DatabaseSchemaRepair {
    fun repair(driver: SqlDriver, version: Long) {
        DatabaseSchemaInventory.validateColumns(driver, version, includeProjection = false)
        val missing = DatabaseSchemaInventory.missingObjects(driver, version)
        check(missing.keys.none { it.startsWith("table:") }) { "数据库业务表缺失，保留原库" }
        if (missing.isEmpty()) return
        val legacy = legacyTriggers(version)
        // Views precede triggers that use them. Index creation still enforces uniqueness.
        missing.entries.sortedBy { if (it.key.startsWith("view:")) 0 else 1 }.forEach { (name, sql) ->
            driver.execute(null, legacy[name] ?: sql, 0)
        }
        rebuildSearch(driver, version)
        DatabaseSchemaInventory.validate(driver, version)
    }

    private fun legacyTriggers(version: Long): Map<String, String> {
        val statements = mutableMapOf<String, String>()
        fun record(sql: String) {
            Regex("CREATE TRIGGER (?:IF NOT EXISTS )?([a-zA-Z0-9_]+)").find(sql)?.let {
                statements["trigger:${it.groupValues[1]}"] = sql
            }
        }
        if (version < 41) DatabaseMigration.migrateFts4Schema(::record)
        if (version < 45) DatabaseMigration.migrateDiaryThreadSchema(::record)
        return statements
    }

    private fun rebuildSearch(driver: SqlDriver, version: Long) {
        // Missing historical triggers may have left FTS stale. Rebuild only derived search rows.
        val diary = if (version < 41)
            "SELECT id,content,tags FROM diary" else
            "SELECT d.id,d.content,p.tags FROM diary d LEFT JOIN diary_active_tags p ON p.diary_id=d.id"
        val projections = linkedMapOf(
            "article_fts(rowid,title,ai_title,ai_content)" to "SELECT id,title,ai_title,ai_content FROM article",
            "book_fts(rowid,title,author)" to "SELECT id,title,author FROM book",
            "book_viewpoint_fts(rowid,title,content,example)" to "SELECT id,title,content,example FROM book_viewpoint",
            "memory_entry_fts(rowid,title,content)" to "SELECT id,title,content FROM memory_entry",
            "diary_fts(rowid,content,tags)" to diary,
        )
        projections.forEach { (table, select) ->
            driver.execute(null, "DELETE FROM ${table.substringBefore('(')}", 0)
            driver.execute(null, "INSERT INTO $table $select", 0)
        }
    }
}
