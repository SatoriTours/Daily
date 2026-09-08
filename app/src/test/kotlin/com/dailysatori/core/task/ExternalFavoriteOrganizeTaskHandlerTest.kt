package com.dailysatori.core.task

import android.content.ContextWrapper
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.data.repository.*
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.externalfavorites.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ExternalFavoriteOrganizeTaskHandlerTest {
    @Test
    fun requestDuringRunningBatchQueuesOneDurableFollowup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val tasks = AsyncTaskRepository(DailySatoriDatabase(driver))
            val scheduler = ExternalFavoriteSyncScheduler(ContextWrapper(null), tasks)
            val running = scheduler.enqueueOrganization(2)!!
            assertTrue(tasks.claimForRun(running, "test", Long.MAX_VALUE))
            val next = scheduler.enqueueOrganization(2)!!
            assertNotEquals(running, next)
            assertTrue(tasks.waitingForPredecessor(next))
            assertEquals(next, scheduler.enqueueOrganization(2))
            assertEquals(next, scheduler.enqueueOrganization(2, afterTaskId = running))
        } finally {
            driver.close()
        }
    }

    @Test
    fun batchesPersistResultsAndQueueOnlyRemainingItems() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = ExternalFavoriteSourceRepository(db, { it }, { it })
            val items = ExternalFavoriteItemRepository(db)
            val articles = ArticleRepository(db)
            val tasks = AsyncTaskRepository(db)
            val sourceId = sources.save(provider = "x", displayName = "test", accountId = "account", accountName = "account", authJson = "{}")
            repeat(21) { index ->
                items.upsertDraft(sourceId, ExternalFavoriteItemDraft("x", "$index", "https://x.com/test/status/$index",
                    "收藏", "正文 $index", "test", null, null, "{}", contentHash = "$index", aiInputHash = "$index"))
            }
            ExternalFavoriteImporter(items, articles).importPendingForSource(sourceId, 30)
            val scheduler = ExternalFavoriteSyncScheduler(ContextWrapper(null), tasks)
            val taskId = scheduler.enqueueOrganization(sourceId)!!
            assertEquals(taskId, scheduler.enqueueOrganization(sourceId))
            val handler = ExternalFavoriteOrganizeTaskHandler(
                ExternalFavoriteAiOrganizer(items, articles, generateAnalysis = { ExternalFavoriteAiAnalysis("标题", "摘要", "正文") }),
                items, sources, scheduler, NoopFavoriteSyncHttpLogger,
            )
            val progress = mutableListOf<Long>()
            val reporter = object : AsyncTaskProgressReporter {
                override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
                    progress += current
                }
            }
            assertIs<AsyncTaskExecutionResult.Success>(handler.execute(taskId, """{"sourceId":$sourceId}""", "", reporter))
            assertEquals(20, items.getBySource(sourceId).count { it.ai_status == "completed" })
            assertEquals(1, items.pendingAiBySource(sourceId, 30).size)
            assertEquals((0L..20L).toList() + 20L, progress)
            val next = tasks.runnableTasksByType(handler.type, Long.MAX_VALUE, 10).first { it.id != taskId }
            assertTrue(tasks.waitingForPredecessor(next.id))
            assertTrue(next.payload_json.contains("_afterTaskId"))
            handler.execute(next.id, """{"sourceId":$sourceId}""", "", reporter)
            assertEquals(21, items.getBySource(sourceId).count { it.ai_status == "completed" })
        } finally {
            driver.close()
        }
    }
}
