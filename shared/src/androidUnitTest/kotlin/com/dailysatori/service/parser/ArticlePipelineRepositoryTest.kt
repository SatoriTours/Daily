package com.dailysatori.service.parser

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.service.ai.*
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.service.externalfavorites.ExternalFavoriteAiOrganizer
import com.dailysatori.service.externalfavorites.ExternalFavoriteImporter
import com.dailysatori.service.externalfavorites.ExternalFavoriteItemDraft
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import kotlin.test.*

class ArticlePipelineRepositoryTest {
    @Test fun failedAiKeepsTheLatestSuccessfulSummary() = runBlocking {
        withArticle { db, articles, id, _ ->
            HttpClient(MockEngine {
                articles.updateAiContent(id, "已经保存的成功摘要。", "已保存标题", null)
                respondCompletion("invalid json")
            }).use { client ->
                assertFailsWith<ArticleAiProcessingException> {
                    parser(db, client).saveWebpage("https://example.com/article", null, null, null, retryOnFailure = true)
                }
                assertEquals("已经保存的成功摘要。", articles.getById(id)?.ai_content)
                assertEquals("已保存标题", articles.getById(id)?.ai_title)
                assertEquals("retrying", articles.getById(id)?.status)
            }
        }
    }

    @Test fun concurrentSavesOfOneArticleShareOneOwner() = runBlocking {
        withArticle { db, articles, id, _ ->
            var requests = 0
            HttpClient(MockEngine {
                requests++
                delay(30)
                respondOverview("文章处理优化", "同一篇文章只处理一次。")
            }).use { client ->
                val service = parser(db, client)
                (1..3).map { async { service.saveWebpage("https://example.com/article", null, null, null) } }.awaitAll()
                assertEquals(1, requests)
                assertEquals("completed", articles.getById(id)?.status)
            }
        }
    }

    @Test fun externalChineseFavoriteUsesOverviewOnlyAndPreservesOriginal() = runBlocking {
        withArticle { db, articles, _, _ ->
            assignBackgroundModel(db)
            val sources = ExternalFavoriteSourceRepository(db)
            val sourceId = sources.save(provider = "x", displayName = "X", accountId = "test", accountName = "test", authJson = "{}")
            val items = ExternalFavoriteItemRepository(db)
            val original = "中文收藏先保存原文，再用一次 AI 请求生成标题和摘要。"
            items.upsertDraft(sourceId, ExternalFavoriteItemDraft("x", "123", "https://x.com/test/status/123",
                "中文收藏", original, "作者", null, null, "{}", contentHash = "a", aiInputHash = "a"))
            ExternalFavoriteImporter(items, articles).importPending()
            var requests = 0
            HttpClient(MockEngine { request ->
                requests++
                val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals("background.example", request.url.host)
                assertEquals("deepseek-v4-flash", body["model"]!!.jsonPrimitive.content)
                assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
                val system = body["messages"]!!.jsonArray.first().jsonObject["content"]!!.jsonPrimitive.content
                assertFalse(system.contains("\"markdown\""))
                respondOverview("中文收藏处理优化", "先保存完整正文，然后生成标题和摘要。")
            }).use { client ->
                val organizer = ExternalFavoriteAiOrganizer(items, articles, AiConfigService(AIConfigRepository(db)),
                    AiService(client), settingRepo = SettingRepository(db))
                assertEquals(1, organizer.organizePending())
                assertEquals(1, requests)
                val item = items.getBySourceExternalId(sourceId, "123")!!
                assertEquals(original, articles.getById(item.article_id!!)?.ai_markdown_content?.substringAfter("## AI 整理")?.trim())
                val summary = articles.getById(item.article_id!!)?.ai_content.orEmpty()
                assertTrue(summary.contains("**核心内容：**"))
                assertTrue(summary.contains("https://x.com/test/status/123"))
            }
        }
    }

    @Test fun deletingArticleRemovesItsAiCheckpoint() = runBlocking {
        withArticle { db, articles, id, _ ->
            val settings = SettingRepository(db)
            settings.upsert("article.ai.v3:$id", "private generated data")
            articles.delete(id)
            assertNull(settings.get("article.ai.v3:$id"))
            assertNull(articles.getById(id))
        }
    }
    @Test fun queuedSaveWaitsForItsOwnAiCompletion() = runBlocking {
        withArticle { db, articles, firstId, original ->
            val ids = listOf(firstId) + (2..3).map { index ->
                articles.createPendingFromUrl("https://example.com/article$index").also {
                    articles.updateOriginalMarkdownContent(it, original)
                }
            }
            HttpClient(MockEngine {
                delay(60)
                respondOverview("文章处理优化", "排队的文章也必须完成 AI 处理。")
            }).use { client ->
                val service = parser(db, client)
                ids.map { id -> async {
                    service.saveWebpage(articles.getById(id)!!.url!!, null, null, null)
                } }.awaitAll()
                ids.forEach { assertEquals("completed", articles.getById(it)?.status, "save 返回时该文章必须已经完成") }
            }
        }
    }
    @Test fun savesFullOriginalAndOverviewWithoutWaitingForCover() = runBlocking {
        withArticle { db, articles, id, original ->
            assignBackgroundModel(db)
            var scheduled = 0
            var requests = 0
            HttpClient(MockEngine {
                requests++
                assertEquals("/v1/chat/completions", it.url.encodedPath)
                assertEquals("background.example", it.url.host)
                val body = Json.parseToJsonElement(it.body.toByteArray().decodeToString()).jsonObject
                assertEquals("deepseek-v4-flash", body["model"]!!.jsonPrimitive.content)
                assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
                respondOverview("文章处理优化", "全文先保存，再生成标题和摘要。")
            }).use { client ->
                articles.fillCoverImageUrlIfMissing(id, "https://example.com/cover.jpg")
                parser(db, client, ArticleCoverScheduler { scheduled++ }).saveWebpage("https://example.com/article", null, null, null)
                val saved = articles.getById(id)!!
                assertEquals("completed", saved.status)
                assertEquals(original, saved.original_markdown_content)
                assertEquals(original, saved.ai_markdown_content)
                assertTrue(saved.ai_content.orEmpty().startsWith("**核心内容：** 全文先保存，再生成标题和摘要。"))
                assertTrue(saved.ai_content.orEmpty().endsWith("来源：[原文](<https://example.com/article>)"))
                assertEquals(1, requests)
                assertEquals(1, scheduled)
                assertNull(saved.cover_image)
            }
        }
    }

