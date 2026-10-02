package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
import com.dailysatori.service.unifiednews.UnifiedNewsSourceItem
import com.dailysatori.service.unifiednews.UnifiedNewsSourceType
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedNewsSummaryRepositoryTest {
    private val window = dailyUnifiedNewsWindowFor(Instant.parse("2026-10-02T10:00:00Z"), TimeZone.UTC)

    @Test
    fun batchedSourcesMatchIndividualQueriesAndRefreshReplacesOldReferences() = withRepository { repository ->
        assertTrue(repository.getSourcesBySummary().isEmpty())
        val firstSources = listOf("R2", "R1").map { key ->
            UnifiedNewsSourceItem(key, UnifiedNewsSourceType.REMOTE_ARTICLE, title = key, summary = key)
        }
        val first = repository.saveSummaryWithSources(window, "first", "content", "success", null, null, 1L, firstSources)
        val previousWindow = dailyUnifiedNewsWindowFor(Instant.parse("2026-10-01T10:00:00Z"), TimeZone.UTC)
        val second = repository.saveSummaryWithSources(previousWindow, "second", "content", "success", null, null, 1L, firstSources.take(1))
        val batch = repository.getSourcesBySummary()
        assertEquals(repository.getSources(first.id), batch[first.id])
        assertEquals(repository.getSources(second.id), batch[second.id])
        assertEquals(listOf("R2", "R1"), batch.getValue(first.id).map { it.ref_key })

        repository.saveSummaryWithSources(window, "updated", "content", "success", null, null, 2L, emptyList())
        val updated = repository.getSourcesBySummary()
        assertTrue(updated[first.id].isNullOrEmpty())
        assertEquals(repository.getSources(second.id), updated[second.id])
    }

    @Test
    fun failedAndEmptyRefreshesKeepTheOriginalContentGenerationTime() = withRepository { repository ->
        for (status in listOf("failed", "empty")) {
            repository.upsertSummary(window, "原总结", "原内容", "success", null, null, 123L)
            assertTrue(repository.preserveExistingContent(window, "刷新结果", status, "无新内容", null))
            val retained = repository.getByWindow(window.summaryDate, window.key.value)!!

            assertEquals("原内容", retained.content)
            assertEquals(123L, retained.generated_at)
            assertEquals(status, retained.status)
            assertEquals("无新内容", retained.error_message)
        }
    }

    @Test
    fun legacySummaryWithoutGenerationTimeKeepsItsPreviousDisplayedTime() = withRepository { repository ->
        val original = repository.upsertSummary(window, "旧总结", "旧内容", "success", null, null, null)
        assertTrue(repository.preserveExistingContent(window, "刷新失败", "failed", "离线", null))
        val retained = repository.getByWindow(window.summaryDate, window.key.value)!!

        assertEquals(original.updated_at, retained.generated_at)
    }

    @Test
    fun absentOrBlankSummaryDoesNotPretendThereIsCachedContent() = withRepository { repository ->
        assertFalse(repository.preserveExistingContent(window, "刷新失败", "failed", "离线", null))
        repository.upsertSummary(window, "无内容", "", "empty", null, null, null)
        assertFalse(repository.preserveExistingContent(window, "刷新失败", "failed", "离线", null))
    }

    private fun withRepository(block: (UnifiedNewsSummaryRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(UnifiedNewsSummaryRepository(DailySatoriDatabase(driver)))
        }
    }
}
