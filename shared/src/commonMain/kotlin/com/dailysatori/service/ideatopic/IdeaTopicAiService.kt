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

    suspend fun propose(context: IdeaAiContext): IdeaDraftContent
}

fun ideaSummaryInstruction(): String =
    "请总结上面提供的这次沟通，覆盖给出的消息，输出简洁的要点式摘要（3-6 条）。不要修改主题正式内容。"

fun ideaDraftInstruction(): String = """
请基于上面的主题正式内容、来源与事件，生成一份主题更新稿。
只输出一个 JSON 对象，且只包含这些字段：title、description、provenanceSummary、conclusions、nextAction、referenceIds。
referenceIds 只能使用上文中实际出现的来源、事件、会话或消息 ID。
不要输出 status、merge、delete 或其他可执行字段，也不要输出 JSON 之外的任何文字。
""".trimIndent()

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

    override suspend fun propose(context: IdeaAiContext): IdeaDraftContent =
        withAiRequestSession(context.sessionId?.let { sessionStore.getOrCreate("idea-topic:$it") }) {
            val config = requireConfig()
            val raw = aiService.complete(
                prompt = context.userPrompt + "\n\n" + ideaDraftInstruction(),
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
                systemPrompt = context.systemPrompt,
                temperature = 0.2,
            )
            parseIdeaDraftResponse(raw, context.allowedReferenceIds)
        }

    private fun requireConfig() = aiConfigService.getDefaultConfig()
        ?.takeIf { it.api_address.isNotBlank() && it.api_token.isNotBlank() }
        ?: throw IdeaTopicException(IdeaTopicError.AiNotConfigured)

    private fun message(role: String, content: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", content)
    }
}

@kotlinx.serialization.Serializable
internal data class IdeaDraftResponse(
    val title: String,
    val description: String = "",
    val provenanceSummary: String = "",
    val conclusions: String = "",
    val nextAction: String = "",
    val referenceIds: List<String> = emptyList(),
)

private val ideaDraftJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }

/**
 * Strictly parses the structured update draft. Unknown fields (for example status/merge/delete),
 * missing titles and references outside the allowed set are rejected as invalid AI responses.
 */
internal fun parseIdeaDraftResponse(raw: String, allowedReferenceIds: Set<String>): IdeaDraftContent {
    val payload = extractJsonObject(raw) ?: throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
    val response = try {
        ideaDraftJson.decodeFromString(IdeaDraftResponse.serializer(), payload)
    } catch (_: Exception) {
        throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
    }
    val title = response.title.trim()
    if (title.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
    if (response.referenceIds.any { it !in allowedReferenceIds }) {
        throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
    }
    return IdeaDraftContent(
        content = IdeaTopicContent(
            title = title,
            description = response.description,
            provenanceSummary = response.provenanceSummary,
            conclusions = response.conclusions,
            nextAction = response.nextAction,
        ),
        referenceIds = response.referenceIds.distinct(),
    )
}

private fun extractJsonObject(raw: String): String? {
    val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return trimmed.substring(start, end + 1)
}
