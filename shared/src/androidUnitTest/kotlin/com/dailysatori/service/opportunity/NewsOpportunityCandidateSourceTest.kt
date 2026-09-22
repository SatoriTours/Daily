package com.dailysatori.service.opportunity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.service.remotenews.RemoteArticleSyncService
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class NewsOpportunityCandidateSourceTest {
    @Test
    fun unreadRemoteNewsIsFetchedAndStoredForDirectArticleNavigation() = withFixture { fixture ->
        val result = fixture.source.load()
        assertEquals(1, result.size)
        val candidate = result.single()
        assertEquals("远程文章", candidate.title)
        assertEquals("url:https://article.test/a", candidate.key)
        assertEquals(0L, candidate.readAt)
        assertTrue(candidate.content.contains("原文正文"))
        val stored = assertNotNull(fixture.articles.getById(assertNotNull(candidate.localArticleId)))
        assertTrue(stored.original_markdown_content.orEmpty().contains("原文正文"))
        assertEquals(0L, stored.is_favorite)
        assertEquals(1, fixture.requests)
        fixture.source.load()
        assertEquals(1L, fixture.mappings.count())
    }

    @Test
    fun remoteFailureUsesCachedOriginalsAndLocalArticlesWithoutInventingBodies() = withFixture { fixture ->
        fixture.source.load()
        val id = fixture.articles.insert(title = "本地文章", url = "https://local.test/a", status = "completed")
        fixture.database.dailySatoriQueries.updateArticleOriginalMarkdownContent("本地正文", 1, id)
        fixture.articles.insert(title = "只有摘要", url = "https://local.test/blank", aiContent = "不能当正文")
        fixture.fail = true
        val result = fixture.source.load()
        assertEquals(setOf("本地文章", "远程文章"), result.map { it.title }.toSet())
        assertTrue(result.all { it.content.isNotBlank() && it.localArticleId != null })
    }

    @Test
    fun unavailableSourcesAreNotReportedAsSuccessfulEmptyRecommendations() = withFixture { fixture ->
        fixture.fail = true
        assertFailsWith<IllegalStateException> { fixture.source.load() }
    }

    private fun withFixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val database = DailySatoriDatabase(driver)
        database.dailySatoriQueries.insertRemoteNewsSource("测试源", "https://news.test", "test-token", 1, 1, 1)
        database.dailySatoriQueries.insertRemoteNewsSource("禁用源", "https://disabled.test", "test-token", 0, 1, 1)
        val fixture = Fixture(database)
        val client = HttpClient(MockEngine { request ->
            fixture.requests++
            assertEquals("news.test", request.url.host)
            assertEquals("/api/v1/external/top_articles_today", request.url.encodedPath)
            if (fixture.fail) respond("{}", HttpStatusCode.ServiceUnavailable) else respond(
                """{"articles":[{"id":1,"title":"远程文章","url":"https://article.test/a#part","content":"原文正文","summary":"摘要"}]}""",
                HttpStatusCode.OK,
            )
        })
        fixture.source = NewsOpportunityCandidateSource(
            fixture.articles, { database.dailySatoriQueries.selectEnabledRemoteNewsSources().executeAsList() },
            RemoteNewsService(client), RemoteArticleSyncService(fixture.articles, fixture.mappings), fixture.mappings,
        )
        try { block(fixture) } finally { client.close(); driver.close() }
    }

    private class Fixture(val database: DailySatoriDatabase) {
        val articles = ArticleRepository(database)
        val mappings = RemoteArticleSyncRepository(database)
        lateinit var source: NewsOpportunityCandidateSource
        var fail = false
        var requests = 0
    }
}
