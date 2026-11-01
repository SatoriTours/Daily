package com.dailysatori.service.hotnews

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class XHotNewsClientTest {
    @Test fun unconfiguredXNeverRequestsTheLegacyFeed() = runBlocking {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            respond("""<rss><channel><item><title>旧订阅内容</title>
                <link>https://x.com/i/status/1</link></item></channel></rss>""")
        }).use { http ->
            val result = runCatching { HotNewsClient(http).fetch(HotNewsPlatform.X, "https://legacy.example/rss") }
            assertTrue(result.isFailure, "Missing X authorization must not return RSS content")
            assertEquals(0, requests, "X must never fall back to an RSS endpoint")
        }
    }

    @Test fun userAuthorizationSearchesRecentPostsForPersonalizedTrendsAndRanksInteractions() = runBlocking {
        val paths = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            assertEquals("api.x.com", request.url.host)
            assertEquals("Bearer test-user-token", request.headers["Authorization"])
            paths += request.url.encodedPath
            if (paths.size == 1) {
                respond("""{"data":[{"trend_name":"Open source"},{"trend_name":"#AI"}]}""")
            } else {
                val query = request.url.parameters
                assertEquals("""("Open source" OR "#AI") -is:retweet -is:reply""", query["query"])
                assertEquals("relevancy", query["sort_order"])
                assertEquals("20", query["max_results"])
                assertEquals("2026-10-31T00:00:00Z", query["start_time"])
                assertTrue(query["post.fields"].orEmpty().contains("public_metrics"))
                respond(posts)
            }
        }).use { http ->
            val x = XHotNewsClient(http, { XHotNewsAuthorization("test-user-token") }, { 1_793_491_200_000L })
            val items = HotNewsClient(http, x::fetch).fetch(HotNewsPlatform.X, "https://legacy.example/rss")
            assertEquals(listOf("/2/users/personalized_trends", "/2/tweets/search/recent"), paths)
            assertEquals(listOf("22", "11"), items.map { it.id })
            assertEquals(listOf(1, 2), items.map { it.rank })
            assertEquals("35", items.first().heat)
            assertEquals(3, items.first().comments)
            assertEquals("https://x.com/i/status/22", items.first().url)
            assertEquals("完整帖子正文", items.first().originalContent)
            assertTrue(items.none { it.fromFeed })
        }
    }

    @Test fun appBearerUsesWorldwideTrendsWithoutFanningOutPerTopic() = runBlocking {
        var requests = 0
        HttpClient(MockEngine { request ->
            requests++
            if (requests == 1) {
                assertEquals("/2/trends/by/woeid/1", request.url.encodedPath)
                respond("""{"data":[{"trend_name":"One"},{"trend_name":"Two"},{"trend_name":"Three"},{"trend_name":"Four"}]}""")
            } else {
                assertEquals("""("One" OR "Two" OR "Three") -is:retweet -is:reply""",
                    request.url.parameters["query"])
                respond(posts)
            }
        }).use { http ->
            val items = XHotNewsClient(http, { XHotNewsAuthorization("test-app-token", userContext = false) }).fetch()
            assertEquals(2, requests)
            assertEquals(2, items.size)
        }
    }

    @Test fun authorizationBillingAndRateLimitsRemainErrorsAndNeverTriggerFallbacks() = runBlocking {
        for ((status, error) in listOf(401 to HotNewsError.X_AUTH_REQUIRED, 403 to HotNewsError.X_ACCESS_DENIED,
                402 to HotNewsError.X_QUOTA_EXCEEDED, 429 to HotNewsError.X_RATE_LIMITED)) {
            var requests = 0
            HttpClient(MockEngine {
                requests++
                respond("""{"detail":"do not log upstream secrets"}""", HttpStatusCode.fromValue(status))
            }).use { http ->
                val x = XHotNewsClient(http, { XHotNewsAuthorization("test-token") })
                val failure = assertFailsWith<XHotNewsException> { HotNewsClient(http, x::fetch).fetch(HotNewsPlatform.X) }
                assertEquals(error, failure.error)
                assertEquals(1, requests)
                assertFalse(failure.message.orEmpty().contains("secrets"))
            }
        }
    }

    @Test fun searchErrorsAreNotMistakenForAnEmptySuccessfulBoard() = runBlocking {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            if (requests == 1) respond("""{"data":[{"trend_name":"AI"}]}""")
            else respond("""{"errors":[{"detail":"search failed"}],"meta":{"result_count":0}}""")
        }).use { http ->
            assertFailsWith<IllegalStateException> {
                XHotNewsClient(http, { XHotNewsAuthorization("test-token") }).fetch()
            }
            assertEquals(2, requests)
        }
    }

    @Test fun genuineZeroSearchResultsAreEmptyAndDoNotFetchIndividualPosts() = runBlocking {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            respond(if (requests == 1) """{"data":[{"trend_name":"AI"}]}""" else """{"meta":{"result_count":0}}""")
        }).use { http ->
            assertTrue(XHotNewsClient(http, { XHotNewsAuthorization("test-token") }).fetch().isEmpty())
            assertEquals(2, requests)
        }
    }

    @Test fun cancellationDoesNotStartAnotherProviderRequest() = runBlocking {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            throw CancellationException("Cancelled")
        }).use { http ->
            val x = XHotNewsClient(http, { XHotNewsAuthorization("test-token") })
            assertFailsWith<CancellationException> { HotNewsClient(http, x::fetch).fetch(HotNewsPlatform.X) }
            assertEquals(1, requests)
        }
    }

    companion object {
        private val posts = """{"data":[
            {"id":"11","text":"A link post","public_metrics":{"like_count":2,"retweet_count":1,"reply_count":0,"quote_count":0}},
            {"id":"22","text":"短正文","note_post":{"text":"完整帖子正文"},"public_metrics":{"like_count":20,"retweet_count":10,"reply_count":3,"quote_count":2}},
            {"id":"22","text":"重复帖子","public_metrics":{"like_count":1}}
        ]}"""
    }
}
