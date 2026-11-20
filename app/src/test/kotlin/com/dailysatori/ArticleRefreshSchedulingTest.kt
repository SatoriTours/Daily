package com.dailysatori

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.ImageRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.service.parser.ExtractedContent
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 用真实解析服务驱动抓取与 AI 管线，验证排队刷新意图不会被在途任务清理。
 * 抓取走 fxEmbed mock，AI 走本地 mock，正文标记用于统计每篇文章的请求次数。
 */
class ArticleRefreshSchedulingTest {
    @Test
    fun refreshRequestedWhileTheSameArticleRunsExecutesOnceAfterRelease() = runBlocking {
        withFixture { fixture ->
            val id = fixture.insertArticle("https://x.com/writer/status/1001")
            fixture.blockFetch("1001")
            val first = async(Dispatchers.Default) { fixture.parser.refreshArticle(id) }
            fixture.awaitFetchStarted("1001")

            fixture.parser.refreshArticle(id)
            fixture.parser.refreshArticle(id)
            fixture.releaseFetch("1001")
            first.await()

            fixture.awaitAiCount("1001", expected = 2)
            fixture.awaitArticleSettled(id)
            delay(200)
            assertEquals(2, fixture.fetchCount("1001"), "在途任务结束后应重抓一次")
            assertEquals(2, fixture.aiCount("1001"), "内容相同也必须重做 AI")
            assertEquals(2, fixture.fetchCount("1001"), "重复点击只能合并为一次排队刷新")
        }
    }

    @Test
    fun queuedRefreshRerunsExtractionAndAiAfterASlotFrees() = runBlocking {
        withFixture { fixture ->
            val a = fixture.insertArticle("https://x.com/writer/status/2001")
            val b = fixture.insertArticle("https://x.com/writer/status/2002")
            val cUrl = "https://x.com/writer/status/2003"
            val extracted = fixture.parser.extractContent(cUrl)
            val c = fixture.insertExtractedArticle(cUrl, extracted)
            fixture.parser.reprocessArticle(c)
            assertEquals(1, fixture.aiCount("2003"), "先按已存正文处理一次")

            fixture.blockFetch("2001")
            fixture.blockFetch("2002")
            val first = async(Dispatchers.Default) { fixture.parser.refreshArticle(a) }
            val second = async(Dispatchers.Default) { fixture.parser.refreshArticle(b) }
            fixture.awaitFetchStarted("2001")
            fixture.awaitFetchStarted("2002")

            fixture.parser.refreshArticle(c)
            assertEquals(1, fixture.fetchCount("2003"), "槽满时刷新只能登记意图")

            fixture.releaseFetch("2001")
            first.await()
            fixture.awaitAiCount("2003", expected = 2)
            fixture.awaitArticleSettled(c)
            fixture.releaseFetch("2002")
            second.await()

            assertEquals(2, fixture.fetchCount("2003"), "排队刷新必须重新抓取")
            assertEquals(2, fixture.aiCount("2003"), "排队刷新必须重做 AI")
        }
    }

    private suspend fun withFixture(test: suspend (Fixture) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            AIConfigRepository(db).insert("deepseek", "https://ai.example/v1", "test-key", "deepseek-flash", isDefault = 1)
            test(Fixture(db))
        }
    }

    private class Fixture(db: DailySatoriDatabase) {
        val articles = ArticleRepository(db)
        private val fetchCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val aiCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val fetchGates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        private val fetchStarted = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        private val markers = ConcurrentHashMap.newKeySet<String>()

        private val engine = MockEngine { request ->
            when (request.url.host) {
                "api.fxtwitter.com" -> {
                    val postId = request.url.encodedPath.trim('/').split('/').last()
                    val count = fetchCounts.computeIfAbsent(postId) { AtomicInteger() }.incrementAndGet()
                    if (count == 1) {
                        fetchStarted.computeIfAbsent(postId) { CompletableDeferred() }.complete(Unit)
                        fetchGates[postId]?.await()
                    }
                    respond(fxEmbedJson(postId), headers = jsonHeaders())
                }
                "ai.example" -> {
                    val body = request.body.toByteArray().decodeToString()
                    markers.firstOrNull { body.contains(it) }?.let { marker ->
                        aiCounts.computeIfAbsent(marker) { AtomicInteger() }.incrementAndGet()
                    }
                    respond(aiResponse(), headers = jsonHeaders())
                }
                else -> error("unexpected request: " + request.url)
            }
        }
        private val client = HttpClient(engine)

        val parser = WebpageParserService(
            articles, TagRepository(db), ImageRepository(db), AiService(client),
            AiConfigService(AIConfigRepository(db)), WebViewLoader(), FileManager(), client,
            ExternalFavoriteSourceRepository(db), XBookmarksConnector(client),
            settingRepo = SettingRepository(db),
        )

        fun insertArticle(url: String): Long {
            val postId = url.substringAfterLast('/')
            markers += bodyText(postId)
            val id = articles.insert(title = "标题" + postId, url = url, status = "completed")
            articles.updateOriginalMarkdownContent(id, bodyText(postId))
            return id
        }

        fun insertExtractedArticle(url: String, extracted: ExtractedContent): Long {
            val postId = url.substringAfterLast('/')
            markers += bodyText(postId)
            val id = articles.insert(title = extracted.title, url = url, status = "completed")
            articles.updateOriginalMarkdownContent(id, extracted.content.orEmpty())
            return id
        }

        fun blockFetch(postId: String) {
            markers += bodyText(postId)
            fetchGates[postId] = CompletableDeferred()
        }

        fun releaseFetch(postId: String) {
            fetchGates[postId]?.complete(Unit)
        }

        suspend fun awaitFetchStarted(postId: String) = withTimeout(10_000) {
            fetchStarted.computeIfAbsent(postId) { CompletableDeferred() }.await()
        }

        suspend fun awaitAiCount(postId: String, expected: Int) = withTimeout(10_000) {
            while (aiCount(postId) < expected) delay(20)
        }

        suspend fun awaitArticleSettled(articleId: Long) = withTimeout(10_000) {
            while (articles.getById(articleId)?.status != "completed") delay(20)
        }

        fun fetchCount(postId: String): Int = fetchCounts[postId]?.get() ?: 0

        fun aiCount(postId: String): Int = aiCounts[bodyText(postId)]?.get() ?: 0

        private fun bodyText(postId: String): String =
            "文章" + postId + "的唯一正文标记，用于统计抓取与 AI 请求。"

        private fun fxEmbedJson(postId: String) = buildJsonObject {
            put("code", 200)
            putJsonObject("tweet") {
                put("id", postId)
                put("text", bodyText(postId))
                putJsonObject("author") {
                    put("id", "1")
                    put("screen_name", "writer")
                    put("name", "Writer")
                }
            }
        }.toString()

        private fun aiResponse(): String {
            val content = buildJsonObject {
                put("title", "排队刷新测试")
                put("facts", "保存的正文已经重新处理。")
                put("importance", "用于验证排队刷新确实执行。")
                put("decision", "不需要额外操作。")
                put("isNews", false)
            }.toString()
            return buildJsonObject {
                put("choices", buildJsonArray {
                    add(buildJsonObject {
                        putJsonObject("message") { put("content", content) }
                        put("finish_reason", "stop")
                    })
                })
            }.toString()
        }

        private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
