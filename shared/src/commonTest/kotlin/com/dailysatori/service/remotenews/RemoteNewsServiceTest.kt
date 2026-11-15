package com.dailysatori.service.remotenews

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class RemoteNewsServiceTest {
    @Test
    fun requestsTheExactConfiguredFeedUrlIncludingQuery(): Unit = runBlocking {
        val service = RemoteNewsService(HttpClient(MockEngine { request ->
            assertEquals(FEED_URL, request.url.toString())
            assertEquals("category=tech&limit=30", request.url.encodedQuery)
            assertEquals(null, request.url.parameters["page"])
            assertEquals(null, request.url.parameters["per_page"])
            respond(feed(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml"))
        }))

        val result = service.fetchSourceArticles(RemoteNewsConfigValues(FEED_URL, ""))

        assertTrue(result is RemoteNewsResult.Success)
        assertEquals(listOf("第一篇", "第二篇"), result.value.articles.map { it.title })
    }

    @Test
    fun plainFeedWithoutTokenSendsNoAuthorizationHeader(): Unit = runBlocking {
        val service = RemoteNewsService(HttpClient(MockEngine { request ->
            assertEquals(null, request.headers[HttpHeaders.Authorization])
            respond(feed(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml"))
        }))

        val result = service.fetchSourceArticles(RemoteNewsConfigValues(FEED_URL, ""))
        assertTrue(result is RemoteNewsResult.Success)
    }

    @Test
    fun followUpDocumentOnAnotherOriginIsReadWithoutTheCredential(): Unit = runBlocking {
        val seen = mutableListOf<Pair<String, String?>>()
        val service = RemoteNewsService(HttpClient(MockEngine { request ->
            seen += request.url.toString() to request.headers[HttpHeaders.Authorization]
            respond(feed(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml"))
        }))

        val result = service.fetchFeedPage(
            RemoteNewsConfigValues(FEED_URL, "private-token"),
            "https://other.example.com/page2",
        )

        assertTrue(result is RemoteNewsResult.Success)
        assertEquals("https://other.example.com/page2", result.value.url)
        assertNull(seen.single().second, "a credential is never carried to another origin")
    }

    @Test
    fun optionalTokenIsStillSentAsBearerForPrivateFeeds(): Unit = runBlocking {
        val service = RemoteNewsService(HttpClient(MockEngine { request ->
            assertEquals("Bearer private-token", request.headers[HttpHeaders.Authorization])
            respond(feed(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml"))
        }))

        val result = service.fetchSourceArticles(RemoteNewsConfigValues(FEED_URL, "private-token"))
        assertTrue(result is RemoteNewsResult.Success)
    }

    @Test
    fun emptyFeedIsSuccessfulButUnusableBodyIsAFailure(): Unit = runBlocking {
        val service = RemoteNewsService(HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/empty" -> respond(
                    """<rss version="2.0"><channel><title>空</title></channel></rss>""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/rss+xml"),
                )
                else -> respond("<html><body>登录页面</body></html>", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/html"))
            }
        }))

        val empty = service.fetchSourceArticles(RemoteNewsConfigValues("https://feed.example/empty", ""))
        assertTrue(empty is RemoteNewsResult.Success)
        assertEquals(emptyList(), empty.value.articles)

        val invalid = service.fetchSourceArticles(RemoteNewsConfigValues("https://feed.example/html", ""))
        assertTrue(invalid is RemoteNewsResult.Failure)
        assertTrue(invalid.message.isNotBlank())
    }

    @Test
    fun httpErrorsExplainTheFeedProblem(): Unit = runBlocking {
        val cases = mapOf(
            HttpStatusCode.NotFound to "地址不存在",
            HttpStatusCode.Unauthorized to "认证失败",
            HttpStatusCode.TooManyRequests to "过于频繁",
        )
        cases.forEach { (status, expected) ->
            val service = RemoteNewsService(HttpClient(MockEngine { respond("", status) }))
            val result = service.fetchSourceArticles(RemoteNewsConfigValues(FEED_URL, ""))
            assertTrue(result is RemoteNewsResult.Failure)
            assertTrue(result.message.contains(expected), "HTTP " + status.value + " message was " + result.message)
        }
    }

    @Test
    fun configurationOnlyRequiresAFeedUrl(): Unit {
        val service = RemoteNewsService(HttpClient(MockEngine { error("no request expected") }))

        val withoutToken = service.configOrFailure("https://feed.example/rss", "")
        assertTrue(withoutToken is RemoteNewsResult.Success)
        val nullToken = service.configOrFailure("https://feed.example/rss", null)
        assertTrue(nullToken is RemoteNewsResult.Success)
        val blankUrl = service.configOrFailure(" ", "token")
        assertTrue(blankUrl is RemoteNewsResult.Failure)
    }

    @Test
    fun requestDoesNotSwallowCoroutineCancellation(): Unit = runBlocking {
        val service = RemoteNewsService(HttpClient(MockEngine { throw CancellationException("cancelled") }))
        assertFailsWith<CancellationException> {
            service.fetchSourceArticles(RemoteNewsConfigValues(FEED_URL, "token"))
        }
        Unit
    }

    @Test
    fun parsesStandardRemoteNewsErrorEnvelope() {
        val message = parseRemoteNewsErrorMessage(
            """
            {
              "error": {
                "code": "unauthorized",
                "message": "Invalid token"
              }
            }
            """.trimIndent(),
        )

        assertEquals("Invalid token", message)
    }

    @Test
    fun parsesSimpleRemoteNewsErrorMessage() {
        val message = parseRemoteNewsErrorMessage(
            """
            {
              "error": "bad_request",
              "message": "page must be greater than 0"
            }
            """.trimIndent(),
        )

        assertEquals("page must be greater than 0", message)
    }

    @Test
    fun remoteNewsApiDocCoversErrorAndCompatibilityRules() {
        val doc = java.io.File("../docs/08-remote-news-api.md").readText()

        assertTrue(doc.contains("## 错误响应格式"))
        assertTrue(doc.contains("## HTTP 状态码约定"))
        assertTrue(doc.contains("## 字段兼容别名"))
        assertTrue(doc.contains("## 字段缺失时的 APP 行为"))
        assertTrue(doc.contains("## 内容、时间与图片规范"))
        assertTrue(doc.contains("`published_at` 表示原文实际发表时间"))
        assertTrue(doc.contains("不要用 `404` 表示今天没有新闻"))
        assertTrue(doc.contains("## RSS / Atom 订阅"))
    }

    private companion object {
        const val FEED_URL = "https://news.example.com/feed/rss?category=tech&limit=30"

        fun feed() = """
            <rss version="2.0"><channel><title>远程新闻</title>
              <item><title>第一篇</title><link>https://news.example.com/1</link>
                <description>摘要一</description><pubDate>Tue, 12 Nov 2026 08:00:00 GMT</pubDate></item>
              <item><title>第二篇</title><link>https://news.example.com/2</link>
                <description>摘要二</description><pubDate>Tue, 12 Nov 2026 07:00:00 GMT</pubDate></item>
            </channel></rss>
        """.trimIndent()
    }
}
