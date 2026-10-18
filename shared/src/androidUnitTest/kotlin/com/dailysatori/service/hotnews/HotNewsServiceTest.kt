package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.unifiednews.UnifiedNewsSourceItem
import com.dailysatori.service.unifiednews.UnifiedNewsSourceType
import com.dailysatori.service.unifiednews.prepareUnifiedNewsSources
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.*

class HotNewsServiceTest {
    @Test fun hackerNewsKeepsPlatformRankAndDiscussionWhenAnItemIsDeleted() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            respond(when (request.url.encodedPath) {
                "/v0/topstories.json" -> "[11,12,13]"
                "/v0/item/11.json" -> """{"id":11,"title":"A document tool","url":"https://example.com/a","type":"story","score":123,"descendants":42}"""
                "/v0/item/12.json" -> """{"id":12,"deleted":true}"""
                else -> """{"id":13,"title":"Ask HN: Reading tools","type":"story","text":"<p>What do you use?</p>"}"""
            })
        })
        val items = HotNewsClient(client).fetch(HotNewsPlatform.HACKER_NEWS)
        assertEquals(listOf(1, 3), items.map { it.rank })
        assertEquals("https://news.ycombinator.com/item?id=11", items.first().discussionUrl)
        assertEquals("https://news.ycombinator.com/item?id=13", items.last().url)
        assertEquals("123", items.first().heat)
        client.close()
    }

    @Test fun githubExtractsDescriptionAndDailyGrowthWithoutUsingTotalStarsAsRank() {
        val items = parseGithubHotNews("""<article class="Box-row"><h2><a href="/acme/docs">acme / docs</a></h2>
            <p>Convert documents into Markdown</p><a href="/acme/docs/stargazers">12,345</a>
            <span class="float-sm-right">678 stars today</span></article>""")
        assertEquals("acme/docs", items.single().id)
        assertEquals("Convert documents into Markdown", items.single().description)
        assertEquals("678", items.single().heat)
        assertEquals(1, items.single().rank)
    }

    @Test fun failedRefreshRetainsChineseCacheAndUnchangedContentIsNotTranslatedAgain() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            var fail = false
            var translations = 0
            val http = HttpClient(MockEngine {
                if (fail) respond("unavailable", HttpStatusCode.ServiceUnavailable)
                else respond("""[{"id":8,"title":"A personal reading tool","url":"https://v2ex.com/t/8","content":"Body","replies":12}]""")
            })
            val store = HotNewsStore(SettingRepository(DailySatoriDatabase(driver)))
            val service = HotNewsService(HotNewsClient(http), store, { items ->
                translations++
                items.map { it.copy(chineseTitle = "一个个人阅读工具", chineseSummary = "整理个人阅读资料") }
            })
            service.refresh(HotNewsPlatform.V2EX, force = true)
            service.refresh(HotNewsPlatform.V2EX, force = true)
            assertEquals(1, translations)
            fail = true
            service.refresh(HotNewsPlatform.V2EX, force = true)
            val board = service.boards.value.first { it.platform == HotNewsPlatform.V2EX }
            assertEquals("一个个人阅读工具", board.items.single().chineseTitle)
            assertEquals(HotNewsError.FETCH_FAILED, board.error)
            assertFalse(board.refreshing)
            assertEquals("一个个人阅读工具", store.load(HotNewsPlatform.V2EX)!!.items.single().chineseTitle)
            http.close()
        }
    }

    @Test fun translationCannotReplaceIdentityAndMustContainChinese() {
        val items = listOf(HotNewsItem("1", HotNewsPlatform.HACKER_NEWS, 1, "Reading tools", "https://example.com"))
        val result = parseHotNewsTranslations("""[{"id":"hackernews:1","title":"中文阅读工具","summary":""}]""", items)
        assertEquals("https://example.com", result.single().url)
        assertEquals("中文阅读工具", result.single().chineseTitle)
        assertFailsWith<IllegalArgumentException> {
            parseHotNewsTranslations("""[{"id":"hackernews:1","title":"Still English","summary":""}]""", items)
        }
        assertFailsWith<IllegalArgumentException> {
            parseHotNewsTranslations("""[{"id":"2","title":"错误条目","summary":""}]""", items)
        }
    }

    @Test fun repositoryWithoutDescriptionGetsAFactualChineseLabelWithoutAi() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val configs = com.dailysatori.data.repository.AIConfigRepository(DailySatoriDatabase(driver))
            HttpClient(MockEngine { error("Repository metadata does not need an AI request") }).use { http ->
                val input = HotNewsItem("android/skills", HotNewsPlatform.GITHUB, 1, "android/skills",
                    "https://github.com/android/skills")
                val translator = HotNewsTranslator(com.dailysatori.service.ai.AiService(http),
                    com.dailysatori.service.ai.AiConfigService(configs))
                val result = translator.translate(listOf(input)).single()
                assertEquals("android/skills：代码仓库", result.chineseTitle)
                assertEquals("", result.chineseSummary)
                assertEquals(input.identity, result.identity)
                assertEquals(input.url, result.url)
            }
        }
    }

    @Test fun versionedNameWithOnlyReferencesGetsAFactualLabelWithoutInventingFacts() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val configs = com.dailysatori.data.repository.AIConfigRepository(DailySatoriDatabase(driver))
            HttpClient(MockEngine { error("A versioned name and reference links need no AI request") }).use { http ->
                val input = HotNewsItem("50129045", HotNewsPlatform.HACKER_NEWS, 1, "DeepSeek-v4.1-Flash-1017",
                    "https://example.com/model", "[https://example.com/source](https://example.com/source)")
                val translator = HotNewsTranslator(com.dailysatori.service.ai.AiService(http),
                    com.dailysatori.service.ai.AiConfigService(configs))
                val result = translator.translate(listOf(input)).single()
                assertEquals("热榜条目：DeepSeek-v4.1-Flash-1017", result.chineseTitle)
                assertEquals(input.description, result.chineseSummary)
                assertEquals(input.identity, result.identity)
                assertEquals(input.url, result.url)
                val prose = input.copy(originalTitle = "Why DeepSeek-v4.1 matters")
                assertFailsWith<HotNewsAiNotConfigured> { translator.translate(listOf(prose)) }
                val described = input.copy(description = "A model for solving reasoning tasks")
                assertFailsWith<HotNewsAiNotConfigured> { translator.translate(listOf(described)) }
            }
        }
    }

    @Test fun chineseTechnologyTitlesCanRetainRepositoryAndProductNames() {
        assertTrue(isChineseHotNewsText("microsoft/markitdown：将文档转成 Markdown"))
        assertTrue(isChineseHotNewsText("用 Python 实现本地阅读工具"))
        assertFalse(isChineseHotNewsText("Building a local-first reading tool"))
        assertFalse(isChineseHotNewsText("Reading tools for organizing documents 中文"))
    }

    @Test fun hotNewsAndExistingNewsMergeWhenOnlyTheUrlFragmentOrTrailingSlashDiffers() {
        val news = UnifiedNewsSourceItem("R1", UnifiedNewsSourceType.REMOTE_ARTICLE,
            sourceUrl = "https://example.com/article/", title = "文档整理工具", summary = "原有新闻摘要", content = "原有新闻提供的完整正文。".repeat(10))
        val hot = UnifiedNewsSourceItem("H1", UnifiedNewsSourceType.HOT_NEWS,
            sourceUrl = "https://example.com/article#discussion", title = "同一工具登上热榜", summary = "热点简介", content = "热榜中的简短介绍。".repeat(5))
        val result = prepareUnifiedNewsSources(listOf(news, hot))
        assertEquals(listOf(news), result)
    }

    @Test fun summaryUsesRecentReadableSourcesAndExcludesTitleOnlyOrStaleEntries() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val store = HotNewsStore(SettingRepository(DailySatoriDatabase(driver)))
            val now = Clock.System.now().toEpochMilliseconds()
            val body = "这是来源中真实可阅读的文章正文，说明了工具如何保存资料，以及整理阅读内容和导出文档的具体方式。"
            val readable = HotNewsItem("8", HotNewsPlatform.V2EX, 1, "本地阅读工具", "https://v2ex.com/t/8", originalContent = body)
            store.save(HotNewsBoard(HotNewsPlatform.V2EX, listOf(readable), now))
            store.save(HotNewsBoard(HotNewsPlatform.GITHUB, listOf(
                HotNewsItem("acme/docs", HotNewsPlatform.GITHUB, 1, "只有标题的文档工具", "https://github.com/acme/docs")), now))
            store.save(HotNewsBoard(HotNewsPlatform.HACKER_NEWS, listOf(
                readable.copy(id = "9", platform = HotNewsPlatform.HACKER_NEWS)), now - 25 * 60 * 60_000L))
            HttpClient(MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) }).use { http ->
                val service = HotNewsService(HotNewsClient(http), store, { it })
                val sources = service.summarySources()
                assertEquals(listOf("v2ex:8"), sources.map { it.sourceFilename })
                assertTrue(sources.single().content.contains(body))
                assertEquals(body, sources.single().summary)
                assertEquals(HotNewsError.FETCH_FAILED, service.boards.value.first { it.platform == HotNewsPlatform.HACKER_NEWS }.error)
            }
        }
    }

    @Test fun cancellationIsPropagatedAndDoesNotMarkTheCacheAsFailed() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val store = HotNewsStore(SettingRepository(DailySatoriDatabase(driver)))
            val cached = HotNewsItem("8", HotNewsPlatform.V2EX, 1, "阅读工具", "https://v2ex.com/t/8")
            store.save(HotNewsBoard(HotNewsPlatform.V2EX, listOf(cached), 1000))
            HttpClient(MockEngine { throw CancellationException("cancelled") }).use { http ->
                val service = HotNewsService(HotNewsClient(http), store, { it })
                assertFailsWith<CancellationException> { service.refresh(HotNewsPlatform.V2EX, force = true) }
                val board = service.boards.value.first { it.platform == HotNewsPlatform.V2EX }
                assertEquals(listOf(cached), board.items)
                assertNull(board.error)
                assertFalse(board.refreshing)
            }
        }
    }
}
