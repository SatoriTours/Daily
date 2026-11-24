package com.dailysatori.core.service

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.data.repository.ArticleAiRepository
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.SessionRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class WebArticleRoutesTest {
    @Test
    fun favoritesListPagesInSqlAndRemoteNewsUsesSmartReadingResults() {
        val context = RuntimeEnvironment.getApplication()
        val client = HttpClient()
        AndroidSqliteDriver(DailySatoriDatabase.Schema, context, null).use { driver ->
            val db = DailySatoriDatabase(driver)
            val articles = ArticleRepository(db)
            val settings = SettingRepository(db)
            settings.upsert("web_server_token", TOKEN)
            repeat(25) { articles.insert(title = "收藏 $it", url = "https://example.com/fav-$it", isFavorite = 1) }
            repeat(30) { articles.insert(title = "普通 $it", url = "https://example.com/plain-$it") }
            val remoteId = articles.insert(
                title = "远程新闻", aiContent = "旧摘要", aiMarkdownContent = "旧译文",
                url = "https://example.com/remote", status = "completed",
            )
            articles.updateOriginalMarkdownContent(remoteId, "# 原文\n\n远程新闻正文内容用于智能阅读。")
            driver.execute(null, "UPDATE article SET source_type='remote_news' WHERE id=$remoteId", 0)
            val plainId = articles.insert(title = "普通文章", aiContent = "普通摘要", url = "https://example.com/plain-article")
            stopKoin()
            startKoin { modules(module {
                single { articles }
                single { settings }
                single { SessionRepository(db) }
                single { ArticleAiRepository(db, settings) }
            }) }
            val service = WebServerService(context)
            try {
                val port = service.start()
                val favoritesPage = runBlockingGet(client, port, "/api/v2/articles?favorites=true&page=2")
                val items = favoritesPage["items"]!!.jsonArray
                assertEquals(5, items.size, "收藏分页必须由 SQL 分页，不能全表拉取后在内存切分")
                val pagination = favoritesPage["pagination"]!!.jsonObject
                assertEquals(25, pagination["totalItems"]!!.jsonPrimitive.int)
                assertEquals(2, pagination["page"]!!.jsonPrimitive.int)

                val remote = runBlockingGet(client, port, "/api/v2/articles/$remoteId")
                assertEquals("旧摘要", remote["aiContent"]!!.jsonPrimitive.content, "没有智能阅读结果时回退文章字段")
                assertEquals("旧译文", remote["aiMarkdownContent"]!!.jsonPrimitive.content)

                val body = ArticleAiRepository(db, settings).input(articles.getById(remoteId)!!)
                db.articleAiQueries.putArticleAiSource(remoteId, body.hash, body.kind.name, null, 0)
                db.articleAiQueries.putArticleAiResult(body.hash, "summary", 1, "zh-Hans", "智能摘要", 0)
                db.articleAiQueries.putArticleAiResult(body.hash, "translation", 1, "zh-Hans", "智能译文", 0)

                val enriched = runBlockingGet(client, port, "/api/v2/articles/$remoteId")
                assertEquals("智能摘要", enriched["aiContent"]!!.jsonPrimitive.content)
                assertEquals("智能译文", enriched["aiMarkdownContent"]!!.jsonPrimitive.content)
                assertEquals("智能摘要", enriched["articleAiSummary"]!!.jsonPrimitive.content)
                assertEquals("智能译文", enriched["articleAiTranslation"]!!.jsonPrimitive.content)
                assertTrue("旧摘要" !in enriched.toString(), "远程新闻应优先展示智能阅读结果")

                val plain = runBlockingGet(client, port, "/api/v2/articles/$plainId")
                assertEquals("普通摘要", plain["aiContent"]!!.jsonPrimitive.content)
                assertEquals("", plain["articleAiSummary"]!!.jsonPrimitive.content)
            } finally {
                service.stop()
                stopKoin()
                client.close()
            }
        }
    }

    private fun runBlockingGet(client: HttpClient, port: Int, path: String): JsonObject = kotlinx.coroutines.runBlocking {
        val response = client.get("http://127.0.0.1:$port$path") { header("Authorization", "Bearer $TOKEN") }
        assertEquals(200, response.status.value, path)
        Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("data").jsonObject
    }

    private companion object {
        const val TOKEN = "local-test-only"
    }
}
