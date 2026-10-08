package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryAttachmentDraft
import com.dailysatori.data.repository.DiaryAttachmentKind
import com.dailysatori.data.repository.DiaryAttachmentProcessingStatus
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryTagRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.memory.MemoryExtractor
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryThreadConsumersTest {
    @Test
    fun tagsSeeReplyButKeepManualPolicy() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        fixture.db.dailySatoriQueries.updateDiaryTags("手动标签", rootId)
        fixture.reply(rootId, "我现在改主意了")

        val snapshot = requireNotNull(fixture.tags.prepare(rootId))

        assertTrue(snapshot.content.contains("我现在改主意了"))
        assertEquals(fixture.threads.getSource(rootId)!!.revision, snapshot.threadRevision)
        assertTrue(fixture.tags.apply(snapshot, listOf("自动标签辉")))
        assertEquals(
            listOf("手动标签", "自动标签辉"),
            parseDiaryTags(fixture.db.dailySatoriQueries.selectDiaryById(rootId).executeAsOne().tags),
        )
    }

    @Test
    fun tagResultBeforeReplyIsRejected() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        val snapshotBeforeReply = requireNotNull(fixture.tags.prepare(rootId))

        fixture.reply(rootId, "我现在改主意了")

        assertFalse(fixture.tags.apply(snapshotBeforeReply, listOf("旧标签")))
        val refreshed = requireNotNull(fixture.tags.prepare(rootId, force = true))
        assertTrue(fixture.tags.apply(refreshed, listOf("新标签")))
        assertEquals("新标签", fixture.db.dailySatoriQueries.selectDiaryById(rootId).executeAsOne().tags)
    }

    @Test
    fun replyIsNotAThreadRootForTags() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        val replyId = fixture.reply(rootId, "续写")

        assertNull(fixture.tags.prepare(replyId))
    }

    @Test
    fun thoughtRefreshSeesReplyAndKeepsRootReference() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        fixture.reply(rootId, "我现在改主意了")
        val prompts = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.coroutineContext[kotlinx.coroutines.Job]!!
        val thoughtRepo = DiaryThoughtRepository(SettingRepository(fixture.db))
        val service = DiaryThoughtService(
            fixture.threads,
            thoughtRepo,
            DiaryThoughtGenerator { prompt, _ ->
                prompts.add(prompt)
                "{\"thoughts\":[]}"
            },
        )
        try {
            service.start(scope) {}
            withTimeout(8_000) { service.runPendingRefresh() }
            assertTrue(prompts.any { it.contains("我现在改主意了") })
            assertTrue(prompts.any { it.contains("\"diaryId\":$rootId") })
            assertTrue(prompts.none { it.contains(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY) })
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun thoughtChatRejectsArchiveBeforeReply() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        val createdAt = fixture.diaries.getById(rootId)!!.created_at
        val thoughtRepo = DiaryThoughtRepository(SettingRepository(fixture.db))
        thoughtRepo.setUseInChat(true)
        thoughtRepo.save(
            DiaryThoughtArchive(
                fingerprint = diaryThoughtFingerprint(listOf(DiaryThoughtSource(rootId, "原始想法", createdAt)), ""),
                thoughts = listOf(
                    DiaryThought("做事准则", "先做重要的事", "明确表达", listOf(DiaryThoughtEvidence(rootId, "原始想法"))),
                ),
                diaryCount = 1,
                generatedAt = 1,
            ),
        )

        fixture.reply(rootId, "我现在改主意了")

        assertNull(DiaryThoughtChatContextProvider(thoughtRepo, fixture.threads).getContext())
    }

    @Test
    fun laterMonthReplyInvalidatesOriginalMonth() = withFixture { fixture ->
        val rootId = fixture.root("原始想法", createdAt = 1_700_000_000_000)
        val before = fixture.diaries.getById(rootId)!!.updated_at
        fixture.reply(rootId, "下个月才补充的事实", createdAt = 1_702_700_000_000)

        val diaries = fixture.diaries.getByDateRangeSync(0, Long.MAX_VALUE)
        val texts = diaryMonthPromptTexts(fixture.threads, diaries)
        val latest = diaries.maxOf { it.updated_at }

        assertEquals(listOf(rootId), diaries.map { it.id })
        assertTrue(texts.single().contains("下个月才补充的事实"))
        assertTrue(latest > before)
        assertTrue(diaryMonthSummaryNeedsRefresh(MonthSummaryFingerprint(1, before), 1, latest, isCurrentMonth = true))
    }

    @Test
    fun knowledgeTaskForReplyWritesRootSourceOnce() = withFixture { fixture ->
            val rootId = fixture.root("主正文")
            val replyId = fixture.reply(rootId, "续写里的新事实")
            fixture.attachments.create(
                replyId,
                DiaryAttachmentDraft(
                    kind = DiaryAttachmentKind.audio,
                    localPath = "/audio/reply.m4a",
                    transcript = "续写录音转写",
                    transcriptStatus = DiaryAttachmentProcessingStatus.completed,
                ),
            )
            val calls = mutableListOf<Triple<Long, String, String>>()
            val coordinator = DiaryKnowledgeCoordinator(
                fixture.attachments,
                fixture.tasks,
                object : MemoryExtractor {
                    override suspend fun extractAndSave(sourceType: String, sourceId: Long, title: String, content: String) {
                        calls += Triple(sourceId, title, content)
                    }
                },
                fixture.threads,
            )

            val taskId = coordinator.enqueue(replyId, fixture.diaries.getById(replyId)!!.updated_at)
            val result = coordinator.execute(taskId, "{\"diaryId\":$replyId}", "", NoopReporter)

            assertTrue(result is AsyncTaskExecutionResult.Success)
            assertEquals(1, calls.size)
            assertEquals(rootId, calls.single().first)
            assertTrue(calls.single().third.contains("续写里的新事实"))
            assertTrue(calls.single().third.contains("续写录音转写"))
    }

    private fun withFixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            block(
                Fixture(
                    db = db,
                    diaries = DiaryRepository(db, driver),
                    threads = DiaryThreadRepository(db, driver),
                    attachments = DiaryAttachmentRepository(db, driver),
                    tasks = AsyncTaskRepository(db),
                    tags = DiaryTagRepository(db, DiaryThreadRepository(db, driver)),
                ),
            )
        } finally {
            driver.close()
        }
    }

    private class Fixture(
        val db: DailySatoriDatabase,
        val diaries: DiaryRepository,
        val threads: DiaryThreadRepository,
        val attachments: DiaryAttachmentRepository,
        val tasks: AsyncTaskRepository,
        val tags: DiaryTagRepository,
    ) {
        fun root(content: String, createdAt: Long = 1_000): Long {
            db.dailySatoriQueries.insertDiary(content, null, null, null, createdAt, createdAt)
            return db.dailySatoriQueries.selectAllDiaries().executeAsList().maxBy { it.id }.id
        }

        fun reply(rootId: Long, content: String, createdAt: Long = 2_000): Long {
            db.dailySatoriQueries.insertDiaryReply(content, null, null, null, createdAt, createdAt, rootId)
            return db.dailySatoriQueries.selectDiaryRepliesForRoot(rootId).executeAsList().last().id
        }
    }

    private object NoopReporter : AsyncTaskProgressReporter {
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = Unit
    }
}
