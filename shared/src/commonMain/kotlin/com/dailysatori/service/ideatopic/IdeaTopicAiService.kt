package com.dailysatori.service.ideatopic

import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiConversationSessionStore
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.ai.withAiRequestSession
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Network boundary for idea topic AI features. Implementations never touch the database.
 * Tests provide a controllable fake; production uses the configured AiService.
 */
interface IdeaTopicAiPort {
    suspend fun reply(context: IdeaAiContext, onChunk: suspend (String) -> Unit): String

    suspend fun summarize(context: IdeaAiContext): String
}

fun ideaSummaryInstruction(): String =
    "请总结上面提供的这次沟通，覆盖给出的消息，输出简洁的要点式摘要（3-6 条）。不要修改主题正式内容。"

/**
 * Production adapter. Reuses the configured AiService, AI config and per-conversation session store.
 * Topic conversations are isolated: only the topic's own conversation key is used and nothing is
 * written to the global chat repository.
 */
class IdeaTopicAiService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val sessionStore: AiConversationSessionStore,
) : IdeaTopicAiPort {

    override suspend fun reply(context: IdeaAiContext, onChunk: suspend (String) -> Unit): String =
        withAiRequestSession(context.sessionId?.let { sessionStore.getOrCreate("idea-topic:$it") }) {
            val config = requireConfig()
            val messages = buildList {
                add(message("system", context.systemPrompt))
                context.messages.forEach { add(message(it.role, it.content)) }
                add(message("user", context.userPrompt))
            }
            val response = aiService.chatCompletionStreaming(
                messages = messages,
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
                temperature = 0.5,
                onChunk = onChunk,
            )
            val content = response?.get("choices")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (content.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
            content
        }

    override suspend fun summarize(context: IdeaAiContext): String =
        withAiRequestSession(context.sessionId?.let { sessionStore.getOrCreate("idea-topic:$it") }) {
            val config = requireConfig()
            val prompt = buildString {
                appendLine(context.userPrompt)
                if (context.messages.isNotEmpty()) {
                    appendLine()
                    appendLine("【需要总结的消息】")
                    context.messages.forEach { appendLine("${it.role}: ${it.content}") }
                }
            }
            val summary = aiService.complete(
                prompt = prompt,
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
                systemPrompt = context.systemPrompt,
                temperature = 0.3,
            ).trim()
            if (summary.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
            summary
        }

    private fun requireConfig() = aiConfigService.getDefaultConfig()
        ?.takeIf { it.api_address.isNotBlank() && it.api_token.isNotBlank() }
        ?: throw IdeaTopicException(IdeaTopicError.AiNotConfigured)

    private fun message(role: String, content: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", content)
    }
}
