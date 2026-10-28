package com.dailysatori.service.migration

import app.cash.sqldelight.db.*
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class MigrationSafetyTest {
    @Test fun unversionedIncompleteDatabaseIsNotMistakenForFreshInstallation() = fixture { driver, settings ->
        driver.execute(null, "DROP TABLE assistant_action", 0)
        assertFails { DatabaseMigration(driver, settings).runMigrations() }
        assertNull(settings.get(SettingKeys.schemaVersion))
        assertOriginalDiary(driver)
    }

    @Test fun declaredCurrentVersionDoesNotBypassStructuralValidation() = fixture { driver, settings ->
        settings.upsert(SettingKeys.schemaVersion, DatabaseConfig.currentSchemaVersion.toString())
        driver.execute(null, "ALTER TABLE reminder DROP COLUMN notes", 0)
        assertFails { DatabaseMigration(driver, settings).runMigrations() }
        assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
        assertOriginalDiary(driver)
    }

    @Test fun invalidNegativeAndNewerVersionsAreRejectedWithoutRewritingMetadata() {
        for (version in listOf("invalid", "-1", (DatabaseConfig.currentSchemaVersion + 1).toString())) {
            fixture { driver, settings ->
                settings.upsert(SettingKeys.schemaVersion, version)
                assertFails("version=$version") { DatabaseMigration(driver, settings).runMigrations() }
                assertEquals(version, settings.get(SettingKeys.schemaVersion))
                assertOriginalDiary(driver)
            }
        }
    }

    @Test fun swallowedLegacyDdlFailureCannotAdvanceVersionAndRollsBackOtherChanges() = fixture(legacy=true) { driver, settings ->
        AssistantLegacyFixture.install(driver)
        settings.upsert(SettingKeys.schemaVersion, "37")
        driver.execute(null, "DROP TABLE assistant_action", 0)
        driver.execute(null, "ALTER TABLE assistant_session DROP COLUMN summary_version", 0)
        val faulty = object : SqlDriver by driver {
            override fun execute(identifier: Int?, sql: String, parameters: Int,
                binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
                if (sql.contains("CREATE TABLE IF NOT EXISTS assistant_action")) error("injected DDL failure")
                return driver.execute(identifier, sql, parameters, binders)
            }
        }
        assertFails { DatabaseMigration(faulty, settings).runMigrations() }
        assertEquals("37", settings.get(SettingKeys.schemaVersion))
        assertEquals(0L, driver.executeQuery(null,
            "SELECT count(*) FROM pragma_table_info('assistant_session') WHERE name='summary_version'",
            { c -> c.next(); QueryResult.Value(c.getLong(0)!!) }, 0).value)
        assertOriginalDiary(driver)
    }

    @Test fun legacyForeignKeyViolationsPreventSuccessfulMigration() = fixture(legacy=true) { driver, settings ->
        settings.upsert(SettingKeys.schemaVersion, "38")
        driver.execute(null, "PRAGMA foreign_keys=OFF", 0)
        driver.execute(null, """INSERT INTO reminder_event(reminder_id,event_type,actual_at)
            VALUES('missing-reminder','dismissed',42)""", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        assertFails { DatabaseMigration(driver, settings).runMigrations() }
        assertEquals("38", settings.get(SettingKeys.schemaVersion))
        assertOriginalDiary(driver)
    }

    @Test fun version38UpgradeAndRepeatedOpeningPreserveOriginalDiaryAndIdentity() = fixture(legacy=true) { driver, settings ->
        settings.upsert(SettingKeys.schemaVersion, "38")
        repeat(2) { DatabaseMigration(driver, settings).runMigrations() }
        assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
        assertOriginalDiary(driver)
    }

    private fun fixture(legacy:Boolean=false,block: (SqlDriver, SettingRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            if(legacy) DiaryLegacyFieldsFixture.install(driver)
            db.dailySatoriQueries.insertDiary("原文\u0000不能截断", "平静", 12, 34)
            if(legacy) driver.execute(null,"UPDATE diary SET tags='标签',images='diary_images/missing.png' WHERE id=1",0)
            else {
                com.dailysatori.data.repository.DiaryTagStore(db).initializeImported(1,listOf("标签"))
                com.dailysatori.data.repository.DiaryImages(db).replace(1,"diary_images/missing.png")
            }
            driver.execute(null,"UPDATE diary SET updated_at=34 WHERE id=1",0)
            block(driver, SettingRepository(db))
        } finally { driver.close() }
    }

    private fun assertOriginalDiary(driver: SqlDriver) {
        val diary = DiaryLegacyFieldsFixture.readDiary(driver,1)
        assertEquals("原文\u0000不能截断", diary.content)
        assertEquals("标签", diary.tags)
        assertEquals("diary_images/missing.png", diary.images)
        assertEquals(12, diary.created_at)
        assertEquals(34, diary.updated_at)
    }
}
