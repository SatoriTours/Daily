package com.dailysatori.service.externalfavorites

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.External_favorite_item
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class XArticleCacheTest {
    @Test fun persistsBodyBeforeAiFailureAndReusesItOnRetry() = runBlocking {
        Env().use { env ->
            val articleId = env.item("123")
            var fetches = 0
            val organizer = env.organizer({ fetches++; fullBody() }) {
                assertEquals(BODY, env.articles.getById(articleId)?.original_markdown_content)
                error("Synthetic AI failure")
            }
            organizer.organizePending()
            assertEquals(BODY, env.articles.getById(articleId)?.original_markdown_content)
            assertEquals("failed", env.items.getBySourceExternalId(env.sourceId, "123")?.ai_status)
            env.organizer({ fetches++; fullBody() }) { input ->
                assertEquals(BODY, input.supplementText)
                analysis()
            }.organizePending(includeFailed = true)
            assertEquals(1, fetches)
        }
    }

    @Test fun unchangedRemoteDraftKeepsCacheWithoutReimporting() = runBlocking {
        Env().use { env ->
            env.item("123")
            var fetches = 0
            env.organizer({ fetches++; fullBody() }) { error("Synthetic AI failure") }.organizePending()
            val (_, changed) = env.items.upsertDraft(env.sourceId, draft("123"))
            assertFalse(changed)
            env.organizer({ fetches++; fullBody() }) { analysis() }.organizePending(includeFailed = true)
            assertEquals(1, fetches)
        }
    }

    @Test fun changedSourceDoesNotReuseOldCacheOrOverwriteManualOriginal() = runBlocking {
        Env().use { env ->
            val id = env.item("123", original = "Manual original")
            var fetches = 0
            env.organizer({ fetches++; fullBody() }) { analysis() }.organizePending()
            env.items.upsertDraft(env.sourceId, draft("123").copy(contentHash = "changed", aiInputHash = "changed-ai"))
            env.organizer({ fetches++; fullBody() }) { analysis() }.organizePending()
            assertEquals(2, fetches)
            assertEquals("Manual original", env.articles.getById(id)?.original_markdown_content)
        }
    }

    @Test fun sourceChangeRefreshesOnlyTheOriginalOwnedByOurCache() = runBlocking {
        Env().use { env ->
            val id = env.item("123")
            env.organizer({ fullBody() }) { analysis() }.organizePending()
            env.items.upsertDraft(env.sourceId, draft("123").copy(contentHash = "changed", aiInputHash = "changed-ai"))
            env.organizer({ fullBody().copy(text = "Updated full article body.") }) { analysis() }.organizePending()
            assertEquals("Updated full article body.", env.articles.getById(id)?.original_markdown_content)
        }
    }

    @Test fun historicalScanAdvancesPastProtectedOriginalsWithoutStarvingOlderItems() = runBlocking {
        Env().use { env ->
            repeat(50) { env.item("${1000 + it}", status = ExternalItemAiStatus.completed, original = "Manual original $it") }
            val older = env.item("2000", status = ExternalItemAiStatus.completed)
            env.sources.updateConfigJson(env.sourceId, """{"custom":"keep"}""")
            var fetches = 0
            val service = FavoriteSyncService(env.sources, env.items, FavoriteConnectorRegistry(listOf(XBookmarksConnector())),
                ExternalFavoriteImporter(env.items, env.articles), env.organizer({ fetches++; fullBody() }) { analysis() })
            service.syncSource(env.sourceId, FavoriteSyncMode.retry_failed)
            assertEquals(0, fetches)
            service.syncSource(env.sourceId, FavoriteSyncMode.retry_failed)
            assertEquals(1, fetches)
            assertEquals(BODY, env.articles.getById(older)?.original_markdown_content)
            assertEquals("keep", favoriteMetadata(env.sources.getById(env.sourceId)!!.config_json).textValue("custom"))
            service.syncSource(env.sourceId, FavoriteSyncMode.retry_failed)
            assertEquals(1, fetches)
        }
    }

    @Test fun sourceChangedWhileFetchingDoesNotPersistStaleBody() = runBlocking {
        Env().use { env ->
            val id = env.item("123")
            var aiCalls = 0
            assertFailsWith<CancellationException> {
                env.organizer({
                    env.items.upsertDraft(env.sourceId, draft("123").copy(contentHash = "changed", aiInputHash = "changed-ai"))
                    fullBody()
                }) { aiCalls++; analysis() }.organizePending()
            }
            assertTrue(env.articles.getById(id)?.original_markdown_content.isNullOrBlank())
            assertEquals(0, aiCalls)
        }
    }

    @Test fun knownArticleRejectsSupplementWithoutCompleteBodyProof() = runBlocking {
        Env().use { env ->
            env.item("123")
            var aiCalls = 0
            env.organizer({ ExternalFavoriteSupplement("https://x.com/writer/status/123", "Title", "https://t.co/a", "x_status") }) {
                aiCalls++; analysis()
            }.organizePending()
            assertEquals(0, aiCalls)
            assertEquals("failed", env.items.getBySourceExternalId(env.sourceId, "123")?.ai_status)
        }
    }

    @Test fun syncRepairsCompletedMetadataOnlyArticleButPreservesManualOriginal() = runBlocking {
        Env().use { env ->
            val repaired = env.item("123", status = ExternalItemAiStatus.completed, original = "Card preview")
            val preserved = env.item("124", status = ExternalItemAiStatus.completed, original = "Manual full original")
            var fetches = 0
            val connector = XBookmarksConnector()
            val service = FavoriteSyncService(env.sources, env.items, FavoriteConnectorRegistry(listOf(connector)),
                ExternalFavoriteImporter(env.items, env.articles), env.organizer({ fetches++; fullBody() }) { analysis() })
            service.syncSource(env.sourceId, FavoriteSyncMode.retry_failed)
            assertEquals(1, fetches)
            assertEquals(BODY, env.articles.getById(repaired)?.original_markdown_content)
            assertEquals("Manual full original", env.articles.getById(preserved)?.original_markdown_content)
            service.syncSource(env.sourceId, FavoriteSyncMode.retry_failed)
            assertEquals(1, fetches)
        }
    }

    @Test fun metricOnlyChangesKeepCompletedStateAndCachedBodyEvenForLegacyHashes() = runBlocking {
        Env().use { env ->
            env.item("123")
            val original = draft("123").copy(normalizedJson = metrics(draft("123").normalizedJson, 1))
            env.items.upsertDraft(env.sourceId, original)
            env.organizer({ fullBody() }) { analysis() }.organizePending()
            val (updated, changed) = env.items.upsertDraft(env.sourceId,
                original.copy(contentHash = "new-stable-hash", normalizedJson = metrics(original.normalizedJson, 2)))
            assertFalse(changed)
            assertEquals("completed", updated.ai_status)
            assertEquals(BODY, cachedFavoriteContent(updated)?.text)
            assertEquals("2", favoriteMetadata(updated.normalized_json).objectValue("public_metrics")?.textValue("like_count"))
        }
    }

    @Test fun metricChangesCannotReviveACacheFromAnOlderBodyVersion() = runBlocking {
        Env().use { env ->
            env.item("123")
            val initial = draft("123").copy(normalizedJson = metrics(draft("123").normalizedJson, 1))
            env.items.upsertDraft(env.sourceId, initial)
            env.organizer({ fullBody() }) { analysis() }.organizePending()
            val edited = initial.copy(contentHash = "edited-source", normalizedJson = initial.normalizedJson.replace("Card preview", "Edited preview"))
            env.items.upsertDraft(env.sourceId, edited)
            val (latest, changed) = env.items.upsertDraft(env.sourceId,
                edited.copy(contentHash = "stable-edited-source", normalizedJson = metrics(edited.normalizedJson, 2)))
            assertFalse(changed)
            assertNull(cachedFavoriteContent(latest))
        }
    }

    @Test fun articleCacheExpiresAfter24Hours() = runBlocking {
        Env().use { env ->
            env.item("123")
            env.organizer({ fullBody() }) { analysis() }.organizePending()
            env.expireCache("123")
            assertNull(cachedFavoriteContent(env.items.getBySourceExternalId(env.sourceId, "123")!!))
        }
    }

    @Test fun expiredCacheRefreshesEditedBodyOnSync() = runBlocking<Unit> {
        Env().use { env ->
            val articleId = env.item("123")
            var fetches = 0
            var aiCalls = 0
            val organizer = env.organizer({ fetches++; fullBody().copy(text = if (fetches == 1) BODY else "Edited article body.") }) {
                aiCalls++; analysis()
            }
            organizer.organizePending()
            env.expireCache("123")
            FavoriteSyncService(env.sources, env.items, FavoriteConnectorRegistry(listOf(XBookmarksConnector())),
                ExternalFavoriteImporter(env.items, env.articles), organizer).syncSource(env.sourceId, FavoriteSyncMode.retry_failed)
            assertEquals(2, fetches)
            assertEquals(2, aiCalls)
            assertEquals("Edited article body.", env.articles.getById(articleId)?.original_markdown_content)
            assertNotNull(cachedFavoriteContent(env.items.getBySourceExternalId(env.sourceId, "123")!!))
        }
    }

    @Test fun unchangedExpiredBodyOnlyRenewsCacheWithoutAnotherAiCall() = runBlocking<Unit> {
        Env().use { env ->
            env.item("123")
            var fetches = 0
            var aiCalls = 0
            val organizer = env.organizer({ fetches++; fullBody() }) { aiCalls++; analysis() }
            organizer.organizePending()
            env.expireCache("123")
            FavoriteSyncService(env.sources, env.items, FavoriteConnectorRegistry(listOf(XBookmarksConnector())),
                ExternalFavoriteImporter(env.items, env.articles), organizer).syncSource(env.sourceId, FavoriteSyncMode.retry_failed)
            assertEquals(2, fetches)
            assertEquals(1, aiCalls)
            val item = env.items.getBySourceExternalId(env.sourceId, "123")!!
            assertEquals("completed", item.ai_status)
            assertNotNull(cachedFavoriteContent(item))
        }
    }

    @Test fun expiredCacheCanRefreshWithoutOverwritingAManualOriginal() = runBlocking {
        Env().use { env ->
            val id = env.item("123", original = "My manually maintained original")
            var fetches = 0
            val organizer = env.organizer({ fetches++; fullBody().copy(text = if (fetches == 1) BODY else "Updated remote body.") }) { analysis() }
            organizer.organizePending()
            env.expireCache("123")
            FavoriteSyncService(env.sources, env.items, FavoriteConnectorRegistry(listOf(XBookmarksConnector())),
                ExternalFavoriteImporter(env.items, env.articles), organizer).syncSource(env.sourceId, FavoriteSyncMode.retry_failed)
            assertEquals(2, fetches)
            assertEquals("My manually maintained original", env.articles.getById(id)?.original_markdown_content)
            assertEquals("Updated remote body.", cachedFavoriteContent(env.items.getBySourceExternalId(env.sourceId, "123")!!)?.text)
        }
    }

    @Test fun expiredCompleteSourceBodyIsRevalidatedAndDoesNotPolluteNewAiInput() = runBlocking {
        Env().use { env ->
            val complete = draft("123").copy(text = BODY,
                normalizedJson = draft("123").normalizedJson.replace("\"article_content_complete\":false", "\"article_content_complete\":true"))
            env.item("123", sourceDraft = complete)
            var fetches = 0
            var aiCalls = 0
            val organizer = env.organizer({ fetches++; fullBody().copy(text = "Edited complete body.") }) { input ->
                aiCalls++
                if (aiCalls == 2) {
                    assertEquals("", input.text)
                    assertEquals("Edited complete body.", input.supplementText)
                }
                analysis()
            }
            organizer.organizePending()
            assertEquals(0, fetches)
            env.expireCache("123")
            FavoriteSyncService(env.sources, env.items, FavoriteConnectorRegistry(listOf(XBookmarksConnector())),
                ExternalFavoriteImporter(env.items, env.articles), organizer).syncSource(env.sourceId, FavoriteSyncMode.retry_failed)
            assertEquals(1, fetches)
            assertEquals(2, aiCalls)
        }
    }

    private fun metrics(raw: String, count: Int): String = kotlinx.serialization.json.JsonObject(
        favoriteMetadata(raw) + ("public_metrics" to kotlinx.serialization.json.buildJsonObject { put("like_count", kotlinx.serialization.json.JsonPrimitive(count)) })
    ).toString()

    private class Env : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val db = DailySatoriDatabase(driver).also { DailySatoriDatabase.Schema.create(driver) }
        val sources = ExternalFavoriteSourceRepository(db)
        val items = ExternalFavoriteItemRepository(db)
        val articles = ArticleRepository(db)
        val sourceId = sources.save(provider = "x", displayName = "X", accountId = "42", accountName = "Writer", authJson = "{}", enabled = true)
        fun item(id: String, status: ExternalItemAiStatus = ExternalItemAiStatus.pending, original: String? = null,
            sourceDraft: ExternalFavoriteItemDraft = draft(id)): Long {
            val (item, _) = items.upsertDraft(sourceId, sourceDraft)
            val articleId = articles.insert(title = "Article", url = "https://x.com/writer/status/$id", status = "completed", aiMarkdownContent = "Old AI summary")
            items.markImported(item.id, articleId, false, status)
            if (original != null) articles.updateOriginalMarkdownContent(articleId, original)
            return articleId
        }
        fun expireCache(id: String) {
            val item = items.getBySourceExternalId(sourceId, id)!!
            val root = favoriteMetadata(item.normalized_json)
            val cache = root.objectValue("fetched_content")!!
            val expired = kotlinx.serialization.json.JsonObject(cache + ("fetched_at" to kotlinx.serialization.json.JsonPrimitive(1L)))
            val raw = kotlinx.serialization.json.JsonObject(root + ("fetched_content" to expired)).toString()
            db.dailySatoriQueries.updateExternalFavoriteItemContentCache(raw, item.updated_at, item.id)
        }
        fun organizer(fetch: suspend () -> ExternalFavoriteSupplement?, generate: suspend (ExternalFavoriteAiInput) -> ExternalFavoriteAiAnalysis) =
            ExternalFavoriteAiOrganizer(items, articles, retryDelayMs = 0, generateAnalysis = generate,
                supplementResolver = object : ExternalFavoriteSupplementResolver {
                    override suspend fun resolve(item: External_favorite_item, input: ExternalFavoriteAiInput, httpLogger: FavoriteSyncHttpLogger, taskId: Long?) = fetch()
                })
        override fun close() = driver.close()
    }
    companion object {
        const val BODY = "Full article body with its final paragraph preserved."
        fun fullBody() = ExternalFavoriteSupplement("https://x.com/i/article/456", "Article", BODY, "x_article", articleContentComplete = true)
        fun analysis() = ExternalFavoriteAiAnalysis("Article", "Summary", "Markdown")
        fun draft(id: String) = ExternalFavoriteItemDraft("x", id, "https://x.com/writer/status/$id", "Article", "https://t.co/a", "Writer", null, null,
            """{"is_article":true,"article_content_complete":false,"primary_url":"https://x.com/i/article/456","canonical_tweet_url":"https://x.com/writer/status/$id","text":"https://t.co/a","url_title":"Article","url_description":"Card preview"}""", contentHash = "source-$id", aiInputHash = "ai-$id")
    }
}
