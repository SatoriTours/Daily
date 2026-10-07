package com.dailysatori.service.externalfavorites

import com.dailysatori.shared.db.External_favorite_source
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class XArticleRetrievalTest {
    @Test fun readsOfficialPlainTextInsteadOfPreview() {
        val draft = XBookmarksResponseParser.parsePostLookup("""{"data":{"id":"123","text":"https://t.co/a","article":{"id":"456","title":"Article","plain_text":"First paragraph.\n\nLast paragraph.","preview_text":"First","description":"Preview only"}}}""")!!
        assertEquals("First paragraph.\n\nLast paragraph.", draft.text)
        assertEquals("https://x.com/i/article/456", draft.canonicalUrl)
        assertEquals("true", Json.parseToJsonElement(draft.normalizedJson).jsonObject["article_content_complete"]?.jsonPrimitive?.content)
    }

    @Test fun interactionMetricsDoNotChangeBodyVersion() {
        val first = XBookmarksResponseParser.parsePostLookup(officialArticle.replace("\"text\":", "\"public_metrics\":{\"like_count\":1},\"text\":"))!!
        val second = XBookmarksResponseParser.parsePostLookup(officialArticle.replace("\"text\":", "\"public_metrics\":{\"like_count\":2},\"text\":"))!!
        assertEquals(first.contentHash, second.contentHash)
        assertEquals(first.aiInputHash, second.aiInputHash)
        assertNotEquals(first.normalizedJson, second.normalizedJson)
    }

    @Test fun keepsAvailableArticleUpdateTimeAsABodyVersionSignal() {
        val first = XBookmarksResponseParser.parsePostLookup(officialArticle.replace("\"article\":{", "\"article\":{\"modified_at\":\"2026-01-01T00:00:00Z\","))!!
        val second = XBookmarksResponseParser.parsePostLookup(officialArticle.replace("\"article\":{", "\"article\":{\"modified_at\":\"2026-01-02T00:00:00Z\","))!!
        assertEquals("2026-01-01T00:00:00Z", favoriteMetadata(first.normalizedJson).textValue("article_modified_at"))
        assertNotEquals(first.contentHash, second.contentHash)
        val fx = parseFxEmbedPost(fxArticle.replace("\"article\":{", "\"article\":{\"updated_at\":\"2026-01-02T00:00:00Z\","), "123")!!
        assertEquals("2026-01-02T00:00:00Z", favoriteMetadata(fx.normalizedJson).textValue("article_modified_at"))
    }

    @Test fun doesNotTreatArticleDescriptionAsBody() {
        val draft = XBookmarksResponseParser.parsePostLookup("""{"data":{"id":"123","text":"https://t.co/a","article":{"title":"Article","description":"A preview longer than twenty characters, not the full article."}}}""")!!
        assertEquals("https://t.co/a", draft.text)
        assertEquals("false", Json.parseToJsonElement(draft.normalizedJson).jsonObject["article_content_complete"]?.jsonPrimitive?.content)
    }

    @Test fun fxEmbedArticleSucceedsWithoutOfficialRequestOrLeakingAuth() = runBlocking {
        val requests = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            requests += request.url.host
            if (request.url.host == "api.fxtwitter.com") {
                assertEquals("/status/123", request.url.encodedPath)
                assertNull(request.headers[HttpHeaders.Authorization])
                assertNull(request.headers[HttpHeaders.Cookie])
                respond(fxArticle, headers = jsonHeaders)
            } else respond(officialArticle, headers = jsonHeaders)
        }).use { client ->
            val draft = XBookmarksConnector(client).fetchPostById(source(), "123")!!
            assertEquals(listOf("api.fxtwitter.com"), requests)
            assertEquals("Article", draft.title)
            assertEquals("Writer", draft.authorName)
            assertEquals("https://x.com/i/article/456", draft.canonicalUrl)
            assertTrue(draft.text.contains("# First paragraph"))
            assertTrue(draft.text.contains("[documentation](https://example.com/docs)"))
            assertTrue(draft.text.contains("![Illustration](https://example.com/image.jpg)"))
            assertTrue(draft.text.endsWith("Last paragraph."))
            val metadata = Json.parseToJsonElement(draft.normalizedJson).jsonObject
            assertTrue(metadata["media"].toString().contains("https://example.com/cover.jpg"))
        }
    }

    @Test fun fxEmbedPreviewOnlyFallsBackToArticleRequestWithoutNoteTweet() = runBlocking {
        val requests = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            requests += request.url.host
            if (request.url.host == "api.fxtwitter.com") {
                respond("""{"code":200,"tweet":{"id":"123","text":"A sufficiently long introductory tweet, not the article body.","article":{"title":"Article","preview_text":"Preview only"}}}""", headers = jsonHeaders)
            } else {
                assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
                assertEquals("/2/tweets/123", request.url.encodedPath)
                val fields = request.url.parameters["tweet.fields"].orEmpty().split(',')
                assertTrue("article" in fields)
                assertFalse("note_tweet" in fields)
                respond(officialArticle, headers = jsonHeaders)
            }
        }).use { client ->
            assertEquals("Official full body, including the final paragraph.", XBookmarksConnector(client).fetchPostById(source(), "123")?.text)
            assertEquals(listOf("api.fxtwitter.com", "api.x.com"), requests)
        }
    }

    @Test fun fxEmbedErrorsAndMalformedResponsesFallBackToOfficial() = runBlocking {
        for ((body, status) in listOf("unavailable" to HttpStatusCode.TooManyRequests,
            "not json" to HttpStatusCode.OK, """{"code":404}""" to HttpStatusCode.OK,
            fxArticle.replace("\"id\":\"123\"", "\"id\":\"999\"") to HttpStatusCode.OK)) {
            HttpClient(MockEngine { request ->
                if (request.url.host == "api.fxtwitter.com") respond(body, status, jsonHeaders)
                else respond(officialArticle, headers = jsonHeaders)
            }).use { client ->
                assertEquals("Official full body, including the final paragraph.", XBookmarksConnector(client).fetchPostById(source(), "123")?.text)
            }
        }
    }

    @Test fun ordinaryLongTweetUsesSeparateOfficialNoteTweetFallback() = runBlocking {
        val fields = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            if (request.url.host == "api.fxtwitter.com") respond("{}", HttpStatusCode.NotFound, jsonHeaders)
            else {
                fields += request.url.parameters["tweet.fields"].orEmpty()
                if ("note_tweet" in fields.last().split(',')) respond("""{"data":{"id":"123","text":"Truncated...","note_tweet":{"text":"Complete long tweet with the end preserved."}}}""", headers = jsonHeaders)
                else respond("""{"data":{"id":"123","text":"Truncated..."}}""", headers = jsonHeaders)
            }
        }).use { client ->
            assertEquals("Complete long tweet with the end preserved.", XBookmarksConnector(client).fetchPostById(source(), "123")?.text)
            assertEquals(2, fields.size)
            assertTrue("article" in fields.first().split(','))
            assertFalse("article" in fields.last().split(','))
        }
    }

    @Test fun cancellationDuringFxEmbedDoesNotTriggerPaidFallback() = runBlocking {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            throw CancellationException("cancelled")
        }).use { client ->
            assertFailsWith<CancellationException> { XBookmarksConnector(client).fetchPostById(source(), "123") }
            assertEquals(1, requests)
        }
    }

    @Test fun fxEmbedSuccessNeverLoadsOfficialAuth() = runBlocking<Unit> {
        HttpClient(MockEngine { respond(fxArticle, headers = jsonHeaders) }).use { client ->
            assertNotNull(XBookmarksConnector(client).fetchPostById(null, "123") { error("Expired official auth must not block FxEmbed") })
        }
    }

    @Test fun incompleteOfficialArticleIsNotReturnedAsFullBody() = runBlocking {
        val hosts = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            hosts += request.url.host
            if (request.url.host == "api.fxtwitter.com") throw java.io.IOException("Network failure")
            respond("""{"data":{"id":"123","text":"A long introduction, not the article body.","article":{"title":"Article","preview_text":"Only a preview"}}}""", headers = jsonHeaders)
        }).use { client ->
            assertNull(XBookmarksConnector(client).fetchPostById(source(), "123"))
            assertEquals(listOf("api.fxtwitter.com", "api.x.com"), hosts)
        }
    }

    @Test fun bookmarkRequestsArticleMetadata() = runBlocking<Unit> {
        HttpClient(MockEngine { request ->
            assertTrue("article" in request.url.parameters["tweet.fields"].orEmpty().split(','))
            respond("""{"data":[],"meta":{"result_count":0}}""", headers = jsonHeaders)
        }).use { client -> XBookmarksConnector(client).fetchPage(source(), null, 10) }
    }

    private fun source() = External_favorite_source(
        id = 1, provider = "x", display_name = "X", account_id = "42", account_name = "Writer",
        enabled = 1, sync_interval_minutes = 720, last_sync_started_at = null, last_sync_completed_at = null,
        last_success_at = null, last_sync_window_started_at = null, last_items_seen_count = 0, last_pages_seen_count = 0,
        last_error = "", last_error_code = "", last_error_message = "", status = "idle", last_sync_mode = "",
        rate_limit_reset_at = null, auth_json = """{"access_token":"secret"}""", config_json = "{}",
        capabilities_json = "{}", created_at = 0, updated_at = 0,
    )

    companion object {
        private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        internal val officialArticle = """{"data":{"id":"123","text":"https://t.co/a","article":{"id":"456","title":"Article","plain_text":"Official full body, including the final paragraph."}}}"""
        internal val fxArticle = """{
          "code":200,"tweet":{"id":"123","url":"https://x.com/writer/status/123","text":"","created_timestamp":1780272000,
          "author":{"id":"42","screen_name":"writer","name":"Writer"},
          "article":{"id":"456","title":"Article","preview_text":"First paragraph",
            "cover_media":{"media_id":"2","media_info":{"original_img_url":"https://example.com/cover.jpg"}},
            "media_entities":[{"media_id":"1","media_key":"3_1","media_info":{"original_img_url":"https://example.com/image.jpg"}}],
            "content":{"blocks":[
              {"type":"header-one","text":"First paragraph","entityRanges":[]},
              {"type":"unstyled","text":"documentation","entityRanges":[{"offset":0,"length":13,"key":0}]},
              {"type":"atomic","text":" ","entityRanges":[{"key":1,"offset":0,"length":1}]},
              {"type":"unstyled","text":"Last paragraph.","entityRanges":[]}
            ],"entityMap":[
              {"key":"0","value":{"type":"LINK","data":{"url":"https://example.com/docs"}}},
              {"key":"1","value":{"type":"MEDIA","data":{"caption":"Illustration","mediaItems":[{"mediaId":"1"}]}}}
            ]}}
        }}"""
    }
}
