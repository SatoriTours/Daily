package com.dailysatori.service.diary

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryAttachmentProcessingStatus
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DiaryTranscriptionCoordinator(
    private val attachmentRepository: DiaryAttachmentRepository,
    private val diaryRepository: DiaryRepository,
    private val taskRepository: AsyncTaskRepository,
    private val transcriptionClient: SpeechTranscriptionClient,
    private val knowledgeCoordinator: DiaryKnowledgeCoordinator? = null,
) : AsyncTaskHandler {
    override val type: String = AsyncTaskType.diary_attachment_transcribe.name

    fun enqueue(attachmentId: Long): Long {
        require(attachmentId > 0)
        val attachment = requireNotNull(attachmentRepository.getById(attachmentId))
        attachmentRepository.updateTranscriptStatus(
            attachmentId,
            attachment.transcript,
            DiaryAttachmentProcessingStatus.queued,
        )
        return taskRepository.enqueue(
            type = type,
            payloadJson = Json.encodeToString(TranscriptionPayload(attachmentId)),
            uniqueKey = "diary-transcribe:$attachmentId",
        )
    }

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val attachmentId = runCatching { Json.decodeFromString<TranscriptionPayload>(payloadJson).attachmentId }
            .getOrElse { return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", it.message.orEmpty()) }
        val attachment = attachmentRepository.getById(attachmentId)
            ?: return AsyncTaskExecutionResult.PermanentFailure("attachment_missing", "Attachment $attachmentId not found")
        if (
            attachment.transcript_status == DiaryAttachmentProcessingStatus.completed &&
            attachment.transcript.isNotBlank()
        ) {
            diaryRepository.getById(attachment.diary_id)?.let { diary ->
                knowledgeCoordinator?.enqueue(diary.id, diary.updated_at)
            }
            return AsyncTaskExecutionResult.Success()
        }
        return try {
            attachmentRepository.updateTranscriptStatus(
                attachmentId,
                attachment.transcript,
                DiaryAttachmentProcessingStatus.processing,
            )
            reporter.report(0, 1, "正在转写")
            val transcript = transcriptionClient.transcribe(attachment.local_path)
            attachmentRepository.persistTranscriptAndDiary(
                id = attachmentId,
                transcript = transcript,
                autoBody = AUTO_TRANSCRIBING_BODY,
                transcriptHeading = TRANSCRIPT_HEADING,
            )
            diaryRepository.getById(attachment.diary_id)?.let { diary ->
                knowledgeCoordinator?.enqueue(diary.id, diary.updated_at)
            }
            reporter.report(1, 1, "转写完成")
            AsyncTaskExecutionResult.Success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            attachmentRepository.updateTranscriptStatus(
                attachmentId,
                attachment.transcript,
                DiaryAttachmentProcessingStatus.failed,
                error.message.orEmpty(),
            )
            AsyncTaskExecutionResult.RetryableFailure("transcription_failed", error.message.orEmpty())
        }
    }

    companion object {
        const val AUTO_TRANSCRIBING_BODY = "这篇日记正在转写…"
        private const val TRANSCRIPT_HEADING = "## 语音转写"
    }
}

@Serializable
private data class TranscriptionPayload(val attachmentId: Long)