    @Test fun aiFailureKeepsOriginalAndDoesNotPretendToBeCompleted() = runBlocking {
        withArticle { db, articles, id, original ->
            HttpClient(MockEngine { respondCompletion("invalid json") }).use { client ->
                assertFailsWith<ArticleAiProcessingException> {
                    parser(db, client).saveWebpage("https://example.com/article", null, null, null, retryOnFailure = true)
                }
                val saved = articles.getById(id)!!
                assertEquals("retrying", saved.status)
                assertEquals(original, saved.original_markdown_content)
                assertEquals(original, saved.ai_markdown_content)
                assertNull(saved.ai_content)
            }
        }
    }

    @Test fun changedOriginalCancelsStaleResultBeforeItCanOverwriteNewContent() = runBlocking {
        withArticle { db, articles, id, _ ->
            HttpClient(MockEngine {
                articles.updateOriginalMarkdownContent(id, "新版正文，旧任务不能覆盖。")
                respondOverview("旧版标题", "旧版摘要。")
            }).use { client ->
                assertFailsWith<CancellationException> {
                    parser(db, client).saveWebpage("https://example.com/article", null, null, null)
                }
                assertEquals("新版正文，旧任务不能覆盖。", articles.getById(id)?.original_markdown_content)
                assertNull(articles.getById(id)?.ai_content)
            }
        }
    }

    @Test fun coverWriteNeverChangesAiStatusAndIgnoresOutdatedUrl() = runBlocking {
        withArticle { _, articles, id, _ ->
            articles.fillCoverImageUrlIfMissing(id, "https://example.com/new.jpg")
            articles.updateStatus(id, "completed")
            articles.updateCoverIfUrlMatches(id, "https://example.com/old.jpg", "old.png")
            assertNull(articles.getById(id)?.cover_image)
            articles.updateCoverIfUrlMatches(id, "https://example.com/new.jpg", "new.png")
            assertEquals("new.png", articles.getById(id)?.cover_image)
            assertEquals("completed", articles.getById(id)?.status)
        }
    }

    private suspend fun withArticle(test: suspend (DailySatoriDatabase, ArticleRepository, Long, String) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val articles = ArticleRepository(db)
            val id = articles.createPendingFromUrl("https://example.com/article")
            val original = "# 中文文章\n\n" + "完整保存正文。".repeat(500) + "\n\n末尾结论。"
            articles.updateOriginalMarkdownContent(id, original)
            val configs = AIConfigRepository(db)
            configs.insert("deepseek", "https://example.com/v1", "test-key", "deepseek-flash", isDefault = 1)
            test(db, articles, id, original)
        }
    }

    private fun parser(db: DailySatoriDatabase, client: HttpClient, covers: ArticleCoverScheduler = ArticleCoverScheduler {}) =
        WebpageParserService(ArticleRepository(db), TagRepository(db), ImageRepository(db), AiService(client),
            AiConfigService(AIConfigRepository(db)), WebViewLoader(), FileManager(), client,
            ExternalFavoriteSourceRepository(db), XBookmarksConnector(),
            settingRepo = SettingRepository(db), coverScheduler = covers)

    private fun MockRequestHandleScope.respondCompletion(content: String) = respond(buildJsonObject {
        put("choices", buildJsonArray { add(buildJsonObject {
            put("message", buildJsonObject { put("content", content) })
            put("finish_reason", "stop")
        }) })
    }.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.respondOverview(title: String, facts: String) = respondCompletion(buildJsonObject {
        put("title", title)
        put("facts", facts)
        put("importance", "保存的原文可在处理过程中阅读。")
        put("decision", "可先阅读原文，再查看处理结果。")
        put("isNews", false)
    }.toString())

    private fun assignBackgroundModel(db: DailySatoriDatabase) {
        val configs = AIConfigRepository(db)
        configs.insert("opencode-go", "https://background.example/v1", "test-token", "deepseek-v4-flash")
        val id = configs.getAllSync().single { it.model_name == "deepseek-v4-flash" }.id
        configs.setPurposeConfig(AiPurpose.EXTERNAL_CONTENT, id)
    }

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
