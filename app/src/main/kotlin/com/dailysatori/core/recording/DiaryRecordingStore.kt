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
    fun start(diaryId: Long, attachmentId: Long): Boolean {
        if (diaryId <= 0 || attachmentId <= 0) return false
        if (mutableState.value !is DiaryRecordingState.Idle && mutableState.value !is DiaryRecordingState.Failed) return false
        session = Session(diaryId, attachmentId)
        mutableState.value = DiaryRecordingState.Starting(diaryId, attachmentId)
        return true
    }

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
        if (mutableState.value !is DiaryRecordingState.Recording && mutableState.value !is DiaryRecordingState.Paused) return false
        val active = session ?: return false
        active.captureElapsed(nowMs())
        mutableState.value = active.stoppingState()
        return true
    }

    @Synchronized
    fun complete(): Boolean {
        if (mutableState.value !is DiaryRecordingState.Stopping) return false
        session = null
        mutableState.value = DiaryRecordingState.Idle
        return true
    }

    @Synchronized
    fun fail(errorCode: String): Boolean {
        val current = mutableState.value
        if (current is DiaryRecordingState.Idle || current is DiaryRecordingState.Failed) return false
        val active = session ?: return false
        active.captureElapsed(nowMs())
        mutableState.value = DiaryRecordingState.Failed(
            diaryId = active.diaryId,
            attachmentId = active.attachmentId,
            elapsedMs = active.accumulatedMs,
            errorCode = errorCode,
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
