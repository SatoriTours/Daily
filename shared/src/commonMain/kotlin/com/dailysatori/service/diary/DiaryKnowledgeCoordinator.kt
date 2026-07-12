package com.dailysatori.service.diary

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryAttachmentProcessingStatus
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.memory.MemoryExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DiaryKnowledgeCoordinator(
    private val attachmentRepository: DiaryAttachmentRepository,
    private val diaryRepository: DiaryRepository,
    private val taskRepository: AsyncTaskRepository,
    private val memoryExtractor: MemoryExtractor,
) : AsyncTaskHandler {
    override val type: String = AsyncTaskType.diary_knowledge_extract.name

    fun enqueue(diaryId: Long, updatedAt: Long): Long {
        require(diaryId > 0)
        attachmentRepository.getForDiary(diaryId)
            .filter {
                it.transcript_status == DiaryAttachmentProcessingStatus.completed &&
                    it.transcript.isNotBlank()
            }
            .forEach {
            attachmentRepository.updateKnowledgeStatus(it.id, DiaryAttachmentProcessingStatus.queued)
            }
        return taskRepository.enqueue(
            type = type,
            payloadJson = Json.encodeToString(KnowledgePayload(diaryId)),
            uniqueKey = "diary-knowledge:$diaryId:$updatedAt",
        )
    }

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val diaryId = runCatching { Json.decodeFromString<KnowledgePayload>(payloadJson).diaryId }
            .getOrElse { return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", it.message.orEmpty()) }
        val diary = diaryRepository.getById(diaryId)
            ?: return AsyncTaskExecutionResult.PermanentFailure("diary_missing", "Diary $diaryId not found")
        val participating = attachmentRepository.getForDiary(diaryId)
            .filter { it.transcript_status == DiaryAttachmentProcessingStatus.completed && it.transcript.isNotBlank() }
        return try {
            participating.forEach { attachmentRepository.updateKnowledgeStatus(it.id, DiaryAttachmentProcessingStatus.processing) }
            val content = (listOf(diary.content) + participating.map { it.transcript })
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            memoryExtractor.extractAndSave("diary", diaryId, "日记", content)
            participating.forEach { attachmentRepository.updateKnowledgeStatus(it.id, DiaryAttachmentProcessingStatus.completed) }
            AsyncTaskExecutionResult.Success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            participating.forEach {
                attachmentRepository.updateKnowledgeStatus(
                    it.id,
                    DiaryAttachmentProcessingStatus.failed,
                    error.message.orEmpty(),
                )
            }
            AsyncTaskExecutionResult.RetryableFailure("knowledge_failed", error.message.orEmpty())
        }
    }
}

@Serializable
private data class KnowledgePayload(val diaryId: Long)
