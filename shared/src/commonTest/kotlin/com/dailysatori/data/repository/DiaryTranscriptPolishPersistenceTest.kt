package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.diary.DiaryPolishedTranscript
import com.dailysatori.service.diary.adoptDiaryPolishVersion
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryTranscriptPolishPersistenceTest {
    @Test fun savingAndReopeningKeepsOriginalAndTracksOnlyAppliedText() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("整理版", null, null, null, 1, 1)
            db.dailySatoriQueries.insertDiary("别的日记", null, null, null, 1, 1)
            val repo = DiaryAttachmentRepository(db, driver)
            val id = repo.create(1, DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/voice.m4a", transcript = "嗯嗯原文"))
            val other = repo.create(2, DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/other.m4a", transcript = "另一个原文"))
            repo.savePolishedTranscripts(1, mapOf(id to DiaryPolishedTranscript("嗯嗯原文", "整理版"),
                other to DiaryPolishedTranscript("另一个原文", "整理版")))
            val reopened = DiaryAttachmentRepository(db, driver)
            assertEquals(mapOf(id to DiaryPolishedTranscript("嗯嗯原文", "整理版")), reopened.polishedTranscripts(1))
            assertEquals("嗯嗯原文", reopened.getById(id)?.transcript)
            assertTrue(reopened.polishedTranscripts(2).isEmpty())
            reopened.updateTranscriptStatus(id, "重新识别的原文", DiaryAttachmentProcessingStatus.completed)
            assertTrue(reopened.polishedTranscripts(1).isEmpty())
            repo.delete(id)
            assertEquals(null, SettingRepository(db).get("diary_polished_transcript_v1:$id"))
        } finally { driver.close() }
    }

    @Test fun savedOpinionsSurviveReopeningAndManualBodyChangesWithoutChangingTheTranscript() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("第二版", null, null, null, 1, 1)
            val repo = DiaryAttachmentRepository(db, driver)
            val id = repo.create(1, DiaryAttachmentDraft(DiaryAttachmentKind.audio, "", transcript = "原文"))
            val first = adoptDiaryPolishVersion("原文", "第一版").withFeedback(1, "别改我的语气")
            val second = adoptDiaryPolishVersion("原文", "第二版", first).withFeedback(2, "时间顺序还不对")
            repo.savePolishedTranscripts(1, mapOf(id to second))
            assertEquals(second, DiaryAttachmentRepository(db, driver).polishedTranscripts(1)[id])
            db.dailySatoriQueries.updateDiary("手动重写后的正文", null, null, null, 2, 1)
            repo.savePolishedTranscripts(1, mapOf(id to second))
            assertEquals(listOf("别改我的语气", "时间顺序还不对"),
                repo.polishedTranscripts(1).getValue(id).adoptedVersions().map { it.feedback })
            assertEquals("原文", repo.getById(id)?.transcript)
        } finally { driver.close() }
    }

    @Test fun unappliedDraftsAndRemovedVersionsAreNotPersisted() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("原文", null, null, null, 1, 1)
            val repo = DiaryAttachmentRepository(db, driver)
            val id = repo.create(1, DiaryAttachmentDraft(DiaryAttachmentKind.audio, "", transcript = "原文"))
            repo.savePolishedTranscripts(1, mapOf(id to DiaryPolishedTranscript("原文", "未采用的预览")))
            assertTrue(repo.polishedTranscripts(1).isEmpty())
            db.dailySatoriQueries.updateDiary("整理版", null, null, null, 2, 1)
            repo.savePolishedTranscripts(1, mapOf(id to DiaryPolishedTranscript("原文", "整理版")))
            repo.savePolishedTranscripts(1, emptyMap())
            assertTrue(repo.polishedTranscripts(1).isEmpty())
            assertEquals("原文", repo.getById(id)?.transcript)
        } finally { driver.close() }
    }
}
