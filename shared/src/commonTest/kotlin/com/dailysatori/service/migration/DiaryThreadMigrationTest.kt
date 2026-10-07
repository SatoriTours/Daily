package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryThreadMigrationTest {
    @Test
    fun version32SampleKeepsDiaryImagesAndAttachmentsAndCanBeRepeated() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            stripThreadStructures(driver)
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db)
            db.dailySatoriQueries.insertDiary("旧日记正文", "旧标签", "平静", "[\"/tmp/old.png\"]", 1_000, 1_000)
            db.dailySatoriQueries.insertDiaryAttachment(
                diary_id = 1, kind = "audio", local_path = "/tmp/old.m4a", display_name = "old.m4a",
                mime_type = "audio/mp4", size_bytes = 10, duration_ms = 20, transcript = "旧转写",
                transcript_status = "completed", knowledge_status = "none", error_message = "",
                created_at = 1_000, updated_at = 1_000,
            )
            val migration = DatabaseMigration(driver, settings, TestCipher)

            repeat(2) {
                settings.upsert(SettingKeys.schemaVersion, "32")
                migration.runMigrations()

                val diary = db.dailySatoriQueries.selectDiaryById(1).executeAsOne()
                assertEquals("旧日记正文", diary.content)
                assertEquals("[\"/tmp/old.png\"]", diary.images)
                assertNull(diary.parent_diary_id)
                val attachment = db.dailySatoriQueries.selectDiaryAttachmentById(1).executeAsOne()
                assertEquals("/tmp/old.m4a", attachment.local_path)
                assertEquals("旧转写", attachment.transcript)
                assertEquals(1L, db.dailySatoriQueries.selectDiaryThreadRevision(1).executeAsOne().revision)
                assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
            }
        } finally {
            driver.close()
        }
    }

    @Test
    fun migratedDatabaseAcceptsRepliesAndVersionedSummaryWrites() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            stripThreadStructures(driver)
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db)
            db.dailySatoriQueries.insertDiary("根", null, null, null, 1_000, 1_000)
            settings.upsert(SettingKeys.schemaVersion, "32")

            DatabaseMigration(driver, settings, TestCipher).runMigrations()

            db.dailySatoriQueries.insertDiaryReply("迁移后的续写", null, null, null, 2_000, 2_000, 1)
            assertEquals(1L, db.dailySatoriQueries.selectDiaryRepliesForRoot(1).executeAsOne().parent_diary_id)
            assertTrue(db.dailySatoriQueries.selectDiaryThreadRevision(1).executeAsOne().revision >= 2L)
        } finally {
            driver.close()
        }
    }

    private fun stripThreadStructures(driver: JdbcSqliteDriver) {
        listOf(
            "diary_thread_revision_after_insert",
            "diary_thread_revision_after_content_update",
            "diary_thread_root_updated_after_insert",
        ).forEach { driver.execute(null, "DROP TRIGGER IF EXISTS $it", 0) }
        driver.execute(null, "DROP TABLE IF EXISTS diary_thread_summary", 0)
        driver.execute(null, "DROP TABLE IF EXISTS diary_thread_revision", 0)
        driver.execute(null, "DROP INDEX IF EXISTS idx_diary_parent_created", 0)
        driver.execute(null, "ALTER TABLE diary DROP COLUMN parent_diary_id", 0)
    }

    private object TestCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
