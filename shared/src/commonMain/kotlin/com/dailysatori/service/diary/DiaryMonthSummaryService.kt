package com.dailysatori.service.diary

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.Diary
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private const val DIARY_MONTH_SUMMARY_SUCCESS = "success"
private const val DIARY_MONTH_SUMMARY_FAILED = "failed"
private const val DIARY_MONTH_PROMPT_MAX_LENGTH = 4_500

data class MonthSummaryFingerprint(
    val diaryCount: Long,
    val latestDiaryUpdatedAt: Long,
)

class DiaryMonthSummaryService(
    private val diaryRepo: DiaryRepository,
    private val summaryRepo: DiaryMonthSummaryRepository,
    private val aiConfigService: AiConfigService,
    private val aiService: AiService,
) {
    private val log = Logger.withTag("DiaryMonthSummary")

    suspend fun refreshRecentMonthsIfNeeded(nowMs: Long = Clock.System.now().toEpochMilliseconds()) {
        val config = aiConfigService.getDefaultConfig() ?: return
        recentDiaryMonthKeys(nowMs).forEachIndexed { index, monthKey ->
            val diaries = diariesForMonth(monthKey)
            if (diaries.isEmpty()) return@forEachIndexed
            val cache = summaryRepo.getByMonth(monthKey)
            val fingerprint = cache?.let { MonthSummaryFingerprint(it.diary_count, it.latest_diary_updated_at) }
            val latestUpdatedAt = diaries.maxOf { it.updated_at }
            val retryFailed = index == 0 && cache?.status == DIARY_MONTH_SUMMARY_FAILED && diaryMonthFailedSummaryCanRetry(cache.generated_at, nowMs)
            if (!retryFailed && !diaryMonthSummaryNeedsRefresh(fingerprint, diaries.size.toLong(), latestUpdatedAt, index == 0)) return@forEachIndexed
            generateAndStore(monthKey, diaries, cache?.summary.orEmpty(), config, nowMs)
        }
    }

    fun fallbackSummary(diaries: List<Diary>): String {
        val tags = diaries.flatMap { diary ->
            diary.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != "null" }.orEmpty()
        }.distinct().take(3)
        val tagText = tags.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "一些普通但明亮的片刻"
        return "这个月的你把 $tagText 留了下来。照片负责记住画面，文字负责留下当时的心。"
    }

    private suspend fun generateAndStore(monthKey: String, diaries: List<Diary>, existingSummary: String, config: com.dailysatori.shared.db.Ai_config, nowMs: Long) {
        val latestUpdatedAt = diaries.maxOf { it.updated_at }
        try {
            val summary = aiService.summarize(
                content = buildDiaryMonthSummaryPrompt(monthKey, diaries.map { it.content }, diaryTags(diaries), diaryMoods(diaries)),
                systemPrompt = "你是 Daily Satori 的日记整理助手，只根据用户日记内容写克制、温柔、真实的一句中文月度总结。",
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
            ).lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            summaryRepo.upsert(monthKey, summary, diaries.size.toLong(), latestUpdatedAt, DIARY_MONTH_SUMMARY_SUCCESS, null, nowMs)
        } catch (e: Exception) {
            log.w(e) { "Diary month summary generation failed for $monthKey" }
            summaryRepo.upsert(monthKey, existingSummary, diaries.size.toLong(), latestUpdatedAt, DIARY_MONTH_SUMMARY_FAILED, e.message, nowMs)
        }
    }

    private fun diariesForMonth(monthKey: String): List<Diary> {
        val start = monthStart(monthKey).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val end = monthStart(monthKey).plus(1, DateTimeUnit.MONTH).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() - 1
        return diaryRepo.getByDateRangeSync(start, end)
    }

    private fun diaryTags(diaries: List<Diary>): List<String> = diaries.flatMap { diary ->
        diary.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != "null" }.orEmpty()
    }.distinct().take(8)

    private fun diaryMoods(diaries: List<Diary>): List<String> = diaries.mapNotNull { diary ->
        diary.mood?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    }.distinct().take(8)
}

fun recentDiaryMonthKeys(nowMs: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): List<String> {
    val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(timeZone).date
    val current = LocalDate(today.year, today.monthNumber, 1)
    return (0..2).map { offset -> current.minus(offset, DateTimeUnit.MONTH).monthKey() }
}

fun diaryMonthSummaryNeedsRefresh(
    cached: MonthSummaryFingerprint?,
    diaryCount: Long,
    latestDiaryUpdatedAt: Long,
    isCurrentMonth: Boolean,
): Boolean {
    if (cached == null) return diaryCount > 0
    if (!isCurrentMonth) return false
    return cached.diaryCount != diaryCount || cached.latestDiaryUpdatedAt != latestDiaryUpdatedAt
}

fun diaryMonthFailedSummaryCanRetry(generatedAt: Long?, nowMs: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
    val lastAttempt = generatedAt ?: return true
    val lastDate = Instant.fromEpochMilliseconds(lastAttempt).toLocalDateTime(timeZone).date
    val currentDate = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(timeZone).date
    return currentDate != lastDate
}

fun buildDiaryMonthSummaryPrompt(
    monthKey: String,
    diaryTexts: List<String>,
    tags: List<String>,
    moods: List<String>,
): String {
    val body = diaryTexts.joinToString("\n---\n") { it.trim() }.take(DIARY_MONTH_PROMPT_MAX_LENGTH - 500)
    return """
        月份：$monthKey
        标签：${tags.joinToString("、").ifBlank { "无" }}
        心情：${moods.joinToString("、").ifBlank { "无" }}

        日记内容：
        $body

        请只输出一句中文自然语言总结，像写给用户自己的月度导语。不要 Markdown 标题，不要列表，不要编造日记里没有的事实。
    """.trimIndent().take(DIARY_MONTH_PROMPT_MAX_LENGTH)
}

private fun monthStart(monthKey: String): LocalDate {
    val year = monthKey.substringBefore("-").toInt()
    val month = monthKey.substringAfter("-").toInt()
    return LocalDate(year, month, 1)
}

private fun LocalDate.monthKey(): String = "$year-${monthNumber.toString().padStart(2, '0')}"
