package com.dailysatori.service.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechTranscriptionFailureTest {
    @Test
    fun httpFailuresSeparateConfigurationModelAndTemporaryErrors() {
        val auth = transcriptionFailureForHttpStatus(401, "invalid token")
        val unsupported = transcriptionFailureForHttpStatus(404, "model not found")
        val tooLarge = transcriptionFailureForHttpStatus(413, "audio file too large")
        val unavailable = transcriptionFailureForHttpStatus(503, "unavailable")

        assertEquals(TranscriptionErrorCode.AUTH_FAILED, auth.code)
        assertFalse(auth.retryable)
        assertEquals(TranscriptionErrorCode.MODEL_UNSUPPORTED, unsupported.code)
        assertFalse(unsupported.retryable)
        assertEquals(TranscriptionErrorCode.AUDIO_TOO_LARGE, tooLarge.code)
        assertFalse(tooLarge.retryable)
        assertEquals(TranscriptionErrorCode.SERVICE_UNAVAILABLE, unavailable.code)
        assertTrue(unavailable.retryable)
    }
}
