package com.dailysatori.core.recording

import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer

internal class DiaryPlaybackLoudness(
    private val createEffect: (sessionId: Int, gainMillibels: Int) -> (() -> Unit) = ::createLoudnessEffect,
) {
    private var sessionId = 0
    private var releaseEffect: (() -> Unit)? = null

    fun attach(audioSessionId: Int) {
        if (audioSessionId > 0 && sessionId == audioSessionId && releaseEffect != null) return
        release()
        if (audioSessionId <= 0) return
        try {
            releaseEffect = createEffect(audioSessionId, SPEECH_GAIN_MILLIBELS)
            sessionId = audioSessionId
        } catch (_: RuntimeException) {
            // Audio effects vary by device; ordinary playback remains available.
        }
    }

    fun release() {
        val release = releaseEffect
        releaseEffect = null
        sessionId = 0
        try {
            release?.invoke()
        } catch (_: RuntimeException) {
            // The platform may have already disposed of the audio session.
        }
    }
}

// +12 dB boosts quiet speech; LoudnessEnhancer compresses signals exceeding the sample range.
private const val SPEECH_GAIN_MILLIBELS = 1200

private fun createLoudnessEffect(sessionId: Int, gainMillibels: Int): () -> Unit {
    val effect = LoudnessEnhancer(sessionId)
    try {
        effect.setTargetGain(gainMillibels)
        check(effect.setEnabled(true) == AudioEffect.SUCCESS)
    } catch (error: RuntimeException) {
        runCatching { effect.release() }
        throw error
    }
    return { effect.release() }
}
