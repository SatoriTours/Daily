package com.dailysatori.service.ai

import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.diagnostics.diagnosticEventListenerFactory
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
import com.dailysatori.config.AiRequestProtocol
import dev.langchain4j.exception.HttpException
import kotlinx.coroutines.CancellationException
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
        route: AiRequestRoute,
        apiToken: String,
        modelName: String,
        headers: Map<String, String>,
        systemPrompt: String?,
        temperature: Double,
    ): String = withContext(Dispatchers.IO) {
        withSafeGoErrors(headers) {
            val messages = buildOpenAiTextCompletionMessages(prompt, systemPrompt).mapNotNull { toChatMessage(it, mutableMapOf()) }
            createModel(route, apiToken, modelName, headers, temperature)
                .chat(ChatRequest.builder().messages(messages).temperature(temperature).build()).aiMessage().text().orEmpty()
        }
    }

    actual suspend fun chatCompletion(
        messages: List<JsonObject>,
        route: AiRequestRoute,
        apiToken: String,
        modelName: String,
        headers: Map<String, String>,
        tools: List<JsonObject>,
        temperature: Double,
    ): JsonObject = withContext(Dispatchers.IO) {
        withSafeGoErrors(headers) {
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
            val response = createModel(route, apiToken, modelName, headers, temperature)
                .chat(requestBuilder.build())
            toOpenAiResponse(response.aiMessage())
        }
    }

    private fun createModel(
        route: AiRequestRoute,
        apiToken: String,
        modelName: String,
        headers: Map<String, String>,
        temperature: Double,
    ): ChatModel = when (route.protocol) {
        AiRequestProtocol.AnthropicMessages -> AnthropicChatModel.builder()
            .httpClientBuilder(langChainHttpClientBuilder(headers))
            .baseUrl(route.clientBaseUrl)
            .apiKey(apiToken)
            .modelName(modelName)
            .temperature(temperature)
            .apply { if (headers.containsKey("x-opencode-session")) maxRetries(0) }
            .timeout(Duration.ofMillis(aiCompletionRequestTimeoutMillis()))
            .build()
        AiRequestProtocol.Gemini -> GoogleAiGeminiChatModel.builder()
            .httpClientBuilder(langChainHttpClientBuilder())
            .baseUrl(route.clientBaseUrl)
            .apiKey(apiToken)
            .modelName(modelName)
            .temperature(temperature)
            .timeout(Duration.ofMillis(aiCompletionRequestTimeoutMillis()))
            .build()
        AiRequestProtocol.OpenAiChatCompletions -> OpenAiChatModel.builder()
            .httpClientBuilder(langChainHttpClientBuilder(headers))
            .baseUrl(route.clientBaseUrl)
            .apiKey(apiToken)
            .modelName(modelName)
            .temperature(temperature)
            .timeout(Duration.ofMillis(aiCompletionRequestTimeoutMillis()))
            .build()
        AiRequestProtocol.OpenAiResponses -> error("Responses API is not implemented")
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

// LangChain4j 1.14's public builder preserves timeout configuration on this client.
internal fun langChainHttpClientBuilder(headers: Map<String, String> = emptyMap()): HttpClientBuilder = OkHttpClient.builder()
    .okHttpClientBuilder(okhttp3.OkHttpClient.Builder()
        .apply {
            if (headers.isNotEmpty()) addInterceptor { chain ->
                val request = chain.request().newBuilder()
                headers.forEach { (name, value) -> request.header(name, value) }
                chain.proceed(request.build())
            }
        }
        .eventListenerFactory(diagnosticEventListenerFactory(DiagnosticSource.AI)))

private inline fun <T> withSafeGoErrors(headers: Map<String, String>, block: () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    if (!headers.containsKey("x-opencode-session")) throw error
    val httpError = generateSequence(error as Throwable) { it.cause }.take(8).filterIsInstance<HttpException>().firstOrNull()
    throw httpError?.let { openCodeGoHttpError(it.statusCode()) }
        ?: openCodeGoUnknownError()
}
