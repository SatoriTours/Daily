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
    override fun availability(): SpeechTranscriptionAvailability {
        val config = aiConfigService.getSpeechConfig()
            ?: return SpeechTranscriptionAvailability.Unavailable(TranscriptionErrorCode.NO_SUPPORTED_CONFIG)
        if (config.api_address.isBlank() || config.api_token.isBlank()) {
            return SpeechTranscriptionAvailability.Unavailable(TranscriptionErrorCode.CONFIG_INVALID)
        }
        return SpeechTranscriptionAvailability.Available
    }

    override suspend fun transcribe(localPath: String): String {
        val config = aiConfigService.getSpeechConfig() ?: throw SpeechTranscriptionException(
            TranscriptionErrorCode.NO_SUPPORTED_CONFIG,
            retryable = false,
            message = "No AI configuration supports audio transcription",
        )
        if (config.api_address.isBlank() || config.api_token.isBlank()) throw SpeechTranscriptionException(
            TranscriptionErrorCode.CONFIG_INVALID,
            retryable = false,
            message = "AI transcription address or token is empty",
        )
        val bytes = fileManager.readFile(localPath)
        if (bytes.isEmpty()) throw SpeechTranscriptionException(
            TranscriptionErrorCode.AUDIO_EMPTY,
            retryable = false,
            message = "Audio file is empty",
        )
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
        if (!response.status.isSuccess()) throw transcriptionFailureForHttpStatus(response.status.value, body)
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
        if (bytes.size > GEMINI_INLINE_LIMIT_BYTES) throw SpeechTranscriptionException(
            TranscriptionErrorCode.AUDIO_TOO_LARGE,
            retryable = false,
            message = "Audio exceeds Gemini inline upload limit",
        )
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
        if (!response.status.isSuccess()) throw transcriptionFailureForHttpStatus(response.status.value, body)
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

internal fun transcriptionFailureForHttpStatus(statusCode: Int, responseBody: String): SpeechTranscriptionException {
    val normalizedBody = responseBody.lowercase()
    val code = when {
        statusCode == 401 || statusCode == 403 -> TranscriptionErrorCode.AUTH_FAILED
        statusCode == 413 || listOf("too large", "size limit", "maximum file").any(normalizedBody::contains) ->
            TranscriptionErrorCode.AUDIO_TOO_LARGE
        statusCode == 404 -> TranscriptionErrorCode.MODEL_UNSUPPORTED
        statusCode == 429 || statusCode >= 500 -> TranscriptionErrorCode.SERVICE_UNAVAILABLE
        statusCode in setOf(400, 415, 422) &&
            listOf("model", "unsupported", "modality").any(normalizedBody::contains) ->
            TranscriptionErrorCode.MODEL_UNSUPPORTED
        else -> TranscriptionErrorCode.REQUEST_REJECTED
    }
    return SpeechTranscriptionException(
        code = code,
        retryable = code == TranscriptionErrorCode.SERVICE_UNAVAILABLE,
        message = "Transcription failed ($statusCode): $responseBody",
    )
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
