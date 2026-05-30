package com.dailysatori.service.ai

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AiService(private val client: HttpClient) {
    private val log = Logger.withTag("AI")
    private val langChainClient = LangChainAiClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun complete(
        prompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String = "openai",
        systemPrompt: String? = null,
        temperature: Double = 0.5,
    ): String {
        if (usesOpenAiCompatibleChatApi(provider)) {
            return rawOpenAiTextCompletion(apiAddress, apiToken, modelName, prompt, systemPrompt, temperature)
        }
        val response = try {
            withTimeout(aiCompletionRequestTimeoutMillis()) {
                langChainClient.complete(
                    prompt = prompt,
                    apiAddress = apiAddress.trim().trimEnd('/'),
                    apiToken = apiToken.trim(),
                    modelName = modelName.trim(),
                    provider = provider.trim(),
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                )
            }
        } catch (e: Exception) {
            log.e(e) { "AI completion failed" }
            throw e
        }
        if (response.isBlank()) throw IllegalStateException("AI returned empty response")
        return response
    }

    private suspend fun rawOpenAiTextCompletion(
        apiAddress: String,
        apiToken: String,
        modelName: String,
        prompt: String,
        systemPrompt: String?,
        temperature: Double,
    ): String {
        val response = rawOpenAiChatCompletion(
            apiAddress = apiAddress,
            apiToken = apiToken,
            modelName = modelName,
            messages = buildOpenAiTextCompletionMessages(prompt, systemPrompt),
            tools = emptyList(),
            temperature = temperature,
        )
        return extractOpenAiTextCompletionContent(response)
    }

    suspend fun testConnection(
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String,
    ): Result<String> {
        return runCatching {
            complete(
                prompt = "请只回复 OK",
                apiAddress = apiAddress,
                apiToken = apiToken,
                modelName = modelName,
                provider = provider,
                temperature = 0.0,
            )
        }
    }

    suspend fun chatCompletion(
        messages: List<JsonObject>,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String = "openai",
        tools: List<JsonObject> = emptyList(),
        temperature: Double = 0.7,
    ): JsonObject? {
        return try {
            if (usesOpenAiCompatibleChatApi(provider)) {
                rawOpenAiChatCompletion(apiAddress, apiToken, modelName, messages, tools, temperature)
            } else {
                langChainClient.chatCompletion(
                    messages = messages,
                    apiAddress = apiAddress.trim().trimEnd('/'),
                    apiToken = apiToken.trim(),
                    modelName = modelName.trim(),
                    provider = provider.trim(),
                    tools = tools,
                    temperature = temperature,
                )
            }
        } catch (e: Exception) {
            log.e(e) { "AI chat completion failed" }
            throw e
        }
    }

    suspend fun chatCompletionStreaming(
        messages: List<JsonObject>,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String = "openai",
        tools: List<JsonObject> = emptyList(),
        temperature: Double = 0.7,
        onChunk: suspend (String) -> Unit,
    ): JsonObject? {
        return try {
            if (usesOpenAiCompatibleChatApi(provider)) {
                rawOpenAiChatCompletionStreaming(apiAddress, apiToken, modelName, messages, tools, temperature, onChunk)
            } else {
                chatCompletion(messages, apiAddress, apiToken, modelName, provider, tools, temperature)
            }
        } catch (e: Exception) {
            log.e(e) { "AI streaming chat completion failed" }
            throw e
        }
    }

    private suspend fun rawOpenAiChatCompletion(
        apiAddress: String,
        apiToken: String,
        modelName: String,
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        temperature: Double,
    ): JsonObject {
        val response = client.post(openAiChatCompletionEndpoint(apiAddress.trim())) {
            timeout {
                requestTimeoutMillis = aiChatRequestTimeoutMillis()
                socketTimeoutMillis = aiChatRequestTimeoutMillis()
            }
            contentType(ContentType.Application.Json)
            bearerAuth(apiToken.trim())
            setBody(buildOpenAiChatCompletionRequest(modelName.trim(), messages, tools, temperature).toString())
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException(body.ifBlank { "AI chat completion failed: HTTP ${response.status.value}" })
        }
        return json.parseToJsonElement(body) as JsonObject
    }

    private suspend fun rawOpenAiChatCompletionStreaming(
        apiAddress: String,
        apiToken: String,
        modelName: String,
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        temperature: Double,
        onChunk: suspend (String) -> Unit,
    ): JsonObject? {
        val fullText = StringBuilder()
        client.preparePost(openAiChatCompletionEndpoint(apiAddress.trim())) {
            timeout {
                requestTimeoutMillis = aiChatRequestTimeoutMillis()
                socketTimeoutMillis = aiChatRequestTimeoutMillis()
            }
            contentType(ContentType.Application.Json)
            bearerAuth(apiToken.trim())
            setBody(buildOpenAiChatCompletionRequest(modelName.trim(), messages, tools, temperature, stream = true).toString())
        }.execute { response ->
            if (response.status.value !in 200..299) {
                throw IllegalStateException(response.bodyAsText().ifBlank { "AI chat completion failed: HTTP ${response.status.value}" })
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val chunk = parseOpenAiStreamingContentChunk(channel.readUTF8Line() ?: break) ?: continue
                fullText.append(chunk)
                onChunk(chunk)
            }
        }
        return if (fullText.isEmpty()) null else buildOpenAiStreamingResponse(fullText.toString())
    }

    suspend fun translate(
        text: String,
        systemPrompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String = "openai",
    ): String {
        return complete(text, apiAddress, apiToken, modelName, provider, systemPrompt)
    }

    suspend fun summarize(
        content: String,
        systemPrompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String = "openai",
    ): String {
        return complete(content, apiAddress, apiToken, modelName, provider, systemPrompt)
    }

    suspend fun htmlToMarkdown(
        html: String,
        systemPrompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String = "openai",
    ): String {
        return complete(html, apiAddress, apiToken, modelName, provider, systemPrompt)
    }
}

