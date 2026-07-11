package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiaryRepositoryDeleteTest {
    @Test
    fun deleteCollectsEveryAttachmentPathAndCleansThemOnlyAfterTheDiaryTransaction() {
        val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt").readText()
        val transactionStart = source.indexOf("q.transactionWithResult")
        val deleteCall = source.indexOf("q.deleteDiary(id)")
        val cleanupCall = source.indexOf("deleteAppOwnedFile(path)")

        assertTrue(source.contains("q.selectAttachmentsForDiary(id).executeAsList()"))
        assertTrue(source.contains("map { it.local_path }"))
        assertTrue(transactionStart >= 0)
        assertTrue(deleteCall > transactionStart)
        assertTrue(cleanupCall > deleteCall)
        assertTrue(source.contains("attachmentPaths.forEach"))
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
            assertTrue(source.contains("attachmentPaths.forEach"))
        } finally {
            driver.close()
        }
    }
}
