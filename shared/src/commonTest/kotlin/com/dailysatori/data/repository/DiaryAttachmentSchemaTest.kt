package com.dailysatori.data.repository

import java.io.File
import com.dailysatori.service.migration.DatabaseMigration
import kotlin.test.Test
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

        assertTrue(repository.contains("manager.isAppDataPath(path)"))
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
}
