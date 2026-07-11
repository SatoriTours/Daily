package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import com.dailysatori.service.migration.DatabaseMigration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryAttachmentSchemaTest {
    @Test
    fun schemaDefinesDiaryAttachmentsWithLifecycleQueries() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(schema.contains("CREATE TABLE diary_attachment"))
        assertTrue(schema.contains("diary_id INTEGER NOT NULL REFERENCES diary(id) ON DELETE CASCADE"))
        assertTrue(schema.contains("transcript_status TEXT NOT NULL DEFAULT 'none'"))
        assertTrue(schema.contains("knowledge_status TEXT NOT NULL DEFAULT 'none'"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_diary_attachment_diary_created"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_diary_attachment_transcript_status"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_diary_attachment_knowledge_status"))
        assertTrue(schema.contains("insertDiaryAttachment:"))
        assertTrue(schema.contains("selectAttachmentsForDiary:"))
        assertTrue(schema.contains("SET transcript = ?, transcript_status = ?"))
        assertTrue(schema.contains("SET knowledge_status = ?, error_message = ?"))
        assertTrue(schema.contains("deleteDiaryAttachmentById:"))
        assertFalse(schema.contains("RETURNING"))
    }

    @Test
    fun migrationCreatesTheCanonicalAttachmentTableAndIndexes() {
        val migration = File("src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()
        val v20Migration = migration.substringAfter("private fun migrateV19ToV20()")
            .substringBefore("private fun getCurrentVersion()")

        assertTrue(migration.contains("migrateV19ToV20()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS diary_attachment"))
        assertTrue(migration.contains("CREATE INDEX IF NOT EXISTS idx_diary_attachment_diary_created"))
        assertTrue(migration.contains("CREATE INDEX IF NOT EXISTS idx_diary_attachment_transcript_status"))
        assertTrue(migration.contains("CREATE INDEX IF NOT EXISTS idx_diary_attachment_knowledge_status"))
        assertFalse(v20Migration.contains("catch"))
    }

    @Test
    fun attachmentDeletionDelegatesOwnershipChecksToTheFileManager() {
        val repository = File("src/commonMain/kotlin/com/dailysatori/data/repository/DiaryAttachmentRepository.kt").readText()

        assertTrue(repository.contains("fileManager?.deleteAppOwnedFile(path)"))
        assertFalse(repository.contains("path.startsWith(manager.getAppDataDir())"))
    }

    @Test
    fun v20MigrationFailurePropagatesForTheNextStartupToRetry() {
        assertFailsWith<IllegalStateException> {
            DatabaseMigration.migrateDiaryAttachmentSchema {
                throw IllegalStateException("simulated schema failure")
            }
        }
    }

    @Test
    fun androidDriverEnablesForeignKeyConstraintsOnOpen() {
        val driver = File("src/androidMain/kotlin/com/dailysatori/platform/DatabaseDriverFactory.android.kt").readText()

        assertTrue(driver.contains("AndroidSqliteDriver.Callback(DailySatoriDatabase.Schema)"))
        assertTrue(driver.contains("override fun onOpen(db: SupportSQLiteDatabase)"))
        assertTrue(driver.contains("db.setForeignKeyConstraintsEnabled(true)"))
        assertTrue(driver.contains("import androidx.sqlite.db.SupportSQLiteDatabase"))
    }

    @Test
    fun deletingDiaryCascadesItsAttachmentsOnSqlite() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val q = db.dailySatoriQueries

            q.insertDiary("diary with attachment", null, null, null, 1, 1)
            q.insertDiaryAttachment(
                diary_id = 1,
                kind = "image",
                local_path = "/data/user/0/com.dailysatori/files/attachment.jpg",
                display_name = "attachment.jpg",
                mime_type = "image/jpeg",
                size_bytes = 1,
                duration_ms = 0,
                transcript = "",
                transcript_status = "none",
                knowledge_status = "none",
                error_message = "",
                created_at = 2,
                updated_at = 2,
            )

            assertEquals(1, q.selectAttachmentsForDiary(1).executeAsList().size)

            q.deleteDiary(1)

            assertEquals(0, q.selectAttachmentsForDiary(1).executeAsList().size)
        } finally {
            driver.close()
        }
    }
}
