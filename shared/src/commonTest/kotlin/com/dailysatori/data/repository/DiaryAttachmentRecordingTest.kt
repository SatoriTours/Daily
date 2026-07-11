package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class DiaryAttachmentRecordingTest {
    @Test
    fun completingRecordingPersistsPlayableMetadataAndQueuesTranscription() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("voice diary", null, null, null, 1, 1)
            val repository = DiaryAttachmentRepository(db, driver)
            val attachmentId = repository.create(
                diaryId = 1,
                draft = DiaryAttachmentDraft(
                    kind = DiaryAttachmentKind.audio,
                    localPath = "",
                    mimeType = "audio/mp4",
                ),
            )

            repository.completeRecording(
                id = attachmentId,
                localPath = "/files/DailySatori/diary/audio/1/voice.m4a",
                sizeBytes = 8_192,
                durationMs = 4_200,
            )

            val attachment = db.dailySatoriQueries.selectDiaryAttachmentById(attachmentId).executeAsOne()
            assertEquals("/files/DailySatori/diary/audio/1/voice.m4a", attachment.local_path)
            assertEquals(8_192, attachment.size_bytes)
            assertEquals(4_200, attachment.duration_ms)
            assertEquals(DiaryAttachmentProcessingStatus.queued, attachment.transcript_status)
            assertEquals("", attachment.error_message)
        } finally {
            driver.close()
        }
    }
}
