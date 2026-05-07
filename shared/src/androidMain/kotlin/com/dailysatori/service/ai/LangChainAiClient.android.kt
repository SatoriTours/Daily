package com.dailysatori.service.ai

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ToolChoice
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonNumberSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchemaElement
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.http.client.HttpClientBuilder
import dev.langchain4j.http.client.okhttp.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Duration

internal actual class LangChainAiClient actual constructor() {
    actual suspend fun complete(
        prompt: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String,
        systemPrompt: String?,
        temperature: Double,
    ): String = withContext(Dispatchers.IO) {
        val normalizedPrompt = if (systemPrompt.isNullOrBlank()) {
            prompt
        } else {
            "${systemPrompt.trim()}\n\n${prompt.trim()}"
        }
        createModel(apiAddress, apiToken, modelName, provider, temperature).chat(normalizedPrompt)
    }

    actual suspend fun chatCompletion(
        messages: List<JsonObject>,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String,
        tools: List<JsonObject>,
        temperature: Double,
    ): JsonObject = withContext(Dispatchers.IO) {
        val toolNames = mutableMapOf<String, String>()
        val chatMessages = messages.mapNotNull { toChatMessage(it, toolNames) }
        val toolSpecifications = tools.mapNotNull { toToolSpecification(it) }
        val requestBuilder = ChatRequest.builder()
            .messages(chatMessages)
            .temperature(temperature)
        if (toolSpecifications.isNotEmpty()) {
            requestBuilder.toolSpecifications(toolSpecifications)
            requestBuilder.toolChoice(ToolChoice.AUTO)
        }
        val response = createModel(apiAddress, apiToken, modelName, provider, temperature)
            .chat(requestBuilder.build())
        toOpenAiResponse(response.aiMessage())
    }

    private fun createModel(
        apiAddress: String,
        apiToken: String,
        modelName: String,
        provider: String,
        temperature: Double,
    ): ChatModel {
        return when (provider.lowercase()) {
            "anthropic" -> AnthropicChatModel.builder()
                .httpClientBuilder(langChainHttpClientBuilder())
                .baseUrl(apiAddress)
                .apiKey(apiToken)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofMillis(aiCompletionRequestTimeoutMillis()))
                .build()
            "gemini" -> GoogleAiGeminiChatModel.builder()
                .baseUrl(apiAddress)
                .apiKey(apiToken)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofMillis(aiCompletionRequestTimeoutMillis()))
                .build()
            else -> OpenAiChatModel.builder()
                .httpClientBuilder(langChainHttpClientBuilder())
                .baseUrl(apiAddress)
                .apiKey(apiToken)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofMillis(aiCompletionRequestTimeoutMillis()))
                .build()
        }
    }

    private fun toChatMessage(message: JsonObject, toolNames: MutableMap<String, String>): ChatMessage? {
        val role = message["role"]?.jsonPrimitive?.contentOrNull ?: return null
        val content = message["content"]?.jsonPrimitive?.contentOrNull ?: ""
        return when (role) {
            "system" -> SystemMessage.from(content)
            "assistant" -> {
                val toolRequests = message["tool_calls"]?.jsonArray?.mapNotNull { toolCall ->
                    val request = toToolExecutionRequest(toolCall.jsonObject)
                    if (request != null) toolNames[request.id()] = request.name()
                    request
                } ?: emptyList()
                AiMessage.from(content, toolRequests)
            }
            "tool" -> {
                val id = message["tool_call_id"]?.jsonPrimitive?.contentOrNull ?: return null
                ToolExecutionResultMessage.from(id, toolNames[id] ?: "tool", content)
            }
            else -> UserMessage.from(content)
        }
    }

    private fun toToolExecutionRequest(toolCall: JsonObject): ToolExecutionRequest? {
        val function = toolCall["function"]?.jsonObject ?: return null
        return ToolExecutionRequest.builder()
            .id(toolCall["id"]?.jsonPrimitive?.contentOrNull ?: "")
            .name(function["name"]?.jsonPrimitive?.contentOrNull ?: return null)
            .arguments(function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}")
            .build()
    }

    private fun toToolSpecification(tool: JsonObject): ToolSpecification? {
        val function = tool["function"]?.jsonObject ?: return null
        val name = function["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val description = function["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val parameters = function["parameters"]?.jsonObject ?: buildJsonObject { put("type", "object") }
        return ToolSpecification.builder()
            .name(name)
            .description(description)
            .parameters(toObjectSchema(parameters))
            .build()
    }

    private fun toObjectSchema(schema: JsonObject): JsonObjectSchema {
        val builder = JsonObjectSchema.builder()
        schema["description"]?.jsonPrimitive?.contentOrNull?.let { builder.description(it) }
        schema["properties"]?.jsonObject?.forEach { (name, element) ->
            builder.addProperty(name, toSchemaElement(element))
        }
        val required = schema["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        if (!required.isNullOrEmpty()) builder.required(required)
        builder.additionalProperties(true)
        return builder.build()
    }

    private fun toSchemaElement(element: JsonElement): JsonSchemaElement {
        val obj = element as? JsonObject ?: return JsonStringSchema.builder().build()
        val description = obj["description"]?.jsonPrimitive?.contentOrNull
        return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "object" -> toObjectSchema(obj)
            "array" -> JsonArraySchema.builder()
                .description(description)
                .items(obj["items"]?.let { toSchemaElement(it) } ?: JsonStringSchema.builder().build())
                .build()
            "integer" -> JsonIntegerSchema.builder().description(description).build()
            "number" -> JsonNumberSchema.builder().description(description).build()
            "boolean" -> JsonBooleanSchema.builder().description(description).build()
            else -> JsonStringSchema.builder().description(description).build()
        }
    }

    private fun toOpenAiResponse(message: AiMessage): JsonObject {
        val toolCalls = buildJsonArray {
            message.toolExecutionRequests().forEach { request ->
                add(buildJsonObject {
                    put("id", request.id())
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", request.name())
                        put("arguments", request.arguments())
                    })
                })
            }
        }
        val responseMessage = buildJsonObject {
            put("role", "assistant")
            put("content", message.text() ?: "")
            if (toolCalls.isNotEmpty()) put("tool_calls", toolCalls)
        }
        return buildJsonObject {
            put("choices", JsonArray(listOf(buildJsonObject { put("message", responseMessage) })))
        }
    }
}

internal fun langChainHttpClientBuilder(): HttpClientBuilder = OkHttpClient.builder()
