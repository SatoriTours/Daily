package com.dailysatori.service.book

import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.mcp.RemoteMcpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class BookViewpointDraft(
    val title: String,
    val content: String,
    val example: String,
)

private val bookIntelligenceJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun isAndroidCallableMcpSource(transport: String): Boolean =
    transport.trim().lowercase() in setOf("remote", "http", "streamable-http")

fun parseBookCandidateJson(response: String): List<BookSearchResult> {
    val array = parseJsonArray(response) ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
        val title = obj.stringValue("title").trim()
        if (title.isBlank()) return@mapNotNull null
        BookSearchResult(
            title = title,
            author = obj.stringValue("author"),
            category = obj.stringValue("category"),
            introduction = obj.stringValue("introduction"),
            isbn = obj.stringValue("isbn"),
            coverUrl = obj.stringValue("coverUrl"),
            sourceSummary = obj.stringValue("sourceSummary"),
        )
    }
}

fun parseBookViewpointJson(response: String): List<BookViewpointDraft> {
    val array = parseJsonArray(response) ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
        val title = obj.stringValue("title").trim()
        val content = obj.stringValue("content").trim()
        val example = obj.stringValue("example").trim()
        if (title.isBlank() || content.isBlank() || example.isBlank()) return@mapNotNull null
        if (!isDetailedViewpointDraft(content, example)) return@mapNotNull null
        BookViewpointDraft(title = completeViewpointTitle(title), content = content, example = example)
    }.take(10)
}

fun buildBookCandidatePrompt(query: String, sourceNotes: String): String = """
    请基于以下检索需求生成候选书籍。
    查询：$query
    如果查询包含中文，优先返回中文书籍、中文作者名和中文资料摘要；只有没有可靠中文资料时才返回外文结果。
    资料摘要：$sourceNotes

    只返回 JSON 数组，不要 Markdown、解释或额外文本。
    每个对象必须包含字段：title、author、category、introduction、isbn、coverUrl、sourceSummary。
    如果某个字段未知，使用空字符串。
""".trimIndent()

fun buildBookViewpointPrompt(
    title: String,
    author: String,
    introduction: String,
    sourceNotes: String,
): String = """
    请为以下书籍生成 10 张结构化观点卡片。
    书名：$title
    作者：$author
    简介：$introduction
    资料摘要：$sourceNotes

    只返回 JSON 数组，不要 Markdown、解释或额外文本。
    数组必须包含 10 个对象，每个对象必须包含字段：title、content、example。
    title 是完整观点句，必须有明确判断或行动主张，不能只是“某某的优化/重要性/方法”这类名词短语。
    content 至少 80 个中文字符，要解释清楚为什么这个观点成立、它解决什么问题、怎么判断做得好不好。
    example 至少 100 个中文字符，必须是具体、完整、可想象的场景，写清人物/组织、遇到的问题、采取的动作和结果。
""".trimIndent()

private fun parseJsonArray(response: String): JsonArray? = runCatching {
    bookIntelligenceJson.parseToJsonElement(extractJsonArray(response)).jsonArray
}.getOrNull()

private fun extractJsonArray(response: String): String {
    val trimmed = response.trim()
    val unfenced = if (trimmed.startsWith("```")) {
        trimmed.lines()
            .drop(1)
            .dropLastWhile { it.trim().startsWith("```") || it.isBlank() }
            .joinToString("\n")
            .trim()
    } else {
        trimmed
    }
    return unfenced.substring(unfenced.indexOf('['), unfenced.lastIndexOf(']') + 1)
}

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: ""

private fun kotlinx.serialization.json.JsonElement.asJsonObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()

fun localizedBookSearchQuery(query: String): String =
    if (query.any { it.code > 127 }) "$query 中文书籍 中文资料" else query

private data class BookSourceNotes(
    val text: String,
    val webResults: List<BookSearchResult>,
)

