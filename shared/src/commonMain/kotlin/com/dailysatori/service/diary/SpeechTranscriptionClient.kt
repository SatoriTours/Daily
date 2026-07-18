package com.dailysatori.service.diary

fun interface SpeechTranscriptionClient {
    suspend fun transcribe(localPath: String): String

    fun availability(): SpeechTranscriptionAvailability = SpeechTranscriptionAvailability.Available
}

sealed interface SpeechTranscriptionAvailability {
    data object Available : SpeechTranscriptionAvailability
    data class Unavailable(val errorCode: String) : SpeechTranscriptionAvailability
}

object TranscriptionErrorCode {
    const val NO_SUPPORTED_CONFIG = "transcription_no_supported_config"
    const val CONFIG_INVALID = "transcription_config_invalid"
    const val AUTH_FAILED = "transcription_auth_failed"
    const val MODEL_UNSUPPORTED = "transcription_model_unsupported"
    const val AUDIO_EMPTY = "transcription_audio_empty"
    const val AUDIO_TOO_LARGE = "transcription_audio_too_large"
    const val REQUEST_REJECTED = "transcription_request_rejected"
    const val SERVICE_UNAVAILABLE = "transcription_service_unavailable"
}

class SpeechTranscriptionException(
    val code: String,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
