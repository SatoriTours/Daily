package com.dailysatori.service.externalfavorites

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class XContentFetchBudgetTest {
    @Test fun concurrentFallbackCannotExceedPerRunRequestLimit() = runBlocking {
        val paidRequests = java.util.concurrent.atomic.AtomicInteger()
        HttpClient(MockEngine { request ->
            if (request.url.host == "api.fxtwitter.com") respond("{}", HttpStatusCode.NotFound, jsonHeaders)
            else { paidRequests.incrementAndGet(); respond(XArticleRetrievalTest.officialArticle, headers = jsonHeaders) }
        }).use { client ->
            val connector = XBookmarksConnector(client)
            val successes = withXContentFetchSession(3) {
                coroutineScope { (1..20).map { async {
                    try { connector.fetchPostById(null, "123", expectedArticle = true) { source() }; 1 }
                    catch (_: XContentDeferredException) { 0 }
                } }.awaitAll().sum() }
            }
            assertEquals(3, paidRequests.get())
            assertEquals(3, successes)
        }
    }

    @Test fun zeroBudgetDoesNotRefreshOfficialAuth() = runBlocking {
        var requests = 0
        HttpClient(MockEngine { requests++; respond("{}", HttpStatusCode.NotFound, jsonHeaders) }).use { client ->
            withXContentFetchSession(0) {
                assertFailsWith<XContentDeferredException> {
                    XBookmarksConnector(client).fetchPostById(null, "123", expectedArticle = true) { error("Must not refresh auth") }
                }
            }
            assertEquals(1, requests)
        }
    }

    @Test fun fxRateLimitBackoffExpiresAndSummaryCountsActualRequests() = runBlocking {
        var now = 1_000L
        var fx = 0
        var paid = 0
        val summaries = mutableListOf<Map<String, String>>()
        val logger = object : FavoriteSyncHttpLogger {
            override fun logRequest(taskId: Long?, label: String, method: String, url: String, parameters: Map<String, String>) {
                if (label == "x_content_fetch_summary") summaries += parameters
            }
            override fun logResponse(taskId: Long?, label: String, statusCode: Int, headers: Map<String, String>, body: String) {}
        }
        HttpClient(MockEngine { request ->
            if (request.url.host == "api.fxtwitter.com") {
                fx++
                respond("{}", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "120"))
            } else { paid++; respond(XArticleRetrievalTest.officialArticle, headers = jsonHeaders) }
        }).use { client ->
            val connector = XBookmarksConnector(client, nowMs = { now })
            withXContentFetchSession(3, logger) {
                repeat(2) { assertNotNull(connector.fetchPostById(null, "123", expectedArticle = true) { source() }) }
                assertEquals(1, fx)
                now += 120_001
                assertNotNull(connector.fetchPostById(null, "123", expectedArticle = true) { source() })
            }
            assertEquals(2, fx)
            assertEquals(3, paid)
            assertEquals("3", summaries.single()["official_requests"])
            assertEquals("1", summaries.single()["fx_backoff"])
        }
    }

    @Test fun knownArticleCannotAcceptFxOrOfficialTweetShell() = runBlocking {
        var paid = 0
        HttpClient(MockEngine { request ->
            if (request.url.host == "api.fxtwitter.com") respond("""{"code":200,"tweet":{"id":"123","text":"https://t.co/a"}}""", headers = jsonHeaders)
            else { paid++; respond("""{"data":{"id":"123","text":"Introductory text, not an article body."}}""", headers = jsonHeaders) }
        }).use { client ->
            assertNull(XBookmarksConnector(client).fetchPostById(null, "123", expectedArticle = true) { source() })
            assertEquals(1, paid)
        }
    }

    private fun source() = com.dailysatori.shared.db.External_favorite_source(
        id = 1, provider = "x", display_name = "X", account_id = "42", account_name = "Writer", enabled = 1,
        sync_interval_minutes = 720, last_sync_started_at = null, last_sync_completed_at = null, last_success_at = null,
        last_sync_window_started_at = null, last_items_seen_count = 0, last_pages_seen_count = 0, last_error = "",
        last_error_code = "", last_error_message = "", status = "idle", last_sync_mode = "", rate_limit_reset_at = null,
        auth_json = """{"access_token":"secret"}""", config_json = "{}", capabilities_json = "{}", created_at = 0, updated_at = 0)
    companion object { private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json") }
}
