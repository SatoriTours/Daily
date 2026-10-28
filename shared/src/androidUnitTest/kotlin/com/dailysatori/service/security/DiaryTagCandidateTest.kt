package com.dailysatori.service.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.diary.*
import com.dailysatori.service.migration.DiaryTagMigration
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.json.Json
import kotlin.test.*

class DiaryTagCandidateTest {
    @Test fun candidateAllowsOnlyExactTagTransformationAndRejectsTampering() = fixture { driver,db ->
        val expected=MigrationContentSnapshot.capture(driver)
        DiaryTagMigration.migrate(driver)
        expected.requirePreserved(driver,migratedTags=true)
        driver.execute(null,"UPDATE diary_tag_definition SET name='changed' WHERE name='读书'",0)
        assertFails { expected.requirePreserved(driver,migratedTags=true) }
    }

    @Test fun retiringKnownStateNeverHidesOtherSettingsOrOriginalDiaryChanges() = fixture { driver,db ->
        val expected=MigrationContentSnapshot.capture(driver)
        DiaryTagMigration.migrate(driver)
        expected.requirePreserved(driver,migratedTags=true)
        driver.execute(null,"UPDATE setting SET value='changed' WHERE key='unrelated'",0)
        assertFails { expected.requirePreserved(driver,migratedTags=true) }
    }

    @Test fun physicalPreThreadDatabaseDoesNotNeedFutureColumnsToCaptureTagConversion() = fixture { driver,db ->
        driver.executeQuery(null,"SELECT name FROM sqlite_master WHERE type='trigger' AND (name LIKE 'diary_thread_%' OR name LIKE 'diary_tag_%')",{
            val names=mutableListOf<String>()
            while(it.next().value) names+=it.getString(0)!!
            app.cash.sqldelight.db.QueryResult.Value(names)
        },0).value.forEach { driver.execute(null,"DROP TRIGGER $it",0) }
        driver.execute(null,"DROP TABLE diary_thread_revision",0)
        driver.execute(null,"DROP INDEX idx_diary_parent_created",0)
        driver.execute(null,"ALTER TABLE diary DROP COLUMN parent_diary_id",0)
        val fingerprint=diaryTagFingerprint("body\u0000tail")
        SettingRepository(db).upsert("diary_tag_state_v1_1",Json.encodeToString(DiaryTagState(
            automatic=listOf("读书"),suppressed=listOf("旅行"),contentFingerprint=fingerprint,processedFingerprint=fingerprint)))
        val expected=MigrationContentSnapshot.capture(driver)
        driver.execute(null,"ALTER TABLE diary ADD COLUMN parent_diary_id INTEGER REFERENCES diary(id)",0)
        com.dailysatori.service.migration.DatabaseMigration.migrateDiaryThreadSchema { sql -> driver.execute(null,sql,0) }
        DiaryTagMigration.migrate(driver)
        expected.requirePreserved(driver,migratedTags=true)
    }

    private fun fixture(block:(JdbcSqliteDriver,DailySatoriDatabase)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
            val db=DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("body\u0000tail", null, 10, 20)
            SettingRepository(db).upsert("diary_tag_state_v1_1",Json.encodeToString(DiaryTagState(
                automatic=listOf("读书"),suppressed=listOf("旅行"),contentFingerprint="old\u0000fingerprint")))
            SettingRepository(db).upsert("unrelated","keep\u0000tail")
            block(driver,db)
        }
    }
}
