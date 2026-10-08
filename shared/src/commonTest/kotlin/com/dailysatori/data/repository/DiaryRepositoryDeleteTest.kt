package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryRepositoryDeleteTest {
    @Test
    fun deleteCollectsEveryAttachmentPathAndCleansThemOnlyAfterTheDiaryTransaction() {
        val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt").readText()
        val transactionStart = source.indexOf("q.transactionWithResult")
        val deleteCall = source.indexOf("q.deleteDiary(id)")
        val cleanupCall = source.indexOf("deleteAppOwnedFile(path)")

        assertTrue(source.contains("q.selectDiaryRepliesForRoot(id).executeAsList()"))
        assertTrue(source.contains("q.selectAttachmentsForDiary(record.id).executeAsList()"))
        assertTrue(source.contains("flatMap { diaryImagePaths(it.images) }"))
        assertTrue(source.contains("replies.forEach { reply -> q.deleteDiary(reply.id) }"))
        assertTrue(source.contains("map { it.local_path }"))
        assertTrue(transactionStart >= 0)
        assertTrue(deleteCall > transactionStart)
        assertTrue(cleanupCall > deleteCall)
        assertTrue(source.contains("attachmentPaths.filter { it.isNotBlank() }.forEach"))
    }

    @Test
    fun deleteRootCleansReplyAttachmentsAndPolishHistory() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val q = db.dailySatoriQueries
            val diaries = DiaryRepository(db, driver)
            val threads = DiaryThreadRepository(db, driver)
            val attachments = DiaryAttachmentRepository(db, driver)
            val settings = SettingRepository(db)

            val rootId = diaries.create(content = "根日记")
            val replyId = threads.createReply(rootId, "续写")
            q.updateDiary("续写", null, null, "/images/reply-a.png,/images/reply-b.png", 5_000, replyId)
            val rootAttachment = attachments.create(
                rootId,
                DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/audio/root.m4a"),
            )
            val replyAttachment = attachments.create(
                replyId,
                DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/audio/reply.m4a"),
            )
            val otherId = diaries.create(content = "其它日记")
            val otherAttachment = attachments.create(
                otherId,
                DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/audio/other.m4a"),
            )
            listOf(rootAttachment, replyAttachment, otherAttachment).forEach { id ->
                settings.upsert("diary_polished_transcript_v1:$id", "{\"content\":\"整理版\"}")
            }

            diaries.delete(rootId)

            assertNull(diaries.getById(rootId))
            assertNull(diaries.getById(replyId))
            assertTrue(q.selectAttachmentsForDiary(rootId).executeAsList().isEmpty())
            assertTrue(q.selectAttachmentsForDiary(replyId).executeAsList().isEmpty())
            assertNull(settings.get("diary_polished_transcript_v1:$rootAttachment"))
            assertNull(settings.get("diary_polished_transcript_v1:$replyAttachment"))
            assertEquals("/audio/other.m4a", attachments.getById(otherAttachment)!!.local_path)
            assertTrue(settings.get("diary_polished_transcript_v1:$otherAttachment") != null)
        } finally {
            driver.close()
        }
    }

    @Test
    fun deleteFailureLeavesDiaryAndAttachmentsAndCannotRunFileCleanup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val repository = DiaryRepository(db, driver)
            val q = db.dailySatoriQueries

            q.insertDiary("delete failure", null, null, null, 1, 1)
            q.insertDiaryAttachment(
                diary_id = 1,
                kind = "file",
                local_path = "/app-owned/should-remain.bin",
                display_name = "should-remain.bin",
                mime_type = "application/octet-stream",
                size_bytes = 1,
                duration_ms = 0,
                transcript = "",
                transcript_status = "none",
                knowledge_status = "none",
                error_message = "",
                created_at = 2,
                updated_at = 2,
            )
            driver.execute(
                null,
                """
                CREATE TRIGGER fail_diary_delete
                BEFORE DELETE ON diary
                BEGIN
                    SELECT RAISE(ABORT, 'simulated diary delete failure');
                END;
                """.trimIndent(),
                0,
            )

            assertFailsWith<Exception> { repository.delete(1) }

            assertEquals(1, q.selectDiaryById(1).executeAsList().size)
            assertEquals(1, q.selectAttachmentsForDiary(1).executeAsList().size)

            val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt").readText()
            assertTrue(source.contains("q.transactionWithResult"))
            assertTrue(source.contains("attachmentPaths.filter { it.isNotBlank() }.forEach"))
        } finally {
            driver.close()
        }
    }
}
