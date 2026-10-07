package com.dailysatori.ui.feature.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.recording.DiaryRecordingState
import com.dailysatori.core.recording.DiaryRecordingStore
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryAttachmentProcessingStatus
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.diary.DiaryMonthSummaryService
import com.dailysatori.service.diary.DiaryThreadSummaryCoordinator
import com.dailysatori.service.diary.DiaryThreadSummaryGenerator
import com.dailysatori.service.diary.DiaryTranscriptionCoordinator
import com.dailysatori.service.memory.MemoryExtractor
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryContinuationEditorTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun replySaveKeepsRootOriginalAndEnqueuesSummary() = runBlocking {
        val fixture = diaryFixture()
        try {
            val rootId = fixture.diaryRepo.create("原始日记")

            val replyId = requireNotNull(fixture.viewModel.saveReplyAndGetId(rootId, "我现在改主意了"))

            assertEquals("原始日记", fixture.diaryRepo.getById(rootId)!!.content)
            assertEquals("我现在改主意了", fixture.diaryRepo.getById(replyId)!!.content)
            assertEquals(rootId, fixture.threadRepo.rootId(replyId))
            assertTrue(
                fixture.tasks.runnableTasksByType(SUMMARY_TYPE, Long.MAX_VALUE).isNotEmpty(),
                "保存续写后必须排队汇总",
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun typedDraftCancelDoesNotCreateReply() = runBlocking {
        val fixture = diaryFixture()
        try {
            val rootId = fixture.diaryRepo.create("原始日记")

            val snapshot = requireNotNull(fixture.threadRepo.getSnapshot(rootId))

            assertEquals(1, snapshot.entries.size)
            assertEquals(0L, requireNotNull(fixture.threadRepo.observeOverviews().first().singleOrNull()).replyCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun recordingUsesReplyIdAndPendingAttachment() = runBlocking {
        val fixture = diaryFixture()
        try {
            val rootId = fixture.diaryRepo.create("原始日记")

            val (replyId, attachmentId) = requireNotNull(fixture.viewModel.prepareReplyRecording(rootId))

            val reply = requireNotNull(fixture.diaryRepo.getById(replyId))
            assertEquals(rootId, reply.parent_diary_id)
            assertEquals(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY, reply.content)
            assertEquals(replyId, fixture.attachmentRepo.getById(attachmentId)!!.diary_id)
            assertEquals(listOf(replyId), fixture.threadRepo.getSnapshot(rootId)!!.attachments.map { it.diary_id })
            assertEquals(0L, fixture.threadRepo.getSnapshot(rootId)!!.pendingAttachmentCount)

            fixture.attachmentRepo.completeRecording(replyId, attachmentId, "/audio/reply.m4a", 10, 20)

            assertEquals(1L, fixture.threadRepo.getSnapshot(rootId)!!.pendingAttachmentCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun dismissDuringTranscriptionKeepsRecording() = runBlocking {
        val fixture = diaryFixture()
        try {
            val rootId = fixture.diaryRepo.create("原始日记")
            val (replyId, attachmentId) = requireNotNull(fixture.viewModel.prepareReplyRecording(rootId))

            fixture.viewModel.closeThread()

            assertFalse(fixture.viewModel.discardDraftReply(replyId))
            assertNull(fixture.viewModel.state.value.selectedThread)
            assertEquals(replyId, fixture.attachmentRepo.getById(attachmentId)!!.diary_id)
            assertEquals(2, fixture.threadRepo.getSnapshot(rootId)!!.entries.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun emptyReplyCleanupDoesNotDeleteAudio() = runBlocking {
        val fixture = diaryFixture()
        try {
            val rootId = fixture.diaryRepo.create("原始日记")
            val (replyId, _) = requireNotNull(fixture.viewModel.prepareReplyRecording(rootId))

            assertFalse(fixture.threadRepo.discardEmptyReply(replyId))
            assertTrue(fixture.threadRepo.getSnapshot(rootId)!!.attachments.isNotEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun emptyTypedReplyCanBeDiscarded() = runBlocking {
        val fixture = diaryFixture()
        try {
            val rootId = fixture.diaryRepo.create("原始日记")
            val emptyReplyId = fixture.threadRepo.createReply(rootId, "")

            assertTrue(fixture.viewModel.discardDraftReply(emptyReplyId))
            assertNull(fixture.diaryRepo.getById(emptyReplyId))
            assertEquals(1, fixture.threadRepo.getSnapshot(rootId)!!.entries.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun openThreadExposesOriginalBodyAndStaleSummaryRevision() = runBlocking {
        val fixture = diaryFixture()
        try {
            val rootId = fixture.diaryRepo.create("原始日记")
            val firstReply = fixture.threadRepo.createReply(rootId, "第一次续写")
            val revision = fixture.threadRepo.getSnapshot(rootId)!!.revision
            assertTrue(fixture.threadRepo.commitSummary(rootId, revision, "AI 汇总文字"))
            fixture.threadRepo.createReply(rootId, "第二次续写")

            fixture.viewModel.openThread(rootId)
            val state = withTimeout(5_000) {
                fixture.viewModel.state.first {
                    it.selectedThread?.root?.id == rootId && it.selectedThread?.summary != null
                }
            }
            val snapshot = requireNotNull(state.selectedThread)
            assertEquals("原始日记", snapshot.entries.first().content)
            assertEquals("第一次续写", snapshot.entries[1].content)
            assertTrue(snapshot.revision > snapshot.summary!!.summaryRevision, "旧汇总必须显示为待更新")
            assertEquals("AI 汇总文字", snapshot.summary!!.text)
            assertEquals(firstReply, snapshot.entries[1].id)

            val overview = withTimeout(5_000) {
                fixture.viewModel.state.first { it.threadOverviews[rootId]?.revision == snapshot.revision }
            }.threadOverviews.getValue(rootId)
            assertEquals(2L, overview.replyCount)
            assertTrue(overview.revision > overview.summary!!.summaryRevision)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun deleteRootStopsRecordingStartedFromReply() = runBlocking {
        val fixture = diaryFixture()
        try {
            val rootId = fixture.diaryRepo.create("原始日记")
            val (replyId, attachmentId) = requireNotNull(fixture.viewModel.prepareReplyRecording(rootId))
            fixture.recordingStore.publish(
                DiaryRecordingState.Recording(diaryId = replyId, attachmentId = attachmentId, elapsedMs = 0),
            )
            var stopCalled = false

            fixture.viewModel.deleteDiary(rootId) {
                stopCalled = true
                fixture.recordingStore.publish(DiaryRecordingState.Idle)
            }

            withTimeout(5_000) {
                while (fixture.diaryRepo.getById(rootId) != null) delay(20)
            }
            assertTrue(stopCalled, "续写录音也必须触发停止保护")
            assertNull(fixture.diaryRepo.getById(replyId))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun retryThreadSummaryEnqueuesExplicitTask() = runBlocking {
        val fixture = diaryFixture()
        try {
            val rootId = fixture.diaryRepo.create("原始日记")
            fixture.threadRepo.createReply(rootId, "续写")

            fixture.viewModel.retryThreadSummary(rootId)

            withTimeout(5_000) {
                var tasks = fixture.tasks.runnableTasksByType(SUMMARY_TYPE, Long.MAX_VALUE)
                while (tasks.isEmpty()) {
                    kotlinx.coroutines.delay(20)
                    tasks = fixture.tasks.runnableTasksByType(SUMMARY_TYPE, Long.MAX_VALUE)
                }
            }
        } finally {
            fixture.close()
        }
    }

    private fun diaryFixture(): DiaryFixture {
        // 文件驱动可让多个线程共享同一数据库；IN_MEMORY 只共享一个连接。
        val directory = Files.createTempDirectory("diary-continuation").toFile()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(directory, "diary.db").absolutePath}")
        val database = DailySatoriDatabase(driver)
        database.transaction { DailySatoriDatabase.Schema.create(driver) }
        val diaryRepo = DiaryRepository(database, driver)
        val attachmentRepo = DiaryAttachmentRepository(database, driver)
        val threadRepo = DiaryThreadRepository(database, driver)
        val tasks = AsyncTaskRepository(database)
        val monthSummaryRepo = DiaryMonthSummaryRepository(database)
        val recordingStore = DiaryRecordingStore()
        val httpClient = HttpClient()
        val coordinator = DiaryThreadSummaryCoordinator(
            threadRepo,
            tasks,
            DiaryThreadSummaryGenerator { _, _ -> """{"summary":"汇总正文"}""" },
        ) { true }
        val viewModel = DiaryViewModel(
            diaryRepo = diaryRepo,
            memoryExtractor = NoopMemoryExtractor(),
            monthSummaryRepo = monthSummaryRepo,
            monthSummaryService = DiaryMonthSummaryService(
                diaryRepo = diaryRepo,
                summaryRepo = monthSummaryRepo,
                aiConfigService = AiConfigService(AIConfigRepository(database, PlainSecretCipher)),
                aiService = AiService(httpClient),
                threads = threadRepo,
            ),
            attachmentRepo = attachmentRepo,
            recordingStore = recordingStore,
            threadRepo = threadRepo,
            threadSummaryCoordinator = coordinator,
        )
        return DiaryFixture(directory, driver, httpClient, diaryRepo, attachmentRepo, threadRepo, tasks, recordingStore, viewModel)
    }

    private class DiaryFixture(
        private val directory: File,
        private val driver: JdbcSqliteDriver,
        private val httpClient: HttpClient,
        val diaryRepo: DiaryRepository,
        val attachmentRepo: DiaryAttachmentRepository,
        val threadRepo: DiaryThreadRepository,
        val tasks: AsyncTaskRepository,
        val recordingStore: DiaryRecordingStore,
        val viewModel: DiaryViewModel,
    ) {
        fun close() {
            // 先取消 ViewModel 作用域，避免关闭驱动后后台观察者报错泄漏到其它测试。
            viewModel.viewModelScope.cancel()
            runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.join() }
            httpClient.close()
            driver.close()
            directory.deleteRecursively()
        }
    }

    private class NoopMemoryExtractor : MemoryExtractor {
        override suspend fun extractAndSave(sourceType: String, sourceId: Long, title: String, content: String) = Unit
    }

    private object PlainSecretCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }

    private companion object {
        const val SUMMARY_TYPE = "diary_thread_summarize"
    }
}
