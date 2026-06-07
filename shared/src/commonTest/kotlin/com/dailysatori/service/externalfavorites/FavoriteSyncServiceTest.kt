package com.dailysatori.service.externalfavorites

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.External_favorite_source
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoriteSyncServiceTest {
    @Test
    fun recentSyncUsesConnectorLimitsAndUpsertsItems() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 2, maxItemsPerRun = 10),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("post-1")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("post-2")), "cursor-3"),
                    FavoriteFetchPage(listOf(xDraft("post-3")), "cursor-4"),
                ),
            )
            var imported = 0
            var organized = 0
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { limit: Long ->
                    imported += 1
                    limit.toInt()
                },
                organizePending = { limit: Long ->
                    organized += 1
                    limit.toInt()
                },
            )

            service.syncSource(sourceId, FavoriteSyncMode.recent)

            assertEquals(listOf(null, "cursor-2"), connector.cursors)
            assertEquals(listOf("post-1", "post-2"), items.getBySource(sourceId).map { it.external_id }.sorted())
            assertEquals(1, imported)
            assertEquals(1, organized)
            sources.getById(sourceId)!!.let { source ->
                assertEquals("idle", source.status)
                assertEquals(2, source.last_pages_seen_count)
                assertEquals(2, source.last_items_seen_count)
                assertEquals("recent", source.last_sync_mode)
                assertEquals("", source.last_error_code)
                assertEquals("", source.last_error_message)
            }
        }
    }

    @Test
    fun retryFailedDoesNotFetchProviderPages() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            items.upsertDraft(sourceId, xDraft("post-1"))
            val connector = FakeConnector(
                pages = emptyList(),
                failOnFetch = true,
            )
            var imported = 0
            var organized = 0
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { limit: Long ->
                    imported += limit.toInt()
                    1
                },
                organizePending = { limit: Long ->
                    organized += limit.toInt()
                    1
                },
            )

            service.syncSource(sourceId, FavoriteSyncMode.retry_failed)

            assertEquals(0, connector.fetchCalls)
            assertTrue(imported > 0)
            assertTrue(organized > 0)
            sources.getById(sourceId)!!.let { source ->
                assertEquals("idle", source.status)
                assertEquals(0, source.last_pages_seen_count)
                assertEquals(0, source.last_items_seen_count)
                assertEquals("retry_failed", source.last_sync_mode)
            }
        }
    }

    @Test
    fun concurrentSyncsForSameSourceAreSerialized() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = SerializingConnector()
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            val first = async { service.syncSource(sourceId, FavoriteSyncMode.recent) }
            val second = async { service.syncSource(sourceId, FavoriteSyncMode.recent) }
            first.await()
            second.await()

            assertFalse(connector.overlapped)
            assertEquals(2, connector.fetchCalls)
            sources.getById(sourceId)!!.let { source ->
                assertEquals("idle", source.status)
                assertEquals("recent", source.last_sync_mode)
                assertEquals("", source.last_error_code)
            }
        }
    }

    @Test
    fun historyContinuesWhenRecentWouldStopOnUnchangedPage() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            items.upsertDraft(sourceId, xDraft("post-1"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 10),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("post-1")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("post-2")), "cursor-3"),
                    FavoriteFetchPage(listOf(xDraft("post-3")), null),
                ),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.history)

            assertEquals(listOf(null, "cursor-2", "cursor-3"), connector.cursors)
            assertEquals(listOf("post-1", "post-2", "post-3"), items.getBySource(sourceId).map { it.external_id }.sorted())
            assertEquals(3, sources.getById(sourceId)!!.last_pages_seen_count)
        }
    }

    @Test
    fun fullRescanContinuesWhenRecentWouldStopOnUnchangedPage() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            items.upsertDraft(sourceId, xDraft("post-1"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 10),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("post-1")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("post-2")), "cursor-3"),
                    FavoriteFetchPage(listOf(xDraft("post-3")), null),
                ),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.full_rescan)

            assertEquals(listOf(null, "cursor-2", "cursor-3"), connector.cursors)
            assertEquals(listOf("post-1", "post-2", "post-3"), items.getBySource(sourceId).map { it.external_id }.sorted())
            assertEquals(3, sources.getById(sourceId)!!.last_pages_seen_count)
        }
    }

    @Test
    fun syncImportsPendingItemsForSyncedSourceBeforeOtherPendingSources() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceA = saveXSource(sources, accountId = "acct-a")
            val sourceB = saveXSource(sources, accountId = "acct-b")
            items.upsertDraft(sourceA, xDraft("post-a", text = "old source A text"))
            delay(2)
            items.upsertDraft(sourceB, xDraft("post-b"))
            val connector = FakeConnector(
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-a", text = "new source A text")), null)),
            )
            val importer = ExternalFavoriteImporter(items, articles)
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importer = importer,
                organizePending = { 0 },
            )

            service.syncSource(sourceA, FavoriteSyncMode.recent)

            val sourceAItem = items.getBySource(sourceA).single()
            val sourceBItem = items.getBySource(sourceB).single()
            assertEquals("imported", sourceAItem.import_status)
            assertEquals("not_imported", sourceBItem.import_status)
            assertEquals("https://x.com/daily/status/post-a", articles.getAllSync().single().url)
        }
    }

    @Test
    fun organizerMarksLinkedPendingAiItemsNotNeeded() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceId = saveXSource(sources)
            val (item, _) = items.upsertDraft(sourceId, xDraft("post-1"))
            val articleId = articles.insert(
                title = "Imported favorite",
                aiContent = "summary",
                aiMarkdownContent = """
                    # X 收藏

                    ## 原文

                    Body post-1

                    ## AI 整理

                    待整理
                """.trimIndent(),
                url = "https://x.com/daily/status/post-1",
                isFavorite = 1,
                status = "completed",
            )
            items.markImported(item.id, articleId, duplicateLinked = false)

            val organized = ExternalFavoriteAiOrganizer(items, articles).organizePending(limit = 10)

            assertEquals(1, organized)
            assertEquals("not_needed", items.getBySource(sourceId).single().ai_status)
        }
    }

    private suspend fun withRepositories(
        block: suspend (
            db: DailySatoriDatabase,
            sources: ExternalFavoriteSourceRepository,
            items: ExternalFavoriteItemRepository,
            articles: ArticleRepository,
        ) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val sources = ExternalFavoriteSourceRepository(
            db = db,
            encryptSecret = { value -> if (value.isBlank()) value else "enc:v1:$value" },
            decryptSecret = { value -> value.removePrefix("enc:v1:") },
            isSecretEncrypted = { value -> value.startsWith("enc:v1:") },
        )
        val items = ExternalFavoriteItemRepository(db)
        val articles = ArticleRepository(db)
        block(db, sources, items, articles)
    }

    private fun saveXSource(
        sources: ExternalFavoriteSourceRepository,
        accountId: String = "acct-1",
    ): Long = sources.save(
        provider = ExternalFavoriteProvider.X.id,
        displayName = "X Favorites",
        accountId = accountId,
        accountName = "@daily",
        authJson = """{"access_token":"secret"}""",
    )

    private companion object {
        fun xDraft(
            externalId: String,
            text: String = "Body $externalId",
        ): ExternalFavoriteItemDraft = ExternalFavoriteItemDraft(
            provider = ExternalFavoriteProvider.X.id,
            externalId = externalId,
            canonicalUrl = "https://x.com/daily/status/$externalId",
            title = "Title $externalId",
            text = text,
            authorName = "Author",
            sourceCreatedAt = 1_700_000_000_000,
            favoritedAt = 1_700_000_100_000,
            normalizedJson = """{"id":"$externalId"}""",
            contentHash = "content-$externalId-$text",
            aiInputHash = "ai-$externalId-$text",
        )

        fun xCapabilities(
            maxPagesPerRun: Int = 3,
            maxItemsPerRun: Int = 300,
        ): FavoriteConnectorCapabilities = FavoriteConnectorCapabilities(
            maxPageSize = 100,
            defaultBackoffMinutes = 15,
            maxPagesPerRun = maxPagesPerRun,
            maxItemsPerRun = maxItemsPerRun,
            supportsFolders = false,
            supportsFavoritedAt = false,
            supportsWriteBack = false,
            supportsRefreshToken = true,
        )
    }

    private class FakeConnector(
        override val capabilities: FavoriteConnectorCapabilities = xCapabilities(),
        private val pages: List<FavoriteFetchPage>,
        private val failOnFetch: Boolean = false,
    ) : FavoriteConnector {
        override val provider: String = ExternalFavoriteProvider.X.id
        val cursors = mutableListOf<String?>()
        var fetchCalls = 0

        override suspend fun fetchPage(
            source: External_favorite_source,
            cursor: String?,
            pageSize: Int,
        ): FavoriteFetchPage {
            fetchCalls += 1
            cursors += cursor
            if (failOnFetch) error("retry_failed must not fetch provider pages")
            return pages.getOrElse(fetchCalls - 1) { FavoriteFetchPage(emptyList(), null) }
        }
    }

    private class SerializingConnector : FavoriteConnector {
        override val provider: String = ExternalFavoriteProvider.X.id
        override val capabilities: FavoriteConnectorCapabilities = xCapabilities(maxPagesPerRun = 1)
        var fetchCalls = 0
        var activeFetches = 0
        var overlapped = false

        override suspend fun fetchPage(
            source: External_favorite_source,
            cursor: String?,
            pageSize: Int,
        ): FavoriteFetchPage {
            activeFetches += 1
            if (activeFetches > 1) overlapped = true
            delay(25)
            fetchCalls += 1
            activeFetches -= 1
            return FavoriteFetchPage(listOf(xDraft("post-$fetchCalls")), null)
        }
    }
}
