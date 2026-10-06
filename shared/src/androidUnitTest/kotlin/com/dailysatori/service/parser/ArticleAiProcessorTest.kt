package com.dailysatori.service.parser

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ArticleAiProcessorTest {
    private val config = NormalizedAiConfigValues("https://example.com/v1", "test-key", "deepseek-flash", "deepseek")

    @Test fun translationKeepsCodeAndDestinationsAndRejectsMissingPlaceholders() {
        val source = "Read [docs](https://example.com/docs).\n\n```kotlin\nprintln(\"English code\")\n```\n\n![图](https://example.com/a.png)"
        val protected = protectArticleMarkdown(source)
        val restored = protected.restore(protected.text.replace("Read", "阅读"))
        assertTrue(restored.contains("println(\"English code\")"))
        assertTrue(restored.contains("https://example.com/docs"))
        assertFailsWith<IllegalArgumentException> { protected.restore("丢失了代码和链接") }
        assertFalse(articleNeedsTranslation("中文技术文章\n\n```kotlin\nThis is English inside code\n```"))
        assertTrue(articleNeedsTranslation("中文标题\n\n" + "This is English body text with many words. ".repeat(10)))
    }

    @Test fun tokenLimitResponseIsRejectedEvenWhenJsonLooksValid() = runBlocking {
        val response = buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject {
                put("finish_reason", "length")
                put("message", buildJsonObject { put("content", "valid-looking content") })
            }) })
        }
        assertFailsWith<IllegalArgumentException> { com.dailysatori.service.ai.extractOpenAiTextCompletionContent(response) }
        Unit
    }

    @Test fun chineseArticleNeedsOnlyOverviewAndKeepsFullOriginal() = runBlocking {
        withProcessor({ overviewResponse() }) { processor, requests, _ ->
            val source = "# 原标题\n\n正文忠实保留。\n\n![图](https://example.com/a.png)"
            var markdown = ""
            processor.process(1, source, "标题", config, onMarkdown = { markdown = it })
            assertEquals(source, markdown)
            assertEquals(1, requests.size)
            assertEquals("disabled", requests.single()["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertTrue(prompt(requests.single()).contains("正文忠实保留"))
        }
    }

    @Test fun failedOverviewDoesNotTriggerThreeFallbackRequests() = runBlocking {
        withProcessor({ "invalid json" }) { processor, requests, _ ->
            assertFailsWith<ArticleAiProcessingException> {
                processor.process(2, "这是正文。", "标题", config)
            }
            assertEquals(1, requests.size)
        }
    }

    @Test fun summaryIsPublishedBeforeSlowTranslationAndRetryReusesSuccessfulStages() = runBlocking {
        var failTranslation = true
        var overviewPublished = false
        withProcessor({ request ->
            if (system(request).contains("DAILY_TRANSLATION_END")) {
                delay(30)
                assertTrue(overviewPublished)
                if (failTranslation) error("network failure")
                "这是一段关于软件性能的英文文章译文。\n<!-- DAILY_TRANSLATION_END -->"
            } else overviewResponse()
        }) { processor, requests, createProcessor ->
            val source = "This is an English article about software performance and reliable processing."
            assertFailsWith<ArticleAiProcessingException> {
                processor.process(3, source, "English", config, onOverview = { overviewPublished = true })
            }
            assertEquals(2, requests.size)
            failTranslation = false
            val resumed = createProcessor()
            var translated = ""
            resumed.process(3, source, "English", config, onMarkdown = { translated = it })
            assertEquals(3, requests.size, "恢复不能重新生成已成功的摘要")
            assertTrue(translated.contains("译文"))
            assertFalse(translated.contains("DAILY_TRANSLATION_END"))
        }
    }

    @Test fun longArticleSummariesIncludeLastChunkAndResumeWithoutRepeatingSuccessfulChunks() = runBlocking {
        var failLast = true
        val seen = mutableListOf<String>()
        withProcessor({ request ->
            val input = prompt(request)
            seen += input
            if (input.contains("末尾独有结论") && failLast) error("network failure")
            overviewResponse()
        }) { processor, _, createProcessor ->
            val source = "开始内容。".repeat(900) + "\n\n" + "中间内容。".repeat(900) + "\n\n末尾独有结论。"
            assertFailsWith<ArticleAiProcessingException> { processor.process(4, source, "长文", config) }
            val successfulFirstCalls = seen.count { it.contains("开始内容") }
            failLast = false
            createProcessor().process(4, source, "长文", config)
            assertEquals(successfulFirstCalls, seen.count { it.contains("开始内容") })
            assertTrue(seen.any { it.contains("末尾独有结论") })
        }
    }

    @Test fun changedSourceInvalidatesCheckpoint() = runBlocking {
        withProcessor({ overviewResponse() }) { processor, requests, _ ->
            processor.process(5, "旧版中文正文。", "标题", config)
            processor.process(5, "新版中文正文。", "标题", config)
            assertEquals(2, requests.size)
            assertTrue(prompt(requests.last()).contains("新版中文正文"))
        }
    }

    @Test fun truncatedTranslationIsRejectedInsteadOfPublished() = runBlocking {
        withProcessor({ request -> if (system(request).contains("DAILY_TRANSLATION_END")) "截断的译文" else overviewResponse() }) { processor, _, _ ->
            var published = false
            assertFailsWith<ArticleAiProcessingException> {
                processor.process(6, "This article contains important facts about reliable software systems.", "English", config,
                    onMarkdown = { published = true })
            }
            assertFalse(published)
        }
    }

    @Test fun cancellationIsPropagatedWithoutConvertingToFailure() = runBlocking {
        withProcessor({ throw CancellationException("cancelled") }) { processor, _, _ ->
            assertFailsWith<CancellationException> { processor.process(7, "中文正文。", "标题", config) }
        }
    }

    private suspend fun withProcessor(
        respondText: suspend (JsonObject) -> String,
        test: suspend (ArticleAiProcessor, MutableList<JsonObject>, () -> ArticleAiProcessor) -> Unit,
    ) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            val requests = mutableListOf<JsonObject>()
            HttpClient(MockEngine { request ->
                val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                requests += body
                val content = respondText(body)
                respond(buildJsonObject {
                    put("choices", buildJsonArray { add(buildJsonObject {
                        put("message", buildJsonObject { put("content", content) })
                        put("finish_reason", "stop")
                    }) })
                }.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val ai = AiService(client)
                test(ArticleAiProcessor(ai, settings), requests) { ArticleAiProcessor(ai, settings) }
            }
        }
    }

    private fun overviewResponse() = """{"title":"软件性能优化","summary":"保留正文事实并改善处理效率。"}"""
    private fun prompt(request: JsonObject) = request["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonPrimitive.content
    private fun system(request: JsonObject) = request["messages"]!!.jsonArray.first().jsonObject["content"]!!.jsonPrimitive.content
}
