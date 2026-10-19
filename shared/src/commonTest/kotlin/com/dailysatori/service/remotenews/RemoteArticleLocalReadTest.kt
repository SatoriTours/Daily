package com.dailysatori.service.remotenews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RemoteArticleLocalReadTest {
    @Test fun uncachedArticleIsSavedBeforeReadingAndThenWorksOffline() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val articles = ArticleRepository(db)
            val mappings = RemoteArticleSyncRepository(db)
            val service = RemoteArticleSyncService(articles, mappings)
            db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "", 1, 1, 1)
            var downloads = 0
            val saved = service.readLocalArticle(1, 42, "2026-10-19") {
                downloads++
                RemoteArticle(id = 42, title = "远程文章", url = "https://example.com/42", summary = "这不是原文的摘要", content = "# 已下载的完整原文")
            }
            assertEquals(1, downloads)
            assertNotNull(articles.getById(saved.id))
            assertEquals("remote_news", saved.source_type)
            assertEquals(saved.id, mappings.findByRemoteIdentity(1, 42)?.article_id)
            articles.toggleFavorite(saved.id)
            val offline = service.readLocalArticle(1, 42, "2026-10-19") { error("不应访问网络") }
            assertEquals(1L, offline.is_favorite)
            assertEquals(saved.original_markdown_content, offline.original_markdown_content)
            assertEquals(listOf(saved.id), articles.getCardsSync(favoritesOnly = true).map { it.id })
            assertTrue(articles.getCardsSync().isEmpty())
            articles.recordManualIntake(saved.url!!)
            assertEquals("# 已下载的完整原文", articles.getById(saved.id)?.original_markdown_content)
            assertEquals(listOf(saved.id), articles.getCardsSync().map { it.id })
            assertEquals(listOf(saved.id), mappings.getArticlesBySource(1, 50, 0).map { it.id })
        }
    }

    @Test fun sourceScopedIdsDoNotReadAnotherSourcesArticleAndPagingDeduplicatesMappings() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val articles = ArticleRepository(db)
            val mappings = RemoteArticleSyncRepository(db)
            val service = RemoteArticleSyncService(articles, mappings)
            db.dailySatoriQueries.insertRemoteNewsSource("One", "https://one.example", "", 1, 1, 1)
            db.dailySatoriQueries.insertRemoteNewsSource("Two", "https://two.example", "", 1, 1, 1)
            val one = service.readLocalArticle(1, 7, "2026-10-19") {
                RemoteArticle(id = 7, title = "One", url = "https://example.com/one", content = "One body")
            }
            val two = service.readLocalArticle(2, 7, "2026-10-19") {
                RemoteArticle(id = 7, title = "Two", url = "https://example.com/two", content = "Two body")
            }
            assertNotEquals(one.id, two.id)
            mappings.upsertMapping(1, 8, one.id, one.url, "2026-10-19", 2)
            assertEquals(listOf(one.id), mappings.getArticlesBySource(1, 50, 0).map { it.id })
            assertEquals(listOf(two.id), mappings.getArticlesBySource(2, 50, 0).map { it.id })
        }
    }
}
