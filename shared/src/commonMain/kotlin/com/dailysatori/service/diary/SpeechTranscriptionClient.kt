package com.dailysatori.service.diary

fun interface SpeechTranscriptionClient {
    suspend fun transcribe(localPath: String): String
}
