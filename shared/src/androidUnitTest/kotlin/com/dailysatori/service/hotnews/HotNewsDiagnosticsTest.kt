package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.diagnostics.*
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.net.SocketException
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class HotNewsDiagnosticsTest {
    @Test
    fun acceptedRetryConnectsBatchAttemptsModelAndResultThroughOneTraceWithoutContent() = runBlocking {
        Fixture(networkFailures = 2).use { fixture ->
            fixture.service.retryTranslations(HotNewsPlatform.GITHUB)
            assertEquals(3, fixture.requests)
            assertNull(fixture.board.error)
            assertEquals("中文工具标题", fixture.board.items.single().chineseTitle)
            val events = fixture.events.toList()
            assertTrue(events.any { it.attributes["operation"] == "hot_news_retry" && it.attributes["hotNewsPlatform"] == "github" })
            assertEquals(listOf("1", "2", "3"), events.filter {
                it.attributes["hotNewsStage"] == "translation_request"
            }.map { it.attributes["attempt"] })
            assertEquals(2, events.count { it.attributes["hotNewsFailure"] == "network" })
            val result = events.single { it.attributes["hotNewsStage"] == "translation_result" }
            assertEquals("1", result.attributes["translatedCount"])
            assertEquals("0", result.attributes["pendingCount"])
            assertTrue(events.any { it.attributes["aiPurpose"] == "external_content" && it.attributes["model"] == "deepseek-v4-flash" })
            assertEquals(1, events.map { it.traceId }.distinct().size)
            assertNotNull(events.first().traceId)
            assertEquals(DiagnosticCode.OPERATION_END, events.last().eventCode)
            assertPrivateDataAbsent(events)
        }
    }

    @Test
    fun exhaustedNetworkRetryRecordsFailureInsteadOfBusinessSuccess() = runBlocking {
        Fixture(networkFailures = 3).use { fixture ->
            fixture.service.retryTranslations(HotNewsPlatform.GITHUB)
            assertEquals(HotNewsError.TRANSLATION_NETWORK_FAILED, fixture.board.error)
            assertEquals(3, fixture.events.count { it.attributes["hotNewsFailure"] == "network" })
            assertEquals(DiagnosticCode.OPERATION_FAILED, fixture.events.last().eventCode)
            assertPrivateDataAbsent(fixture.events.toList())
        }
    }

    @Test
    fun invalidTranslationResponseIsDistinguishedFromTransportFailure() = runBlocking {
        Fixture(invalidTranslation = true).use { fixture ->
            fixture.service.retryTranslations(HotNewsPlatform.GITHUB)
            assertEquals(HotNewsError.TRANSLATION_FAILED, fixture.board.error)
            assertEquals(1, fixture.requests)
            assertTrue(fixture.events.any { it.attributes["hotNewsFailure"] == "format" })
            assertFalse(fixture.events.any { it.attributes["hotNewsFailure"] == "network" })
            assertPrivateDataAbsent(fixture.events.toList())
        }
    }

    @Test
    fun missingAiConfigurationIsDiagnosedWithoutMakingARequest() = runBlocking {
        Fixture(configured = false).use { fixture ->
            fixture.service.retryTranslations(HotNewsPlatform.GITHUB)
            assertEquals(0, fixture.requests)
            assertEquals(HotNewsError.AI_NOT_CONFIGURED, fixture.board.error)
            assertTrue(fixture.events.any { it.attributes["hotNewsFailure"] == "configuration" })
            assertEquals(DiagnosticCode.OPERATION_FAILED, fixture.events.last().eventCode)
        }
    }

    @Test
    fun completedCacheExplainsAnImmediateReturnWithoutCallingAi() = runBlocking {
        Fixture(completedCache = true).use { fixture ->
            fixture.service.retryTranslations(HotNewsPlatform.GITHUB)
            assertEquals(0, fixture.requests)
            val cached = fixture.events.single { it.attributes["hotNewsStage"] == "cache" }
            assertEquals("true", cached.attributes["cacheUsed"])
            assertEquals("0", cached.attributes["pendingCount"])
            assertEquals("1", cached.attributes["count"])
            assertEquals(DiagnosticCode.OPERATION_END, fixture.events.last().eventCode)
        }
    }

    private fun assertPrivateDataAbsent(events: List<SafeDiagnosticEvent>) {
        val encoded = Json.encodeToString(events)
        assertFalse(encoded.contains("canary"), "请求、响应、原始条目和异常消息都不得落盘")
        assertTrue(events.all { it.contentOmitted })
    }

    private class Fixture(
        networkFailures: Int = 0,
        invalidTranslation: Boolean = false,
        configured: Boolean = true,
        completedCache: Boolean = false,
    ) : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val previousDiagnostics = DiagnosticLog.diagnostics
        val events = Collections.synchronizedList(mutableListOf<SafeDiagnosticEvent>())
        var requests = 0
            private set
        private val http = HttpClient(MockEngine {
            requests++
            if (requests <= networkFailures) throw SocketException("private-network-canary")
            val translated = if (invalidTranslation) "private-invalid-response-canary" else """[
                {"id":"github:private-id-canary","title":"中文工具标题","summary":"中文工具简介"}
            ]"""
            respond(buildJsonObject {
                putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", translated) } } }
                put("private", "private-response-canary")
            }.toString())
        })
        val service: HotNewsService
        val board get() = service.boards.value.first { it.platform == HotNewsPlatform.GITHUB }

        init {
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            val configs = AIConfigRepository(database)
            if (configured) configs.insert("deepseek", "https://api.deepseek.com", "private-token-canary", "deepseek-v4-flash", isDefault = 1)
            val store = HotNewsStore(SettingRepository(database))
            val item = HotNewsItem("private-id-canary", HotNewsPlatform.GITHUB, 1, "private-title-canary",
                "https://private-host-canary.invalid/private-path-canary", description = "private-description-canary",
                chineseTitle = if (completedCache) "已完成的中文标题" else null,
                chineseSummary = if (completedCache) "已完成的中文简介" else null)
            store.save(HotNewsBoard(HotNewsPlatform.GITHUB, listOf(item), 1L, HotNewsError.TRANSLATION_FAILED))
            val translator = HotNewsTranslator(AiService(http), AiConfigService(configs))
            service = HotNewsService(HotNewsClient(http), store, translator::translate)
            DiagnosticLog.diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
        }

        override fun close() {
            http.close()
            driver.close()
            DiagnosticLog.diagnostics = previousDiagnostics
        }
    }
}
