package com.dailysatori.service.migration

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReminderMigrationTest {
    @Test
    fun reminderMigrationDoesNotHideSchemaFailures() {
        assertFailsWith<IllegalStateException> {
            DatabaseMigration.migrateReminderSchema(runSql = { throw IllegalStateException("disk failure") })
        }
    }
}
