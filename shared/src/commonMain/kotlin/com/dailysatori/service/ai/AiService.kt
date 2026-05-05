package com.dailysatori.service.ai

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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
        val response = try {
            langChainClient.complete(
                prompt = prompt,
                apiAddress = apiAddress.trim().trimEnd('/'),
                apiToken = apiToken.trim(),
                modelName = modelName.trim(),
                provider = provider.trim(),
                systemPrompt = systemPrompt,
                temperature = temperature,
            )
        } catch (e: Exception) {
            log.e(e) { "AI completion failed" }
            throw e
        }
        if (response.isBlank()) throw IllegalStateException("AI returned empty response")
        return response
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
): JsonObject = buildJsonObject {
    put("model", JsonPrimitive(modelName))
    put("messages", JsonArray(messages))
    put("temperature", JsonPrimitive(temperature))
    if (tools.isNotEmpty()) {
        put("tools", JsonArray(tools))
        put("tool_choice", JsonPrimitive("auto"))
    }
}

fun usesOpenAiCompatibleChatApi(provider: String): Boolean =
    provider.trim().lowercase() !in setOf("anthropic", "gemini")

fun openAiChatCompletionEndpoint(apiAddress: String): String {
    val trimmed = apiAddress.trim().trimEnd('/')
    return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
}

fun aiChatRequestTimeoutMillis(): Long = 120_000L
