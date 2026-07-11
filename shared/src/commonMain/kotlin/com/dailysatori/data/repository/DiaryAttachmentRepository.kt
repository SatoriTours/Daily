package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.platform.FileManager
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Diary_attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

enum class DiaryAttachmentKind {
    audio,
    video,
    image,
    file,
}

object DiaryAttachmentProcessingStatus {
    const val none = "none"
    const val queued = "queued"
    const val processing = "processing"
    const val completed = "completed"
    const val failed = "failed"
}

data class DiaryAttachmentDraft(
    val kind: DiaryAttachmentKind,
    val localPath: String,
    val displayName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
    val transcript: String = "",
    val transcriptStatus: String = DiaryAttachmentProcessingStatus.none,
    val knowledgeStatus: String = DiaryAttachmentProcessingStatus.none,
    val errorMessage: String = "",
)

class DiaryAttachmentRepository(
    private val db: DailySatoriDatabase,
    private val fileManager: FileManager? = null,
) {
    private val q get() = db.dailySatoriQueries

    fun create(diaryId: Long, draft: DiaryAttachmentDraft): Long {
        require(diaryId > 0) { "diaryId must be positive" }
        val now = Clock.System.now().toEpochMilliseconds()
        return q.transactionWithResult {
            q.insertDiaryAttachment(
                diary_id = diaryId,
                kind = draft.kind.name,
                local_path = draft.localPath,
                display_name = draft.displayName,
                mime_type = draft.mimeType,
                size_bytes = draft.sizeBytes,
                duration_ms = draft.durationMs,
                transcript = draft.transcript,
                transcript_status = draft.transcriptStatus,
                knowledge_status = draft.knowledgeStatus,
                error_message = draft.errorMessage,
                created_at = now,
                updated_at = now,
            ).executeAsOne()
        }
    }

    fun observeForDiary(diaryId: Long): Flow<List<Diary_attachment>> {
        require(diaryId > 0) { "diaryId must be positive" }
        return q.selectAttachmentsForDiary(diaryId).asFlow().mapToList(Dispatchers.IO)
    }

    fun updateTranscriptStatus(id: Long, transcript: String, status: String, errorMessage: String = "") {
        q.updateDiaryAttachmentTranscriptStatus(
            transcript = transcript,
            transcript_status = status,
            error_message = errorMessage,
            updated_at = Clock.System.now().toEpochMilliseconds(),
            id = id,
        )
    }

    fun updateKnowledgeStatus(id: Long, status: String, errorMessage: String = "") {
        q.updateDiaryAttachmentKnowledgeStatus(
            knowledge_status = status,
            error_message = errorMessage,
            updated_at = Clock.System.now().toEpochMilliseconds(),
            id = id,
        )
    }

    fun updateStatuses(id: Long, transcriptStatus: String, knowledgeStatus: String, errorMessage: String = "") {
        q.updateDiaryAttachmentStatuses(
            transcript_status = transcriptStatus,
            knowledge_status = knowledgeStatus,
            error_message = errorMessage,
            updated_at = Clock.System.now().toEpochMilliseconds(),
            id = id,
        )
    }

    fun delete(id: Long) {
        val localPath = q.transactionWithResult {
            val attachment = q.selectDiaryAttachmentById(id).executeAsOneOrNull()
            q.deleteDiaryAttachmentById(id)
            attachment?.local_path
        }
        deleteAppOwnedFile(localPath)
    }

    private fun deleteAppOwnedFile(path: String?) {
        val manager = fileManager ?: return
        if (path != null && path.startsWith(manager.getAppDataDir())) {
            manager.deleteFile(path)
        }
    }
}
