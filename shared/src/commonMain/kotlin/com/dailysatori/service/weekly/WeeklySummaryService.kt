package com.dailysatori.service.weekly

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.AiService
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.*

class WeeklySummaryService(
    private val aiService: AiService,
    private val articleRepo: ArticleRepository,
    private val diaryRepo: DiaryRepository,
    private val viewpointRepo: BookViewpointRepository,
    private val weeklySummaryRepo: WeeklySummaryRepository,
    private val aiConfigService: com.dailysatori.service.ai.AiConfigService,
) {
    private val log = Logger.withTag("WeeklySummary")

    fun getLastCompletedWeekRange(): Pair<Long, Long>? {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val dayOfWeek = today.dayOfWeek.value
        val lastSunday = today.minus(dayOfWeek.toLong(), DateTimeUnit.DAY)
        val lastMonday = lastSunday.minus(6, DateTimeUnit.DAY)
        val nextMonday = lastSunday.plus(1, DateTimeUnit.DAY)
        return Pair(
            lastMonday.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            nextMonday.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() - 1,
        )
    }

    suspend fun checkAndGenerate(): Boolean {
        val range = getLastCompletedWeekRange() ?: return false
        val existing = weeklySummaryRepo.getByWeekRange(range.first, range.second)
        if (existing?.status == WEEKLY_STATUS_COMPLETED && existing.content.isNotBlank()) return true
        return generateWeeklySummary(range.first, range.second)
    }

    suspend fun generateWeeklySummary(weekStartMs: Long, weekEndMs: Long): Boolean {
        val summary = try {
            weeklySummaryRepo.getOrCreate(weekStartMs, weekEndMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Failed to create weekly summary record" }
            return false
        }
        if (!weeklySummaryRepo.claimGeneration(summary.id)) {
            log.i { "Weekly summary generation is already running for week starting $weekStartMs" }
            return false
        }
        return try {
            val articles = articleRepo.getWeeklyFavoritesByDateRangeSync(weekStartMs, weekEndMs)
                .mapNotNull { article ->
                    weeklyArticleContent(
                        article.ai_content,
                        article.ai_markdown_content,
                        article.original_markdown_content,
                        article.title,
                    )?.let { content -> WeeklyMaterial(article.id, article.ai_title ?: article.title ?: "未命名文章", content) }
                }
            val diaries = diaryRepo.getByDateRangeSync(weekStartMs, weekEndMs)
                .mapNotNull { diary -> diary.content.trim().takeIf(String::isNotBlank)?.let { WeeklyMaterial(diary.id, "日记", it) } }
            val viewpoints = viewpointRepo.getAllSync()
                .filter { it.created_at in weekStartMs..weekEndMs && it.status == "ready" }
                .mapNotNull { viewpoint ->
                    listOf(viewpoint.content, viewpoint.example)
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .joinToString("\n\n")
                        .takeIf(String::isNotBlank)
                        ?.let { WeeklyMaterial(viewpoint.id, viewpoint.title.ifBlank { "读书观点" }, it) }
                }

            if (articles.isEmpty() && diaries.isEmpty() && viewpoints.isEmpty()) {
                weeklySummaryRepo.update(
                    id = summary.id,
                    content = "本周暂无可总结的收藏、日记或读书观点。",
                    articleCount = 0,
                    diaryCount = 0,
                    viewpointCount = 0,
                    articleIds = null,
                    diaryIds = null,
                    viewpointIds = null,
                    appIdeas = null,
                    status = WEEKLY_STATUS_COMPLETED,
                )
                return true
            }

            weeklySummaryRepo.update(
                id = summary.id,
                content = summary.content,
                articleCount = articles.size.toLong(),
                diaryCount = diaries.size.toLong(),
                viewpointCount = viewpoints.size.toLong(),
                articleIds = articles.idsOrNull(),
                diaryIds = diaries.idsOrNull(),
                viewpointIds = viewpoints.idsOrNull(),
                appIdeas = summary.app_ideas,
                status = WEEKLY_STATUS_GENERATING,
            )
            val config = aiConfigService.getDefaultConfig()
                ?: throw IllegalStateException("AI config not set")
            if (config.api_address.isBlank() || config.api_token.isBlank() || config.model_name.isBlank()) {
                throw IllegalStateException("AI config not set")
            }
            val content = aiService.summarize(
                content = weeklySummaryInput(articles, diaries, viewpoints),
                systemPrompt = weeklySummaryPrompt(),
                apiAddress = config.api_address.trim().trimEnd('/'),
                apiToken = config.api_token.trim(),
                modelName = config.model_name.trim(),
                provider = config.provider.trim(),
            ).trim()
            if (content.isBlank()) throw IllegalStateException("AI returned empty weekly summary")
            weeklySummaryRepo.update(
                id = summary.id,
                content = content,
                articleCount = articles.size.toLong(),
                diaryCount = diaries.size.toLong(),
                viewpointCount = viewpoints.size.toLong(),
                articleIds = articles.idsOrNull(),
                diaryIds = diaries.idsOrNull(),
                viewpointIds = viewpoints.idsOrNull(),
                appIdeas = null,
                status = WEEKLY_STATUS_COMPLETED,
            )
            log.i { "Weekly summary generated for week starting $weekStartMs" }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Failed to generate weekly summary" }
            val latest = weeklySummaryRepo.getByWeekRange(weekStartMs, weekEndMs) ?: summary
            val hasLastSuccessfulSummary = summary.status == WEEKLY_STATUS_COMPLETED && summary.content.isNotBlank()
            weeklySummaryRepo.update(
                id = latest.id,
                content = latest.content,
                articleCount = latest.article_count ?: 0,
                diaryCount = latest.diary_count ?: 0,
                viewpointCount = latest.viewpoint_count ?: 0,
                articleIds = latest.article_ids,
                diaryIds = latest.diary_ids,
                viewpointIds = latest.viewpoint_ids,
                appIdeas = latest.app_ideas,
                status = if (hasLastSuccessfulSummary) WEEKLY_STATUS_COMPLETED else WEEKLY_STATUS_FAILED,
            )
            false
        }
    }

    fun getLatest() = weeklySummaryRepo.getLatest()
    fun getAll() = weeklySummaryRepo.getAll()
}

internal data class WeeklyMaterial(val id: Long, val title: String, val content: String)

internal fun weeklyArticleContent(
    aiContent: String?,
    aiMarkdownContent: String?,
    originalMarkdownContent: String?,
    title: String?,
): String? = listOf(aiContent, aiMarkdownContent, originalMarkdownContent, title)
    .firstNotNullOfOrNull { value ->
        value?.trim()?.takeIf { it.isNotBlank() && !it.isArticleProcessingErrorMessage() }
    }

private fun String.isArticleProcessingErrorMessage(): Boolean =
    this == "Job was cancelled" ||
        this == "StandaloneCoroutine was cancelled" ||
        this == "AI summary generation returned empty result" ||
        startsWith("AI processing failed", ignoreCase = true)

internal fun weeklySummaryInput(
    articles: List<WeeklyMaterial>,
    diaries: List<WeeklyMaterial>,
    viewpoints: List<WeeklyMaterial>,
): String = buildString {
    fun appendSection(title: String, items: List<WeeklyMaterial>) {
        if (items.isEmpty()) return
        appendLine("## $title")
        items.forEachIndexed { index, item ->
            appendLine("### ${index + 1}. ${item.title}")
            appendLine(item.content.take(WEEKLY_ITEM_CHAR_LIMIT))
            appendLine()
        }
    }
    appendSection("收藏文章", articles.take(WEEKLY_MAX_ARTICLES))
    appendSection("日记", diaries.take(WEEKLY_MAX_DIARIES))
    appendSection("读书观点", viewpoints.take(WEEKLY_MAX_VIEWPOINTS))
}.take(WEEKLY_TOTAL_CHAR_LIMIT)

internal fun weeklySummaryPrompt(): String = """
    你是谨慎的中文个人周报整理助手。请仅根据用户提供的本周收藏文章、日记和读书观点生成 Markdown 周总结。

    要求：
    1. 使用中文，先给出一段简短总览，再归纳本周主题、关键收获和下周可执行建议。
    2. 不要求所有来源都存在；某类内容缺失或个别文章处理失败时，基于其余可用内容正常总结。
    3. 不编造事实，不推测用户未表达的情绪、经历或计划。
    4. 合并重复信息，避免逐条复述来源；没有充分依据时省略对应结论。
    5. 只输出可直接展示的 Markdown 正文，不输出处理说明或代码块围栏。
""".trimIndent()

private fun List<WeeklyMaterial>.idsOrNull(): String? =
    joinToString(",") { it.id.toString() }.takeIf(String::isNotBlank)

private const val WEEKLY_STATUS_GENERATING = "generating"
private const val WEEKLY_STATUS_COMPLETED = "completed"
private const val WEEKLY_STATUS_FAILED = "failed"
private const val WEEKLY_MAX_ARTICLES = 24
private const val WEEKLY_MAX_DIARIES = 40
private const val WEEKLY_MAX_VIEWPOINTS = 24
private const val WEEKLY_ITEM_CHAR_LIMIT = 2_000
private const val WEEKLY_TOTAL_CHAR_LIMIT = 60_000
