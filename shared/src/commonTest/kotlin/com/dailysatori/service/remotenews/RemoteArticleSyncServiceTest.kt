package com.dailysatori.service.remotenews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RemoteArticleSyncServiceTest {
    @Test
    fun syncStoresRemoteContentAsOriginalMarkdownWithoutFavoriting() {
        withSyncService { db, articleRepo, syncRepo ->
            val now = 1_800_000_000_000
            db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, now, now)
            val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsList().single().id
            val article = RemoteArticle(
                id = 42,
                title = "Remote title",
                url = "https://example.com/remote",
                summary = "Remote summary",
                viewpoints = listOf("Point A"),
                content = "# Remote original",
                publishedAt = "2026-06-22T01:00:00Z",
            )
            val service = RemoteArticleSyncService(articleRepo, syncRepo)

            val result = service.syncSourceArticles(sourceId, "2026-06-22", listOf(article), now)

            assertEquals(1, result.inserted)
            assertEquals(0, result.updated)
            val saved = syncRepo.getArticlesBySourceDate(sourceId, "2026-06-22").single()
            assertEquals("Remote summary\n\n## 关键观点\n\n- Point A", saved.ai_content)
            assertEquals("# Remote original", saved.ai_markdown_content)
            assertEquals(0L, saved.is_favorite)
        }
    }

    @Test
    fun syncNeverFallsBackSummaryIntoOriginalMarkdownAndPreservesFavorite() {
        withSyncService { db, articleRepo, syncRepo ->
            val now = 1_800_000_000_000
            db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, now, now)
            val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsList().single().id
            val articleId = articleRepo.insert(
                title = "Old title",
                aiContent = "Old summary",
                aiMarkdownContent = "Old summary",
                url = "https://example.com/remote",
                isFavorite = 1,
                status = "completed",
            )
            db.dailySatoriQueries.updateArticleSourceType("remote_news", now, articleId)
            syncRepo.upsertMapping(sourceId, 42, articleId, "https://example.com/remote", "2026-06-22", now)
            val article = RemoteArticle(
                id = 42,
                title = "Remote title",
                url = "https://example.com/remote",
                summary = "New summary",
                content = null,
            )
            val service = RemoteArticleSyncService(articleRepo, syncRepo)

            val result = service.syncSourceArticles(sourceId, "2026-06-22", listOf(article), now + 1_000)

            assertEquals(0, result.inserted)
            assertEquals(1, result.updated)
            val saved = articleRepo.getById(articleId)!!
            assertEquals(1L, saved.is_favorite)
            assertEquals("Old summary", saved.ai_markdown_content)
            assertFalse(saved.ai_markdown_content == article.summary)
        }
    }

    private fun withSyncService(test: (DailySatoriDatabase, ArticleRepository, RemoteArticleSyncRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        test(db, ArticleRepository(db), RemoteArticleSyncRepository(db))
        driver.close()
    }
}
