package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteArticleSyncService
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemoteArticleSyncRepositoryTest {
    @Test
    fun upsertsSourceScopedMappingAndReadsArticlesForSourceDate() = withSyncRepositories { db, articleRepo, syncRepo ->
        val now = 1_800_000_000_000
        db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, now, now)
        val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsList().single().id
        val articleId = articleRepo.insert(
            title = "Remote title",
            aiContent = "Remote summary",
            aiMarkdownContent = "# Remote original",
            url = "https://example.com/remote",
            isFavorite = 0,
            status = "completed",
        )

        syncRepo.upsertMapping(
            remoteSourceId = sourceId,
            remoteArticleId = 42,
            articleId = articleId,
            url = "https://example.com/remote",
            sourceDate = "2026-06-22",
            now = now,
        )
        syncRepo.upsertMapping(
            remoteSourceId = sourceId,
            remoteArticleId = 42,
            articleId = articleId,
            url = "https://example.com/remote",
            sourceDate = "2026-06-22",
            now = now + 1_000,
        )

        val mapping = syncRepo.findByRemoteIdentity(sourceId, 42)
        assertNotNull(mapping)
        assertEquals(articleId, mapping.article_id)
        assertEquals(now, mapping.first_seen_at)
        assertEquals(now + 1_000, mapping.last_seen_at)

        val articles = syncRepo.getArticlesBySourceDate(sourceId, "2026-06-22")
        assertEquals(listOf(articleId), articles.map { it.id })
        assertEquals("# Remote original", articles.single().ai_markdown_content)
    }

    @Test
    fun readsSyncedRemoteArticlesBySourceAcrossDatesWithPaging() = withSyncRepositories { db, articleRepo, syncRepo ->
        val now = 1_800_000_000_000
        db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, now, now)
        val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsList().single().id
        val olderId = articleRepo.insert(
            title = "Older remote",
            aiContent = "Older summary",
            aiMarkdownContent = "# Older",
            url = "https://example.com/older",
            isFavorite = 0,
            status = "completed",
            pubDate = now - 86_400_000,
        )
        val newerId = articleRepo.insert(
            title = "Newer remote",
            aiContent = "Newer summary",
            aiMarkdownContent = "# Newer",
            url = "https://example.com/newer",
            isFavorite = 0,
            status = "completed",
            pubDate = now,
        )

        syncRepo.upsertMapping(sourceId, 41, olderId, "https://example.com/older", "2026-06-21", now)
        syncRepo.upsertMapping(sourceId, 42, newerId, "https://example.com/newer", "2026-06-22", now + 1_000)

        assertEquals(listOf(newerId), syncRepo.getArticlesBySource(sourceId, limit = 1, offset = 0).map { it.id })
        assertEquals(listOf(olderId), syncRepo.getArticlesBySource(sourceId, limit = 1, offset = 1).map { it.id })
    }

    @Test
    fun syncedRemoteArticlesKeepOriginalMarkdownAndStayOutOfLocalArticleList() = withSyncRepositories { db, articleRepo, syncRepo ->
        val now = 1_800_000_000_000
        db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, now, now)
        val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsList().single().id

        RemoteArticleSyncService(articleRepo, syncRepo).syncSourceArticles(
            remoteSourceId = sourceId,
            sourceDate = "2026-06-22",
            articles = listOf(
                RemoteArticle(
                    id = 42,
                    title = "Remote title",
                    url = "https://example.com/remote-sync",
                    summary = "Remote summary",
                    content = "# Remote original",
                ),
            ),
            now = now,
        )

        val synced = syncRepo.getArticlesBySourceDate(sourceId, "2026-06-22").single()
        assertEquals("remote_news", synced.source_type)
        assertTrue(synced.original_markdown_content.orEmpty().contains("# Remote original"))
        assertEquals("# Remote original", synced.ai_markdown_content)
        assertEquals(emptyList(), articleRepo.getLocalSync().map { it.id })
    }

    @Test
    fun syncingRemoteArticleWithExistingLocalUrlDoesNotHideLocalArticle() = withSyncRepositories { db, articleRepo, syncRepo ->
        val now = 1_800_000_000_000
        db.dailySatoriQueries.insertRemoteNewsSource("Tech", "https://remote.example", "token", 1, now, now)
        val sourceId = db.dailySatoriQueries.selectRemoteNewsSources().executeAsList().single().id
        val localArticleId = articleRepo.insert(
            title = "Local title",
            aiContent = "Local summary",
            aiMarkdownContent = "# Local original",
            url = "https://example.com/already-local",
            isFavorite = 0,
            status = "completed",
        )

        RemoteArticleSyncService(articleRepo, syncRepo).syncSourceArticles(
            remoteSourceId = sourceId,
            sourceDate = "2026-06-22",
            articles = listOf(
                RemoteArticle(
                    id = 43,
                    title = "Remote title",
                    url = "https://example.com/already-local",
                    summary = "Remote summary",
                    content = "# Remote original",
                ),
            ),
            now = now,
        )

        val article = articleRepo.getById(localArticleId)!!
        assertEquals("local", article.source_type)
        assertEquals(listOf(localArticleId), articleRepo.getLocalSync().map { it.id })
        assertEquals(localArticleId, syncRepo.findByRemoteIdentity(sourceId, 43)!!.article_id)
    }

    private fun withSyncRepositories(test: (DailySatoriDatabase, ArticleRepository, RemoteArticleSyncRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        test(db, ArticleRepository(db), RemoteArticleSyncRepository(db))
        driver.close()
    }
}
