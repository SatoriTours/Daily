package com.dailysatori.service.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.migration.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class ReminderCandidateTest {
    @Test fun physicalPre42CandidatePreservesOriginalPayloadsHistoryAndSequences()=fixture { driver ->
        val expected=MigrationContentSnapshot.capture(driver)
        migrate(driver)
        expected.requirePreserved(driver,migratedReminders=true)
        ReminderLegacyFixture.assertMigrated(driver)
    }
    @Test fun candidateRejectsChangedOriginalOverridesAndExtraPolicies()=fixture { driver ->
        val expected=MigrationContentSnapshot.capture(driver)
        migrate(driver)
        driver.execute(null,"UPDATE reminder_ai_draft SET override_json='changed'",0)
        assertFails { expected.requirePreserved(driver,migratedReminders=true) }
        driver.execute(null,"UPDATE reminder_ai_draft SET override_json=?",1) { bindString(0,"edited original\u0000tail") }
        expected.requirePreserved(driver,migratedReminders=true)
        driver.execute(null,"INSERT INTO reminder_policy_snapshot VALUES('extra',1,'hash','unrelated',1)",0)
        assertFails { expected.requirePreserved(driver,migratedReminders=true) }
    }
    @Test fun lateOriginalReminderChangeCannotBeHiddenByItsConversionPlan()=fixture { driver ->
        val expected=MigrationContentSnapshot.capture(driver)
        driver.execute(null,"UPDATE reminder SET content='late change'",0)
        assertFails { expected.requirePreserved(driver) }
    }
    private fun migrate(driver:JdbcSqliteDriver) {
        DatabaseMigration(driver,SettingRepository(DailySatoriDatabase(driver))).runMigrations()
    }
    private fun fixture(block:(JdbcSqliteDriver)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            ReminderLegacyFixture.seed(driver)
            SettingRepository(DailySatoriDatabase(driver)).upsert("schema_version","41")
            block(driver)
        }
    }
}
