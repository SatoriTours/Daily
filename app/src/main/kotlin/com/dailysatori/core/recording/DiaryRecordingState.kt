package com.dailysatori.core.recording

sealed interface DiaryRecordingState {
    val diaryId: Long?
    val attachmentId: Long?
    val elapsedMs: Long
    val createdAtMonotonicMs: Long?

    data object Idle : DiaryRecordingState {
        override val diaryId: Long? = null
        override val attachmentId: Long? = null
        override val elapsedMs: Long = 0
        override val createdAtMonotonicMs: Long? = null
    }

    data class Starting(
        override val diaryId: Long,
        override val attachmentId: Long,
        override val elapsedMs: Long = 0,
        override val createdAtMonotonicMs: Long = 0,
    ) : DiaryRecordingState

    data class Recording(
        override val diaryId: Long,
        override val attachmentId: Long,
        override val elapsedMs: Long,
        override val createdAtMonotonicMs: Long = 0,
    ) : DiaryRecordingState

    data class Paused(
        override val diaryId: Long,
        override val attachmentId: Long,
        override val elapsedMs: Long,
        override val createdAtMonotonicMs: Long = 0,
    ) : DiaryRecordingState

    data class Stopping(
        override val diaryId: Long,
        override val attachmentId: Long,
        override val elapsedMs: Long,
        override val createdAtMonotonicMs: Long = 0,
    ) : DiaryRecordingState

    data class Failed(
        override val diaryId: Long,
        override val attachmentId: Long,
        override val elapsedMs: Long,
        val errorCode: String,
        val localPath: String? = null,
        override val createdAtMonotonicMs: Long = 0,
    ) : DiaryRecordingState

    data class PersistenceFailed(
        override val diaryId: Long,
        override val attachmentId: Long,
        override val elapsedMs: Long,
        val localPath: String? = null,
        override val createdAtMonotonicMs: Long = 0,
    ) : DiaryRecordingState
}
