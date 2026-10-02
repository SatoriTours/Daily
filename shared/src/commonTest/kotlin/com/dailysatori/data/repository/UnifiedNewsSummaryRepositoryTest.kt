package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
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
