package com.dailysatori.service.diary

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SpeechTranscriptionApi(private val httpClient: HttpClient) {
    suspend fun transcribe(config: SpeechConfig, bytes: ByteArray, fileName: String): String {
        config.validationError()?.let { throw speechFailure(TranscriptionErrorCode.CONFIG_INVALID, it) }
        if (bytes.isEmpty()) throw speechFailure(TranscriptionErrorCode.AUDIO_EMPTY, "Audio file is empty")
        if (bytes.size > speechAudioLimit(config)) throw speechFailure(TranscriptionErrorCode.AUDIO_TOO_LARGE, "Audio exceeds upload limit")
        val format = fileName.substringAfterLast('.', "m4a").lowercase()
        val response = when (config.provider) {
            "dashscope" -> postJson(qwenEndpoint(config), config, qwenRequest(config, bytes, format))
            "gemini" -> httpClient.post(geminiGenerateContentEndpoint(config.apiAddress, config.model)) {
                timeout { requestTimeoutMillis = TRANSCRIPTION_TIMEOUT_MS; socketTimeoutMillis = TRANSCRIPTION_TIMEOUT_MS }
                contentType(ContentType.Application.Json)
                headers.append("x-goog-api-key", config.apiKey.trim())
                setBody(geminiRequest(bytes, format).toString())
            }
            else -> postAudio(config, bytes, format)
        }
        val body = response.body<String>()
        if (!response.status.isSuccess()) throw transcriptionFailureForHttpStatus(response.status.value, body)
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw speechFailure(TranscriptionErrorCode.REQUEST_REJECTED, "Invalid transcription response")
        val businessError = ((root["base_resp"] as? JsonObject)?.get("status_code") as? JsonPrimitive)?.intOrNull
        if (businessError != null && businessError != 0) throw speechFailure(TranscriptionErrorCode.REQUEST_REJECTED, "Transcription rejected")
        return runCatching { transcriptionText(config, root) }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw speechFailure(TranscriptionErrorCode.REQUEST_REJECTED, "Transcription response is missing text")
    }

    private suspend fun postJson(endpoint: String, config: SpeechConfig, request: JsonObject) = httpClient.post(endpoint) {
        timeout { requestTimeoutMillis = TRANSCRIPTION_TIMEOUT_MS; socketTimeoutMillis = TRANSCRIPTION_TIMEOUT_MS }
        bearerAuth(config.apiKey.trim())
        contentType(ContentType.Application.Json)
        setBody(request.toString())
    }

    private suspend fun postAudio(config: SpeechConfig, bytes: ByteArray, format: String) = httpClient.post(
        if (config.provider == "minimax") config.apiAddress.trimEnd('/').removeSuffix("/speech_to_text") + "/speech_to_text"
        else speechTranscriptionEndpoint(config.apiAddress),
    ) {
        timeout { requestTimeoutMillis = TRANSCRIPTION_TIMEOUT_MS; socketTimeoutMillis = TRANSCRIPTION_TIMEOUT_MS }
        bearerAuth(config.apiKey.trim())
        setBody(MultiPartFormDataContent(formData {
            append("model", config.model)
            if (config.provider == "minimax") append("response_format", "json")
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, speechMimeType(format))
                append(HttpHeaders.ContentDisposition, "filename=\"diary.$format\"")
            })
        }))
    }
}

private const val TRANSCRIPTION_TIMEOUT_MS = 180_000L

@OptIn(ExperimentalEncodingApi::class)
private fun qwenRequest(config: SpeechConfig, bytes: ByteArray, format: String): JsonObject = buildJsonObject {
    put("model", config.model)
    val messages = buildJsonArray {
        add(buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "input_audio")
                    putJsonObject("input_audio") { put("data", "data:${speechMimeType(format)};base64,${Base64.encode(bytes)}") }
                })
            }
        })
    }
    if (config.model == "qwen3-asr-flash") {
        put("messages", messages)
        put("stream", false)
    } else {
        putJsonObject("input") { put("messages", messages) }
        putJsonObject("parameters") { put("format", format) }
    }
}

