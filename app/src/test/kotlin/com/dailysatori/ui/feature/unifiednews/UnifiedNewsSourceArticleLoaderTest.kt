package com.dailysatori.ui.feature.unifiednews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteArticleSyncService
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behaviour of the subscription list: it is a read-only view of stored rows. Nothing here can start a
 * download or write a row, which is what makes pull-to-refresh and list paging safe.
 */
class UnifiedNewsSourceArticleLoaderTest {
    @Test
    fun anEmptyLocalSourceReturnsTheEmptyPageAndWritesNothing() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "示例", "https://feed.test/feed", "", enabled = true)
            val sourceId = sources.getAll().single().id
            val syncRepo = RemoteArticleSyncRepository(db)
            val loader = UnifiedNewsSourceArticleLoader(sources, syncRepo)

            val page = loader.load(sourceId, offset = 0, pageSize = 30)

            assertEquals(emptyList(), page.articles)
            assertFalse(page.hasMore)
            assertFalse(page.missingSource)
            assertEquals(0L, syncRepo.count())
            assertEquals(0L, ArticleRepository(db).count())
        }
    }

    @Test
    fun storedArticlesArePagedWithoutWritingAnything() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "示例", "https://feed.test/feed", "", enabled = true)
            val sourceId = sources.getAll().single().id
            val syncRepo = RemoteArticleSyncRepository(db)
            val articleRepo = ArticleRepository(db)
            RemoteArticleSyncService(articleRepo, syncRepo).syncSourceArticles(
                remoteSourceId = sourceId,
                sourceDate = "2026-11-15",
                articles = (1..3).map { id ->
                    RemoteArticle(id = id.toLong(), title = "文章" + id, url = "https://feed.test/" + id, content = "正文")
                },
                now = 1_000L,
            )
            val articlesBefore = articleRepo.count()
            val loader = UnifiedNewsSourceArticleLoader(sources, syncRepo)

            val first = loader.load(sourceId, offset = 0, pageSize = 2)
            val second = loader.load(sourceId, offset = 2, pageSize = 2)

            assertEquals(2, first.articles.size)
            assertTrue(first.hasMore)
            assertEquals(1, second.articles.size)
            assertFalse(second.hasMore)
            assertEquals(articlesBefore, articleRepo.count(), "reading the list never stores an article")
        }
    }

    @Test
    fun aDeletedSourceIsReportedInsteadOfAnEmptyList() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            val loader = UnifiedNewsSourceArticleLoader(sources, RemoteArticleSyncRepository(db))

            val page = loader.load(sourceId = 99L, offset = 0, pageSize = 30)

            assertTrue(page.missingSource)
            assertEquals(emptyList(), page.articles)
        }
    }
}
