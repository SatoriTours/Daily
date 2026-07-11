package com.dailysatori.core.recording

import java.io.File

interface DiaryRecorder {
    fun start(outputFile: File)
    fun pause()
    fun resume()
    fun stop(): DiaryRecordingOutput
    fun releasePreservingOutput(): DiaryRecordingOutput?
}

data class DiaryRecordingOutput(
    val file: File,
    val durationMs: Long,
)

class DiaryRecorderException(
    val errorCode: String,
    cause: Throwable? = null,
) : Exception(errorCode, cause)

object DiaryRecordingErrorCode {
    const val PERMISSION_DENIED = "microphone_permission_denied"
    const val RECORDER_BUSY = "recorder_busy"
    const val START_FAILED = "recorder_start_failed"
    const val USER_CANCELLED = "recording_user_cancelled"
    const val INVALID_STATE = "recorder_invalid_state"
    const val ATTACHMENT_INVALID = "recording_attachment_invalid"
    const val STORAGE_FAILED = "recording_storage_failed"
    const val FINALIZE_FAILED = "recording_finalize_failed"
    const val PERSIST_FAILED = "recording_persist_failed"
    const val FOREGROUND_START_NOT_ALLOWED = "foreground_start_not_allowed"
    const val FOREGROUND_SECURITY_DENIED = "foreground_security_denied"
}
