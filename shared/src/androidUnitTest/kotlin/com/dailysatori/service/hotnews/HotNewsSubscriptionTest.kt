package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.test.*

class HotNewsSubscriptionTest {
    @Test fun unavailableFeedAndUnconfiguredFeedHaveDistinctErrors() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val store = HotNewsStore(SettingRepository(DailySatoriDatabase(driver)))
            var requests = 0
            HttpClient(MockEngine { requests++; respond("Not Found", HttpStatusCode.NotFound) }).use { http ->
                val service = HotNewsService(HotNewsClient(http), store, { error("No items to translate") })
                service.refresh(HotNewsPlatform.X)
                service.refresh(HotNewsPlatform.XIAOHONGSHU)
                val x = service.boards.value.first { it.platform == HotNewsPlatform.X }
                val xiaohongshu = service.boards.value.first { it.platform == HotNewsPlatform.XIAOHONGSHU }
                assertEquals(HotNewsError.FETCH_FAILED, x.error)
                assertEquals(HotNewsError.FEED_NOT_CONFIGURED, xiaohongshu.error)
                assertTrue(x.items.isEmpty() && xiaohongshu.items.isEmpty())
                assertEquals(1, requests)
            }
        }
    }

    @Test fun disablingCachedSourcesExcludesThemFromNewSummariesWithoutFetching() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            val store = HotNewsStore(settings)
            store.save(HotNewsBoard(HotNewsPlatform.V2EX, listOf(HotNewsItem("8", HotNewsPlatform.V2EX, 1,
                "新的新闻", "https://www.v2ex.com/t/8", originalContent = "这是已经缓存的完整正文内容。".repeat(5))),
                fetchedAt = Clock.System.now().toEpochMilliseconds()))
            HttpClient(MockEngine { error("Disabled sources must not fetch") }).use { http ->
                val service = HotNewsService(HotNewsClient(http), store, { error("Must not translate") },
                    { error("Must not fetch content") })
                HotNewsPlatform.entries.filter { it.core }.forEach { platform ->
                    service.configure(platform, false, store.preferences.value.source(platform).feedUrl)
                }
                assertTrue(service.summarySources().isEmpty())
                assertEquals(1, store.load(HotNewsPlatform.V2EX)!!.items.size)
            }
        }
    }

    @Test fun feedAddressRequiresAnExplicitHttpSchemeAndRejectsCredentials() {
        listOf("rsshub.app/zhihu/hot", "ftp://feed.example/rss", "https://user:secret@feed.example/rss").forEach {
            assertFailsWith<IllegalArgumentException> { normalizeNewsFeedUrl(it) }
        }
        assertEquals("https://feed.example/rss", normalizeNewsFeedUrl(" https://feed.example/rss "))
        assertEquals("", normalizeNewsFeedUrl(" "))
    }

    @Test fun disableAndReenableDiscardsOldInFlightResultsAndPersistsConfiguration() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            HttpClient(MockEngine {
                started.complete(Unit)
                release.await()
                respond("""[{"id":8,"title":"旧的新闻","url":"https://www.v2ex.com/t/8"}]""")
            }).use { http ->
                val store = HotNewsStore(settings)
                val service = HotNewsService(HotNewsClient(http), store, { it })
                val refresh = launch { service.refresh(HotNewsPlatform.V2EX, force = true) }
                started.await()
                service.configure(HotNewsPlatform.V2EX, false, "https://feed.example/v2ex")
                service.configure(HotNewsPlatform.V2EX, true, "https://feed.example/v2ex")
                release.complete(Unit)
                refresh.join()
                assertTrue(service.boards.value.first { it.platform == HotNewsPlatform.V2EX }.items.isEmpty())
                assertFalse(service.boards.value.first { it.platform == HotNewsPlatform.V2EX }.refreshing)
                val restored = HotNewsStore(settings).preferences.value.source(HotNewsPlatform.V2EX)
                assertTrue(restored.enabled)
                assertEquals("https://feed.example/v2ex", restored.feedUrl)
                assertEquals(2, restored.version)
            }
        }
    }

    @Test fun missingSubscriptionAndHtmlChallengeAreReportedAsFailures() = runBlocking {
        var requests = 0
        HttpClient(MockEngine { requests++; respond("<html><body>Verify you are human</body></html>") }).use { http ->
            val client = HotNewsClient(http)
            assertFailsWith<NewsFeedNotConfigured> { client.fetch(HotNewsPlatform.XIAOHONGSHU) }
            assertEquals(0, requests)
            assertFailsWith<IllegalArgumentException> { client.fetch(HotNewsPlatform.X, "https://feed.example/x") }
            assertEquals(1, requests)
        }
        val defaults = HotNewsSourcePreferences()
        assertEquals(8, HotNewsPlatform.entries.count { defaults.source(it).enabled })
        assertFalse(defaults.source(HotNewsPlatform.CHINA_NEWS).enabled)
        assertFalse(defaults.source(HotNewsPlatform.IT_HOME).enabled)
    }

    @Test fun officialFailureFallsBackToRssHubAndKeepsReadableContentAndLinks() = runBlocking {
        HttpClient(MockEngine { request ->
            if (request.url.host == "www.zhihu.com") respond("blocked", HttpStatusCode.Forbidden)
            else respond("""<rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
                <channel><item><guid>question-17</guid><title>新的阅读工具</title>
                <link>https://www.zhihu.com/question/17</link><description>简短摘要</description>
                <content:encoded><![CDATA[<p>这是文章正文，介绍了完整的实现过程。</p>]]></content:encoded>
                </item><item><title>重复</title><guid>question-17</guid><link>https://www.zhihu.com/question/17</link></item>
                <item><title>无效链接</title><link>javascript:alert(1)</link></item></channel></rss>""")
        }).use { http ->
            val item = HotNewsClient(http).fetch(HotNewsPlatform.ZHIHU).single()
            assertEquals("新的阅读工具", item.originalTitle)
            assertEquals("https://www.zhihu.com/question/17", item.url)
            assertEquals("question-17", item.id)
            assertTrue(item.fromFeed)
            assertTrue(item.originalContent.contains("完整的实现过程"))
        }
    }

    @Test fun rssHubAtomFeedKeepsAlternateArticleLinkAndHtmlContent() = runBlocking {
        HttpClient(MockEngine { request ->
            if (request.url.host == "www.v2ex.com") respond("blocked", HttpStatusCode.Forbidden)
            else respond("""<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>topic-42</id>
                <title>阅读器的新版本</title><link rel="self" href="https://feed.example/42"/>
                <link rel="alternate" href="https://www.v2ex.com/t/42"/>
                <content type="html">&lt;p&gt;改进了离线阅读和内容导出。&lt;/p&gt;</content>
                </entry></feed>""")
        }).use { http ->
            val item = HotNewsClient(http).fetch(HotNewsPlatform.V2EX).single()
            assertEquals("https://www.v2ex.com/t/42", item.url)
            assertTrue(item.originalContent.contains("离线阅读"))
        }
    }

    @Test fun disabledPlatformDoesNotMakeRequestsAfterReloadingSettings() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            settings.upsert("hot_news.sources.v1", """{"sources":{"v2ex":{"enabled":false}}}""")
            var requests = 0
            HttpClient(MockEngine {
                requests++
                respond("""[{"id":8,"title":"新闻","url":"https://www.v2ex.com/t/8"}]""")
            }).use { http ->
                val service = HotNewsService(HotNewsClient(http), HotNewsStore(settings), { it })
                service.refresh(HotNewsPlatform.V2EX, force = true)
                assertEquals(0, requests)
            }
        }
    }
}