fun buildOpenAiChatCompletionRequest(
    modelName: String,
    messages: List<JsonObject>,
    tools: List<JsonObject>,
    temperature: Double,
    stream: Boolean = false,
): JsonObject = buildJsonObject {
    put("model", JsonPrimitive(modelName))
    put("messages", JsonArray(messages))
    put("temperature", JsonPrimitive(temperature))
    if (stream) put("stream", JsonPrimitive(true))
    if (tools.isNotEmpty()) {
        put("tools", JsonArray(tools))
        put("tool_choice", JsonPrimitive("auto"))
    }
}

fun parseOpenAiStreamingContentChunk(line: String): String? {
    val data = line.trim().takeIf { it.startsWith("data:") }
        ?.removePrefix("data:")
        ?.trim()
        ?: return null
    if (data == "[DONE]") return null
    return runCatching {
        streamingJson.parseToJsonElement(data).jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("delta")?.jsonObject?.get("content")
            ?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}

private fun buildOpenAiStreamingResponse(content: String): JsonObject = buildJsonObject {
    put("choices", JsonArray(listOf(buildJsonObject {
        put("message", buildJsonObject {
            put("role", JsonPrimitive("assistant"))
            put("content", JsonPrimitive(content))
        })
    })))
}

private val streamingJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun buildOpenAiTextCompletionMessages(prompt: String, systemPrompt: String?): List<JsonObject> = buildList {
    if (!systemPrompt.isNullOrBlank()) {
        add(buildJsonObject {
            put("role", JsonPrimitive("system"))
            put("content", JsonPrimitive(systemPrompt.trim()))
        })
    }
    add(buildJsonObject {
        put("role", JsonPrimitive("user"))
        put("content", JsonPrimitive(prompt.trim()))
    })
}

fun extractOpenAiTextCompletionContent(response: JsonObject): String {
    val content = response["choices"]?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("message")?.jsonObject?.get("content")
        ?.jsonPrimitive?.contentOrNull
        ?.trim()
        .orEmpty()
    if (content.isBlank()) throw IllegalStateException("AI returned empty response")
    return content
}

fun usesOpenAiCompatibleChatApi(provider: String): Boolean =
    provider.trim().lowercase() !in setOf("anthropic", "gemini")

fun openAiChatCompletionEndpoint(apiAddress: String): String {
    val trimmed = apiAddress.trim().trimEnd('/')
    return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
}

fun aiChatRequestTimeoutMillis(): Long = 120_000L

fun aiCompletionRequestTimeoutMillis(): Long = com.dailysatori.config.AIConfig.timeoutMs
