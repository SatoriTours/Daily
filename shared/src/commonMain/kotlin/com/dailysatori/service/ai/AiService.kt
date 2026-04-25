package com.dailysatori.service.ai

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class AiSummaryResult(
    val title: String = "",
    val keyPoints: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val summary: String = "",
)

class AiService(private val client: HttpClient) {
    private val log = Logger.withTag("AI")

    suspend fun complete(
        prompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        systemPrompt: String? = null,
        temperature: Double = 0.5,
    ): String {
        val messages = mutableListOf<JsonObject>()
        systemPrompt?.let {
            messages.add(buildJsonObject { put("role", "system"); put("content", it) })
        }
        messages.add(buildJsonObject { put("role", "user"); put("content", prompt) })

        val requestBody = buildJsonObject {
            put("model", modelName)
            put("messages", JsonArray(messages))
            put("temperature", temperature)
        }

        return try {
            val response = client.post("$apiAddress/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiToken")
                setBody(requestBody.toString())
            }
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val choices = json["choices"]?.jsonArray
            choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            log.e(e) { "AI completion failed" }
            ""
        }
    }

    suspend fun translate(text: String, systemPrompt: String, apiAddress: String, apiToken: String, modelName: String): String {
        return complete(text, apiAddress, apiToken, modelName, systemPrompt)
    }

    suspend fun summarize(content: String, systemPrompt: String, apiAddress: String, apiToken: String, modelName: String): String {
        return complete(content, apiAddress, apiToken, modelName, systemPrompt)
    }

    suspend fun htmlToMarkdown(html: String, systemPrompt: String, apiAddress: String, apiToken: String, modelName: String): String {
        return complete(html, apiAddress, apiToken, modelName, systemPrompt)
    }
}
