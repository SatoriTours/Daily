package com.dailysatori.service.ai

import co.touchlab.kermit.Logger
import com.dailysatori.config.AiRequestProtocol
import com.dailysatori.service.diagnostics.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
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

    private suspend fun <T> withAiOperation(
        provider: String, model: String, isFailure: (T) -> Boolean = { false }, block: suspend () -> T,
    ): T = withAiRequestSession {
        DiagnosticLog.diagnostics.operation(
            DiagnosticSource.AI, fields = mapOf("provider" to provider, "model" to model), isFailure = isFailure, block = block,
        )
    }

    /** Private archives must not expose provider error bodies through logging or exception causes. */
    suspend fun completePrivate(
        prompt: String, apiAddress: String, apiToken: String, modelName: String,
        provider: String, systemPrompt: String, purpose: AiPurpose? = null,
    ): String = try {
        withAiRequestSession {
            val route = resolveAiRequestRoute(provider, modelName, apiAddress)
            val headers = aiRequestHeaders(provider)
            val response = if (route.protocol == AiRequestProtocol.OpenAiChatCompletions) {
                rawOpenAiTextCompletion(apiAddress, apiToken, modelName, prompt, systemPrompt, 0.0,
                    thinkingMode = aiPurposeThinkingMode(provider, modelName, purpose, disableThinking = true), recordUsage = false, headers = headers)
            } else {
                withTimeout(aiCompletionRequestTimeoutMillis()) {
                    langChainClient.complete(prompt, route, apiToken.trim(), modelName.trim(), headers, systemPrompt, 0.0)
                }
            }
            require(response.isNotBlank() && response.length <= 100_000)
            response
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Sanitize outside withContext so stack-trace recovery cannot attach even a sanitized cause.
        throw IllegalStateException("AI 请求失败，请检查配置或稍后重试")
    }

    suspend fun complete(
        prompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String = "openai",
        systemPrompt: String? = null,
        temperature: Double = 0.5,
        disableThinking: Boolean = false,
        purpose: AiPurpose? = null,
    ): String = withAiOperation(provider, modelName) {
        val route = resolveAiRequestRoute(provider, modelName, apiAddress)
        val headers = aiRequestHeaders(provider)
        if (route.protocol == AiRequestProtocol.OpenAiChatCompletions) {
            return@withAiOperation rawOpenAiTextCompletion(
                apiAddress, apiToken, modelName, prompt, systemPrompt, temperature,
                thinkingMode = aiPurposeThinkingMode(provider, modelName, purpose, disableThinking), headers = headers,
            )
        }
        val response = try {
            withTimeout(aiCompletionRequestTimeoutMillis()) {
                langChainClient.complete(
                    prompt = prompt,
                    route = route,
                    apiToken = apiToken.trim(),
                    modelName = modelName.trim(),
                    headers = headers,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                )
            }
        } catch (e: Exception) {
            log.e(e) { "AI completion failed" }
            throw e
        }
        if (response.isBlank()) throw IllegalStateException("AI returned empty response")
        response
    }

    private suspend fun rawOpenAiTextCompletion(
        apiAddress: String,
        apiToken: String,
        modelName: String,
        prompt: String,
        systemPrompt: String?,
        temperature: Double,
        thinkingMode: AiThinkingMode,
        recordUsage: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val response = rawOpenAiChatCompletion(
            apiAddress = apiAddress,
            apiToken = apiToken,
            modelName = modelName,
            messages = buildOpenAiTextCompletionMessages(prompt, systemPrompt),
            tools = emptyList(),
            temperature = temperature,
            thinkingMode = thinkingMode,
            recordUsage = recordUsage,
            headers = headers,
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
        purpose: AiPurpose? = null,
    ): JsonObject? = withAiOperation(provider, modelName, isFailure = { it == null }) {
        val route = resolveAiRequestRoute(provider, modelName, apiAddress)
        val headers = aiRequestHeaders(provider)
        try {
            if (route.protocol == AiRequestProtocol.OpenAiChatCompletions) {
                rawOpenAiChatCompletion(apiAddress, apiToken, modelName, messages, tools, temperature,
                    thinkingMode = aiPurposeThinkingMode(provider, modelName, purpose), headers = headers)
            } else {
                langChainClient.chatCompletion(
                    messages = messages,
                    route = route,
                    apiToken = apiToken.trim(),
                    modelName = modelName.trim(),
                    headers = headers,
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
        purpose: AiPurpose? = null,
        onChunk: suspend (String) -> Unit,
    ): JsonObject? = withAiOperation(provider, modelName, isFailure = { it == null }) {
        val route = resolveAiRequestRoute(provider, modelName, apiAddress)
        val headers = aiRequestHeaders(provider)
        try {
            if (route.protocol == AiRequestProtocol.OpenAiChatCompletions) {
                rawOpenAiChatCompletionStreaming(apiAddress, apiToken, modelName, messages, tools, temperature, onChunk, headers,
                    aiPurposeThinkingMode(provider, modelName, purpose))
            } else {
                chatCompletion(messages, apiAddress, apiToken, modelName, provider, tools, temperature, purpose)
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
        thinkingMode: AiThinkingMode = AiThinkingMode.DEFAULT,
        recordUsage: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = withSafeGoResponse(headers) {
        val response = client.post(openAiChatCompletionEndpoint(apiAddress.trim())) {
            timeout {
                requestTimeoutMillis = aiChatRequestTimeoutMillis()
                socketTimeoutMillis = aiChatRequestTimeoutMillis()
            }
            contentType(ContentType.Application.Json)
            bearerAuth(apiToken.trim())
            headers.forEach { (name, value) -> header(name, value) }
            setBody(buildOpenAiChatCompletionRequest(
                modelName.trim(), messages, tools, temperature, thinkingMode = thinkingMode,
            ).toString())
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            if (headers.containsKey("x-opencode-session")) throw openCodeGoHttpError(response.status.value)
            throw IllegalStateException(body.ifBlank { "AI chat completion failed: HTTP ${response.status.value}" })
        }
        val parsed = json.parseToJsonElement(body) as JsonObject
        val usage = parsed["usage"] as? JsonObject
        if (usage != null && recordUsage) {
            DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.AI, fields = mapOf(
                "inputTokens" to ((usage["prompt_tokens"] as? JsonPrimitive)?.contentOrNull ?: ""),
                "outputTokens" to ((usage["completion_tokens"] as? JsonPrimitive)?.contentOrNull ?: ""),
            ))
        }
        parsed
    }

    private suspend fun rawOpenAiChatCompletionStreaming(
        apiAddress: String,
        apiToken: String,
        modelName: String,
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        temperature: Double,
        onChunk: suspend (String) -> Unit,
        headers: Map<String, String>,
        thinkingMode: AiThinkingMode,
    ): JsonObject? = withSafeGoResponse(headers) {
        val fullText = StringBuilder()
        val started = DiagnosticLog.elapsed()
        var firstChunk = true
        client.preparePost(openAiChatCompletionEndpoint(apiAddress.trim())) {
            timeout {
                requestTimeoutMillis = aiChatRequestTimeoutMillis()
                socketTimeoutMillis = aiChatRequestTimeoutMillis()
            }
            contentType(ContentType.Application.Json)
            bearerAuth(apiToken.trim())
            headers.forEach { (name, value) -> header(name, value) }
            setBody(buildOpenAiChatCompletionRequest(modelName.trim(), messages, tools, temperature,
                stream = true, thinkingMode = thinkingMode).toString())
        }.execute { response ->
            if (response.status.value !in 200..299) {
                if (headers.containsKey("x-opencode-session")) throw openCodeGoHttpError(response.status.value)
                throw IllegalStateException(response.bodyAsText().ifBlank { "AI chat completion failed: HTTP ${response.status.value}" })
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val chunk = parseOpenAiStreamingContentChunk(channel.readUTF8Line() ?: break) ?: continue
                if (firstChunk) {
                    firstChunk = false
                    DiagnosticLog.diagnostics.emit(DiagnosticCode.AI_FIRST_CHUNK, DiagnosticSource.AI,
                        fields = mapOf("firstChunkMs" to (DiagnosticLog.elapsed() - started).toString()))
                }
                fullText.append(chunk)
                onChunk(chunk)
            }
        }
        if (fullText.isEmpty()) null else buildOpenAiStreamingResponse(fullText.toString())
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
        disableThinking: Boolean = false,
        purpose: AiPurpose? = null,
    ): String {
        return complete(content, apiAddress, apiToken, modelName, provider, systemPrompt,
            disableThinking = disableThinking, purpose = purpose)
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
    disableThinking: Boolean = false,
    thinkingMode: AiThinkingMode = AiThinkingMode.DEFAULT,
): JsonObject = buildJsonObject {
    put("model", JsonPrimitive(modelName))
    put("messages", JsonArray(messages))
    put("temperature", JsonPrimitive(temperature))
    if (thinkingMode == AiThinkingMode.DEEP) {
        put("thinking", buildJsonObject { put("type", JsonPrimitive("enabled")) })
        if (modelName.startsWith("deepseek-v4", ignoreCase = true)) put("reasoning_effort", JsonPrimitive("high"))
    } else if (disableThinking || thinkingMode == AiThinkingMode.FAST) {
        put("thinking", buildJsonObject { put("type", JsonPrimitive("disabled")) })
    }
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
    val reason = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("finish_reason")?.jsonPrimitive?.contentOrNull
    require(reason != "length" && reason != "content_filter") { "AI response was incomplete" }
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
