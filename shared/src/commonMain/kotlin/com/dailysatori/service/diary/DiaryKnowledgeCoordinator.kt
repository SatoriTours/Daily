package com.dailysatori.service.diary

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryAttachmentProcessingStatus
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryThreadRepository
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
    private val taskRepository: AsyncTaskRepository,
    private val memoryExtractor: MemoryExtractor,
    private val threads: DiaryThreadRepository,
) : AsyncTaskHandler {
    override val type: String = AsyncTaskType.diary_knowledge_extract.name

    fun enqueue(diaryId: Long, updatedAt: Long): Long {
        require(diaryId > 0)
        val rootId = threads.rootId(diaryId) ?: diaryId
        val snapshot = threads.getSnapshot(rootId)
        val attachments = snapshot?.attachments ?: attachmentRepository.getForDiary(diaryId)
        attachments
            .filter {
                it.transcript_status == DiaryAttachmentProcessingStatus.completed &&
                    it.transcript.isNotBlank()
            }
            .forEach {
            attachmentRepository.updateKnowledgeStatus(it.id, DiaryAttachmentProcessingStatus.queued)
            }
        return taskRepository.enqueue(
            type = type,
            payloadJson = Json.encodeToString(KnowledgePayload(rootId)),
            uniqueKey = "diary-knowledge:$rootId:${snapshot?.root?.updated_at ?: updatedAt}",
        )
    }

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val recordId = runCatching { Json.decodeFromString<KnowledgePayload>(payloadJson).diaryId }
            .getOrElse { return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", it.message.orEmpty()) }
        val rootId = threads.rootId(recordId)
            ?: return AsyncTaskExecutionResult.PermanentFailure("diary_missing", "Diary $recordId not found")
        val snapshot = threads.getSnapshot(rootId)
            ?: return AsyncTaskExecutionResult.PermanentFailure("diary_missing", "Diary $rootId not found")
        val participating = snapshot.attachments
            .filter { it.transcript_status == DiaryAttachmentProcessingStatus.completed && it.transcript.isNotBlank() }
        return try {
            participating.forEach { attachmentRepository.updateKnowledgeStatus(it.id, DiaryAttachmentProcessingStatus.processing) }
            val content = (listOf(renderDiaryThreadContent(snapshot.entries)) + participating.map { it.transcript })
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            memoryExtractor.extractAndSave("diary", rootId, "日记", content)
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
