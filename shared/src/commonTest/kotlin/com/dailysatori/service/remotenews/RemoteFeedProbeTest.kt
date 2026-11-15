package com.dailysatori.service.remotenews

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteFeedProbeTest {
    @Test
    fun plainFeedReportsTheSingleUrlModeAndReadsOnlyOnce() = runBlocking {
        var requests = 0
        val service = RemoteNewsService(HttpClient(MockEngine {
            requests++
            respond(rss("""<item><title>一</title><link>https://feed.test/1</link><description>正文</description></item>"""))
        }))

        val result = service.probeSourceFeed(RemoteNewsConfigValues(ENTRY, ""))

        assertTrue(result is RemoteNewsResult.Success)
        assertEquals(1, requests)
        assertEquals("plain", result.value.mode)
        assertEquals(1, result.value.pagesChecked)
        assertTrue(result.value.hasBody)
        assertTrue(result.value.message.contains("连接成功，获取到 1 篇文章"))
        assertTrue(result.value.message.contains("普通订阅模式：每次同步当前 URL"), result.value.message)
    }

    @Test
    fun pagedFeedVerifiesADeclaredFollowUpPage() = runBlocking {
        val requested = mutableListOf<String>()
        val service = RemoteNewsService(HttpClient(MockEngine { request ->
            requested += request.url.toString()
            when (request.url.encodedPath) {
                "/index.rss" -> respond(
                    rss(
                        """<item><title>一</title><link>https://feed.test/1</link></item>""",
                        """<atom:link rel="next" href="page2.rss"/>""",
                        atomNamespace = true,
                    ),
                )
                "/page2.rss" -> respond(
                    rss(
                        """<item><title>二</title><link>https://feed.test/2</link></item>""",
                        """<atom:link rel="next" href="page3.rss"/>""",
                        atomNamespace = true,
                    ),
                )
                else -> respond(rss("""<item><title>三</title><link>https://feed.test/3</link></item>"""))
            }
        }))

        val result = service.probeSourceFeed(RemoteNewsConfigValues(ENTRY, ""))

        assertTrue(result is RemoteNewsResult.Success)
        assertEquals(listOf(ENTRY, "https://feed.test/page2.rss", "https://feed.test/page3.rss"), requested)
        assertEquals("paged", result.value.mode)
        assertEquals(3, result.value.pagesChecked)
        assertTrue(result.value.message.contains("已验证 2 个分页"), result.value.message)
        assertTrue(result.value.message.contains("支持标准分页同步"), result.value.message)
        assertTrue(result.value.message.contains("仅验证少量页面，不保证全部历史或全文完整"), result.value.message)
    }

    @Test
    fun unreadableFollowUpPageFallsBackToThePlainModeMessage() = runBlocking {
        val service = RemoteNewsService(HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/index.rss" -> respond(
                    rss(
                        """<item><title>一</title><link>https://feed.test/1</link></item>""",
                        """<atom:link rel="next" href="page2.rss"/>""",
                        atomNamespace = true,
                    ),
                )
                else -> respond("<html><body>登录</body></html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
            }
        }))

        val result = service.probeSourceFeed(RemoteNewsConfigValues(ENTRY, ""))

        assertTrue(result is RemoteNewsResult.Success)
        assertEquals("plain", result.value.mode)
        assertTrue(result.value.message.contains("后续页读取失败"), result.value.message)
    }

    @Test
    fun crossOriginFollowUpLinkIsVerifiedWithoutTheToken() = runBlocking {
        val requested = mutableListOf<Pair<String, String?>>()
        val service = RemoteNewsService(HttpClient(MockEngine { request ->
            requested += request.url.toString() to request.headers[HttpHeaders.Authorization]
            when (request.url.host) {
                "feed.test" -> respond(
                    rss(
                        """<item><title>一</title><link>https://feed.test/1</link></item>""",
                        """<atom:link rel="next" href="https://other.test/page2.rss"/>""",
                        atomNamespace = true,
                    ),
                )
                else -> respond(rss("""<item><title>二</title><link>https://other.test/2</link></item>"""))
            }
        }))

        val result = service.probeSourceFeed(RemoteNewsConfigValues(ENTRY, "token"))

        assertTrue(result is RemoteNewsResult.Success)
        assertEquals(2, requested.size)
        assertEquals("Bearer token", requested.first().second)
        assertNull(requested.last().second, "a declared cross-site document never receives the token")
        assertTrue(result.value.message.contains("支持标准分页同步"), result.value.message)
    }

    @Test
    fun titleOnlyFeedSaysThatNoBodyWasFound() = runBlocking {
        val service = RemoteNewsService(HttpClient(MockEngine {
            respond(rss("""<item><title>只有标题</title><link>https://feed.test/1</link></item>"""))
        }))

        val result = service.probeSourceFeed(RemoteNewsConfigValues(ENTRY, ""))

        assertTrue(result is RemoteNewsResult.Success)
        assertFalse(result.value.hasBody)
        assertTrue(result.value.message.contains("未检测到正文内容"), result.value.message)
    }

    private fun rss(item: String, head: String = "", atomNamespace: Boolean = false): String =
        """
        <rss version="2.0"${if (atomNamespace) " xmlns:atom=\"http://www.w3.org/2005/Atom\"" else ""}>
          <channel><title>示例</title>$head$item</channel>
        </rss>
        """.trimIndent()

    private companion object {
        const val ENTRY = "https://feed.test/index.rss"
    }
}
