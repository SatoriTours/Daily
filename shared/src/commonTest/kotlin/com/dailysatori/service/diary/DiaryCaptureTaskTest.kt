package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryAttachmentDraft
import com.dailysatori.data.repository.DiaryAttachmentKind
import com.dailysatori.data.repository.DiaryAttachmentProcessingStatus
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.memory.MemoryExtractor
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiaryCaptureTaskTest {
    @Test
    fun transcriptionIsIdempotentAndPreservesUserEdits() = runBlocking {
        withFixture { fixture ->
            val diaryId = fixture.diaries.create("用户已经修改")
            val attachmentId = fixture.attachments.create(
                diaryId,
                DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/audio/test.m4a", mimeType = "audio/mp4"),
            )
            val coordinator = DiaryTranscriptionCoordinator(
                fixture.attachments,
                fixture.diaries,
                fixture.tasks,
                SpeechTranscriptionClient { "转写内容" },
            )

            val first = coordinator.enqueue(attachmentId)
            val duplicate = coordinator.enqueue(attachmentId)
            assertEquals(first, duplicate)
            val result = coordinator.execute(first, "{\"attachmentId\":$attachmentId}", "", NoopReporter)

            assertIs<AsyncTaskExecutionResult.Success>(result)
            assertEquals("用户已经修改\n\n## 语音转写\n\n转写内容", fixture.diaries.getById(diaryId)?.content)
            val attachment = fixture.db.dailySatoriQueries.selectDiaryAttachmentById(attachmentId).executeAsOne()
            assertEquals("转写内容", attachment.transcript)
            assertEquals(DiaryAttachmentProcessingStatus.completed, attachment.transcript_status)

            coordinator.execute(first, "{\"attachmentId\":$attachmentId}", "", NoopReporter)
            assertEquals("用户已经修改\n\n## 语音转写\n\n转写内容", fixture.diaries.getById(diaryId)?.content)
        }
    }

    @Test
    fun transcriptionFailureKeepsAudioAndMarksAttachmentFailed() = runBlocking {
        withFixture { fixture ->
            val diaryId = fixture.diaries.create(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY)
            val attachmentId = fixture.attachments.create(
                diaryId,
                DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/audio/keep.m4a"),
            )
            val coordinator = DiaryTranscriptionCoordinator(
                fixture.attachments,
                fixture.diaries,
                fixture.tasks,
                SpeechTranscriptionClient { error("network down") },
            )

            val result = coordinator.execute(1, "{\"attachmentId\":$attachmentId}", "", NoopReporter)

            assertIs<AsyncTaskExecutionResult.RetryableFailure>(result)
            val attachment = fixture.db.dailySatoriQueries.selectDiaryAttachmentById(attachmentId).executeAsOne()
            assertEquals("/audio/keep.m4a", attachment.local_path)
            assertEquals(DiaryAttachmentProcessingStatus.failed, attachment.transcript_status)
            assertEquals(TranscriptionErrorCode.SERVICE_UNAVAILABLE, attachment.error_message)
        }
    }

    @Test
    fun unsupportedModelIsPermanentAndKeepsAStableUiErrorCode() = runBlocking {
        withFixture { fixture ->
            val diaryId = fixture.diaries.create(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY)
            val attachmentId = fixture.attachments.create(
                diaryId,
                DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/audio/keep.m4a"),
            )
            val coordinator = DiaryTranscriptionCoordinator(
                fixture.attachments,
                fixture.diaries,
                fixture.tasks,
                SpeechTranscriptionClient {
                    throw SpeechTranscriptionException(
                        code = TranscriptionErrorCode.MODEL_UNSUPPORTED,
                        retryable = false,
                        message = "model does not support audio",
                    )
                },
            )

            val result = coordinator.execute(1, "{\"attachmentId\":$attachmentId}", "", NoopReporter)

            assertIs<AsyncTaskExecutionResult.PermanentFailure>(result)
            assertEquals(TranscriptionErrorCode.MODEL_UNSUPPORTED, result.code)
            assertEquals(
                TranscriptionErrorCode.MODEL_UNSUPPORTED,
                fixture.attachments.getById(attachmentId)?.error_message,
            )
        }
    }

    @Test
    fun manualRetryPreflightsSpeechConfigurationBeforeCreatingTask() = runBlocking {
        withFixture { fixture ->
            val diaryId = fixture.diaries.create(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY)
            val attachmentId = fixture.attachments.create(
                diaryId,
                DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/audio/keep.m4a"),
            )
            val client = object : SpeechTranscriptionClient {
                override fun availability(): SpeechTranscriptionAvailability =
                    SpeechTranscriptionAvailability.Unavailable(TranscriptionErrorCode.NO_SUPPORTED_CONFIG)

                override suspend fun transcribe(localPath: String): String = error("must not run")
            }
            val coordinator = DiaryTranscriptionCoordinator(
                fixture.attachments,
                fixture.diaries,
                fixture.tasks,
                client,
            )

            val result = coordinator.retry(attachmentId)

            assertIs<TranscriptionRetryResult.Unavailable>(result)
            assertEquals(TranscriptionErrorCode.NO_SUPPORTED_CONFIG, result.errorCode)
            assertEquals(
                TranscriptionErrorCode.NO_SUPPORTED_CONFIG,
                fixture.attachments.getById(attachmentId)?.error_message,
            )
            assertTrue(fixture.tasks.runnableTasks(Long.MAX_VALUE).isEmpty())
        }
    }

    @Test
    fun autoTranscribingBodyIsReplacedExactly() = runBlocking {
        withFixture { fixture ->
            val diaryId = fixture.diaries.create(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY)
            val attachmentId = fixture.attachments.create(
                diaryId,
                DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/audio/test.m4a"),
            )
            val coordinator = DiaryTranscriptionCoordinator(
                fixture.attachments,
                fixture.diaries,
                fixture.tasks,
                SpeechTranscriptionClient { "只有转写" },
            )

            coordinator.execute(1, "{\"attachmentId\":$attachmentId}", "", NoopReporter)

            assertEquals("只有转写", fixture.diaries.getById(diaryId)?.content)
        }
    }

    @Test
    fun knowledgeUsesRealDiaryIdAndCompletedTranscripts() = runBlocking {
        withFixture { fixture ->
            val diaryId = fixture.diaries.create("正文")
            val attachmentId = fixture.attachments.create(
                diaryId,
                DiaryAttachmentDraft(
                    kind = DiaryAttachmentKind.audio,
                    localPath = "/audio/test.m4a",
                    transcript = "附件转写",
                    transcriptStatus = DiaryAttachmentProcessingStatus.completed,
                ),
            )
            val calls = mutableListOf<Pair<Long, String>>()
            val coordinator = DiaryKnowledgeCoordinator(
                fixture.attachments,
                fixture.diaries,
                fixture.tasks,
                object : MemoryExtractor {
                    override suspend fun extractAndSave(sourceType: String, sourceId: Long, title: String, content: String) {
                        assertEquals("diary", sourceType)
                        calls += sourceId to content
                    }
                },
            )

            val taskId = coordinator.enqueue(diaryId, fixture.diaries.getById(diaryId)!!.updated_at)
            val result = coordinator.execute(taskId, "{\"diaryId\":$diaryId}", "", NoopReporter)

            assertIs<AsyncTaskExecutionResult.Success>(result)
            assertEquals(listOf(diaryId to "正文\n\n附件转写"), calls)
            val attachment = fixture.db.dailySatoriQueries.selectDiaryAttachmentById(attachmentId).executeAsOne()
            assertEquals(DiaryAttachmentProcessingStatus.completed, attachment.knowledge_status)
            assertTrue(taskId > 0)
        }
    }

    @Test
    fun knowledgeFailureMarksOnlyParticipatingAttachmentFailed() = runBlocking {
        withFixture { fixture ->
            val diaryId = fixture.diaries.create("正文")
            val attachmentId = fixture.attachments.create(
                diaryId,
                DiaryAttachmentDraft(
                    kind = DiaryAttachmentKind.audio,
                    localPath = "/audio/test.m4a",
                    transcript = "附件转写",
                    transcriptStatus = DiaryAttachmentProcessingStatus.completed,
                ),
            )
            val coordinator = DiaryKnowledgeCoordinator(
                fixture.attachments,
                fixture.diaries,
                fixture.tasks,
                object : MemoryExtractor {
                    override suspend fun extractAndSave(sourceType: String, sourceId: Long, title: String, content: String) {
                        error("AI unavailable")
                    }
                },
            )

            val result = coordinator.execute(1, "{\"diaryId\":$diaryId}", "", NoopReporter)

            assertIs<AsyncTaskExecutionResult.RetryableFailure>(result)
            val attachment = fixture.db.dailySatoriQueries.selectDiaryAttachmentById(attachmentId).executeAsOne()
            assertEquals(DiaryAttachmentProcessingStatus.failed, attachment.knowledge_status)
            assertEquals("/audio/test.m4a", attachment.local_path)
        }
    }

    private suspend inline fun withFixture(block: suspend (Fixture) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            block(
                Fixture(
                    db,
                    DiaryRepository(db, driver),
                    DiaryAttachmentRepository(db, driver),
                    AsyncTaskRepository(db),
                ),
            )
        } finally {
            driver.close()
        }
    }

    private data class Fixture(
        val db: DailySatoriDatabase,
        val diaries: DiaryRepository,
        val attachments: DiaryAttachmentRepository,
        val tasks: AsyncTaskRepository,
    )

    private object NoopReporter : AsyncTaskProgressReporter {
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = Unit
    }
}
