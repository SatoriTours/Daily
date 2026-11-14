package com.dailysatori.service.remotenews

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteArticleSyncBatchTest {
    @Test
    fun cancellingBetweenArticlesKeepsCommittedArticlesAndPublishesNoHalfWrittenOne() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            var inserts = 0
            val interrupted = object : SqlDriver by driver {
                override fun execute(identifier: Int?, sql: String, parameters: Int,
                    binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
                    if (sql.startsWith("INSERT INTO article (") && ++inserts == 2) {
                        throw CancellationException("Cancelled page sync")
                    }
                    return driver.execute(identifier, sql, parameters, binders)
                }
            }
            val db = DailySatoriDatabase(interrupted)
            db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, 1, 1)
            val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsOne().id
            val articleRepo = ArticleRepository(db)
            val syncRepo = RemoteArticleSyncRepository(db)
            val articles = (1L..3L).map { RemoteArticle(it, "标题", "https://example.com/$it", content = "正文") }
            val query = db.dailySatoriQueries.selectRemoteArticleSyncItemsBySource(sourceId, 50, 0)
            var notifications = 0
            val listener = object : Query.Listener {
                override fun queryResultsChanged() { notifications++ }
            }
            query.addListener(listener)
            try {
                // Cancellation now lands at the second article's own transaction: the first article keeps its
                // committed article+mapping+snapshot, the interrupted one is rolled back whole.
                assertFailsWith<CancellationException> {
                    RemoteArticleSyncService(articleRepo, syncRepo)
                        .syncSourceArticles(sourceId, "2026-06-22", articles, 1)
                }
                assertEquals(2, inserts)
                assertEquals(1L, syncRepo.countBySource(sourceId))
                assertNotNull(articleRepo.getByUrl("https://example.com/1"))
                assertNull(articleRepo.getByUrl("https://example.com/2"))
                assertEquals(1, notifications)
            } finally {
                query.removeListener(listener)
            }
        }
    }

    @Test
    fun oneRejectedMappingDoesNotDiscardOtherArticlesInThePage() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, 1, 1)
            val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsOne().id
            driver.execute(null, """
                CREATE TRIGGER reject_mapping BEFORE INSERT ON remote_article_sync_item
                WHEN NEW.remote_article_id = 2 BEGIN SELECT RAISE(ABORT, "bad article"); END;
            """.trimIndent(), 0)
            val syncRepo = RemoteArticleSyncRepository(db)
            val articles = (1L..3L).map { RemoteArticle(it, "标题", "https://example.com/$it", content = "正文") }

            val result = RemoteArticleSyncService(ArticleRepository(db), syncRepo)
                .syncSourceArticles(sourceId, "2026-06-22", articles, 1)

            assertEquals(2, result.inserted)
            assertEquals(1, result.skipped)
            assertEquals(1, result.failures.size)
            assertEquals(setOf(1L, 3L), syncRepo.getMappingsBySourceDate(sourceId, "2026-06-22")
                .map { it.remote_article_id }.toSet())
        }
    }

    @Test
    fun syncingOnePagePublishesEveryCommittedArticleAndEndsWithTheFullPage() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val now = 1_800_000_000_000L
            db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, now, now)
            val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsOne().id
            val syncRepo = RemoteArticleSyncRepository(db)
            val service = RemoteArticleSyncService(ArticleRepository(db), syncRepo)
            val articles = (1L..50L).map { id ->
                RemoteArticle(id, "标题 $id", "https://example.com/$id", "摘要 $id", content = "完整正文 $id")
            }
            val query = db.dailySatoriQueries.selectRemoteArticleSyncItemsBySource(sourceId, 50, 0)
            val observedPageSizes = mutableListOf<Int>()
            val listener = object : Query.Listener {
                override fun queryResultsChanged() {
                    observedPageSizes += query.executeAsList().size
                }
            }
            query.addListener(listener)
            try {
                val inserted = service.syncSourceArticles(sourceId, "2026-06-22", articles, now)

                assertEquals(50, inserted.inserted)
                assertTrue(inserted.failures.isEmpty())
                // Per-article commits publish each committed article; durability wins over a single page-wide
                // notification, and the final notification always carries the full page.
                assertTrue(observedPageSizes.isNotEmpty(), "each committed article is published")
                assertEquals(50, observedPageSizes.last(), "the full page is visible once the source completes")
                assertEquals(50, syncRepo.getArticlesBySource(sourceId, 50, 0).size)

                observedPageSizes.clear()
                val updated = service.syncSourceArticles(sourceId, "2026-06-22",
                    articles.map { it.copy(content = "更新正文 ${it.id}") }, now + 1_000)

                assertEquals(50, updated.updated)
                assertTrue(updated.failures.isEmpty())
                assertEquals(50, observedPageSizes.last())
                assertTrue(syncRepo.getArticlesBySource(sourceId, 50, 0)
                    .all { it.original_markdown_content.orEmpty().contains("更新正文") })
            } finally {
                query.removeListener(listener)
            }
        }
    }
}
