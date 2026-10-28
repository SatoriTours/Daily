package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver

/** Unprovable legacy states are history, not runnable drafts. Preserve their original cells. */
internal object ReminderDraftLegacyArchive {
    const val name = "reminder_ai_draft_legacy"
    private const val table = """CREATE TABLE IF NOT EXISTS reminder_ai_draft_legacy(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        batch_id TEXT NOT NULL REFERENCES reminder_ai_batch(id) ON DELETE CASCADE,
        source_index INTEGER NOT NULL,source_text TEXT NOT NULL,draft_json TEXT NOT NULL,
        override_json TEXT NOT NULL DEFAULT '',selected INTEGER NOT NULL DEFAULT 1,
        discarded INTEGER NOT NULL DEFAULT 0,confirmation_state TEXT NOT NULL DEFAULT 'PENDING',reminder_id TEXT,
        confirmed INTEGER NOT NULL DEFAULT 0,confirmed_at INTEGER,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,
        UNIQUE(batch_id,source_index))"""
    val defaults = mapOf("override_json" to ReminderDraftMigration.Cell.of(""),
        "selected" to ReminderDraftMigration.Cell.number(1),"discarded" to ReminderDraftMigration.Cell.number(0),
        "confirmation_state" to ReminderDraftMigration.Cell.of("PENDING"),"reminder_id" to ReminderDraftMigration.Cell.of(null))

    fun create(driver: SqlDriver) { driver.execute(null, table, 0) }

    fun read(driver: SqlDriver): List<Map<String, ReminderDraftMigration.Cell>> =
        if (DatabaseSchemaInventory.strings(driver,"SELECT name FROM sqlite_master WHERE name='$name'").isEmpty()) emptyList()
        else ReminderDraftMigration.read(driver,name,ReminderDraftMigration.legacyColumns)

    fun retain(driver: SqlDriver, rows: List<Map<String, ReminderDraftMigration.Cell>>) {
        create(driver)
        val found = DatabaseSchemaInventory.strings(driver,"SELECT name FROM pragma_table_info('reminder_ai_draft')")
        val columns = ReminderDraftMigration.legacyColumns
        val projection = columns.joinToString { if (it in found) "\"$it\"" else defaults.getValue(it).sql() }
        rows.forEach { row ->
            driver.execute(null,"INSERT INTO $name(${columns.joinToString()}) SELECT $projection FROM reminder_ai_draft WHERE id=${row.getValue("id").long()}",0)
        }
    }
}
