package com.dailysatori.service.mcp

import co.touchlab.kermit.Logger
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.book.BookSearchResult
import kotlinx.coroutines.CancellationException
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
    val matchReason: String? = null,
)

class McpAgentService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val toolRegistry: McpToolRegistry,
    private val aiSearchOrchestrator: AiSearchOrchestrator,
) {
    private val log = Logger.withTag("MCPAgent")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val MAX_TOOL_CALL_ROUNDS = 5
    }

    suspend fun processQueryStreaming(
        query: String,
        onStep: (String, String) -> Unit,
        onChunk: suspend (String) -> Unit,
    ): McpAgentResult {
        return try {
            processQueryWithStreamingFinalAnswer(query, onStep, onChunk)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Streaming AI chat failed, falling back to non-streaming path" }
            return processQuery(query, onStep)
        }
    }

    suspend fun processQuery(
        query: String,
        onStep: (String, String) -> Unit,
    ): McpAgentResult {
        val collectedResults = mutableListOf<McpSearchResult>()
        val localSearch = aiSearchOrchestrator.search(query)
        collectedResults.addAll(localSearch.references)
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
                if (localSearch.references.isNotEmpty()) {
                    return McpAgentResult(
                        answer = buildAiSearchFallbackAnswer(query, localSearch.references) + "\n\nAI 服务未配置，以上为本地搜索结果。",
                        searchResults = localSearch.references,
                    )
                }
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
                put("content", aiSearchUserContentForQuery(query, localSearch))
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
                    answer = if (localSearch.references.isNotEmpty()) {
                        buildAiSearchFallbackAnswer(query, localSearch.references)
                    } else if (collectedResults.isNotEmpty()) {
                        buildFallbackAnswer(query, collectedResults)
                    } else {
                        buildMcpErrorResponse("AI 请求失败，请稍后重试")
                    },
                    searchResults = localSearch.references.ifEmpty { collectedResults },
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

            val answerForRefs = finalAnswer ?: buildFallbackAnswer(query, collectedResults)
            val cleanAnswer = privacyMasker.restore(removeMcpRefsTag(answerForRefs))
            val filteredResults = filterRelevantMcpResults(collectedResults, answerForRefs)
            val preciseResults = preciseSearchResultsForQuery(query, filteredResults)
            val referenceBase = preciseResults.ifEmpty { localSearch.references }
            val searchResults = referencesForAnswer(answerForRefs, referenceBase, collectedResults)
            McpAgentResult(answer = cleanAnswer, searchResults = searchResults)
        } catch (e: CancellationException) {
            throw e
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

    private suspend fun processQueryWithStreamingFinalAnswer(
        query: String,
        onStep: (String, String) -> Unit,
        onChunk: suspend (String) -> Unit,
    ): McpAgentResult {
        val collectedResults = mutableListOf<McpSearchResult>()
        val localSearch = aiSearchOrchestrator.search(query)
        collectedResults.addAll(localSearch.references)
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

        val config = aiConfigService.getDefaultConfig()
        if (config == null || config.api_address.isBlank() || config.api_token.isBlank()) {
            return processQuery(query, onStep)
        }

        updateStep("正在理解您的问题...", "processing")

        val messages = mutableListOf<JsonObject>()
        messages.add(buildJsonObject {
            put("role", "system")
            put("content", buildSystemPrompt())
        })

        messages.add(buildJsonObject {
            put("role", "user")
            put("content", aiSearchUserContentForQuery(query, localSearch))
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
                answer = if (localSearch.references.isNotEmpty()) {
                    buildAiSearchFallbackAnswer(query, localSearch.references)
                } else if (collectedResults.isNotEmpty()) {
                    buildFallbackAnswer(query, collectedResults)
                } else {
                    buildMcpErrorResponse("AI 请求失败，请稍后重试")
                },
                searchResults = localSearch.references.ifEmpty { collectedResults },
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
                val fallbackFinalAnswer = finalAnswer
                finalAnswer = fetchFinalAnswerStreaming(messages, apiUrl, apiToken, modelName, provider, privacyMasker, onChunk)
                if (finalAnswer == null) {
                    finalAnswer = fallbackFinalAnswer
                    val fallbackChunk = privacyMasker.restore(removeMcpRefsTag(fallbackFinalAnswer.orEmpty()))
                    if (fallbackChunk.isNotBlank()) onChunk(fallbackChunk)
                }
                completeStep()
                break
            }
        }

        if (finalAnswer == null) {
            updateStep("正在整理答案...", "processing")
            finalAnswer = fetchFinalAnswerStreaming(messages, apiUrl, apiToken, modelName, provider, privacyMasker, onChunk)
            completeStep()
        }

        val answerForRefs = finalAnswer ?: buildFallbackAnswer(query, collectedResults)
        val cleanAnswer = privacyMasker.restore(removeMcpRefsTag(answerForRefs))
        val filteredResults = filterRelevantMcpResults(collectedResults, answerForRefs)
        val preciseResults = preciseSearchResultsForQuery(query, filteredResults)
        val referenceBase = preciseResults.ifEmpty { localSearch.references }
        val searchResults = referencesForAnswer(answerForRefs, referenceBase, collectedResults)
        return McpAgentResult(answer = cleanAnswer, searchResults = searchResults)
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

    private suspend fun fetchFinalAnswerStreaming(
        messages: MutableList<JsonObject>,
        apiUrl: String,
        apiToken: String,
        modelName: String,
        provider: String,
        privacyMasker: PrivacyMasker,
        onChunk: suspend (String) -> Unit,
    ): String? {
        val presenter = StreamingAnswerPresenter(privacyMasker)
        val response = aiService.chatCompletionStreaming(
            messages = messages,
            apiAddress = apiUrl,
            apiToken = apiToken,
            modelName = modelName,
            provider = provider,
            tools = emptyList(),
            onChunk = { chunk ->
                presenter.append(chunk).takeIf { it.isNotEmpty() }?.let { onChunk(it) }
            },
        )
        return response?.let {
            it["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
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
            } catch (e: CancellationException) {
                throw e
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

internal class StreamingAnswerPresenter(
    private val privacyMasker: PrivacyMasker,
) {
    private val rawAnswer = StringBuilder()
    private var emittedAnswer = ""

    fun append(chunk: String): String {
        rawAnswer.append(chunk)
        val visibleAnswer = cleanVisiblePrefix(rawAnswer.toString())
        if (!visibleAnswer.startsWith(emittedAnswer)) return ""

        val delta = visibleAnswer.removePrefix(emittedAnswer)
        emittedAnswer = visibleAnswer
        return delta
    }

    private fun cleanVisiblePrefix(answer: String): String {
        val heldAnswer = holdIncompletePlaceholder(holdIncompleteRefsComment(answer))
        return privacyMasker.restore(removeMcpRefsTag(heldAnswer))
    }

    private fun holdIncompleteRefsComment(answer: String): String {
        val refsStart = refsMetadataStartIndex(answer) ?: return answer
        return answer.substring(0, refsStart).trimEnd()
    }

    private fun refsMetadataStartIndex(answer: String): Int? {
        var searchStart = 0
        while (searchStart < answer.length) {
            val markerStart = answer.indexOf('<', searchStart)
            if (markerStart < 0) return null
            if (couldStartRefsMetadata(answer.substring(markerStart))) return markerStart
            searchStart = markerStart + 1
        }
        return null
    }

    private fun couldStartRefsMetadata(suffix: String): Boolean {
        if (suffix.length < 4) return "<!--".startsWith(suffix.lowercase())
        if (!suffix.startsWith("<!--")) return false

        val afterCommentStart = suffix.drop(4).trimStart()
        if (afterCommentStart.isEmpty()) return true

        val lower = afterCommentStart.lowercase()
        if ("refs".startsWith(lower)) return true
        if (!lower.startsWith("refs")) return false

        val afterRefs = lower.drop(4).trimStart()
        return afterRefs.isEmpty() || afterRefs.startsWith(":")
    }

    private fun holdIncompletePlaceholder(answer: String): String {
        val openBracket = answer.lastIndexOf('[')
        if (openBracket < 0 || openBracket < answer.lastIndexOf(']')) return answer

        val suffix = answer.substring(openBracket)
        return if (Regex("\\[[A-Z0-9_]*").matches(suffix)) {
            answer.substring(0, openBracket)
        } else {
            answer
        }
    }
}
