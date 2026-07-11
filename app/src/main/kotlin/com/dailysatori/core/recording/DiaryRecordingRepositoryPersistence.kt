package com.dailysatori.core.recording

import com.dailysatori.data.repository.DiaryAttachmentRepository

class DiaryRecordingRepositoryPersistence(
    private val attachmentRepository: DiaryAttachmentRepository,
) : DiaryRecordingPersistence {
    override suspend fun begin(diaryId: Long, attachmentId: Long) {
        attachmentRepository.beginRecording(diaryId = diaryId, id = attachmentId)
    }

    override suspend fun complete(
        diaryId: Long,
        attachmentId: Long,
        output: DiaryRecordingOutput,
    ) {
        attachmentRepository.completeRecording(
            diaryId = diaryId,
            id = attachmentId,
            localPath = output.file.absolutePath,
            sizeBytes = output.file.length(),
            durationMs = output.durationMs,
        )
    }

    override suspend fun fail(
        diaryId: Long,
        attachmentId: Long,
        output: DiaryRecordingOutput?,
        errorCode: String,
    ) {
        attachmentRepository.failRecording(
            diaryId = diaryId,
            id = attachmentId,
            localPath = output?.file?.absolutePath,
            sizeBytes = output?.file?.length() ?: 0,
            durationMs = output?.durationMs ?: 0,
            errorCode = errorCode,
        )
    }
}
