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

    @Test fun newsBriefSeparatesFactsAnalysisAndVerifiedSourceInOneRequest() = runBlocking {
        withProcessor({ briefResponse() }) { processor, requests, _ ->
            var summary = ""
            processor.process(20, "10月5日，平台宣布故障恢复，根因报告尚未发布。", "平台故障恢复", config,
                sourceUrl = "https://example.com/incident", onOverview = { summary = it.summary })
            assertTrue(summary.startsWith("10月5日\n\n**新闻事实：**"))
            assertTrue(summary.contains("**为什么重要（分析）：**"))
            assertTrue(summary.contains("**工作／技术决策（分析）：**"))
            assertTrue(summary.endsWith("来源：[原文](<https://example.com/incident>)"))
            assertTrue(summary.contains("根因报告尚未发布"))
            assertEquals(1, requests.size)
        }
    }

    @Test fun nonNewsBriefUsesCoreContentAndDoesNotInventEventTime() = runBlocking {
        withProcessor({ briefResponse(news = false, time = null) }) { processor, _, _ ->
            var summary = ""
            for (sourceUrl in listOf("", "javascript:alert(1)", "example.com/report")) {
                processor.process(21, "技术文章讲解可靠处理流程。", "可靠处理", config,
                    sourceUrl = sourceUrl, onOverview = { summary = it.summary })
                assertFalse(summary.contains("来源："), "没有合法原文地址时不能构造来源链接")
            }
            assertTrue(summary.startsWith("**核心内容：**"))
            assertFalse(summary.contains("新闻事实"))
            assertFalse(summary.contains("10月5日"))
            assertFalse(summary.contains("javascript:"))
        }
    }

    @Test fun incompleteBriefAndModelInventedLinksAreNotPublished() = runBlocking {
        for (response in listOf(briefResponse().replace("建议检查失败任务。", ""),
            briefResponse().replace("建议检查失败任务。", "参考 https://invented.example/report"))) {
            withProcessor({ response }) { processor, _, _ ->
                var published = false
                assertFailsWith<ArticleAiProcessingException> {
                    processor.process(22, "平台恢复服务，根因未披露。", "恢复服务", config,
                        onOverview = { published = true })
                }
                assertFalse(published)
            }
        }
    }

    @Test fun longArticleExtractsFactsBeforeProducingOneFinalAnalysis() = runBlocking {
        withProcessor({ request ->
            if (system(request).contains("只提取资料中的事实")) factsResponse() else briefResponse()
        }) { processor, requests, _ ->
            processor.process(23, "详细的技术事实。".repeat(1_000) + "\n\n末尾独有结论。", "长文", config)
            val analyses = requests.filter { system(it).contains("工作／技术决策") }
            assertEquals(1, analyses.size)
            assertTrue(requests.count { system(it).contains("只提取资料中的事实") } >= 2)
        }
    }

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
            if (system(request).contains("只提取资料中的事实")) factsResponse() else overviewResponse()
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

    @Test fun oldOverviewCheckpointIsRegeneratedWithNewFormat() = runBlocking {
        val source = "中文正文。"
        val fingerprint = com.dailysatori.service.externalfavorites.sha256Hex(
            "article-v3\n$source\n标题\n${config.apiAddress}\n${config.provider}\n${config.modelName}")
        withProcessor({ overviewResponse() }, prepare = { settings ->
            settings.upsert("article.ai.v3:24", buildJsonObject {
                put("fingerprint", fingerprint)
                put("overview", buildJsonObject { put("title", "旧版标题"); put("summary", "旧版普通摘要。") })
            }.toString())
        }) { processor, requests, _ ->
            var summary = ""
            processor.process(24, source, "标题", config, onOverview = { summary = it.summary })
            assertEquals(1, requests.size)
            assertTrue(summary.contains("工作／技术决策（分析）"))
            assertFalse(summary.contains("旧版普通摘要"))
        }
    }

    @Test fun changedSourceUrlDoesNotReuseTheOldSourceLink() = runBlocking {
        withProcessor({ overviewResponse() }) { processor, requests, _ ->
            var summary = ""
            for (url in listOf("https://example.com/old", "https://example.com/new")) {
                processor.process(25, "中文正文。", "标题", config, sourceUrl = url, onOverview = { summary = it.summary })
            }
            assertEquals(2, requests.size)
            assertTrue(summary.contains("https://example.com/new"))
            assertFalse(summary.contains("https://example.com/old"))
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

    @Test fun goLongArticleRequestsShareOneSessionButOtherArticlesDoNot() = runBlocking {
        val sessions = java.util.Collections.synchronizedList(mutableListOf<String>())
        withProcessor({ request ->
            if (system(request).contains("只提取资料中的事实")) factsResponse() else briefResponse()
        }, observeRequest = { sessions += it.headers["x-opencode-session"].orEmpty() }) { processor, requests, _ ->
            val go = config.copy(apiAddress = "https://opencode.ai/zen/go/v1", provider = "opencode-go", modelName = "glm-5.2")
            processor.process(100, "详细的技术事实。".repeat(1_000), "长文", go)
            assertTrue(requests.size >= 3)
            assertEquals(1, sessions.distinct().size)
            val first = sessions.first()
            assertTrue(first.isNotBlank())
            processor.process(101, "另一篇文章。", "短文", go)
            assertNotEquals(first, sessions.last())
        }
    }

    private suspend fun withProcessor(
        respondText: suspend (JsonObject) -> String,
        prepare: (SettingRepository) -> Unit = {},
        observeRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {},
        test: suspend (ArticleAiProcessor, MutableList<JsonObject>, () -> ArticleAiProcessor) -> Unit,
    ) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            prepare(settings)
            val requests = mutableListOf<JsonObject>()
            HttpClient(MockEngine { request ->
                observeRequest(request)
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

    private fun overviewResponse() = briefResponse(news = false, time = null)
    private fun factsResponse() = """{"title":"软件性能优化","summary":"保留正文事实并改善处理效率。"}"""
    private fun briefResponse(news: Boolean = true, time: String? = "10月5日") = buildJsonObject {
        put("title", "平台故障已恢复，先检查失败任务")
        put("isNews", news)
        put("eventTime", time?.let(::JsonPrimitive) ?: JsonNull)
        put("facts", "平台宣布故障恢复，根因报告尚未发布。")
        put("importance", "平台故障可能影响任务执行，具体影响仍需核查。")
        put("decision", "建议检查失败任务。")
    }.toString()
    private fun prompt(request: JsonObject) = request["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonPrimitive.content
    private fun system(request: JsonObject) = request["messages"]!!.jsonArray.first().jsonObject["content"]!!.jsonPrimitive.content
}
