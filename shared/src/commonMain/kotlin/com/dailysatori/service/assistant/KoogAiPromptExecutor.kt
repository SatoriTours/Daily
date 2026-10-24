package com.dailysatori.service.assistant

import ai.koog.agents.core.tools.*
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.*
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.assistant.data.AssistantStreamRedactor
import com.dailysatori.shared.db.Ai_config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.*

/** Koog owns the agent loop; Daily retains configured providers, transport and request diagnostics. */
class KoogAiPromptExecutor(
    private val service: AiService,
    private val config: Ai_config,
    private val sanitize: (String) -> String = { it },
    private val onPartial: (suspend (String) -> Unit)? = null,
    private val streamFilter: (() -> AssistantStreamRedactor)? = null,
) : PromptExecutor {
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> =
        sanitizedFailure {
            require(model.id == config.model_name)
            if (onPartial != null) return@sanitizedFailure decodeResponse(streamResponse(prompt, tools, onPartial))
            val response = service.chatCompletion(
                messages = transportMessages(prompt.messages),
                apiAddress = config.api_address, apiToken = config.api_token,
                modelName = config.model_name, provider = config.provider,
                tools = tools.map(::transportTool),
                temperature = prompt.params.temperature ?: 0.5,
                purpose = AiPurpose.INTERACTIVE
            ) ?: error("Missing response")
            decodeResponse(response)
        }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = channelFlow {
        sanitizedFailure {
            require(model.id == config.model_name)
            var previous = ""
            streamResponse(prompt, emptyList()) { prefix ->
                if (prefix.length > previous.length) {
                    require(prefix.startsWith(previous)) { "Unsafe streaming prefix changed" }
                    send(prefix.removePrefix(previous))
                    previous = prefix
                }
            }
        }
    }

    private suspend fun streamResponse(prompt: Prompt, tools: List<ToolDescriptor>, publish: suspend (String) -> Unit): JsonObject {
        val filter = streamFilter?.invoke()
        val collected = StringBuilder()
        var published = ""
        publish("")
        val response = service.chatCompletionStreaming(
            messages = transportMessages(prompt.messages),
            apiAddress = config.api_address, apiToken = config.api_token,
            modelName = config.model_name, provider = config.provider,
            tools = tools.map(::transportTool),
            temperature = prompt.params.temperature ?: 0.5, purpose = AiPurpose.INTERACTIVE,
            onChunk = { chunk ->
                require(collected.length + chunk.length <= 200_000)
                collected.append(chunk)
                // Callers without a credential snapshot retain the safe, buffered legacy behavior.
                filter?.prefix(collected.toString())?.let { prefix ->
                    if (prefix.length > published.length) {
                        require(prefix.startsWith(published)) { "Unsafe streaming prefix changed" }
                        publish(prefix); published = prefix
                    }
                }
            },
        ) ?: error("Missing response")
        val message = response["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject
        val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val final = filter?.prefix(content, complete = true) ?: sanitize(content)
        val hasTools = message["tool_calls"]?.jsonArray?.isNotEmpty() == true
        if (hasTools) publish("") else {
            require(final.startsWith(published)) { "Unsafe final streaming prefix changed" }
            publish(final)
        }
        // Capture the request's credential snapshot for the final structured response as well.
        val safeMessage = JsonObject(message.mapValues { (key, value) ->
            when {
                key in setOf("content", "reasoning_content") && value is JsonPrimitive && value.isString ->
                    JsonPrimitive(filter?.prefix(value.content, complete = true) ?: sanitize(value.content))
                else -> value
            }
        })
        return JsonObject(response + ("choices" to buildJsonArray {
            add(JsonObject(response["choices"]!!.jsonArray.first().jsonObject + ("message" to safeMessage)))
        }))
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("助手传输不提供内容审核接口")

    private suspend fun <T> sanitizedFailure(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // No provider body, endpoint, credentials, prompt text or exception cause escapes to Koog.
        throw IllegalStateException("助手 AI 请求失败，请检查配置或稍后重试")
    }

    private fun transportMessages(messages: List<Message>): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        var index = 0
        while (index < messages.size) {
            val message = messages[index]
            when (message) {
                is Message.Tool.Call -> {
                    val roundId = message.metaInfo.additionalInfo["daily_response_id"]
                    val round = messages.drop(index).takeWhile {
                        it is Message.Tool && (it !is Message.Tool.Call ||
                            it.metaInfo.additionalInfo["daily_response_id"] == roundId)
                    }
                    result += toolCallsMessage(round.filterIsInstance<Message.Tool.Call>())
                    round.filterIsInstance<Message.Tool.Result>().forEach { result += toolResultMessage(it) }
                    index += round.size
                }
                is Message.Tool.Result -> {
                    result += toolResultMessage(message)
                    index++
                }
                else -> {
                    result += buildJsonObject {
                        put("role", message.role.name.lowercase()); put("content", sanitize(message.content))
                    }
                    index++
                }
            }
        }
        return result
    }

    private fun toolResultMessage(message: Message.Tool.Result): JsonObject = buildJsonObject {
        put("role", "tool"); put("tool_call_id", requireNotNull(message.id))
        put("content", sanitize(message.content))
    }

    private fun toolCallsMessage(calls: List<Message.Tool.Call>): JsonObject = buildJsonObject {
        put("role", "assistant")
        put("content", "")
        calls.first().metaInfo.additionalInfo["reasoning_content"]?.let { put("reasoning_content", it) }
        put("tool_calls", buildJsonArray {
            calls.forEach { call -> add(buildJsonObject {
                put("id", requireNotNull(call.id)); put("type", "function")
                put("function", buildJsonObject { put("name", call.tool); put("arguments", sanitize(call.content)) })
            }) }
        })
    }

    private fun decodeResponse(response: JsonObject): List<Message.Response> {
        val message = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: error("Missing assistant message")
        val reasoning = message["reasoning_content"]?.jsonPrimitive?.contentOrNull
        val meta = ResponseMetaInfo.create(kotlinx.datetime.Clock.System,
            additionalInfo = buildMap {
                // Koog inserts each result directly after its call; retain model-round boundaries when regrouping.
                put("daily_response_id", kotlin.random.Random.nextLong().toString())
                reasoning?.let { put("reasoning_content", sanitize(it)) }
            })
        val calls = message["tool_calls"]?.jsonArray.orEmpty()
        if (calls.isNotEmpty()) return calls.map { element ->
            val call = element.jsonObject
            val function = requireNotNull(call["function"]).jsonObject
            Message.Tool.Call(
                id = requireNotNull(call["id"]).jsonPrimitive.content,
                tool = requireNotNull(function["name"]).jsonPrimitive.content,
                content = sanitize(requireNotNull(function["arguments"]).jsonPrimitive.content),
                metaInfo = meta
            )
        }
        val content = message["content"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: error("Empty assistant response")
        return listOf(Message.Assistant(sanitize(content), meta))
    }

    private fun transportTool(tool: ToolDescriptor): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", tool.name); put("description", tool.description)
            put("parameters", buildJsonObject {
                put("type", "object")
                put("properties", JsonObject((tool.requiredParameters + tool.optionalParameters).associate { parameter ->
                    parameter.name to parameterSchema(parameter)
                }))
                put("required", JsonArray(tool.requiredParameters.map { JsonPrimitive(it.name) }))
                put("additionalProperties", false)
            })
        })
    }

    private fun parameterSchema(parameter: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", parameter.description)
        typeSchema(parameter.type).forEach { (key, value) -> put(key, value) }
    }

    private fun typeSchema(type: ToolParameterType): JsonObject = buildJsonObject {
        when (type) {
            ToolParameterType.String -> put("type", "string")
            ToolParameterType.Integer -> put("type", "integer")
            ToolParameterType.Float -> put("type", "number")
            ToolParameterType.Boolean -> put("type", "boolean")
            is ToolParameterType.Enum -> {
                put("type", "string"); put("enum", JsonArray(type.entries.map(::JsonPrimitive)))
            }
            is ToolParameterType.List -> { put("type", "array"); put("items", typeSchema(type.itemsType)) }
            is ToolParameterType.Object -> {
                put("type", "object")
                put("properties", JsonObject(type.properties.associate { it.name to parameterSchema(it) }))
                put("required", JsonArray(type.requiredProperties.map(::JsonPrimitive)))
                put("additionalProperties", type.additionalProperties ?: false)
            }
        }
    }
}
