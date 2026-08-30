package com.dailysatori.service.migration

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
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
}
