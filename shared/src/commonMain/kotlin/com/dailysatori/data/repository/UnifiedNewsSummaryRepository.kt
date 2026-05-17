package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.service.unifiednews.UnifiedNewsSourceItem
import com.dailysatori.service.unifiednews.UnifiedNewsWindow
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class UnifiedNewsSummaryRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Unified_news_summary>> =
        q.selectUnifiedNewsSummaries().asFlow().mapToList(Dispatchers.IO)

    fun getLatestSuccessful(): Unified_news_summary? =
        q.selectLatestSuccessfulUnifiedNewsSummary().executeAsOneOrNull()

    fun getByWindow(summaryDate: String, windowKey: String): Unified_news_summary? =
        q.selectUnifiedNewsSummaryByWindow(summaryDate, windowKey).executeAsOneOrNull()

    fun getByDate(summaryDate: String): Unified_news_summary? =
        q.selectUnifiedNewsSummaryByDate(summaryDate).executeAsOneOrNull()

    fun getByDateFlow(summaryDate: String): Flow<Unified_news_summary?> =
        q.selectUnifiedNewsSummaryByDate(summaryDate).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun getSources(summaryId: Long): List<Unified_news_source> =
        q.selectUnifiedNewsSources(summaryId).executeAsList()

    fun upsertSummary(
        window: UnifiedNewsWindow,
        title: String,
        content: String,
        status: String,
        errorMessage: String?,
        sourceWarnings: String?,
        generatedAt: Long?,
    ): Unified_news_summary {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        insertOrUpdateSummary(window, title, content, status, errorMessage, sourceWarnings, generatedAt, now)
        return requireNotNull(getByWindow(window.summaryDate, window.key.value))
    }

    fun saveSummaryWithSources(
        window: UnifiedNewsWindow,
        title: String,
        content: String,
        status: String,
        errorMessage: String?,
        sourceWarnings: String?,
        generatedAt: Long?,
        sources: List<UnifiedNewsSourceItem>,
    ): Unified_news_summary {
        var saved: Unified_news_summary? = null
        q.transaction {
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            insertOrUpdateSummary(window, title, content, status, errorMessage, sourceWarnings, generatedAt, now)
            val summary = requireNotNull(q.selectUnifiedNewsSummaryByWindow(window.summaryDate, window.key.value).executeAsOneOrNull())
            q.deleteUnifiedNewsSources(summary.id)
            insertSources(summary.id, sources)
            saved = summary
        }
        return requireNotNull(saved)
    }

    private fun insertOrUpdateSummary(
        window: UnifiedNewsWindow,
        title: String,
        content: String,
        status: String,
        errorMessage: String?,
        sourceWarnings: String?,
        generatedAt: Long?,
        now: Long,
    ) {
        val existing = q.selectUnifiedNewsSummaryByWindow(window.summaryDate, window.key.value).executeAsOneOrNull()
        if (existing == null) {
            q.insertUnifiedNewsSummary(
                window.summaryDate,
                window.key.value,
                window.startMs,
                window.endMs,
                title,
                content,
                status,
                errorMessage,
                sourceWarnings,
                generatedAt,
                now,
                now,
            )
            return
        }
        q.updateUnifiedNewsSummary(
            window.startMs,
            window.endMs,
            title,
            content,
            status,
            errorMessage,
            sourceWarnings,
            generatedAt,
            now,
            window.summaryDate,
            window.key.value,
        )
    }

    private fun insertSources(summaryId: Long, sources: List<UnifiedNewsSourceItem>) {
        sources.forEach { source ->
            q.insertUnifiedNewsSource(
                summaryId,
                source.refKey,
                source.sourceType.dbValue,
                source.sourceId,
                source.sourceFilename,
                source.sourceUrl,
                source.title,
                source.summary,
                source.sourceTime,
            )
        }
    }
}
