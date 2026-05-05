package com.dailysatori.service.mcp

import co.touchlab.kermit.Logger
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.book.BookSearchResult
import kotlinx.serialization.json.*

data class McpToolResult(val success: Boolean, val data: JsonObject? = null)
data class McpAgentResult(val answer: String, val searchResults: List<McpSearchResult>)
data class McpSearchResult(
    val id: Long,
    val type: String,
    val title: String,
    val summary: String?,
    val createdAt: String?,
    val tags: List<String>? = null,
    val isFavorite: Boolean? = null,
)

class McpAgentService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val toolRegistry: McpToolRegistry,
) {
    private val log = Logger.withTag("MCPAgent")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val MAX_TOOL_CALL_ROUNDS = 5
    }

    suspend fun processQuery(
        query: String,
        onStep: (String, String) -> Unit,
    ): McpAgentResult {
        val collectedResults = mutableListOf<McpSearchResult>()
        var currentStepName: String? = null

        fun updateStep(stepName: String, status: String) {
            if (currentStepName != null && currentStepName != stepName) {
                onStep(currentStepName!!, "completed")
            }
            currentStepName = stepName
            onStep(stepName, status)
        }

        fun completeStep() {
            if (currentStepName != null) onStep(currentStepName!!, "completed")
            onStep("完成", "completed")
        }

        return try {
            val config = aiConfigService.getDefaultConfig()
            if (config == null || config.api_address.isBlank() || config.api_token.isBlank()) {
                return McpAgentResult(
                    answer = buildMcpErrorResponse("AI 服务未配置，请先在设置中配置 AI 接口"),
                    searchResults = emptyList(),
                )
            }

            updateStep("正在理解您的问题...", "processing")

            val messages = mutableListOf<JsonObject>()
            messages.add(buildJsonObject {
                put("role", "system")
                put("content", buildSystemPrompt())
            })

            messages.add(buildJsonObject {
                put("role", "user")
                put("content", query)
            })

            val tools = toolRegistry.buildToolDefinitions()
            val privacyMasker = PrivacyMasker()
            val apiUrl = config.api_address.trimEnd('/')
            val apiToken = config.api_token
            val modelName = config.model_name
            val provider = config.provider
            var finalAnswer: String? = null

            for (round in 0 until MAX_TOOL_CALL_ROUNDS) {
                val response = requestChatCompletionWithRetry(
                    messages = messages,
                    apiUrl = apiUrl,
                    apiToken = apiToken,
                    modelName = modelName,
                    provider = provider,
                    tools = tools,
                ) ?: return McpAgentResult(
                    answer = if (collectedResults.isNotEmpty()) {
                        buildFallbackAnswer(query, collectedResults)
                    } else {
                        buildMcpErrorResponse("AI 请求失败，请稍后重试")
                    },
                    searchResults = collectedResults,
                )

                val message = response["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject
                val toolCalls = message?.get("tool_calls")?.jsonArray

                if (message == null) { completeStep(); break }

                if (toolCalls != null && toolCalls.isNotEmpty()) {
                    updateStep("正在查询数据...", "processing")
                    messages.add(buildAssistantToolMessage(message))
                    executeToolCalls(toolCalls, messages, collectedResults, privacyMasker)
                    updateStep("正在生成回答...", "processing")
                } else {
                    finalAnswer = message["content"]?.jsonPrimitive?.contentOrNull
                    completeStep()
                    break
                }
            }

            if (finalAnswer == null) {
                updateStep("正在整理答案...", "processing")
                finalAnswer = fetchFinalAnswer(messages, apiUrl, apiToken, modelName, provider)
                completeStep()
            }

            val filteredResults = filterRelevantMcpResults(collectedResults, finalAnswer ?: "")
            val preciseResults = preciseSearchResultsForQuery(query, filteredResults)
            val cleanAnswer = privacyMasker.restore(removeMcpRefsTag(finalAnswer ?: buildFallbackAnswer(query, collectedResults)))
            McpAgentResult(answer = cleanAnswer, searchResults = preciseResults)
        } catch (e: Exception) {
            log.e(e) { "MCP Agent processing failed" }
            if (currentStepName != null) onStep(currentStepName!!, "error")
            onStep("处理失败", "error")
            McpAgentResult(
                answer = buildMcpErrorResponse("处理失败: ${e.message}"),
                searchResults = collectedResults,
            )
        }
    }

    private suspend fun executeToolCalls(
        toolCalls: kotlinx.serialization.json.JsonArray,
        messages: MutableList<JsonObject>,
        collectedResults: MutableList<McpSearchResult>,
        privacyMasker: PrivacyMasker,
    ) {
        for (toolCall in toolCalls) {
            val tc = toolCall.jsonObject
            val function = tc["function"]?.jsonObject
            val toolName = function?.get("name")?.jsonPrimitive?.contentOrNull ?: continue
            val arguments = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
            val toolCallId = tc["id"]?.jsonPrimitive?.contentOrNull ?: ""

            val toolResult = toolRegistry.executeTool(toolName, arguments)
            collectedResults.addAll(extractMcpSearchResults(toolName, toolResult))

            val resultContent = toolResult.data?.toString() ?: buildJsonObject {
                put("success", toolResult.success); put("error", "unknown")
            }.toString()

            messages.add(buildJsonObject {
                put("role", "tool")
                put("tool_call_id", toolCallId)
                put("content", privacyMasker.mask(resultContent))
            })
        }
    }

    private suspend fun fetchFinalAnswer(
        messages: MutableList<JsonObject>,
        apiUrl: String,
        apiToken: String,
        modelName: String,
        provider: String,
    ): String? {
        val response = requestChatCompletionWithRetry(
            messages = messages,
            apiUrl = apiUrl,
            apiToken = apiToken,
            modelName = modelName,
            provider = provider,
            tools = emptyList(),
        )
        return response?.let {
            it["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
        }
    }

    private suspend fun requestChatCompletionWithRetry(
        messages: List<JsonObject>,
        apiUrl: String,
        apiToken: String,
        modelName: String,
        provider: String,
        tools: List<JsonObject>,
    ): JsonObject? {
        var lastError: Exception? = null
        for (attempt in aiSummaryRetryAttempts()) {
            try {
                return aiService.chatCompletion(
                    messages = messages,
                    apiAddress = apiUrl,
                    apiToken = apiToken,
                    modelName = modelName,
                    provider = provider,
                    tools = tools,
                    temperature = 0.7,
                )
            } catch (e: Exception) {
                lastError = e
                log.w(e) { "AI assistant request failed, attempt $attempt/${aiSummaryRetryAttempts().size}" }
            }
        }
        log.e(lastError) { "AI assistant request failed after retries" }
        return null
    }

    private fun buildSystemPrompt(): String {
        val now = kotlinx.datetime.Clock.System.now()
        val epochMs = now.toEpochMilliseconds()
        val today = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toString().substring(0, 10)
        val yesterday = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs - 86400000).toString().substring(0, 10)
        val beforeYesterday = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs - 172800000).toString().substring(0, 10)
        val currentTime = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toString().substring(0, 19)

        return buildMcpSystemPrompt(
            today = today,
            yesterday = yesterday,
            beforeYesterday = beforeYesterday,
            currentTime = currentTime,
        )
    }

    suspend fun searchBookOnline(query: String): List<BookSearchResult> {
        val config = aiConfigService.getDefaultConfig() ?: return emptyList()
        if (config.api_token.isBlank()) return emptyList()
        val systemPrompt = buildMcpBookSearchPrompt(query)
        val response = aiService.complete(
            prompt = query,
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = config.provider,
            systemPrompt = systemPrompt,
        )
        return parseBookSearchResults(response)
    }

    private fun parseBookSearchResults(response: String): List<BookSearchResult> {
        if (response.isBlank()) return emptyList()
        return try {
            val cleaned = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val array = json.parseToJsonElement(cleaned).jsonArray
            array.map { el ->
                val obj = el.jsonObject
                BookSearchResult(
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    author = obj["author"]?.jsonPrimitive?.content ?: "",
                    introduction = obj["introduction"]?.jsonPrimitive?.content ?: "",
                    coverUrl = obj["coverUrl"]?.jsonPrimitive?.content ?: "",
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}

fun buildAssistantToolMessage(message: JsonObject): JsonObject = buildJsonObject {
    message.forEach { (key, value) -> put(key, value) }
    put("role", JsonPrimitive("assistant"))
    if (message["content"] == null) put("content", JsonPrimitive(""))
}
