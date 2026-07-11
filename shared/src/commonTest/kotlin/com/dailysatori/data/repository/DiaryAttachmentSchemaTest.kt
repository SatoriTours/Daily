package com.dailysatori.data.repository

import java.io.File
import kotlin.test.Test
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
    }

    @Test
    fun migrationCreatesTheCanonicalAttachmentTableAndIndexes() {
        val migration = File("src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

        assertTrue(migration.contains("migrateV19ToV20()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS diary_attachment"))
        assertTrue(migration.contains("CREATE INDEX IF NOT EXISTS idx_diary_attachment_diary_created"))
        assertTrue(migration.contains("CREATE INDEX IF NOT EXISTS idx_diary_attachment_transcript_status"))
        assertTrue(migration.contains("CREATE INDEX IF NOT EXISTS idx_diary_attachment_knowledge_status"))
    }
}