class BookIntelligenceService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val bookSearchService: BookSearchService,
    private val mcpServerRepository: McpServerRepository,
    private val remoteMcpClient: RemoteMcpClient,
) {
    suspend fun searchBooks(query: String): List<BookSearchResult> {
        val searchQuery = localizedBookSearchQuery(query)
        val config = aiConfigService.getDefaultConfig() ?: return bookSearchService.search(searchQuery)
        if (config.api_token.isBlank()) return bookSearchService.search(searchQuery)
        val sourceNotes = collectSourceNotes(searchQuery)
        val aiResponse = completeWithDefaultAi(
            config = config,
            prompt = searchQuery,
            systemPrompt = buildBookCandidatePrompt(searchQuery, sourceNotes.text),
        ) ?: return sourceNotes.webResults
        return rankBookCandidates(searchQuery, sourceNotes.webResults, parseBookCandidateJson(aiResponse))
    }

    suspend fun generateViewpoints(book: BookSearchResult): List<BookViewpointDraft> {
        val config = aiConfigService.getDefaultConfig() ?: return fallbackBookViewpoints(book)
        if (config.api_token.isBlank()) return fallbackBookViewpoints(book)
        val sourceNotes = collectSourceNotes("${book.title} ${book.author} 核心观点 书评 目录")
        val aiResponse = completeWithDefaultAi(
            config = config,
            prompt = book.title,
            systemPrompt = buildBookViewpointPrompt(
                title = book.title,
                author = book.author,
                introduction = book.introduction,
                sourceNotes = sourceNotes.text,
            ),
        ) ?: return fallbackBookViewpoints(book)
        val parsed = parseBookViewpointJson(aiResponse)
        if (parsed.size >= 10) return parsed
        return (parsed + fallbackBookViewpoints(book)).take(10)
    }

    private suspend fun collectSourceNotes(query: String): BookSourceNotes {
        val remoteServers = mcpServerRepository.getEnabled().filter {
            isAndroidCallableMcpSource(it.template_type.ifBlank { it.config_json }) ||
                it.server_url.startsWith("http")
        }
        val remoteNotes = remoteMcpClient.collectSourceNotes(remoteServers, query)
        val webResults = bookSearchService.search(query).take(5)
        val webResultNotes = webResults.joinToString("\n") { result ->
            "- ${result.title} ${result.author}: ${result.introduction.take(300)}"
        }
        val mcpNote = buildMcpSourceNote(remoteServers.isEmpty(), remoteNotes.isNotBlank(), remoteServers.joinToString { it.name })
        return BookSourceNotes("$mcpNote\n$remoteNotes\n$webResultNotes".trim(), webResults)
    }

    private fun buildMcpSourceNote(noRemoteServers: Boolean, hasRemoteNotes: Boolean, names: String): String = when {
        noRemoteServers -> "未发现 Android 可直接调用的远程 MCP，使用 AI 与内置网络搜索兜底。"
        hasRemoteNotes -> "已调用远程 MCP：$names。远程 MCP 失败时使用内置网络搜索兜底。"
        else -> "远程 MCP 未返回可用资料，使用 AI 与内置网络搜索兜底。"
    }

    private suspend fun completeWithDefaultAi(
        config: Ai_config,
        prompt: String,
        systemPrompt: String,
    ): String? {
        return try {
            aiService.complete(
                prompt = prompt,
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
                systemPrompt = systemPrompt,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}

fun rankBookCandidates(
    query: String,
    sourceResults: List<BookSearchResult>,
    aiResults: List<BookSearchResult>,
): List<BookSearchResult> {
    val sourceByTitle = sourceResults.associateBy { normalizeTitleForRanking(it.title) }
    val enrichedAi = aiResults.map { ai ->
        val source = sourceByTitle[normalizeTitleForRanking(ai.title)]
        if (source == null) ai else ai.copy(
            author = source.author.ifBlank { ai.author },
            coverUrl = source.coverUrl.ifBlank { ai.coverUrl },
            sourceUrl = source.sourceUrl.ifBlank { ai.sourceUrl },
            sourceSummary = source.sourceSummary.ifBlank { ai.sourceSummary },
        )
    }
    return (sourceResults + enrichedAi)
        .distinctBy { normalizeTitleForRanking(it.title) }
        .sortedByDescending { candidateScore(query, it) }
}

private fun candidateScore(query: String, result: BookSearchResult): Int {
    val normalizedQuery = normalizeTitleForRanking(query.removeSuffix("中文书籍 中文资料"))
    val normalizedTitle = normalizeTitleForRanking(result.title)
    var score = 0
    if (normalizedTitle == normalizedQuery) score += 100
    if (normalizedTitle.contains(normalizedQuery) || normalizedQuery.contains(normalizedTitle)) score += 40
    if (result.sourceUrl.contains("douban.com")) score += 20
    if (result.author.isNotBlank()) score += 10
    if (result.coverUrl.isNotBlank()) score += 5
    return score
}

private fun normalizeTitleForRanking(value: String): String =
    value.lowercase().replace("中文书籍", "").replace("中文资料", "").replace(Regex("[：:—\\-\\s]"), "").trim()

private fun completeViewpointTitle(title: String): String {
    val trimmed = title.trim()
    if (trimmed.endsWith("。") || trimmed.endsWith("！") || trimmed.endsWith("？")) return trimmed
    if (trimmed.contains("，") || trimmed.contains("必须") || trimmed.contains("需要") || trimmed.contains("才能")) {
        return "$trimmed。"
    }
    return "$trimmed，必须转化为可执行的判断。"
}

private fun isDetailedViewpointDraft(content: String, example: String): Boolean =
    content.length >= 80 && example.length >= 100

fun fallbackBookViewpoints(book: BookSearchResult): List<BookViewpointDraft> {
    val basis = listOf(book.introduction, book.sourceSummary, "围绕本书主题建立可实践的理解框架。").first { it.isNotBlank() }
    val title = book.title.ifBlank { "这本书" }
    val viewpoints = listOf(
        "先看清系统边界，才能避免局部努力拖累整体结果。" to "例如一个内容团队总觉得文章阅读量低，编辑只是不停改标题。后来负责人把选题来源、首段表达、发布时间、转发渠道和读者评论串起来看，发现真正的问题是选题太晚进入热点，等文章发出时讨论已经过去。团队改成每天上午先做选题判断，下午只写通过验证的题目，阅读量才开始稳定。",
        "关键动作要服务整体节奏，而不是追求单点效率最高。" to "例如一个产品团队为了让开发更快，把需求文档压缩成几行结论。工程师确实很快开工，但每次做到一半都发现边界不清，返工更多。后来产品经理先用半小时讲清用户场景、成功标准和不做什么，再进入排期，前期看似慢了一点，整体交付反而更稳。",
        "把约束条件说清楚，团队才知道该优化什么。" to "例如一家小公司同时要求客服回复快、问题解决彻底、成本还要下降，客服主管每天都在救火。后来老板明确优先级：付费客户先保证一次解决，普通咨询用模板快速分流，疑难问题集中复盘。团队终于知道哪些问题值得投入时间，客户满意度也不再靠个人硬扛。",
        "判断标准必须落到可观察指标，否则观点很难变成行动。" to "例如一个学习者说自己要提升表达能力，但只凭感觉判断有没有进步。后来他把目标拆成三个指标：三分钟内讲清背景、给出一个具体例子、最后能提出明确请求。每次会议后他按这三个点复盘，两个月后同事开始更快理解他的方案。",
        "跨角色协同比各自努力更能提升结果质量。" to "例如销售答应客户两周上线新功能，设计、开发和测试却各自排自己的计划，最后只能临时加班补救。后来团队把重要客户需求放到同一张看板上，销售确认承诺前先看研发容量，设计提前给出低保真方案，测试同步准备场景，交付不再靠最后几天硬冲。",
        "不要用一次成功经验替代持续复盘。" to "例如一个创业团队某次直播卖货效果很好，就认定以后都要靠直播增长。连续几场后转化下降，大家才发现第一次成功来自老客户集中购买和主播临场发挥。团队复盘后把直播改成新品讲解、老客答疑和限量活动三种脚本，不再把偶然爆发当成固定方法。",
        "复杂问题里的小改动，要先验证再全面推广。" to "例如一位店长想把所有员工的排班都改成早晚高峰加人，以减少顾客等待。试行一周后发现中午备货没人做，晚高峰反而缺材料。她没有立刻全店推行，而是先在周末班组测试新分工，把备货时间单独锁住，再逐步推广，最后等待时间和出错率都下降。",
        "信息透明能减少无效等待和重复沟通。" to "例如一个装修项目中，业主每天问设计师材料到哪了，工长又反复催采购确认时间。项目经理后来建了一个简单表格，把选材确认、下单、到货、施工日期都写清楚，并标出风险项。业主知道什么时候该确认，工长知道哪天能开工，沟通从情绪化催促变成了共同看进度。",
        "长期能力来自标准化和例外处理的平衡。" to "例如一家咖啡店为了保证出杯速度，把所有饮品流程都标准化，普通订单很快，但熟客提出少糖、换奶时员工总是慌乱。店长后来保留标准流程，同时列出三类允许调整的例外规则，新员工照着也能处理。速度没有明显下降，熟客体验却更稳定。",
        "下一步行动要足够具体，观点才不会停留在口号。" to "例如一个人读完一本管理书后，没有只写“要提升协作”这样的笔记，而是选定下周一的项目会做实验：会前发一页背景，会上先确认目标和分工，会后当天同步决策记录。这个动作很小，但能直接检验观点是否真的改变了工作方式。",
    )
    return viewpoints.map { (viewpoint, example) ->
        BookViewpointDraft(
            title = "$title：$viewpoint",
            content = "$basis 这不是一句抽象提醒，而是在要求读者把书里的概念放回真实系统里检查：谁在等待、哪里积压、哪个指标被局部优化误导、下一步行动会改变什么。只有把原因、判断标准和行动边界说清楚，观点才算完整。",
            example = example,
        )
    }
}
