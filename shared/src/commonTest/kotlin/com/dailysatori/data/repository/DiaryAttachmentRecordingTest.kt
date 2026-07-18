package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
                diaryId = 1,
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

    @Test
    fun recordingUpdatesRejectMissingCrossDiaryAndNonAudioAttachments() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("first", null, null, null, 1, 1)
            db.dailySatoriQueries.insertDiary("second", null, null, null, 1, 1)
            val repository = DiaryAttachmentRepository(db, driver)
            val audioId = repository.create(1, DiaryAttachmentDraft(DiaryAttachmentKind.audio, ""))
            val imageId = repository.create(1, DiaryAttachmentDraft(DiaryAttachmentKind.image, "/image.jpg"))

            repository.beginRecording(1, audioId)

            assertEquals(
                DiaryAttachmentRecordingTargetError.NOT_FOUND,
                assertFailsWith<DiaryAttachmentRecordingTargetException> {
                    repository.completeRecording(1, 999, "/voice.m4a", 10, 20)
                }.error,
            )
            assertEquals(
                DiaryAttachmentRecordingTargetError.DIARY_MISMATCH,
                assertFailsWith<DiaryAttachmentRecordingTargetException> {
                    repository.beginRecording(2, audioId)
                }.error,
            )
            assertEquals(
                DiaryAttachmentRecordingTargetError.NOT_AUDIO,
                assertFailsWith<DiaryAttachmentRecordingTargetException> {
                    repository.failRecording(1, imageId, null, 0, 0, "failed")
                }.error,
            )

            assertEquals("", db.dailySatoriQueries.selectDiaryAttachmentById(audioId).executeAsOne().local_path)
            assertEquals("/image.jpg", db.dailySatoriQueries.selectDiaryAttachmentById(imageId).executeAsOne().local_path)
        } finally {
            driver.close()
        }
    }

    @Test
    fun failureWithoutAnActualPartialFilePreservesExistingMediaMetadata() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("voice diary", null, null, null, 1, 1)
            val repository = DiaryAttachmentRepository(db, driver)
            val attachmentId = repository.create(
                1,
                DiaryAttachmentDraft(
                    kind = DiaryAttachmentKind.audio,
                    localPath = "/existing.m4a",
                    sizeBytes = 80,
                    durationMs = 90,
                ),
            )

            repository.failRecording(
                diaryId = 1,
                id = attachmentId,
                localPath = null,
                sizeBytes = 0,
                durationMs = 0,
                errorCode = "recorder_start_failed",
            )

            val attachment = db.dailySatoriQueries.selectDiaryAttachmentById(attachmentId).executeAsOne()
            assertEquals("/existing.m4a", attachment.local_path)
            assertEquals(80, attachment.size_bytes)
            assertEquals(90, attachment.duration_ms)
            assertEquals(DiaryAttachmentProcessingStatus.failed, attachment.transcript_status)
            assertEquals("recorder_start_failed", attachment.error_message)
        } finally {
            driver.close()
        }
    }

    @Test
    fun interruptedRecordingIsMarkedFailedOnNextAppSession() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("voice diary", null, null, null, 1, 1)
            val repository = DiaryAttachmentRepository(db, driver)
            val interruptedId = repository.create(1, DiaryAttachmentDraft(DiaryAttachmentKind.audio, ""))
            val untouchedId = repository.create(1, DiaryAttachmentDraft(DiaryAttachmentKind.audio, ""))

            repository.beginRecording(1, interruptedId)
            repository.recoverInterruptedRecordings(startedBefore = Long.MAX_VALUE)

            val interrupted = repository.getById(interruptedId)!!
            assertEquals(DiaryAttachmentProcessingStatus.failed, interrupted.transcript_status)
            assertEquals("recording_process_interrupted", interrupted.error_message)
            assertEquals(DiaryAttachmentProcessingStatus.none, repository.getById(untouchedId)!!.transcript_status)
        } finally {
            driver.close()
        }
    }

    @Test
    fun recoveryDoesNotFailRecordingStartedAfterCurrentProcessOpened() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("voice diary", null, null, null, 1, 1)
            val repository = DiaryAttachmentRepository(db, driver)
            val attachmentId = repository.create(1, DiaryAttachmentDraft(DiaryAttachmentKind.audio, ""))

            repository.beginRecording(1, attachmentId)
            repository.recoverInterruptedRecordings(startedBefore = Long.MIN_VALUE)

            val attachment = repository.getById(attachmentId)!!
            assertEquals(DiaryAttachmentProcessingStatus.none, attachment.transcript_status)
            assertEquals("recording_active", attachment.error_message)
        } finally {
            driver.close()
        }
    }
}
