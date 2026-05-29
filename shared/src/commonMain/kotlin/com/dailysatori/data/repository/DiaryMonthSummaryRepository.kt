package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Diary_month_summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class DiaryMonthSummaryRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Diary_month_summary>> =
        q.selectDiaryMonthSummaries().asFlow().mapToList(Dispatchers.IO)

    fun getByMonth(monthKey: String): Diary_month_summary? =
        q.selectDiaryMonthSummaryByMonth(monthKey).executeAsOneOrNull()

    fun upsert(
        monthKey: String,
        summary: String,
        diaryCount: Long,
        latestDiaryUpdatedAt: Long,
        status: String,
        errorMessage: String?,
        generatedAt: Long?,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.upsertDiaryMonthSummary(
            monthKey,
            summary,
            diaryCount,
            latestDiaryUpdatedAt,
            status,
            errorMessage,
            generatedAt,
            now,
            now,
        )
    }
}
