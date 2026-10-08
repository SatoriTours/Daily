package com.dailysatori.service.ideatopic

import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiPurpose
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
    "请总结上面提供的持续讨论，覆盖实际给出的消息，输出简洁的要点式摘要（3-6 条）。" +
        "结合此前摘要，保留用户的验证反馈、判断与最新纠正，区分 AI 推断和仍未验证的问题；" +
        "如有分歧或推翻旧判断，要明确记录。不要主动制定下一步，不要修改主题正式内容或状态。"

fun ideaDraftInstruction(): String = """
请基于上面的主题正式内容、来源与事件，生成一份主题更新稿。
只输出一个 JSON 对象，结构如下：
{"title":"标题","description":"描述","provenanceSummary":"来龙去脉","conclusions":"研究结论","nextAction":"下一步","referenceIds":[]}
前六个字段必须全部是字符串；多条结论使用字符串内的换行，不得返回数组或对象。referenceIds 必须是字符串数组。
referenceIds 只能从上下文明确列出的「允许引用的ID白名单」选择；正文中的原始记录ID、摘要转述的消息ID不自动具有引用资格，不确定时用空数组。
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

    /** Last raw draft payload; diagnostics only, never persisted or logged. */
    internal var lastRawDraftResponse: String? = null
        private set

    override suspend fun reply(context: IdeaAiContext, onChunk: suspend (String) -> Unit): String =
        withAiRequestSession(context.sessionId?.let { sessionStore.getOrCreate("idea-topic:$it") }) {
            val config = requireConfig(AiPurpose.INTERACTIVE)
            val messages = buildList {
                add(message("system", context.systemPrompt))
                context.messages.forEach { add(message(it.role, "【消息 ${it.id}】\n${it.content}")) }
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
                purpose = AiPurpose.INTERACTIVE,
            )
            val content = response?.get("choices")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (content.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
            content
        }

    override suspend fun summarize(context: IdeaAiContext): String =
        withAiRequestSession(context.sessionId?.let { sessionStore.getOrCreate("idea-topic:$it") }) {
            val config = requireConfig(AiPurpose.REFLECTION)
            val prompt = buildString {
                appendLine(context.userPrompt)
                if (context.messages.isNotEmpty()) {
                    appendLine()
                    appendLine("【需要总结的消息】")
                    context.messages.forEach { appendLine("[${it.id}] ${it.role}: ${it.content}") }
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
                purpose = AiPurpose.REFLECTION,
            ).trim()
            if (summary.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidAiResponse)
            summary
        }

    override suspend fun propose(context: IdeaAiContext): IdeaDraftContent =
        withAiRequestSession(context.sessionId?.let { sessionStore.getOrCreate("idea-topic:$it") }) {
            val config = requireConfig(AiPurpose.REFLECTION)
            val raw = aiService.complete(
                prompt = context.userPrompt,
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
                systemPrompt = context.systemPrompt,
                temperature = 0.2,
                purpose = AiPurpose.REFLECTION,
            )
            lastRawDraftResponse = raw
            parseIdeaDraftResponse(raw, context.allowedReferenceIds)
        }

    private fun requireConfig(purpose: AiPurpose) = aiConfigService.getConfig(purpose)
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
