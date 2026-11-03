package com.dailysatori.service.assistant

import ai.koog.agents.core.tools.*
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.*
import ai.koog.prompt.streaming.StreamFrame
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
) : PromptExecutor() {
    override fun close() = Unit // AiService and its transport are owned by the application.

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant =
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

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = channelFlow {
        sanitizedFailure {
            require(model.id == config.model_name)
            var previous = ""
            val response = streamResponse(prompt, tools) { prefix ->
                if (prefix.length > previous.length) {
                    require(prefix.startsWith(previous)) { "Unsafe streaming prefix changed" }
                    send(StreamFrame.TextDelta(prefix.removePrefix(previous)))
                    previous = prefix
                }
            }
            val decoded = decodeResponse(response)
            decoded.parts.filterIsInstance<MessagePart.Tool.Call>().forEach {
                send(StreamFrame.ToolCallComplete(it.id, it.tool, it.args))
            }
            send(StreamFrame.End(metaInfo = decoded.metaInfo))
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
    } catch (safe: com.dailysatori.service.ai.AiHttpFailure) {
        throw safe
    } catch (budget: AssistantRequestBudgetExceeded) {
        throw budget
    } catch (_: Exception) {
        // No provider body, endpoint, credentials, prompt text or exception cause escapes to Koog.
        throw IllegalStateException("助手 AI 请求失败，请检查配置或稍后重试")
    }

    private fun transportMessages(messages: List<Message>): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        messages.forEach { message ->
            val calls = message.parts.filterIsInstance<MessagePart.Tool.Call>()
            val results = message.parts.filterIsInstance<MessagePart.Tool.Result>()
            if (calls.isNotEmpty()) {
                result += toolCallsMessage(message, calls)
            } else if (results.isEmpty() || message.textContent().isNotEmpty()) {
                result += buildJsonObject {
                    put("role", message.role.name.lowercase()); put("content", sanitize(message.textContent()))
                }
            }
            results.forEach { result += toolResultMessage(it) }
        }
        return result
    }

    private fun toolResultMessage(message: MessagePart.Tool.Result): JsonObject = buildJsonObject {
        put("role", "tool"); put("tool_call_id", requireNotNull(message.id))
        put("content", sanitize(message.output))
    }

    private fun toolCallsMessage(message: Message, calls: List<MessagePart.Tool.Call>): JsonObject = buildJsonObject {
        put("role", "assistant")
        put("content", sanitize(message.textContent()))
        message.metaInfo.metadata?.get("reasoning_content")?.jsonPrimitive?.contentOrNull?.let {
            put("reasoning_content", sanitize(it))
        }
        put("tool_calls", buildJsonArray {
            calls.forEach { call -> add(buildJsonObject {
                put("id", requireNotNull(call.id)); put("type", "function")
                put("function", buildJsonObject { put("name", call.tool); put("arguments", sanitize(call.args)) })
            }) }
        })
    }

    private fun decodeResponse(response: JsonObject): Message.Assistant {
        val message = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: error("Missing assistant message")
        val reasoning = message["reasoning_content"]?.jsonPrimitive?.contentOrNull
        val meta = ResponseMetaInfo(kotlin.time.Clock.System.now(), metadata = buildJsonObject {
            reasoning?.let { put("reasoning_content", sanitize(it)) }
        })
        val calls = message["tool_calls"]?.jsonArray.orEmpty()
        val parts = calls.map { element ->
            val call = element.jsonObject
            val function = requireNotNull(call["function"]).jsonObject
            MessagePart.Tool.Call(
                id = requireNotNull(call["id"]).jsonPrimitive.content,
                tool = requireNotNull(function["name"]).jsonPrimitive.content,
                args = sanitize(requireNotNull(function["arguments"]).jsonPrimitive.content),
            )
        }
        val content = message["content"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        require(parts.isNotEmpty() || content != null) { "Empty assistant response" }
        return Message.Assistant(buildList {
            content?.let { add(MessagePart.Text(sanitize(it))) }
            addAll(parts)
        }, meta)
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
            ToolParameterType.Null -> put("type", "null")
            is ToolParameterType.AnyOf -> put("anyOf", JsonArray(type.types.map(::parameterSchema)))
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
