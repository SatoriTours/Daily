package com.dailysatori.service.migration

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.cash.sqldelight.db.QueryResult
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.security.SecretValueCipher
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase

class ReminderMigrationTest {
    @Test
    fun reminderMigrationDoesNotHideSchemaFailures() {
        assertFailsWith<IllegalStateException> {
            DatabaseMigration.migrateReminderSchema(runSql = { throw IllegalStateException("disk failure") })
        }
    }

    @Test
    fun legacyReminderMigratesAsOnce() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "ALTER TABLE reminder DROP COLUMN recurrence_rule", 0)
            driver.execute(null, "INSERT INTO reminder (id, content, status, start_date, end_date, first_reminder_time, active_day_rule, time_zone_id, profile_json, version, dismissal_count, created_at, updated_at) VALUES ('legacy', 'legacy', 'ACTIVE', '2026-09-01', '2026-09-01', '09:00', 'daily', 'UTC', '{}', 0, 0, 1, 1)", 0)

            DatabaseMigration.migrateReminderRecurrenceSchema { sql -> driver.execute(null, sql, 0) }

            assertEquals("once", DailySatoriDatabase(driver).dailySatoriQueries.selectReminderById("legacy").executeAsOne().recurrence_rule)
        } finally {
            driver.close()
        }
    }

    @Test
    fun reminderAiBatchMigrationCreatesLatestTablesAndIndexes() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "DROP INDEX IF EXISTS idx_reminder_ai_draft_batch_source", 0)
            driver.execute(null, "DROP INDEX IF EXISTS idx_reminder_ai_batch_task", 0)
            driver.execute(null, "DROP INDEX IF EXISTS idx_reminder_ai_batch_status_updated", 0)
            driver.execute(null, "DROP INDEX IF EXISTS idx_reminder_ai_batch_active_key", 0)
            driver.execute(null, "DROP TABLE reminder_ai_draft", 0)
            driver.execute(null, "DROP TABLE reminder_ai_batch", 0)
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db)
            db.dailySatoriQueries.insertReminder("legacy", "preserved", "ACTIVE", "2026-09-02", "2026-09-02", "09:00", "daily", "once", "UTC", "{}", 0, null, 0, null, null, null, null, 1, 1)
            settings.upsert(SettingKeys.schemaVersion, "24")

            DatabaseMigration(driver, settings, TestCipher).runMigrations()
            db.dailySatoriQueries.insertReminderAiBatch("batch", null, "source", "source", "UTC", "2026-09-02", "PARSING", null, 0, 3, null, "", null, 1, 1)

            assertEquals(27L, DatabaseConfig.currentSchemaVersion)
            assertEquals("27", settings.get(SettingKeys.schemaVersion))
            assertEquals("preserved", db.dailySatoriQueries.selectReminderById("legacy").executeAsOne().content)
            assertEquals("source", db.dailySatoriQueries.selectReminderAiBatchById("batch").executeAsOne().original_input)
            assertEquals(null, db.dailySatoriQueries.selectReminderAiBatchById("batch").executeAsOne().parent_batch_id)
            assertEquals(1, schemaCount(driver, "reminder_ai_batch"))
            assertEquals(1, schemaCount(driver, "reminder_ai_draft"))
            assertTrue(schemaSql(driver, "idx_reminder_ai_batch_active_key").contains("WHERE status IN ('PARSING', 'RUNNING')"))
            assertTrue(schemaSql(driver, "idx_reminder_ai_batch_status_updated").contains("status, updated_at DESC"))
            assertTrue(schemaSql(driver, "idx_reminder_ai_batch_task").contains("task_id"))
            assertTrue(schemaSql(driver, "idx_reminder_ai_draft_batch_source").contains("batch_id, source_index ASC"))
            assertFailsWith<Exception> {
                db.dailySatoriQueries.insertReminderAiBatch("duplicate", null, "source", "source", "UTC", "2026-09-02", "PARSING", null, 0, 3, null, "", null, 1, 1)
            }
        } finally {
            driver.close()
        }
    }

    @Test
    fun version26DraftIsPreservedAndReceivesVersion27Defaults() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE reminder_ai_draft", 0)
            driver.execute(null, "CREATE TABLE reminder_ai_draft (id INTEGER PRIMARY KEY AUTOINCREMENT, batch_id TEXT NOT NULL REFERENCES reminder_ai_batch(id) ON DELETE CASCADE, source_index INTEGER NOT NULL, source_text TEXT NOT NULL, draft_json TEXT NOT NULL, confirmed INTEGER NOT NULL DEFAULT 0, confirmed_at INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE(batch_id, source_index))", 0)
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db)
            db.dailySatoriQueries.insertReminderAiBatch("v26", null, "source", "source", "UTC", "2026-09-02", "READY_FOR_CONFIRMATION", null, 0, 3, null, "", null, 1, 1)
            driver.execute(null, "INSERT INTO reminder_ai_draft(batch_id, source_index, source_text, draft_json, created_at, updated_at) VALUES ('v26', 0, 'keep', '{}', 1, 1)", 0)
            settings.upsert(SettingKeys.schemaVersion, "26")

            DatabaseMigration(driver, settings, TestCipher).runMigrations()

            val draft = db.dailySatoriQueries.selectReminderAiDraftsByBatchId("v26").executeAsOne()
            assertEquals("keep", draft.source_text)
            assertEquals("", draft.override_json)
            assertEquals(1L, draft.selected)
            assertEquals(0L, draft.discarded)
            assertEquals("PENDING", draft.confirmation_state)
            assertEquals(null, draft.reminder_id)
        } finally {
            driver.close()
        }
    }

    private fun schemaCount(driver: JdbcSqliteDriver, name: String): Long = driver.executeQuery(null, "SELECT count(*) FROM sqlite_master WHERE name = '$name'", { cursor ->
        check(cursor.next().value)
        QueryResult.Value(cursor.getLong(0) ?: 0)
    }, 0).value

    private fun schemaSql(driver: JdbcSqliteDriver, name: String): String = driver.executeQuery(null, "SELECT sql FROM sqlite_master WHERE name = '$name'", { cursor ->
        check(cursor.next().value)
        QueryResult.Value(cursor.getString(0).orEmpty())
    }, 0).value

    private object TestCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
