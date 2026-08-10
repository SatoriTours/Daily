package com.dailysatori.service.remotenews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.REMOTE_PROCESSING_PENDING
import com.dailysatori.data.repository.REMOTE_PROCESSING_STALE
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            assertTrue(saved.original_markdown_content.orEmpty().contains("# Remote original"))
            assertEquals(0L, saved.is_favorite)
            assertEquals(REMOTE_PROCESSING_PENDING, syncRepo.findByArticleId(saved.id)?.processing_state)
        }
    }

    @Test
    fun changedRemoteSourceKeepsLastChineseResultAndMarksItStale() {
        withSyncService { db, articleRepo, syncRepo ->
            val now = 1_800_000_000_000
            db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, now, now)
            val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsList().single().id
            val service = RemoteArticleSyncService(articleRepo, syncRepo)
            val first = RemoteArticle(42, "English title", "https://example.com/revision", "English summary", content = "English body")
            service.syncSourceArticles(sourceId, "2026-06-22", listOf(first), now)
            val saved = syncRepo.getArticlesBySourceDate(sourceId, "2026-06-22").single()
            val firstHash = syncRepo.findByArticleId(saved.id)!!.source_content_hash
            assertTrue(syncRepo.markProcessingSuccess(saved.id, firstHash, "article-general-v2"))
            articleRepo.update(
                id = saved.id,
                title = saved.title,
                aiTitle = "中文标题",
                aiContent = "中文总结",
                aiMarkdownContent = "中文正文",
                url = saved.url,
                isFavorite = 1,
                comment = saved.comment,
                status = "completed",
                coverImage = saved.cover_image,
                coverImageUrl = saved.cover_image_url,
                pubDate = saved.pub_date,
            )

            val changed = first.copy(summary = "Changed English summary", content = "Changed English body")
            service.syncSourceArticles(sourceId, "2026-06-22", listOf(changed), now + 1_000)

            val refreshed = articleRepo.getById(saved.id)!!
            val mapping = syncRepo.findByArticleId(saved.id)!!
            assertEquals("中文总结", refreshed.ai_content)
            assertEquals("中文正文", refreshed.ai_markdown_content)
            assertTrue(refreshed.original_markdown_content.orEmpty().contains("Changed English body"))
            assertEquals(REMOTE_PROCESSING_STALE, mapping.processing_state)
            assertEquals(firstHash, mapping.processed_content_hash)
            assertFalse(mapping.source_content_hash == mapping.processed_content_hash)
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

    @Test
    fun favoritingPendingEnglishSnapshotRequestsGeneralArticleProcessing() {
        withSyncService { db, articleRepo, syncRepo ->
            val now = 1_800_000_000_000
            db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, now, now)
            val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsList().single().id
            val article = RemoteArticle(
                id = 42,
                title = "New model release",
                url = "https://example.com/pending",
                summary = "Faster coding tools for software teams",
                content = "The release improves reliability for developers.",
            )
            RemoteArticleSyncService(articleRepo, syncRepo)
                .syncSourceArticles(sourceId, "2026-06-22", listOf(article), now)
            val saved = syncRepo.getArticlesBySourceDate(sourceId, "2026-06-22").single()

            val result = RemoteArticleFavoriteService(articleRepo, syncRepo).toggleFavorite(article, saved.id)

            assertTrue(result.isFavorite)
            assertTrue(result.needsReprocessing)
            assertTrue(syncRepo.findByArticleId(saved.id)?.favorited_at != null)
        }
    }

    private fun withSyncService(
        test: suspend (DailySatoriDatabase, ArticleRepository, RemoteArticleSyncRepository) -> Unit,
    ) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        test(db, ArticleRepository(db), RemoteArticleSyncRepository(db))
        driver.close()
    }
}
