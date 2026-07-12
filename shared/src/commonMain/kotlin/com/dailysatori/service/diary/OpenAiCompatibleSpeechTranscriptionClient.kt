package com.dailysatori.service.diary

import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.service.ai.AiConfigService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenAiCompatibleSpeechTranscriptionClient(
    private val httpClient: HttpClient,
    private val aiConfigService: AiConfigService,
    private val settingRepository: SettingRepository,
    private val fileManager: FileManager,
) : SpeechTranscriptionClient {
    override suspend fun transcribe(localPath: String): String {
        val config = requireNotNull(aiConfigService.getDefaultConfig()) { "No default AI configuration" }
        require(config.api_address.isNotBlank()) { "AI API address is empty" }
        require(config.api_token.isNotBlank()) { "AI API token is empty" }
        val bytes = fileManager.readFile(localPath)
        require(bytes.isNotEmpty()) { "Audio file is empty" }
        val model = settingRepository.get(SettingKeys.speechModel)?.trim().orEmpty().ifBlank { DEFAULT_MODEL }
        val fileName = localPath.substringAfterLast('/').ifBlank { "recording.m4a" }
        val response = httpClient.post(speechTranscriptionEndpoint(config.api_address)) {
            bearerAuth(config.api_token.trim())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("model", model)
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "audio/mp4")
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }
        val body = response.body<String>()
        check(response.status.isSuccess()) { "Transcription failed (${response.status.value}): $body" }
        return Json.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.content
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("Transcription response is missing text")
    }

    companion object {
        const val DEFAULT_MODEL = "whisper-1"
    }
}

internal fun speechTranscriptionEndpoint(apiAddress: String): String {
    val base = apiAddress.trim().trimEnd('/').removeSuffix("/chat/completions")
    return if (base.endsWith("/audio/transcriptions")) base else "$base/audio/transcriptions"
}
