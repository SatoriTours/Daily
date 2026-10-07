package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryAttachmentDraft
import com.dailysatori.data.repository.DiaryAttachmentKind
import com.dailysatori.data.repository.DiaryAttachmentProcessingStatus
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryThreadSummaryCoordinatorTest {
    @Test
    fun noReplyDoesNotCallAi() = withFixture { fixture ->
        var calls = 0
        val rootId = fixture.root("单篇日记")
        val coordinator = coordinator(fixture) { _, _ -> calls++; """{"summary":"不该调用"}""" }

        assertNull(coordinator.enqueue(rootId))
        assertEquals(0, calls)
        assertTrue(fixture.threads.pendingSummaryRootIds().isEmpty())
    }

    @Test
    fun summaryUsesAllEntriesAndPersistsWithMatchingRevision() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        fixture.reply(rootId, "我现在改主意了")
        val prompts = mutableListOf<String>()
        val coordinator = coordinator(fixture) { prompt, _ -> prompts.add(prompt); """{"summary":"汇总正文"}""" }

        val taskId = requireNotNull(coordinator.enqueue(rootId))
        val result = coordinator.execute(taskId, fixture.payload(taskId), "", NoopReporter)

        assertTrue(result is AsyncTaskExecutionResult.Success)
        val snapshot = requireNotNull(fixture.threads.getSnapshot(rootId))
        assertEquals("汇总正文", snapshot.summary!!.text)
        assertEquals(snapshot.revision, snapshot.summary!!.sourceRevision)
        assertEquals(DiaryThreadSummaryStatus.ready, snapshot.summary!!.status)
        assertTrue(prompts.any { it.contains("我现在改主意了") })
    }

    @Test
    fun failurePreservesLastSummaryAndKeepsTaskDeduplicated() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        fixture.reply(rootId, "续写")
        val working = coordinator(fixture) { _, _ -> """{"summary":"上次成功汇总"}""" }
        val first = requireNotNull(working.enqueue(rootId))
        working.execute(first, fixture.payload(first), "", NoopReporter)

        val failing = coordinator(fixture) { _, _ -> error("网络不可用") }
        val second = requireNotNull(failing.enqueue(rootId))
        val result = failing.execute(second, fixture.payload(second), "", NoopReporter)

        assertTrue(result is AsyncTaskExecutionResult.RetryableFailure)
        val snapshot = requireNotNull(fixture.threads.getSnapshot(rootId))
        assertEquals("上次成功汇总", snapshot.summary!!.text)
        assertEquals(DiaryThreadSummaryStatus.failed, snapshot.summary!!.status)
        assertFalse(fixture.threads.pendingSummaryRootIds().contains(rootId), "终态失败不得自动重排")
        assertEquals(second, failing.enqueue(rootId), "重试中的同一版本不重复入队")
    }

    @Test
    fun terminalFormatFailureStopsAutoRecoveryUntilRetryOrNewRevision() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        fixture.reply(rootId, "续写")
        val invalid = coordinator(fixture) { _, _ -> "not json" }
        val first = requireNotNull(invalid.enqueue(rootId))

        val result = invalid.execute(first, fixture.payload(first), "", NoopReporter)

        assertTrue(result is AsyncTaskExecutionResult.PermanentFailure)
        val snapshot = requireNotNull(fixture.threads.getSnapshot(rootId))
        assertTrue(snapshot.summary!!.text.isBlank())
        assertEquals(DiaryThreadSummaryStatus.failed, snapshot.summary!!.status)
        assertFalse(fixture.threads.pendingSummaryRootIds().contains(rootId), "终态失败不得形成自动重排循环")

        fixture.reply(rootId, "新的续写")
        assertTrue(fixture.threads.pendingSummaryRootIds().contains(rootId), "新原文版本可以重新入队")
    }

    @Test
    fun oldSuccessAndOldFailureCannotOverwriteNewRevision() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        fixture.reply(rootId, "第一次续写")
        val staleRevision = requireNotNull(fixture.threads.getSnapshot(rootId)).revision
        fixture.reply(rootId, "第二次续写")

        assertFalse(fixture.threads.commitSummary(rootId, staleRevision, "过期成功"))
        assertFalse(
            fixture.threads.markSummaryState(rootId, staleRevision, DiaryThreadSummaryStatus.failed, "旧请求失败"),
        )
        assertNull(fixture.threads.getSnapshot(rootId)!!.summary)
        assertTrue(fixture.threads.pendingSummaryRootIds().contains(rootId))
    }

    @Test
    fun deletionDuringRequestDoesNotRecreateRoot() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        fixture.reply(rootId, "续写")
        val coordinator = coordinator(fixture) { _, _ -> """{"summary":"汇总"}""" }
        val taskId = requireNotNull(coordinator.enqueue(rootId))
        val payload = fixture.payload(taskId)

        fixture.diaries.delete(rootId)
        val result = coordinator.execute(taskId, payload, "", NoopReporter)

        assertTrue(result is AsyncTaskExecutionResult.Success)
        assertNull(fixture.threads.getSnapshot(rootId))
        assertFalse(fixture.threads.pendingSummaryRootIds().contains(rootId))
    }

    @Test
    fun enqueueFailureIsRecoveredOnStart() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        fixture.reply(rootId, "续写")
        val unconfigured = DiaryThreadSummaryCoordinator(
            fixture.threads,
            fixture.tasks,
            DiaryThreadSummaryGenerator { _, _ -> """{"summary":"汇总"}""" },
        ) { false }
        assertNull(unconfigured.enqueue(rootId))
        assertTrue(fixture.threads.pendingSummaryRootIds().contains(rootId), "配置缺失不清除待汇总状态")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.coroutineContext[kotlinx.coroutines.Job]!!
        val scheduled = Channel<Long>(Channel.UNLIMITED)
        try {
            coordinator(fixture) { _, _ -> """{"summary":"汇总"}""" }.start(scope) { scheduled.trySend(it) }
            val taskId = withTimeout(5_000) { scheduled.receive() }
            assertEquals(rootId, fixture.rootIdOf(taskId))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun sameRevisionDeduplicatesTasks() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        fixture.reply(rootId, "续写")
        val coordinator = coordinator(fixture) { _, _ -> """{"summary":"汇总"}""" }

        val first = requireNotNull(coordinator.enqueue(rootId))
        val second = requireNotNull(coordinator.enqueue(rootId))

        assertEquals(first, second)
    }

    @Test
    fun placeholderTranscriptsAreNotSummarizedAsFact() = withFixture { fixture ->
        val rootId = fixture.root("原始想法")
        val replyId = fixture.reply(rootId, "")
        fixture.attachments.create(
            replyId,
            DiaryAttachmentDraft(
                kind = DiaryAttachmentKind.audio,
                localPath = "/audio/reply.m4a",
                transcriptStatus = DiaryAttachmentProcessingStatus.queued,
            ),
        )
        val prompts = mutableListOf<String>()
        val coordinator = coordinator(fixture) { prompt, _ -> prompts.add(prompt); """{"summary":"汇总"}""" }

        assertEquals(1L, fixture.threads.getSnapshot(rootId)!!.pendingAttachmentCount)
        val taskId = requireNotNull(coordinator.enqueue(rootId))
        coordinator.execute(taskId, fixture.payload(taskId), "", NoopReporter)

        assertTrue(prompts.isNotEmpty())
        assertTrue(prompts.none { it.contains(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY) })
        assertTrue(prompts.first().contains("原始想法"))
    }

    @Test
    fun purePendingBodyDoesNotWriteSummaryOrCallAi() = withFixture { fixture ->
        val rootId = fixture.root(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY)
        fixture.reply(rootId, DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY)
        var calls = 0
        val coordinator = coordinator(fixture) { _, _ -> calls++; """{"summary":"不该调用"}""" }

        val taskId = requireNotNull(coordinator.enqueue(rootId))
        val result = coordinator.execute(taskId, fixture.payload(taskId), "", NoopReporter)

        assertTrue(result is AsyncTaskExecutionResult.Success)
        assertEquals(0, calls)
        assertNull(fixture.threads.getSnapshot(rootId)!!.summary)
        assertTrue(fixture.threads.pendingSummaryRootIds().contains(rootId))
    }

    private fun coordinator(
        fixture: Fixture,
        complete: suspend (String, String) -> String,
    ) = DiaryThreadSummaryCoordinator(
        fixture.threads,
        fixture.tasks,
        DiaryThreadSummaryGenerator(complete),
    ) { true }

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
    ) {
        fun root(content: String, createdAt: Long = 1_000): Long {
            db.dailySatoriQueries.insertDiary(content, null, null, null, createdAt, createdAt)
            return db.dailySatoriQueries.selectAllDiaries().executeAsList().maxBy { it.id }.id
        }

        fun reply(rootId: Long, content: String, createdAt: Long = 2_000): Long {
            db.dailySatoriQueries.insertDiaryReply(content, null, null, null, createdAt, createdAt, rootId)
            return db.dailySatoriQueries.selectDiaryRepliesForRoot(rootId).executeAsList().last().id
        }

        fun payload(taskId: Long): String = requireNotNull(tasks.getById(taskId)).payload_json

        fun rootIdOf(taskId: Long): Long =
            Json.parseToJsonElement(payload(taskId)).jsonObject["rootId"]!!.jsonPrimitive.content.toLong()
    }

    private object NoopReporter : AsyncTaskProgressReporter {
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = Unit
    }
}
