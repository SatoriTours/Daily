package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.diary.DiaryThreadSummaryStatus
import com.dailysatori.service.diary.DiaryTranscriptionCoordinator
import com.dailysatori.service.diary.renderDiaryThreadContent
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryThreadRepositoryTest {
    @Test
    fun retainsOriginalAndTwoRepliesWithStableOrder() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始想法")
        val replyA = fixture.reply(rootId, "第一次续写")
        val replyB = fixture.reply(rootId, "第二次续写")

        val snapshot = requireNotNull(fixture.threads.getSnapshot(rootId))

        assertEquals(listOf(rootId, replyA, replyB), snapshot.entries.map { it.id })
        assertEquals("原始想法", fixture.diaries.getById(rootId)!!.content)
        assertEquals("第一次续写", fixture.diaries.getById(replyA)!!.content)
        assertEquals(listOf(rootId, replyA, replyB), snapshot.entries.map { it.id }.distinct())
        assertEquals(rootId, fixture.threads.rootId(replyB))
        assertEquals(rootId, fixture.threads.rootId(rootId))
    }

    @Test
    fun sameMillisecondRepliesKeepInsertionOrder() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "同一毫秒")
        fixture.db.dailySatoriQueries.insertDiaryReply("a", null, null, null, 1000, 1000, rootId)
        fixture.db.dailySatoriQueries.insertDiaryReply("b", null, null, null, 1000, 1000, rootId)

        val ids = requireNotNull(fixture.threads.getSnapshot(rootId)).entries.map { it.id }

        assertEquals(ids, ids.sorted())
        assertEquals(3, ids.size)
    }

    @Test
    fun rejectsNestedReply() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        val replyId = fixture.reply(rootId, "续写")

        assertFailsWith<IllegalArgumentException> { runBlocking { fixture.reply(replyId, "嵌套") } }
        assertFailsWith<IllegalArgumentException> { runBlocking { fixture.reply(9999, "无主") } }
    }

    @Test
    fun snapshotRejectsReplyAsRoot() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        val replyId = fixture.reply(rootId, "续写")

        assertNull(fixture.threads.getSnapshot(replyId))
        assertNull(fixture.threads.getSnapshot(4242))
    }

    @Test
    fun rejectsStaleSummaryAfterDirectSqlUpdate() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        fixture.reply(rootId, "续写")
        val oldRevision = requireNotNull(fixture.threads.getSnapshot(rootId)).revision

        fixture.db.dailySatoriQueries.updateDiary("转写回写的正文", null, null, null, 2000, rootId)

        assertFalse(fixture.threads.commitSummary(rootId, oldRevision, "过期结果"))
        val newRevision = requireNotNull(fixture.threads.getSnapshot(rootId)).revision
        assertTrue(newRevision > oldRevision)
        assertTrue(fixture.threads.commitSummary(rootId, newRevision, "最新汇总"))
        assertEquals("最新汇总", fixture.threads.getSnapshot(rootId)!!.summary!!.text)
        assertEquals(DiaryThreadSummaryStatus.ready, fixture.threads.getSnapshot(rootId)!!.summary!!.status)
    }

    @Test
    fun tagOnlyUpdateDoesNotBumpRevision() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        val revision = requireNotNull(fixture.threads.getSnapshot(rootId)).revision

        fixture.db.dailySatoriQueries.updateDiaryTags("标签", rootId)

        assertEquals(revision, requireNotNull(fixture.threads.getSnapshot(rootId)).revision)
    }

    @Test
    fun replyInsertBumpsRootUpdatedAtMonotonically() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        val before = fixture.diaries.getById(rootId)!!.updated_at

        fixture.reply(rootId, "续写")

        val after = fixture.diaries.getById(rootId)!!.updated_at
        assertTrue(after > before, "root updated_at must move forward: $after > $before")
        assertEquals("原始", fixture.diaries.getById(rootId)!!.content)
    }

    @Test
    fun directContentWriteBumpsRevisionAndRootUpdatedAt() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        val revision = requireNotNull(fixture.threads.getSnapshot(rootId)).revision
        val updatedBefore = fixture.diaries.getById(rootId)!!.updated_at

        fixture.db.dailySatoriQueries.updateDiary("自动标题\n\n原始", null, null, null, 5, rootId)

        val revisionAfter = requireNotNull(fixture.threads.getSnapshot(rootId)).revision
        val updatedAfter = fixture.diaries.getById(rootId)!!.updated_at
        assertTrue(revisionAfter > revision, "revision $revisionAfter > $revision")
        assertTrue(updatedAfter > updatedBefore, "updated_at $updatedAfter > $updatedBefore")
    }

    @Test
    fun replyTranscriptWriteBumpsRootRevision() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        val replyId = fixture.reply(rootId, "录音中")
        val revision = requireNotNull(fixture.threads.getSnapshot(rootId)).revision

        fixture.db.dailySatoriQueries.updateDiary("真实转写", null, null, null, 9, replyId)

        assertTrue(requireNotNull(fixture.threads.getSnapshot(rootId)).revision > revision)
    }

    @Test
    fun summaryCommitRejectsDeletedRoot() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        fixture.reply(rootId, "续写")
        val revision = requireNotNull(fixture.threads.getSnapshot(rootId)).revision

        fixture.diaries.delete(rootId)

        assertFalse(fixture.threads.commitSummary(rootId, revision, "复活"))
        assertFalse(fixture.threads.markSummaryState(rootId, revision, DiaryThreadSummaryStatus.failed, "删除后失败"))
        assertNull(fixture.threads.getSnapshot(rootId))
    }

    @Test
    fun oldFailureCannotOverwriteNewRevisionState() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        fixture.reply(rootId, "续写")
        val staleRevision = requireNotNull(fixture.threads.getSnapshot(rootId)).revision
        fixture.reply(rootId, "又一条续写")

        assertFalse(fixture.threads.markSummaryState(rootId, staleRevision, DiaryThreadSummaryStatus.failed, "旧请求失败"))
        assertNull(fixture.threads.getSnapshot(rootId)!!.summary)
    }

    @Test
    fun markSummaryStatePreservesLastSuccessfulText() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        fixture.reply(rootId, "续写")
        val revision = requireNotNull(fixture.threads.getSnapshot(rootId)).revision
        assertTrue(fixture.threads.commitSummary(rootId, revision, "上次成功汇总"))

        assertTrue(fixture.threads.markSummaryState(rootId, revision, DiaryThreadSummaryStatus.failed, "boom"))

        val summary = requireNotNull(fixture.threads.getSnapshot(rootId)!!.summary)
        assertEquals("上次成功汇总", summary.text)
        assertEquals(DiaryThreadSummaryStatus.failed, summary.status)
        assertEquals("boom", summary.errorMessage)
    }

    @Test
    fun placeholderBodyIsNotRenderedAsOriginalText() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY)
        fixture.reply(rootId, "真正的续写")

        val source = requireNotNull(fixture.threads.getSource(rootId))

        assertFalse(source.content.contains(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY))
        assertTrue(source.content.contains("真正的续写"))
        assertTrue(source.content.contains("#$rootId"))
    }

    @Test
    fun renderDiaryThreadContentCarriesRecordBoundariesAndRealBodies() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始想法")
        val replyId = fixture.reply(rootId, "想法变了")
        val entries = requireNotNull(fixture.threads.getSnapshot(rootId)).entries

        val rendered = renderDiaryThreadContent(entries)

        assertTrue(rendered.contains("#$rootId"))
        assertTrue(rendered.contains("#$replyId"))
        assertTrue(rendered.contains("原始想法"))
        assertTrue(rendered.indexOf("原始想法") < rendered.indexOf("想法变了"))
    }

    @Test
    fun discardEmptyReplyOnlyRemovesContentlessReplies() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        val emptyReply = fixture.reply(rootId, "")
        val textReply = fixture.reply(rootId, "有内容")
        val audioReply = fixture.reply(rootId, "")
        val attachmentId = fixture.attachments.create(
            audioReply,
            DiaryAttachmentDraft(kind = DiaryAttachmentKind.audio, localPath = "/tmp/a.m4a"),
        )

        assertFalse(fixture.threads.discardEmptyReply(rootId))
        assertFalse(fixture.threads.discardEmptyReply(audioReply))
        assertFalse(fixture.threads.discardEmptyReply(textReply))
        assertTrue(fixture.threads.discardEmptyReply(emptyReply))
        assertNull(fixture.diaries.getById(emptyReply))
        assertEquals(audioReply, fixture.attachments.getById(attachmentId)!!.diary_id)
        assertEquals(listOf(rootId, textReply, audioReply), fixture.threads.getSnapshot(rootId)!!.entries.map { it.id })
    }

    @Test
    fun pendingRootsCoverMissingStaleAndRunningSummariesOnly() = withThreads { fixture ->
        val single = fixture.createRoot(content = "单篇")
        val fresh = fixture.createRoot(content = "等待汇总")
        fixture.reply(fresh, "续写")
        val failed = fixture.createRoot(content = "失败")
        fixture.reply(failed, "续写")
        val failedRevision = fixture.threads.getSnapshot(failed)!!.revision
        assertTrue(fixture.threads.markSummaryState(failed, failedRevision, DiaryThreadSummaryStatus.failed, "boom"))
        val stale = fixture.createRoot(content = "过期")
        fixture.reply(stale, "续写")
        val staleRevision = fixture.threads.getSnapshot(stale)!!.revision
        assertTrue(fixture.threads.commitSummary(stale, staleRevision, "旧汇总"))
        fixture.reply(stale, "新续写")

        val pending = fixture.threads.pendingSummaryRootIds().toSet()

        assertFalse(pending.contains(single))
        assertTrue(pending.contains(fresh))
        assertFalse(pending.contains(failed))
        assertTrue(pending.contains(stale))
    }

    @Test
    fun overviewRevisionMarksOldSummaryAsStale() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        fixture.reply(rootId, "续写")
        val revision = fixture.threads.getSnapshot(rootId)!!.revision
        assertTrue(fixture.threads.commitSummary(rootId, revision, "旧汇总"))

        val fresh = runBlocking { fixture.threads.observeOverviews().first() }.single()
        assertEquals(revision, fresh.revision)
        assertEquals(revision, fresh.summary!!.summaryRevision)
        assertEquals(1L, fresh.replyCount)

        fixture.reply(rootId, "新续写")
        val stale = runBlocking { fixture.threads.observeOverviews().first() }.single()
        assertTrue(stale.revision > stale.summary!!.summaryRevision, "旧汇总必须显示为待更新")

        assertTrue(fixture.threads.markSummaryState(rootId, stale.revision, DiaryThreadSummaryStatus.running))
        val running = runBlocking { fixture.threads.observeOverviews().first() }.single()
        assertEquals(stale.revision, running.summary!!.sourceRevision)
        assertEquals(revision, running.summary!!.summaryRevision, "旧 summaryRevision 必须保留")
        assertTrue(running.revision > running.summary!!.summaryRevision)
    }

    @Test
    fun observesSnapshotWithAttachmentsGroupedByDiary() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        val replyId = fixture.reply(rootId, "续写")
        fixture.attachments.create(rootId, DiaryAttachmentDraft(DiaryAttachmentKind.image, "/tmp/1.png"))
        fixture.attachments.create(replyId, DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/tmp/2.m4a"))

        val snapshot = runBlocking { fixture.threads.observeThread(rootId).first() }!!

        assertEquals(setOf(rootId, replyId), snapshot.attachments.map { it.diary_id }.toSet())
        assertEquals(2, snapshot.attachments.size)
    }

    @Test
    fun pendingAttachmentCountTracksUnfinishedTranscription() = withThreads { fixture ->
        val rootId = fixture.createRoot(content = "原始")
        val replyId = fixture.reply(rootId, "续写")
        val attachmentId = fixture.attachments.create(
            replyId,
            DiaryAttachmentDraft(
                kind = DiaryAttachmentKind.audio,
                localPath = "/tmp/a.m4a",
                transcriptStatus = DiaryAttachmentProcessingStatus.queued,
            ),
        )
        assertEquals(1L, fixture.threads.getSnapshot(rootId)!!.pendingAttachmentCount)

        fixture.attachments.updateTranscriptStatus(attachmentId, "文本", DiaryAttachmentProcessingStatus.completed)

        assertEquals(0L, fixture.threads.getSnapshot(rootId)!!.pendingAttachmentCount)
    }

    private fun withThreads(block: (Fixture) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            block(
                Fixture(
                    db = db,
                    diaries = DiaryRepository(db, driver),
                    threads = com.dailysatori.data.repository.DiaryThreadRepository(db, driver),
                    attachments = DiaryAttachmentRepository(db, driver),
                ),
            )
        } finally {
            driver.close()
        }
    }

    private data class Fixture(
        val db: DailySatoriDatabase,
        val diaries: DiaryRepository,
        val threads: DiaryThreadRepository,
        val attachments: DiaryAttachmentRepository,
    ) {
        fun createRoot(content: String): Long = runBlocking { diaries.create(content = content) }

        fun reply(rootId: Long, content: String): Long = runBlocking { threads.createReply(rootId, content) }
    }
}
