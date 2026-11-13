package com.dailysatori.service.remotenews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Switching a source from one feed URL to another must never delete already synced history. */
class RemoteRssSourcePreservationTest {
    @Test
    fun syncedHistorySurvivesFeedSwitchBrokenFeedAndShorterFeed() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val sources = RemoteNewsSourceRepository(db)
        val articles = ArticleRepository(db)
        val mappings = RemoteArticleSyncRepository(db)
        val sync = RemoteArticleSyncService(articles, mappings)
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/feed-v1" -> respond(FEED_V1, HttpStatusCode.OK, RSS_HEADERS)
                "/feed-v2" -> respond(FEED_V2, HttpStatusCode.OK, RSS_HEADERS)
                "/feed-empty" -> respond(
                    """<rss version="2.0"><channel><title>空</title></channel></rss>""",
                    HttpStatusCode.OK,
                    RSS_HEADERS,
                )
                else -> respond("gone", HttpStatusCode.InternalServerError)
            }
        })
        try {
            sources.save(null, "Tech", "$BASE/feed-v1", "", true)
            val source = sources.getEnabled().single()
            val remote = RemoteNewsService(client)

            val first = syncFeed(remote, sync, source)
            assertEquals(2, first.inserted)
            assertEquals(2L, mappings.countBySource(source.id))
            val initial = mappings.getArticlesBySource(source.id, 50, 0)
            val favorite = initial.first { it.title == "A" }
            db.dailySatoriQueries.markArticleFavoriteById(NOW, favorite.id)

            // Same feed again: nothing is duplicated and the favorite survives.
            val repeated = syncFeed(remote, sync, source)
            assertEquals(0, repeated.inserted)
            assertEquals(2L, mappings.countBySource(source.id))
            assertEquals(1L, articles.getById(favorite.id)?.is_favorite)

            // A broken feed after the user edits the URL keeps every synced article.
            sources.save(source.id, "Tech", "$BASE/broken", "", true)
            val broken = remote.fetchSourceArticles(config(sources.getById(source.id)!!))
            assertTrue(broken is RemoteNewsResult.Failure)
            assertEquals(2L, mappings.countBySource(source.id))
            assertTrue(articles.getById(favorite.id)?.ai_markdown_content.orEmpty().contains("正文A"))
            assertEquals(1L, articles.getById(favorite.id)?.is_favorite)

            // A new feed missing the old entries adds its own article and keeps the history.
            sources.save(source.id, "Tech", "$BASE/feed-v2", "", true)
            val switched = syncFeed(remote, sync, sources.getById(source.id)!!)
            assertEquals(1, switched.inserted)
            assertEquals(3L, mappings.countBySource(source.id))
            val titles = mappings.getArticlesBySource(source.id, 50, 0).map { it.title }
            assertEquals(setOf("A", "B", "C"), titles.toSet())
            val preserved = articles.getById(favorite.id)
            assertEquals("A", preserved?.title)
            assertEquals(1L, preserved?.is_favorite)
            assertTrue(preserved?.ai_markdown_content.orEmpty().contains("正文A"))

            // An empty (but valid) feed adds nothing and still deletes nothing.
            sources.save(source.id, "Tech", "$BASE/feed-empty", "", true)
            val emptySync = syncFeed(remote, sync, sources.getById(source.id)!!)
            assertEquals(0, emptySync.inserted)
            assertEquals(3L, mappings.countBySource(source.id))
            assertEquals(setOf("A", "B", "C"), mappings.getArticlesBySource(source.id, 50, 0).map { it.title }.toSet())
        } finally {
            client.close()
            driver.close()
        }
    }

    private suspend fun syncFeed(
        remote: RemoteNewsService,
        sync: RemoteArticleSyncService,
        source: com.dailysatori.shared.db.Remote_news_source,
    ): RemoteArticleSyncResult {
        val fetched = remote.fetchSourceArticles(config(source))
        assertTrue(fetched is RemoteNewsResult.Success, "fetch failed: " + (fetched as? RemoteNewsResult.Failure)?.message.orEmpty())
        return sync.syncSourceArticles(source.id, "2026-11-12", fetched.value.articles, NOW)
    }

    private fun config(source: com.dailysatori.shared.db.Remote_news_source) =
        RemoteNewsConfigValues(source.base_url, source.api_token)

    private companion object {
        const val BASE = "https://feed.example"
        const val NOW = 1_762_928_400_000L
        val RSS_HEADERS = headersOf(HttpHeaders.ContentType, "application/rss+xml")
        val FEED_V1 = """
            <rss version="2.0"><channel><title>Tech</title>
              <item><title>A</title><link>https://example.com/a</link>
                <guid>tag:example.com,2026:a</guid>
                <description><![CDATA[<p>正文A</p>]]></description>
                <pubDate>Tue, 12 Nov 2026 08:00:00 GMT</pubDate></item>
              <item><title>B</title><link>https://example.com/b</link>
                <guid>tag:example.com,2026:b</guid>
                <description><![CDATA[<p>正文B</p>]]></description>
                <pubDate>Tue, 12 Nov 2026 07:00:00 GMT</pubDate></item>
            </channel></rss>
        """.trimIndent()
        val FEED_V2 = """
            <rss version="2.0"><channel><title>Tech</title>
              <item><title>C</title><link>https://example.com/c</link>
                <guid>tag:example.com,2026:c</guid>
                <description><![CDATA[<p>正文C</p>]]></description>
                <pubDate>Tue, 12 Nov 2026 09:00:00 GMT</pubDate></item>
            </channel></rss>
        """.trimIndent()
    }
}
