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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedNewsSummaryRepositoryTest {
    private val window = dailyUnifiedNewsWindowFor(Instant.parse("2026-10-02T10:00:00Z"), TimeZone.UTC)

    @Test
    fun batchedSourcesMatchIndividualQueriesAndRefreshReplacesOldReferences() = withRepository { repository ->
        assertTrue(repository.getSourcesBySummary().isEmpty())
        val firstSources = listOf("R2", "R1").map { key ->
            UnifiedNewsSourceItem(key, UnifiedNewsSourceType.REMOTE_ARTICLE, title = key, summary = key)
        }
        val first = requireNotNull(repository.saveSummaryWithSources(window, "first", "content", "success", null, null, 1L, firstSources))
        val previousWindow = dailyUnifiedNewsWindowFor(Instant.parse("2026-10-01T10:00:00Z"), TimeZone.UTC)
        val second = requireNotNull(repository.saveSummaryWithSources(previousWindow, "second", "content", "success", null, null, 1L, firstSources.take(1)))
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
    fun aWriteWithoutTheExecutionLeaseIsRefusedInsideTheSameTransaction() = withRepositoryAndTasks { repository, tasks ->
        val taskId = tasks.enqueue("remote_news_fetch", """{"mode":"due"}""", uniqueKey = "remote_news_fetch:due:1:news")
        assertTrue(tasks.claimForRun(taskId, "owner", Long.MAX_VALUE))
        val guard = { tasks.hasRunningLease(taskId, "owner") }

        assertTrue(
            repository.saveSummaryWithSources(window, "first", "内容一", "success", null, null, 1L,
                emptyList(), leaseGuard = guard) != null,
            "the current owner writes",
        )
        // The lease is gone before the second write: a late result must not replace the row.
        tasks.cancel(taskId)
        assertFalse(guard())
        assertNull(
            repository.saveSummaryWithSources(window, "second", "内容二", "success", null, null, 2L,
                emptyList(), leaseGuard = guard),
            "a run without the lease cannot write",
        )
        assertNull(
            repository.preserveExistingContent(window, "third", "failed", "失败", null, leaseGuard = guard),
            "the preserve path is guarded too",
        )
        val stored = requireNotNull(repository.getByWindow(window.summaryDate, window.key.value))
        assertEquals("first", stored.title)
        assertEquals("内容一", stored.content)
        assertEquals(1L, stored.generated_at)
    }

    @Test
    fun failedAndEmptyRefreshesKeepTheOriginalContentGenerationTime() = withRepository { repository ->
        for (status in listOf("failed", "empty")) {
            repository.upsertSummary(window, "原总结", "原内容", "success", null, null, 123L)
            assertEquals(true, repository.preserveExistingContent(window, "刷新结果", status, "无新内容", null))
            val retained = repository.getByWindow(window.summaryDate, window.key.value)!!

            assertEquals("原内容", retained.content)
            assertEquals(123L, retained.generated_at)
            assertEquals(status, retained.status)
            assertEquals("无新内容", retained.error_message)
        }
    }

    @Test
    fun legacySummaryWithoutGenerationTimeKeepsItsPreviousDisplayedTime() = withRepository { repository ->
        val original = requireNotNull(repository.upsertSummary(window, "旧总结", "旧内容", "success", null, null, null))
        assertEquals(true, repository.preserveExistingContent(window, "刷新失败", "failed", "离线", null))
        val retained = repository.getByWindow(window.summaryDate, window.key.value)!!

        assertEquals(original.updated_at, retained.generated_at)
    }

    @Test
    fun absentOrBlankSummaryDoesNotPretendThereIsCachedContent() = withRepository { repository ->
        assertEquals(false, repository.preserveExistingContent(window, "刷新失败", "failed", "离线", null))
        repository.upsertSummary(window, "无内容", "", "empty", null, null, null)
        assertEquals(false, repository.preserveExistingContent(window, "刷新失败", "failed", "离线", null))
    }

    private fun withRepositoryAndTasks(block: (UnifiedNewsSummaryRepository, AsyncTaskRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            block(UnifiedNewsSummaryRepository(db), AsyncTaskRepository(db))
        }
    }

    private fun withRepository(block: (UnifiedNewsSummaryRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(UnifiedNewsSummaryRepository(DailySatoriDatabase(driver)))
        }
    }
}
