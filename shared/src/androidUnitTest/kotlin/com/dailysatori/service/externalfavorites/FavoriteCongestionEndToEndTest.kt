package com.dailysatori.service.externalfavorites

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.ai.AiQueueWaitTimeout
import com.dailysatori.service.ai.AiRequestCoordinator
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.ai.withAiRequestSession
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verification-only end-to-end probe: real AiService + real admission gate + real organizer,
 * with the gate fully congested (both permits held). Design claim: the favorite stays PENDING and
 * is counted as deferred, never failed, so an existing 3/20 progress keeps advancing.
 */
class FavoriteCongestionEndToEndTest {
    @Test
    fun congestedGateOnTheRealPathKeepsTheFavoritePending() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = ExternalFavoriteSourceRepository(db)
            val items = ExternalFavoriteItemRepository(db)
            val articles = ArticleRepository(db)
            val configs = AIConfigRepository(db)
            configs.insert("deepseek", "https://example.com/v1", "test-key", "deepseek-flash", isDefault = 1)
            configs.setPurposeConfig(AiPurpose.EXTERNAL_CONTENT, configs.getAllSync().first().id)

            val sourceId = sources.save(provider = "x", displayName = "X", accountId = "42", accountName = "Writer", authJson = "{}", enabled = true)
            val (item, _) = items.upsertDraft(sourceId, ExternalFavoriteItemDraft("x", "123", "https://x.com/writer/status/123", "Article",
                "Body text that is long enough for analysis.", "Writer", null, null, "{}", contentHash = "hash", aiInputHash = "ai"))
            val articleId = articles.insert(title = "Article", url = "https://x.com/writer/status/123", status = "completed")
            articles.updateOriginalMarkdownContent(articleId, "正文内容足够长，用于测试真实 AI 路径的拥塞行为。")
            articles.updateAiTitle(articleId, "Article")
            items.markImported(item.id, articleId, false)

            val gate = AiRequestCoordinator(backgroundIntervalMs = 0)
            HttpClient(MockEngine {
                delay(20_000)
                respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val aiService = AiService(client, gate)
                val release = CompletableDeferred<Unit>()
                val holdersStarted = CompletableDeferred<Unit>()
                var holderCount = 0
                val holders = listOf(
                    launch { withAiRequestSession { gate.withRequest("https://example.com/v1", AiPurpose.EXTERNAL_CONTENT) { holderCount++; if (holderCount == 2) holdersStarted.complete(Unit); release.await() } } },
                    launch { withAiRequestSession { gate.withRequest("https://example.com/v1", AiPurpose.INTERACTIVE) { holderCount++; if (holderCount == 2) holdersStarted.complete(Unit); release.await() } } },
                )
                holdersStarted.await()

                val organizer = ExternalFavoriteAiOrganizer(
                    items, articles,
                    aiConfigService = AiConfigService(AIConfigRepository(db)),
                    aiService = aiService,
                    supplementResolver = object : ExternalFavoriteSupplementResolver {
                        override suspend fun resolve(item: com.dailysatori.shared.db.External_favorite_item,
                            input: ExternalFavoriteAiInput, httpLogger: FavoriteSyncHttpLogger, taskId: Long?) = null
                    },
                    retryDelayMs = 0,
                    itemTimeoutMs = 400,
                    settingRepo = SettingRepository(db),
                )
                val progress = mutableListOf<ExternalFavoriteAiProgress>()
                val startedAt = System.currentTimeMillis()
                organizer.organizePendingForSource(sourceId, limit = 1) { progress += it }
                val elapsed = System.currentTimeMillis() - startedAt
                val stored = items.getBySourceExternalId(sourceId, "123")!!
                println("REGRESSION_E2E elapsed=" + elapsed + " ai_status=" + stored.ai_status + " msg=" + stored.last_error_message + " error=" + stored.last_error_code +
                    " progress=" + progress.map { "p=" + it.processed + "f=" + it.failed + "d=" + it.deferred })
                release.complete(Unit)
                holders.forEach { it.cancel() }
                assertEquals(ExternalItemAiStatus.pending.name, stored.ai_status, "congestion must keep the favorite pending")
                assertEquals(0, progress.last().failed, "an unsent favorite is not a failure")
                assertEquals(1, progress.last().deferred, "an unsent favorite is deferred")
            }
        }
    }

    /**
     * Core path from the acceptance probe: a deferred item stays pending, keeps its completed neighbour, and the
     * very next run finishes it. Nothing is redone and no failure is recorded in between.
     */
    @Test
    fun aDeferredFavoriteIsFinishedByTheNextRun() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = ExternalFavoriteSourceRepository(db)
            val items = ExternalFavoriteItemRepository(db)
            val articles = ArticleRepository(db)
            val sourceId = sources.save(provider = "x", displayName = "X", accountId = "42", accountName = "Writer", authJson = "{}", enabled = true)
            val (pendingItem, _) = items.upsertDraft(sourceId, ExternalFavoriteItemDraft("x", "123", "https://x.com/writer/status/123", "Article",
                "Body text long enough.", "Writer", null, null, "{}", contentHash = "hash", aiInputHash = "ai"))
            items.markImported(pendingItem.id, articles.insert(title = "Article", url = "https://x.com/writer/status/123", status = "completed"), false)
            val (doneItem, _) = items.upsertDraft(sourceId, ExternalFavoriteItemDraft("x", "456", "https://x.com/writer/status/456", "Article",
                "Second body long enough.", "Writer", null, null, "{}", contentHash = "h2", aiInputHash = "ai2"))
            items.markImported(doneItem.id, articles.insert(title = "Article2", url = "https://x.com/writer/status/456", status = "completed"), false)
            items.markAiState(doneItem.id, ExternalItemAiStatus.completed.name)

            suspend fun runOrganizer(generate: suspend () -> ExternalFavoriteAiAnalysis): List<ExternalFavoriteAiProgress> {
                val progress = mutableListOf<ExternalFavoriteAiProgress>()
                ExternalFavoriteAiOrganizer(
                    items, articles, retryDelayMs = 0, itemTimeoutMs = 90_000, generateAnalysis = { generate() },
                ).organizePendingForSource(sourceId, limit = 20) { progress += it }
                return progress
            }

            val first = runOrganizer { throw AiQueueWaitTimeout(90_000) }
            val afterDeferral = items.getBySourceExternalId(sourceId, "123")!!
            assertEquals(ExternalItemAiStatus.pending.name, afterDeferral.ai_status, "a deferral never fails the item")
            assertEquals(1, first.last().deferred)
            assertEquals(ExternalItemAiStatus.completed.name, items.getBySourceExternalId(sourceId, "456")!!.ai_status,
                "an already completed favourite is not redone")

            runOrganizer { ExternalFavoriteAiAnalysis("t", "s", "m") }
            assertEquals(ExternalItemAiStatus.completed.name, items.getBySourceExternalId(sourceId, "123")!!.ai_status,
                "the deferred item is picked up and finished by the next run")
        }
    }
}
