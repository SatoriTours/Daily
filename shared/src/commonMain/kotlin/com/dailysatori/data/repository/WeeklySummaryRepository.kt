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
        return getByWeekRange(weekStart, weekEnd) ?: run {
            insert(weekStart, weekEnd, "", 0, 0, 0, null, null, null, null)
            getByWeekRange(weekStart, weekEnd)!!
        }
    }
}
