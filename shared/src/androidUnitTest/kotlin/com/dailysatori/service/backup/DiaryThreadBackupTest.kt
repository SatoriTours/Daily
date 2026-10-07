package com.dailysatori.service.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryAttachmentDraft
import com.dailysatori.data.repository.DiaryAttachmentKind
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryThreadBackupTest {
    @Test
    fun backupRoundTripKeepsThreadSourcesAndPendingSummary() = withDatabase { driver ->
        val db = DailySatoriDatabase(driver)
        val appDataDir = "/data/user/0/com.dailysatori/files/DailySatori"
        val diaries = DiaryRepository(db, driver)
        val threads = DiaryThreadRepository(db, driver)
        val attachments = DiaryAttachmentRepository(db, driver)
        val rootId = diaries.create(content = "根原文")
        val replyId = threads.createReply(rootId, "第一次续写")
        db.dailySatoriQueries.updateDiary("根原文", null, null, "$appDataDir/diary_images/root.png", 1_000, rootId)
        db.dailySatoriQueries.updateDiary("第一次续写", null, null, "$appDataDir/diary_images/reply.png", 2_000, replyId)
        attachments.create(rootId, DiaryAttachmentDraft(DiaryAttachmentKind.audio, "$appDataDir/diary/audio/root.m4a"))
        attachments.create(replyId, DiaryAttachmentDraft(DiaryAttachmentKind.audio, "$appDataDir/diary/audio/reply.m4a"))
        val summarizedRevision = threads.getSnapshot(rootId)!!.revision
        assertTrue(threads.commitSummary(rootId, summarizedRevision, "旧汇总"))
        threads.createReply(rootId, "原文又变了")

        val backupData = BackupDatabaseData(driver)
        val files = backupData.userFiles(appDataDir)

        assertTrue(files.contains("diary_images/root.png"), "主日记图片必须随备份")
        assertTrue(files.contains("diary_images/reply.png"), "续写图片必须随备份")
        assertTrue(files.contains("diary/audio/root.m4a"))
        assertTrue(files.contains("diary/audio/reply.m4a"), "续写录音必须随备份")

        backupData.prepareRestore(appDataDir, "/backups", files.toSet())

        assertEquals("diary_images/reply.png", diaries.getById(replyId)!!.images)
        assertEquals(
            "$appDataDir/diary/audio/reply.m4a",
            attachments.getForDiary(replyId).single().local_path,
        )
        val source = requireNotNull(threads.getSource(rootId))
        assertTrue(source.content.contains("根原文"))
        assertTrue(source.content.contains("第一次续写"))
        assertTrue(source.content.contains("原文又变了"))
        val summary = threads.getSnapshot(rootId)!!.summary!!
        assertEquals("旧汇总", summary.text)
        assertTrue(summary.summaryRevision < source.revision, "恢复后旧汇总仍标记待更新")

        assertEquals(listOf(rootId), threads.pendingSummaryRootIds(), "恢复后可以补做汇总")
    }

    private fun withDatabase(block: suspend (JdbcSqliteDriver) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            block(driver)
        } finally {
            driver.close()
        }
    }
}
