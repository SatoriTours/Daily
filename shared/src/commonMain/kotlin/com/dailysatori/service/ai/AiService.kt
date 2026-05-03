package com.dailysatori.service.ai

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonObject

class AiService(@Suppress("UNUSED_PARAMETER") client: HttpClient) {
    private val log = Logger.withTag("AI")
    private val langChainClient = LangChainAiClient()

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
            langChainClient.chatCompletion(
                messages = messages,
                apiAddress = apiAddress.trim().trimEnd('/'),
                apiToken = apiToken.trim(),
                modelName = modelName.trim(),
                provider = provider.trim(),
                tools = tools,
                temperature = temperature,
            )
        } catch (e: Exception) {
            log.e(e) { "AI chat completion failed" }
            null
        }
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
