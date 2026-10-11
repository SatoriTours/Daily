package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.unifiednews.*
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.test.*

class HotNewsDailyGenerationTest {
    @Test fun dailyGenerationIncludesAllAvailableCorePlatformsAndExcludesDisabledSourcesOnRegeneration() =
        withGenerator("## 今日要点\n- 多个平台讨论资料导出工具。[H1][F1]") { fixture ->
            assertTrue(fixture.service.generateDaily().success)
            val first = fixture.summaries.getLatestSuccessful()!!
            val sources = fixture.summaries.getSources(first.id)
            assertEquals(HotNewsPlatform.entries.filter { it.core }.map { it.key }.toSet(), sources
                .filter { it.source_type == "hot_news" }.map { it.source_filename!!.substringBefore(':') }.toSet())
            assertTrue(fixture.prompts.single().contains("github 的来源正文"))
            assertTrue(fixture.prompts.single().contains("linuxdo 的来源正文"))
            fixture.hotNews.configure(HotNewsPlatform.V2EX, false, HotNewsPlatform.V2EX.defaultFeedUrl)
            assertTrue(fixture.service.generateDaily(force = true).success)
            assertFalse(fixture.summaries.getSources(first.id).any { it.source_filename?.startsWith("v2ex:") == true })
            assertFalse(fixture.prompts.last().contains("v2ex 的来源正文"))
            assertEquals(2, fixture.prompts.size)
        }

    @Test fun dailyGenerationRejectsAiOutputThatOmitsAvailableHotContent() =
        withGenerator("## 今日要点\n- 收藏文章介绍了资料导出流程。[F1]") { fixture ->
            val result = fixture.service.generateDaily()
            assertFalse(result.success)
            assertEquals(UnifiedNewsSummaryStatus.FAILED, result.status)
            assertTrue(result.message.orEmpty().contains("热点"))
            assertNull(fixture.summaries.getLatestSuccessful())
        }

    @Test fun dailyGenerationStillSucceedsWithOnlyFavoritesWhenAllCorePlatformsAreDisabled() =
        withGenerator("## 今日要点\n- 收藏文章介绍了资料导出流程。[F1]") { fixture ->
            HotNewsPlatform.entries.filter { it.core }.forEach { platform ->
                fixture.hotNews.configure(platform, false, fixture.hotNews.preferences.value.source(platform).feedUrl)
            }
            assertTrue(fixture.service.generateDaily().success)
            assertTrue(fixture.summaries.getSources(fixture.summaries.getLatestSuccessful()!!.id)
                .all { it.source_type == "local_favorite" })
        }

    private data class Fixture(val service: UnifiedNewsSummaryService, val hotNews: HotNewsService,
        val summaries: UnifiedNewsSummaryRepository, val prompts: List<String>)

    private fun withGenerator(response: String, block: suspend (Fixture) -> Unit) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val store = HotNewsStore(SettingRepository(db))
            val now = Clock.System.now().toEpochMilliseconds()
            HotNewsPlatform.entries.filter { it.core }.forEach { platform ->
                store.save(HotNewsBoard(platform, listOf(HotNewsItem("1", platform, 1,
                    "${platform.displayName} 资料导出工具", "https://example.com/${platform.key}/1",
                    originalContent = "${platform.key} 的来源正文介绍了将阅读资料保存在本地，并导出为 Markdown 文档的方法。".repeat(2))), now))
            }
            val articles = ArticleRepository(db)
            articles.insert(title = "收藏文章的资料整理方法", url = "https://example.com/favorite", isFavorite = 1,
                aiContent = "收藏文章介绍了如何整理个人阅读资料，并导出为 Markdown 方便在不同工具之间移动。".repeat(2))
            val configs = AIConfigRepository(db)
            configs.insert("openai", "https://ai.example/v1", "test-token", "test-model", isDefault = 1)
            val prompts = mutableListOf<String>()
            HttpClient(MockEngine { request ->
                assertEquals("ai.example", request.url.host, "Fresh cached or disabled sources must not fetch")
                val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                prompts += body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonPrimitive.content
                respond(buildJsonObject { putJsonArray("choices") { addJsonObject {
                    putJsonObject("message") { put("content", response) }; put("finish_reason", "stop")
                } } }.toString())
            }).use { http ->
                val hotNews = HotNewsService(HotNewsClient(http), store, { error("Cached Chinese text must not translate") })
                val summaries = UnifiedNewsSummaryRepository(db)
                val service = UnifiedNewsSummaryService(AiService(http), AiConfigService(configs), articles,
                    RemoteNewsSourceRepository(db), RemoteArticleSyncRepository(db), summaries, hotNews)
                block(Fixture(service, hotNews, summaries, prompts))
            }
        }
    }
}