private fun qwenEndpoint(config: SpeechConfig): String {
    val base = config.apiAddress.trimEnd('/').removeSuffix("/compatible-mode/v1")
    return if (config.model == "qwen3-asr-flash") "$base/compatible-mode/v1/chat/completions"
    else "$base/api/v1/services/aigc/multimodal-generation/generation"
}

@OptIn(ExperimentalEncodingApi::class)
private fun geminiRequest(bytes: ByteArray, format: String): JsonObject = buildJsonObject {
    putJsonArray("contents") {
        add(buildJsonObject {
            putJsonArray("parts") {
                add(buildJsonObject { put("text", "请准确转写这段音频，只返回转写文字，不要添加说明。") })
                add(buildJsonObject {
                    putJsonObject("inline_data") {
                        put("mime_type", speechMimeType(format))
                        put("data", Base64.encode(bytes))
                    }
                })
            }
        })
    }
}

private fun transcriptionText(config: SpeechConfig, root: JsonObject): String? = when (config.provider) {
    "dashscope" -> if (config.model == "qwen3-asr-flash") root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
    else root["output"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
    "gemini" -> root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
        ?.get("parts")?.jsonArray?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("")
    else -> root["text"]?.jsonPrimitive?.contentOrNull
}

internal fun speechAudioLimit(config: SpeechConfig): Int = when (config.provider) {
    "minimax", "siliconflow" -> 50 * 1024 * 1024
    "dashscope" -> if (config.model == "qwen-audio-3.0-asr-flash") 10 * 1024 * 1024 else 20 * 1024 * 1024
    "gemini" -> 20 * 1024 * 1024 // Bound inline Base64 memory on mobile.
    else -> 25 * 1024 * 1024
}

private fun speechMimeType(format: String): String = when (format) {
    "m4a", "mp4" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    else -> "audio/$format"
}

internal fun speechFailure(code: String, message: String): SpeechTranscriptionException =
    SpeechTranscriptionException(code, retryable = code == TranscriptionErrorCode.SERVICE_UNAVAILABLE, message = message)

internal fun transcriptionFailureForHttpStatus(statusCode: Int, responseBody: String): SpeechTranscriptionException {
    val body = responseBody.lowercase()
    val code = when {
        statusCode == 401 || statusCode == 403 -> TranscriptionErrorCode.AUTH_FAILED
        statusCode == 413 || listOf("too large", "size limit", "maximum file").any(body::contains) -> TranscriptionErrorCode.AUDIO_TOO_LARGE
        statusCode == 404 -> TranscriptionErrorCode.MODEL_UNSUPPORTED
        statusCode == 429 || statusCode >= 500 -> TranscriptionErrorCode.SERVICE_UNAVAILABLE
        statusCode in setOf(400, 415, 422) && listOf("model", "unsupported", "modality").any(body::contains) -> TranscriptionErrorCode.MODEL_UNSUPPORTED
        else -> TranscriptionErrorCode.REQUEST_REJECTED
    }
    // Error bodies can contain echoed credentials or user audio/text. Never persist them.
    return speechFailure(code, "Transcription failed (HTTP $statusCode)")
}

internal fun geminiGenerateContentEndpoint(apiAddress: String, model: String): String {
    val base = apiAddress.trim().trimEnd('/')
    val host = if (Url(base).host == "generativelanguage.googleapis.com") "https://generativelanguage.googleapis.com"
    else base.removeSuffix("/v1beta")
    return "$host/v1beta/models/${model.trim()}:generateContent"
}

internal fun speechTranscriptionEndpoint(apiAddress: String): String {
    val base = apiAddress.trim().trimEnd('/').removeSuffix("/chat/completions")
    return if (base.endsWith("/audio/transcriptions")) base else "$base/audio/transcriptions"
}
