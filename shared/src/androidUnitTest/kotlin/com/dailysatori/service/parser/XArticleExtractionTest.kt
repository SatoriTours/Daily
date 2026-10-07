package com.dailysatori.service.parser

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.service.externalfavorites.XArticleRetrievalTest
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class XArticleExtractionTest {
    @Test fun initialExtractionUsesFxEmbedWithoutWebViewOrXAccount() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            HttpClient(MockEngine { request ->
                assertEquals("api.fxtwitter.com", request.url.host)
                respond(XArticleRetrievalTest.fxArticle, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val parser = WebpageParserService(ArticleRepository(db), TagRepository(db), ImageRepository(db),
                    AiService(client), AiConfigService(AIConfigRepository(db, PlainCipher)), WebViewLoader(), FileManager(),
                    client, ExternalFavoriteSourceRepository(db, { it }, { it }), XBookmarksConnector(client), settingRepo = SettingRepository(db))
                val extracted = kotlinx.coroutines.withTimeout(1_500) { parser.extractContent("https://x.com/writer/status/123") }
                assertEquals("Article", extracted.title)
                assertTrue(extracted.content.orEmpty().contains("Last paragraph."))
                assertEquals("https://example.com/cover.jpg", extracted.coverImageUrl)
            }
        }
    }

    @Test fun unmappedArticleLinkFailsClearlyInsteadOfFallingBackToWebView() = runBlocking {
        withEnvironment { db, sources, client ->
            val error = assertFailsWith<IllegalStateException> { kotlinx.coroutines.withTimeout(1_000) { parser(db, sources, client).extractContent("https://x.com/i/article/456") } }
            assertTrue(error.message.orEmpty().contains("同步收藏"))
        }
    }

    @Test fun supplementUsesTheOwningSourceInsteadOfTheFirstEnabledAccount() = runBlocking {
        withEnvironment { db, sources, client ->
            sources.save(provider = "x", displayName = "First", accountId = "1", accountName = "first", authJson = """{"access_token":"first-token"}""", enabled = true)
            val own = sources.save(provider = "x", displayName = "Second", accountId = "2", accountName = "second", authJson = """{"access_token":"second-token"}""", enabled = true)
            val items = ExternalFavoriteItemRepository(db)
            val (item, _) = items.upsertDraft(own, com.dailysatori.service.externalfavorites.XArticleCacheTest.draft("123"))
            val resolver = com.dailysatori.service.externalfavorites.DefaultExternalFavoriteSupplementResolver(sources,
                XBookmarksConnector(client), parser(db, sources, client))
            val input = com.dailysatori.service.externalfavorites.ExternalFavoriteAiInput("x", "Article", item.text, "Writer", null, item.canonical_url!!)
            assertTrue(resolver.resolve(item, input)?.articleContentComplete == true)
        }
    }

    @Test fun cachedArticleProofRequiresOfficialFallbackWhenFxOnlyReturnsATweetShell() = runBlocking {
        val shell = """{"code":200,"tweet":{"id":"123","text":"An introductory tweet without the actual article body."}}"""
        withEnvironment(shell) { db, sources, client ->
            val sourceId = sources.save(provider = "x", displayName = "X", accountId = "2", accountName = "Writer",
                authJson = """{"access_token":"second-token"}""", enabled = true)
            val items = ExternalFavoriteItemRepository(db)
            val (item, _) = items.upsertDraft(sourceId, com.dailysatori.service.externalfavorites.XArticleCacheTest.draft("123").copy(
                normalizedJson = """{"primary_url":"https://x.com/writer/status/123","canonical_tweet_url":"https://x.com/writer/status/123"}"""))
            items.cacheContentIfUnchanged(item, com.dailysatori.service.externalfavorites.XArticleCacheTest.fullBody()) { true }
            val cached = items.getBySourceExternalId(sourceId, "123")!!
            val resolver = com.dailysatori.service.externalfavorites.DefaultExternalFavoriteSupplementResolver(sources,
                XBookmarksConnector(client), parser(db, sources, client))
            val input = com.dailysatori.service.externalfavorites.ExternalFavoriteAiInput("x", "Article", cached.text, "Writer", null, cached.canonical_url!!)
            assertTrue(resolver.resolve(cached, input)?.articleContentComplete == true)
        }
    }

    @Test fun mappedArticleLinkFetchesItsParentPostWithoutAnEnabledXAccount() = runBlocking {
        withEnvironment(XArticleRetrievalTest.fxArticle) { db, sources, client ->
            val sourceId = sources.save(provider = "x", displayName = "Disabled", accountId = "2", accountName = "Writer", authJson = "{}", enabled = false)
            ExternalFavoriteItemRepository(db).upsertDraft(sourceId, com.dailysatori.service.externalfavorites.XArticleCacheTest.draft("123"))
            val extracted = parser(db, sources, client).extractContent("https://mobile.twitter.com/i/article/456?ref=test")
            assertTrue(extracted.content.orEmpty().contains("Last paragraph."))
            assertEquals("Article", extracted.title)
        }
    }

    @Test fun bookmarkedQuoteUsesItsMappedArticleParent() = runBlocking {
        withEnvironment(XArticleRetrievalTest.fxArticle) { db, sources, client ->
            val sourceId = sources.save(provider = "x", displayName = "X", accountId = "2", accountName = "Writer", authJson = "{}", enabled = false)
            val referenced = com.dailysatori.service.externalfavorites.XArticleCacheTest.draft("123").copy(
                externalId = "999", canonicalUrl = "https://x.com/reader/status/999")
            ExternalFavoriteItemRepository(db).upsertDraft(sourceId, referenced)
            assertTrue(parser(db, sources, client).extractContent("https://x.com/reader/status/999").content.orEmpty().contains("Last paragraph."))
        }
    }

    @Test fun wrongArticleMappingDoesNotReturnAnotherArticlesBody() = runBlocking {
        withEnvironment(XArticleRetrievalTest.fxArticle) { db, sources, client ->
            val sourceId = sources.save(provider = "x", displayName = "Disabled", accountId = "2", accountName = "Writer", authJson = "{}", enabled = false)
            val draft = com.dailysatori.service.externalfavorites.XArticleCacheTest.draft("123")
            ExternalFavoriteItemRepository(db).upsertDraft(sourceId, draft.copy(normalizedJson = draft.normalizedJson.replace("/article/456", "/article/777")))
            val error = assertFailsWith<IllegalStateException> { parser(db, sources, client).extractContent("https://x.com/i/article/777") }
            assertTrue(error.message.orEmpty().contains("映射不一致"))
        }
    }

    @Test fun directArticleLinkAcceptsAConfirmedShortBody() = runBlocking {
        val fx = """{"code":200,"tweet":{"id":"123","text":"","article":{"id":"456","title":"Article","plain_text":"Short genuine body."}}}"""
        withEnvironment(fx) { db, sources, client ->
            val sourceId = sources.save(provider = "x", displayName = "X", accountId = "2", accountName = "Writer", authJson = "{}", enabled = false)
            ExternalFavoriteItemRepository(db).upsertDraft(sourceId, com.dailysatori.service.externalfavorites.XArticleCacheTest.draft("123"))
            assertEquals("Short genuine body.", parser(db, sources, client).extractContent("https://x.com/i/article/456").content)
        }
    }

    private suspend fun withEnvironment(fxBody: String = "{}", block: suspend (DailySatoriDatabase, ExternalFavoriteSourceRepository, HttpClient) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = ExternalFavoriteSourceRepository(db, { it }, { it })
            HttpClient(MockEngine { request ->
                if (request.url.host == "api.fxtwitter.com") {
                    assertEquals("/status/123", request.url.encodedPath)
                    respond(fxBody, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else {
                    assertEquals("Bearer second-token", request.headers[HttpHeaders.Authorization])
                    respond(XArticleRetrievalTest.officialArticle, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }).use { block(db, sources, it) }
        }
    }

    private fun parser(db: DailySatoriDatabase, sources: ExternalFavoriteSourceRepository, client: HttpClient) =
        WebpageParserService(ArticleRepository(db), TagRepository(db), ImageRepository(db), AiService(client),
            AiConfigService(AIConfigRepository(db, PlainCipher)), WebViewLoader(), FileManager(), client, sources,
            XBookmarksConnector(client), settingRepo = SettingRepository(db), externalFavoriteItemRepo = ExternalFavoriteItemRepository(db))

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
