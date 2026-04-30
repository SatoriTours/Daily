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

private enum class ProviderType { OPENAI, ANTHROPIC, GEMINI }

class AiService(private val client: HttpClient) {
    private val log = Logger.withTag("AI")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun selectProvider(provider: String): ProviderType {
        return when {
            provider.equals("anthropic", ignoreCase = true) -> ProviderType.ANTHROPIC
            provider.equals("gemini", ignoreCase = true) -> ProviderType.GEMINI
            else -> ProviderType.OPENAI
        }
    }

    suspend fun complete(
        prompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String = "openai",
        systemPrompt: String? = null,
        temperature: Double = 0.5,
    ): String {
        return when (selectProvider(provider)) {
            ProviderType.ANTHROPIC -> completeAnthropic(prompt, apiAddress, apiToken, modelName, systemPrompt, temperature)
            ProviderType.GEMINI -> completeGemini(prompt, apiAddress, apiToken, modelName, systemPrompt, temperature)
            ProviderType.OPENAI -> completeOpenAI(prompt, apiAddress, apiToken, modelName, systemPrompt, temperature)
        }
    }

    private suspend fun completeOpenAI(
        prompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        systemPrompt: String?,
        temperature: Double,
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
            val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val choices = responseJson["choices"]?.jsonArray
            choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            log.e(e) { "AI completion failed" }
            ""
        }
    }

    private suspend fun completeAnthropic(
        prompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        systemPrompt: String?,
        temperature: Double,
    ): String {
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
        }
        return try {
            val response = anthropicPost(apiAddress, apiToken, modelName, systemPrompt, messages, emptyList(), temperature)
            response?.get("content")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            log.e(e) { "AI completion failed" }
            ""
        }
    }

    private suspend fun completeGemini(
        prompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        systemPrompt: String?,
        temperature: Double,
    ): String {
        val contents = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", prompt) })
                })
            })
        }
        return try {
            val response = client.post("$apiAddress/v1beta/models/$modelName:generateContent?key=$apiToken") {
                contentType(ContentType.Application.Json)
                val body = buildJsonObject {
                    put("contents", contents)
                    if (systemPrompt != null) {
                        put("system_instruction", buildJsonObject {
                            put("parts", buildJsonArray {
                                add(buildJsonObject { put("text", systemPrompt) })
                            })
                        })
                    }
                    put("generationConfig", buildJsonObject {
                        put("temperature", temperature)
                    })
                }
                setBody(body.toString())
            }
            val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
            responseJson["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            log.e(e) { "AI completion failed" }
            ""
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
        return when (selectProvider(provider)) {
            ProviderType.ANTHROPIC -> chatCompletionAnthropic(messages, apiAddress, apiToken, modelName, tools, temperature)
            ProviderType.GEMINI -> chatCompletionGemini(messages, apiAddress, apiToken, modelName, tools, temperature)
            ProviderType.OPENAI -> chatCompletionOpenAI(messages, apiAddress, apiToken, modelName, tools, temperature)
        }
    }

    private suspend fun chatCompletionOpenAI(
        messages: List<JsonObject>,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        tools: List<JsonObject>,
        temperature: Double,
    ): JsonObject? {
        val requestBody = buildJsonObject {
            put("model", modelName)
            put("messages", JsonArray(messages))
            put("temperature", temperature)
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools))
                put("tool_choice", "auto")
            }
        }

        return try {
            val response = client.post("$apiAddress/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiToken")
                setBody(requestBody.toString())
            }
            json.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (e: Exception) {
            log.e(e) { "AI chat completion failed" }
            null
        }
    }

    private suspend fun chatCompletionAnthropic(
        messages: List<JsonObject>,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        tools: List<JsonObject>,
        temperature: Double,
    ): JsonObject? {
        val systemPrompt = messages.firstOrNull {
            it["role"]?.jsonPrimitive?.contentOrNull == "system"
        }?.get("content")?.jsonPrimitive?.contentOrNull

        val filteredMessages = messages.filter {
            it["role"]?.jsonPrimitive?.contentOrNull != "system"
        }.map { msg ->
            val role = msg["role"]?.jsonPrimitive?.contentOrNull ?: "user"
            val content = msg["content"]?.jsonPrimitive?.contentOrNull
            val toolCalls = msg["tool_calls"]?.jsonArray
            val toolCallId = msg["tool_call_id"]?.jsonPrimitive?.contentOrNull

            when {
                role == "tool" && toolCallId != null -> buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", toolCallId)
                            put("content", content ?: "")
                        })
                    })
                }
                role == "assistant" && toolCalls != null -> {
                    val contentBlocks = buildJsonArray {
                        toolCalls.forEach { tc ->
                            val tcObj = tc.jsonObject
                            val func = tcObj["function"]?.jsonObject
                            add(buildJsonObject {
                                put("type", "tool_use")
                                put("id", tcObj["id"]?.jsonPrimitive?.contentOrNull ?: "")
                                put("name", func?.get("name")?.jsonPrimitive?.contentOrNull ?: "")
                                put("input", parseJsonSafely(func?.get("arguments")?.jsonPrimitive?.contentOrNull ?: "{}"))
                            })
                        }
                    }
                    buildJsonObject {
                        put("role", "assistant")
                        put("content", contentBlocks)
                    }
                }
                else -> buildJsonObject {
                    put("role", role)
                    put("content", content ?: "")
                }
            }
        }

        val anthropicTools = tools.map { tool ->
            val func = tool.jsonObject["function"]?.jsonObject
            buildJsonObject {
                put("name", func?.get("name")?.jsonPrimitive?.contentOrNull ?: "")
                put("description", func?.get("description")?.jsonPrimitive?.contentOrNull ?: "")
                val params = func?.get("parameters")?.jsonObject
                if (params != null) {
                    put("input_schema", params)
                }
            }
        }

        return try {
            val response = anthropicPost(apiAddress, apiToken, modelName, systemPrompt, filteredMessages, anthropicTools, temperature)
            convertAnthropicResponseToOpenAI(response)
        } catch (e: Exception) {
            log.e(e) { "AI chat completion failed" }
            null
        }
    }

    private suspend fun anthropicPost(
        apiAddress: String,
        apiToken: String,
        modelName: String,
        systemPrompt: String?,
        messages: List<JsonElement>,
        tools: List<JsonObject>,
        temperature: Double,
    ): JsonObject? {
        val requestBody = buildJsonObject {
            put("model", modelName)
            put("max_tokens", 4096)
            put("messages", JsonArray(messages))
            if (systemPrompt != null) {
                put("system", systemPrompt)
            }
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools))
            }
            put("temperature", temperature)
        }

        val httpResponse = client.post("$apiAddress/v1/messages") {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiToken)
            header("anthropic-version", "2023-06-01")
            setBody(requestBody.toString())
        }
        return json.parseToJsonElement(httpResponse.bodyAsText()).jsonObject
    }

    private fun convertAnthropicResponseToOpenAI(anthropicResponse: JsonObject?): JsonObject? {
        if (anthropicResponse == null) return null

        val content = anthropicResponse["content"]?.jsonArray ?: return anthropicResponse

        val textContent = buildString {
            content.forEach { block ->
                val blockObj = block.jsonObject
                if (blockObj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    append(blockObj["text"]?.jsonPrimitive?.contentOrNull ?: "")
                }
            }
        }

        val toolCalls = buildJsonArray {
            content.forEach { block ->
                val blockObj = block.jsonObject
                if (blockObj["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                    add(buildJsonObject {
                        put("id", blockObj["id"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", blockObj["name"]?.jsonPrimitive?.contentOrNull ?: "")
                            put("arguments", blockObj["input"].toString())
                        })
                    })
                }
            }
        }

        val message = buildJsonObject {
            put("role", "assistant")
            put("content", textContent)
            if (toolCalls.isNotEmpty()) {
                put("tool_calls", toolCalls)
            }
        }

        return buildJsonObject {
            put("choices", buildJsonArray {
                add(buildJsonObject { put("message", message) })
            })
        }
    }

    private suspend fun chatCompletionGemini(
        messages: List<JsonObject>,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        tools: List<JsonObject>,
        temperature: Double,
    ): JsonObject? {
        val systemPrompt = messages.firstOrNull {
            it["role"]?.jsonPrimitive?.contentOrNull == "system"
        }?.get("content")?.jsonPrimitive?.contentOrNull

        val contents = messages.filter {
            it["role"]?.jsonPrimitive?.contentOrNull != "system"
        }.map { msg ->
            val role = when (msg["role"]?.jsonPrimitive?.contentOrNull) {
                "assistant" -> "model"
                else -> "user"
            }
            buildJsonObject {
                put("role", role)
                put("parts", buildJsonArray {
                    val content = msg["content"]?.jsonPrimitive?.contentOrNull
                    if (!content.isNullOrBlank()) {
                        add(buildJsonObject { put("text", content) })
                    }
                })
            }
        }

        return try {
            val response = client.post("$apiAddress/v1beta/models/$modelName:generateContent?key=$apiToken") {
                contentType(ContentType.Application.Json)
                val body = buildJsonObject {
                    put("contents", JsonArray(contents))
                    if (systemPrompt != null) {
                        put("system_instruction", buildJsonObject {
                            put("parts", buildJsonArray {
                                add(buildJsonObject { put("text", systemPrompt) })
                            })
                        })
                    }
                    put("generationConfig", buildJsonObject {
                        put("temperature", temperature)
                    })
                }
                setBody(body.toString())
            }
            val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val text = responseJson["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content ?: ""

            buildJsonObject {
                put("choices", buildJsonArray {
                    add(buildJsonObject {
                        put("message", buildJsonObject {
                            put("role", "assistant")
                            put("content", text)
                        })
                    })
                })
            }
        } catch (e: Exception) {
            log.e(e) { "AI chat completion failed" }
            null
        }
    }

    private fun parseJsonSafely(text: String): JsonElement {
        return try {
            json.parseToJsonElement(text)
        } catch (_: Exception) {
            JsonPrimitive(text)
        }
    }

    suspend fun translate(text: String, systemPrompt: String, apiAddress: String, apiToken: String, modelName: String, provider: String = "openai"): String {
        return complete(text, apiAddress, apiToken, modelName, provider, systemPrompt)
    }

    suspend fun summarize(content: String, systemPrompt: String, apiAddress: String, apiToken: String, modelName: String, provider: String = "openai"): String {
        return complete(content, apiAddress, apiToken, modelName, provider, systemPrompt)
    }

    suspend fun htmlToMarkdown(html: String, systemPrompt: String, apiAddress: String, apiToken: String, modelName: String, provider: String = "openai"): String {
        return complete(html, apiAddress, apiToken, modelName, provider, systemPrompt)
    }
}
