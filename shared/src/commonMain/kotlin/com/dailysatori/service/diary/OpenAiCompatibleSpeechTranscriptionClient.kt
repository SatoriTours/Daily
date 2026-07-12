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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class OpenAiCompatibleSpeechTranscriptionClient(
    private val httpClient: HttpClient,
    private val aiConfigService: AiConfigService,
    private val settingRepository: SettingRepository,
    private val fileManager: FileManager,
) : SpeechTranscriptionClient {
    override suspend fun transcribe(localPath: String): String {
        val config = requireNotNull(aiConfigService.getSpeechConfig()) {
            "No AI configuration supports audio transcription"
        }
        require(config.api_address.isNotBlank()) { "AI API address is empty" }
        require(config.api_token.isNotBlank()) { "AI API token is empty" }
        val bytes = fileManager.readFile(localPath)
        require(bytes.isNotEmpty()) { "Audio file is empty" }
        val model = settingRepository.get(SettingKeys.speechModel)?.trim().orEmpty().ifBlank { DEFAULT_MODEL }
        val fileName = localPath.substringAfterLast('/').ifBlank { "recording.m4a" }
        if (config.provider.lowercase() in GEMINI_PROVIDERS) {
            return transcribeWithGemini(
                apiAddress = config.api_address,
                apiToken = config.api_token,
                model = config.model_name,
                bytes = bytes,
            )
        }
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

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun transcribeWithGemini(
        apiAddress: String,
        apiToken: String,
        model: String,
        bytes: ByteArray,
    ): String {
        require(bytes.size <= GEMINI_INLINE_LIMIT_BYTES) { "Audio exceeds Gemini inline upload limit" }
        val request = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", "请准确转写这段音频，只返回转写文字，不要添加说明。") })
                        add(buildJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "audio/mp4")
                                put("data", Base64.encode(bytes))
                            }
                        })
                    }
                })
            }
        }
        val response = httpClient.post(geminiGenerateContentEndpoint(apiAddress, model)) {
            contentType(ContentType.Application.Json)
            headers.append("x-goog-api-key", apiToken.trim())
            setBody(request.toString())
        }
        val body = response.body<String>()
        check(response.status.isSuccess()) { "Gemini transcription failed (${response.status.value}): $body" }
        val root = Json.parseToJsonElement(body).jsonObject
        return root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("Gemini transcription response is missing text")
    }

    companion object {
        const val DEFAULT_MODEL = "whisper-1"
        private const val GEMINI_INLINE_LIMIT_BYTES = 20 * 1024 * 1024
        private val GEMINI_PROVIDERS = setOf("gemini", "google", "google-gemini")
    }
}

internal fun geminiGenerateContentEndpoint(apiAddress: String, model: String): String {
    val host = if (apiAddress.contains("generativelanguage.googleapis.com")) {
        "https://generativelanguage.googleapis.com"
    } else {
        apiAddress.trim().trimEnd('/')
    }
    return "$host/v1beta/models/${model.trim()}:generateContent"
}

internal fun speechTranscriptionEndpoint(apiAddress: String): String {
    val base = apiAddress.trim().trimEnd('/').removeSuffix("/chat/completions")
    return if (base.endsWith("/audio/transcriptions")) base else "$base/audio/transcriptions"
}
