package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Weekly_summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class WeeklySummaryRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Weekly_summary>> =
        q.selectWeeklySummaries().asFlow().mapToList(Dispatchers.IO)

    fun getLatest() = q.selectLatestWeeklySummary().executeAsOneOrNull()

    fun getByWeekRange(startMs: Long, endMs: Long) =
        q.selectWeeklySummaryByWeekRange(startMs, endMs).executeAsOneOrNull()

    fun insert(
        weekStartDate: Long,
        weekEndDate: Long,
        content: String,
        articleCount: Long,
        diaryCount: Long,
        viewpointCount: Long,
        articleIds: String?,
        diaryIds: String?,
        viewpointIds: String?,
        appIdeas: String?,
        status: String = "pending",
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertWeeklySummary(
            weekStartDate, weekEndDate, content, articleCount, diaryCount,
            viewpointCount, articleIds, diaryIds, viewpointIds, appIdeas, status, now, now,
        )
    }

    fun update(
        id: Long,
        content: String,
        articleCount: Long,
        diaryCount: Long,
        viewpointCount: Long,
        articleIds: String?,
        diaryIds: String?,
        viewpointIds: String?,
        appIdeas: String?,
        status: String,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateWeeklySummary(
            content, articleCount, diaryCount, viewpointCount,
            articleIds, diaryIds, viewpointIds, appIdeas, status, now, id,
        )
    }

    fun getOrCreate(weekStart: Long, weekEnd: Long): Weekly_summary {
        return db.transactionWithResult {
            getByWeekRange(weekStart, weekEnd) ?: run {
                insert(weekStart, weekEnd, "", 0, 0, 0, null, null, null, null)
                checkNotNull(getByWeekRange(weekStart, weekEnd))
            }
        }
    }

    fun claimGeneration(id: Long, staleAfterMs: Long = 30 * 60 * 1000L): Boolean = db.transactionWithResult {
        val current = q.selectWeeklySummaries().executeAsList().firstOrNull { it.id == id } ?: return@transactionWithResult false
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if (current.status == "generating" && current.updated_at > now - staleAfterMs) {
            return@transactionWithResult false
        }
        q.updateWeeklySummary(
            current.content,
            current.article_count,
            current.diary_count,
            current.viewpoint_count,
            current.article_ids,
            current.diary_ids,
            current.viewpoint_ids,
            current.app_ideas,
            "generating",
            now,
            id,
        )
        true
    }
}
