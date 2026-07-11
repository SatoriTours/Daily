package com.dailysatori.core.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiaryRecordingStore(
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutableState = MutableStateFlow<DiaryRecordingState>(DiaryRecordingState.Idle)
    val state: StateFlow<DiaryRecordingState> = mutableState.asStateFlow()

    private var session: Session? = null

    @Synchronized
    fun requestStart(diaryId: Long, attachmentId: Long): DiaryRecordingStartResult {
        if (diaryId <= 0 || attachmentId <= 0) return DiaryRecordingStartResult.Invalid
        val active = session
        if (mutableState.value !is DiaryRecordingState.Idle && active != null) {
            return if (active?.diaryId == diaryId && active.attachmentId == attachmentId) {
                DiaryRecordingStartResult.AlreadyActive
            } else {
                DiaryRecordingStartResult.Busy
            }
        }
        session = Session(diaryId, attachmentId)
        mutableState.value = DiaryRecordingState.Starting(diaryId, attachmentId)
        return DiaryRecordingStartResult.Accepted
    }

    @Synchronized
    fun releaseFailedSession(): Boolean {
        if (mutableState.value !is DiaryRecordingState.Failed || session == null) return false
        session = null
        return true
    }

    @Synchronized
    fun start(diaryId: Long, attachmentId: Long): Boolean =
        requestStart(diaryId, attachmentId) == DiaryRecordingStartResult.Accepted

    @Synchronized
    fun markRecording(): Boolean {
        val current = mutableState.value
        val active = session ?: return false
        if (current !is DiaryRecordingState.Starting) return false
        active.runningSinceMs = nowMs()
        mutableState.value = active.recordingState()
        return true
    }

    @Synchronized
    fun pause(): Boolean {
        if (mutableState.value !is DiaryRecordingState.Recording) return false
        val active = session ?: return false
        active.captureElapsed(nowMs())
        mutableState.value = active.pausedState()
        return true
    }

    @Synchronized
    fun resume(): Boolean {
        if (mutableState.value !is DiaryRecordingState.Paused) return false
        val active = session ?: return false
        active.runningSinceMs = nowMs()
        mutableState.value = active.recordingState()
        return true
    }

    @Synchronized
    fun stop(): Boolean {
        if (
            mutableState.value !is DiaryRecordingState.Starting &&
            mutableState.value !is DiaryRecordingState.Recording &&
            mutableState.value !is DiaryRecordingState.Paused
        ) return false
        val active = session ?: return false
        active.captureElapsed(nowMs())
        mutableState.value = active.stoppingState()
        return true
    }

    @Synchronized
    fun complete(): Boolean {
        val current = mutableState.value
        if (
            current !is DiaryRecordingState.Stopping &&
            !(current is DiaryRecordingState.Failed && current.errorCode == DiaryRecordingErrorCode.PERSIST_FAILED)
        ) return false
        session = null
        mutableState.value = DiaryRecordingState.Idle
        return true
    }

    @Synchronized
    fun fail(errorCode: String, localPath: String? = null): Boolean {
        val current = mutableState.value
        if (current is DiaryRecordingState.Idle) return false
        val active = session ?: return false
        if (current is DiaryRecordingState.Failed) {
            mutableState.value = current.copy(errorCode = errorCode, localPath = localPath ?: current.localPath)
            return true
        }
        active.captureElapsed(nowMs())
        mutableState.value = DiaryRecordingState.Failed(
            diaryId = active.diaryId,
            attachmentId = active.attachmentId,
            elapsedMs = active.accumulatedMs,
            errorCode = errorCode,
            localPath = localPath,
        )
        return true
    }

    @Synchronized
    fun refreshElapsed() {
        if (mutableState.value !is DiaryRecordingState.Recording) return
        mutableState.value = session?.recordingState() ?: return
    }

    private inner class Session(
        val diaryId: Long,
        val attachmentId: Long,
        var accumulatedMs: Long = 0,
        var runningSinceMs: Long? = null,
    ) {
        fun elapsed(atMs: Long = nowMs()): Long =
            accumulatedMs + (runningSinceMs?.let { (atMs - it).coerceAtLeast(0) } ?: 0)

        fun captureElapsed(atMs: Long) {
            accumulatedMs = elapsed(atMs)
            runningSinceMs = null
        }

        fun recordingState() = DiaryRecordingState.Recording(diaryId, attachmentId, elapsed())
        fun pausedState() = DiaryRecordingState.Paused(diaryId, attachmentId, accumulatedMs)
        fun stoppingState() = DiaryRecordingState.Stopping(diaryId, attachmentId, accumulatedMs)
    }
}

enum class DiaryRecordingStartResult(val errorCode: String?) {
    Accepted(null),
    AlreadyActive(null),
    Busy(DiaryRecordingErrorCode.RECORDER_BUSY),
    Invalid(DiaryRecordingErrorCode.INVALID_STATE),
}
