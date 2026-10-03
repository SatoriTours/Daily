package com.dailysatori.service.diary

import com.dailysatori.platform.FileManager

class OpenAiCompatibleSpeechTranscriptionClient(
    private val settings: SpeechSettingsService,
    private val api: SpeechTranscriptionApi,
    private val fileManager: FileManager,
) : SpeechTranscriptionClient {
    override fun availability(): SpeechTranscriptionAvailability {
        val config = settings.load() ?: return SpeechTranscriptionAvailability.Unavailable(
            if (settings.hasSavedConfig()) TranscriptionErrorCode.CONFIG_INVALID else TranscriptionErrorCode.NO_SUPPORTED_CONFIG,
        )
        return if (config.validationError() == null) SpeechTranscriptionAvailability.Available
        else SpeechTranscriptionAvailability.Unavailable(TranscriptionErrorCode.CONFIG_INVALID)
    }

    override suspend fun transcribe(localPath: String): String {
        val config = settings.load() ?: throw speechFailure(
            if (settings.hasSavedConfig()) TranscriptionErrorCode.CONFIG_INVALID else TranscriptionErrorCode.NO_SUPPORTED_CONFIG,
            "Speech transcription is not configured",
        )
        config.validationError()?.let { throw speechFailure(TranscriptionErrorCode.CONFIG_INVALID, it) }
        if (fileManager.fileSize(localPath) > speechAudioLimit(config)) {
            throw speechFailure(TranscriptionErrorCode.AUDIO_TOO_LARGE, "Audio exceeds upload limit")
        }
        return api.transcribe(config, fileManager.readFile(localPath), localPath.substringAfterLast('/'))
    }

    companion object { const val DEFAULT_MODEL = "whisper-1" }
}
