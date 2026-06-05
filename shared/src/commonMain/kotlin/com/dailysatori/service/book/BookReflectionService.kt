package com.dailysatori.service.book

import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class BookReflectionPromptMessage(
    val role: String,
    val content: String,
)

data class BookReflectionAiResult(
    val content: String,
)

class BookReflectionService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
) {
    suspend fun answer(
        bookTitle: String,
        author: String,
        viewpointTitle: String,
        viewpointContent: String,
        viewpointExample: String,
        existingSummaries: List<String>,
        recentMessages: List<BookReflectionPromptMessage>,
        userQuestion: String,
        onChunk: suspend (String) -> Unit,
    ): BookReflectionAiResult {
        val config = aiConfigService.getDefaultConfig()
            ?: return BookReflectionAiResult(bookReflectionAiNotConfiguredMessage())
        if (config.api_address.isBlank() || config.api_token.isBlank()) {
            return BookReflectionAiResult(bookReflectionAiNotConfiguredMessage())
        }

        val messages = listOf(
            buildJsonObject {
                put("role", "system")
                put("content", bookReflectionAnswerSystemPrompt())
            },
            buildJsonObject {
                put("role", "user")
                put(
                    "content",
                    buildBookReflectionUserPrompt(
                        bookTitle = bookTitle,
                        author = author,
                        viewpointTitle = viewpointTitle,
                        viewpointContent = viewpointContent,
                        viewpointExample = viewpointExample,
                        existingSummaries = existingSummaries,
                        recentMessages = recentMessages,
                        userQuestion = userQuestion,
                    ),
                )
            },
        )
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
            ?.jsonPrimitive?.contentOrNull.orEmpty()
        return BookReflectionAiResult(content.ifBlank { bookReflectionBlankResponseMessage() })
    }

    suspend fun summarize(
        bookTitle: String,
        viewpointTitle: String,
        messages: List<BookReflectionPromptMessage>,
    ): String {
        val config = aiConfigService.getDefaultConfig()
            ?: throw IllegalStateException(bookReflectionAiNotConfiguredMessage())
        if (config.api_address.isBlank() || config.api_token.isBlank()) {
            throw IllegalStateException(bookReflectionAiNotConfiguredMessage())
        }
        val content = buildString {
            appendLine("书名：$bookTitle")
            appendLine("观点：$viewpointTitle")
            appendLine("交流过程：")
            messages.forEach { appendLine("${it.role}：${it.content}") }
        }
        return aiService.complete(
            prompt = content,
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = config.provider,
            systemPrompt = bookReflectionSummarySystemPrompt(),
            temperature = 0.3,
        ).trim().ifBlank { bookReflectionBlankSummaryMessage() }
    }
}

fun bookReflectionAnswerSystemPrompt(): String = """
你是 Daily Satori 的读书思考助手。你的任务不是泛泛聊天，而是帮助用户把当前读书观点想透。
回答要求：
1. 先用简短语言解释核心点。
2. 补充 2 到 3 个用户可能漏掉的角度。
3. 最后提出 1 到 2 个反问，帮助用户继续思考。
4. 默认保持简洁，不要写成长文。
""".trimIndent()

fun bookReflectionSummarySystemPrompt(): String = """
你要把一段围绕读书观点的交流沉淀成用户下次容易回看的总结。
不要只总结 AI 说了什么，要提炼用户这段思考推进到了哪里。
必须使用以下固定结构：
我理解到的核心：
我补上的角度：
还值得继续想的问题：
""".trimIndent()

fun buildBookReflectionUserPrompt(
    bookTitle: String,
    author: String,
    viewpointTitle: String,
    viewpointContent: String,
    viewpointExample: String,
    existingSummaries: List<String>,
    recentMessages: List<BookReflectionPromptMessage>,
    userQuestion: String,
): String = buildString {
    appendLine("书名：$bookTitle")
    appendLine("作者：$author")
    appendLine("当前观点标题：$viewpointTitle")
    appendLine("观点正文：$viewpointContent")
    appendLine("观点例子：$viewpointExample")
    appendLine("已有片段总结：")
    if (existingSummaries.isEmpty()) appendLine("无") else existingSummaries.forEach { appendLine(it) }
    appendLine("当前片段最近消息：")
    if (recentMessages.isEmpty()) appendLine("无") else recentMessages.forEach { appendLine("${it.role}：${it.content}") }
    appendLine("用户本次问题：$userQuestion")
}

fun bookReflectionAiNotConfiguredMessage(): String = "AI 服务未配置，请先在设置中配置 AI 接口"
fun bookReflectionBlankResponseMessage(): String = "这次没有生成有效回复，请稍后重试。"
fun bookReflectionBlankSummaryMessage(): String = "我理解到的核心：这段交流还没有形成清晰结论。\n我补上的角度：可以继续补充具体例子和反面情况。\n还值得继续想的问题：这个观点和我的真实经验有什么关系？"
