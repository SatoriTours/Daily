package com.dailysatori.service.weekly

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.AiService
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
        return Pair(
            lastMonday.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            lastSunday.atTime(23, 59, 59).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
        )
    }

    suspend fun checkAndGenerate(): Boolean {
        val range = getLastCompletedWeekRange() ?: return false
        val existing = weeklySummaryRepo.getByWeekRange(range.first, range.second)
        return existing != null
    }

    suspend fun generateWeeklySummary(weekStartMs: Long, weekEndMs: Long): Boolean {
        return try {
            val summary = weeklySummaryRepo.getOrCreate(weekStartMs, weekEndMs)
            log.i { "Weekly summary generated for week starting $weekStartMs" }
            true
        } catch (e: Exception) {
            log.e(e) { "Failed to generate weekly summary" }
            false
        }
    }

    fun getLatest() = weeklySummaryRepo.getLatest()
    fun getAll() = weeklySummaryRepo.getAll()
}
