package com.dailysatori.service.externalfavorites

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.External_favorite_source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

            assertEquals(listOf<String?>(null), connector.cursors)
            assertEquals(listOf("post-1"), items.getBySource(sourceId).map { it.external_id }.sorted())
            assertEquals(1, imported)
            assertEquals(1, organized)
            sources.getById(sourceId)!!.let { source ->
                assertEquals("idle", source.status)
                assertEquals(1, source.last_pages_seen_count)
                assertEquals(1, source.last_items_seen_count)
                assertEquals("recent", source.last_sync_mode)
                assertEquals("", source.last_error_code)
                assertEquals("", source.last_error_message)
            }
        }
    }

    @Test
    fun syncSkipsRemoteDetailForExistingCompleteFavoriteItem() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val existingDraft = xDraft("post-1", text = "Detailed saved body")
            val incomingListDraft = xDraft("post-1", text = "Short bookmark body")
            items.upsertDraft(sourceId, existingDraft)
            var shouldFetchExistingDetail: Boolean? = null
            val connector = object : FavoriteConnector {
                override val provider: String = ExternalFavoriteProvider.X.id
                override val capabilities: FavoriteConnectorCapabilities = xCapabilities(maxPagesPerRun = 1)

                override suspend fun fetchPage(
                    source: External_favorite_source,
                    cursor: String?,
                    pageSize: Int,
                    httpLogger: FavoriteSyncHttpLogger,
                    taskId: Long?,
                    shouldFetchDetail: FavoriteFetchDetailPolicy,
                    sinceExternalId: String?,
                ): FavoriteFetchPage {
                    shouldFetchExistingDetail = shouldFetchDetail(incomingListDraft)
                    return FavoriteFetchPage(listOf(incomingListDraft), null)
                }
            }
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { _: Long -> 0 },
                organizePending = { _: Long -> 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.sync)

            assertEquals(false, shouldFetchExistingDetail)
            assertEquals("Detailed saved body", items.getBySource(sourceId).single().text)
        }
    }

    @Test
    fun syncStopsFetchingWhenLatestLocalExternalIdIsSeen() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            items.upsertDraft(sourceId, xDraft("100"))
            items.upsertDraft(sourceId, xDraft("101"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("103"), xDraft("102")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("101"), xDraft("100")), "cursor-3"),
                    FavoriteFetchPage(listOf(xDraft("99")), null),
                ),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { _: Long -> 0 },
                organizePending = { _: Long -> 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.sync)

            assertEquals(listOf<String?>(null, "cursor-2"), connector.cursors)
            assertEquals(listOf(20, 20), connector.pageSizes)
            assertEquals(listOf("102", "103"), items.getBySource(sourceId).map { it.external_id }.filter { it > "101" }.sorted())
        }
    }

    @Test
    fun fullRescanDoesNotPassSinceIdAndUsesLargerPages() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            items.upsertDraft(sourceId, xDraft("101"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 300),
                pages = listOf(FavoriteFetchPage(listOf(xDraft("102")), null)),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { _: Long -> 0 },
                organizePending = { _: Long -> 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.full_rescan)

            assertEquals(listOf<String?>(null), connector.sinceExternalIds)
            assertEquals(listOf(100), connector.pageSizes)
        }
    }

    @Test
    fun syncDoesNotSpendAiBudgetWhenNoFavoriteChanged() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val existingDraft = xDraft("post-1", text = "Detailed saved body")
            items.upsertDraft(sourceId, existingDraft)
            var organizeCalls = 0
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 1),
                pages = listOf(FavoriteFetchPage(listOf(existingDraft), null)),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePendingForSource = { _, _ ->
                    organizeCalls += 1
                    0
                },
            )

            service.syncSource(sourceId, FavoriteSyncMode.sync)

            assertEquals(0, organizeCalls)
        }
    }

    @Test
    fun unifiedSyncCapsProviderPagesAndStoresBackfillCursor() = runBlocking {
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
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.history)

            assertEquals(listOf(null, "cursor-2"), connector.cursors)
            assertEquals(listOf("post-1", "post-2"), items.getBySource(sourceId).map { it.external_id }.sorted())
            sources.getById(sourceId)!!.let { source ->
                assertEquals("history", source.last_sync_mode)
                assertTrue(source.config_json.contains(""""history_cursor":"cursor-3""""))
                assertTrue(source.config_json.contains(""""history_complete":false"""))
            }
        }
    }

    @Test
    fun unifiedSyncContinuesSavedBackfillCursorWhenLatestPageIsUnchanged() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(
                sources = sources,
                configJson = """{"history_cursor":"cursor-3","history_complete":false}""",
            )
            items.upsertDraft(sourceId, xDraft("post-1"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 10),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("post-1")), "cursor-2"),
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

            assertEquals(listOf(null, "cursor-3"), connector.cursors)
            assertEquals(listOf("post-1", "post-3"), items.getBySource(sourceId).map { it.external_id }.sorted())
            sources.getById(sourceId)!!.let { source ->
                assertTrue(source.config_json.contains(""""history_complete":true"""))
                assertFalse(source.config_json.contains("history_cursor"))
            }
        }
    }

    @Test
    fun unifiedSyncContinuesSavedBackfillCursorEvenWhenLatestPageChanged() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(
                sources = sources,
                configJson = """{"history_cursor":"cursor-10","history_complete":false}""",
            )
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 100),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("new-post")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("old-post-10")), "cursor-11"),
                    FavoriteFetchPage(listOf(xDraft("old-post-11")), "cursor-12"),
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

            assertEquals(listOf(null, "cursor-10", "cursor-11"), connector.cursors)
            assertEquals(
                listOf("new-post", "old-post-10", "old-post-11"),
                items.getBySource(sourceId).map { it.external_id }.sorted(),
            )
            sources.getById(sourceId)!!.let { source ->
                assertTrue(source.config_json.contains(""""history_cursor":"cursor-12""""))
                assertTrue(source.config_json.contains(""""history_complete":false"""))
            }
        }
    }

    @Test
    fun unifiedSyncStartsBackfillInSameRunWhenLatestPageIsUnchangedAndNoCursorIsSaved() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            items.upsertDraft(sourceId, xDraft("post-1"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 300),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("post-1")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("post-2")), "cursor-3"),
                    FavoriteFetchPage(listOf(xDraft("post-3")), "cursor-4"),
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
            sources.getById(sourceId)!!.let { source ->
                assertEquals(3, source.last_pages_seen_count)
                assertEquals(3, source.last_items_seen_count)
                assertTrue(source.config_json.contains(""""history_cursor":"cursor-4""""))
                assertTrue(source.config_json.contains(""""history_complete":false"""))
            }
        }
    }

    @Test
    fun unifiedSyncContinuesAfterUnchangedNinetyFiveItemLatestPageWithNextCursor() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val existingLatest = (1..95).map { index -> xDraft("post-$index") }
            existingLatest.forEach { items.upsertDraft(sourceId, it) }
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 300),
                pages = listOf(
                    FavoriteFetchPage(existingLatest, "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("post-96")), null),
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

            assertEquals(listOf(null, "cursor-2"), connector.cursors)
            assertEquals(96, items.getBySource(sourceId).size)
            sources.getById(sourceId)!!.let { source ->
                assertEquals(2, source.last_pages_seen_count)
                assertEquals(96, source.last_items_seen_count)
                assertTrue(source.config_json.contains(""""history_complete":true"""))
                assertTrue(source.config_json.contains(""""history_complete_anchor_cursor":"cursor-2""""))
            }
        }
    }

    @Test
    fun unifiedSyncRepairsLegacyCompleteStateWhenLatestPageStillHasNextCursor() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(
                sources = sources,
                configJson = """{"history_complete":true}""",
            )
            items.upsertDraft(sourceId, xDraft("post-1"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 300),
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
            sources.getById(sourceId)!!.let { source ->
                assertTrue(source.config_json.contains(""""history_complete":true"""))
                assertTrue(source.config_json.contains(""""history_complete_anchor_cursor":"cursor-2""""))
            }
        }
    }

    @Test
    fun progressDoesNotReportLegacyCompleteAsCompleteWhenLatestCursorDiffers() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(
                sources = sources,
                configJson = """{"history_complete":true}""",
            )
            items.upsertDraft(sourceId, xDraft("post-1"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 2, maxItemsPerRun = 300),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("post-1")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("post-2")), null),
                ),
            )
            val progress = mutableListOf<FavoriteSyncProgress>()
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.history) { progress += it }

            val latestProgress = progress.first { it.phase == "latest" }
            assertFalse(latestProgress.historyComplete)
            assertTrue(progress.any { it.phase == "complete" && it.historyComplete })
        }
    }

    @Test
    fun finalProgressReportsCompleteAfterLocalWork() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 2, maxItemsPerRun = 300),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("post-1")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("post-2")), null),
                ),
            )
            val progress = mutableListOf<FavoriteSyncProgress>()
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.history) { progress += it }

            val finalProgress = progress.last()
            assertEquals("complete", finalProgress.phase)
            assertEquals(2, finalProgress.pagesSeen)
            assertEquals(2, finalProgress.itemsSeen)
            assertTrue(finalProgress.historyComplete)
        }
    }

    @Test
    fun unifiedSyncSkipsBackfillWhenCompleteStateMatchesLatestCursor() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(
                sources = sources,
                configJson = """{"history_complete":true,"history_complete_anchor_cursor":"cursor-2"}""",
            )
            items.upsertDraft(sourceId, xDraft("post-1"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 300),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("post-1")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("post-2")), "cursor-3"),
                ),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.sync)

            assertEquals(listOf<String?>(null), connector.cursors)
            assertEquals(listOf("post-1"), items.getBySource(sourceId).map { it.external_id }.sorted())
        }
    }

    @Test
    fun completeHistoryIncrementalSyncStopsAfterFirstUnchangedOlderPage() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(
                sources = sources,
                configJson = """{"history_complete":true,"history_complete_anchor_cursor":"cursor-2"}""",
            )
            items.upsertDraft(sourceId, xDraft("old-post"))
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 5, maxItemsPerRun = 100),
                pages = listOf(
                    FavoriteFetchPage(listOf(xDraft("new-post")), "cursor-2"),
                    FavoriteFetchPage(listOf(xDraft("old-post")), "cursor-3"),
                    FavoriteFetchPage(listOf(xDraft("too-old-post")), null),
                ),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.sync)

            assertEquals(listOf(null, "cursor-2"), connector.cursors)
            assertEquals(listOf("new-post", "old-post"), items.getBySource(sourceId).map { it.external_id }.sorted())
            sources.getById(sourceId)!!.let { source ->
                assertTrue(source.config_json.contains(""""history_complete":true"""))
                assertTrue(source.config_json.contains(""""history_complete_anchor_cursor":"cursor-2""""))
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
            assertEquals(50, imported)
            assertEquals(10, organized)
            sources.getById(sourceId)!!.let { source ->
                assertEquals("idle", source.status)
                assertEquals(0, source.last_pages_seen_count)
                assertEquals(0, source.last_items_seen_count)
                assertEquals("retry_failed", source.last_sync_mode)
            }
        }
    }

    @Test
    fun normalSyncStillRetriesLocalImportFailuresWhenFetchedItemsAreUnchanged() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            items.upsertDraft(sourceId, xDraft("post-1"))
            val connector = FakeConnector(
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-1")), null)),
            )
            var importLimit = 0L
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPendingForSource = { scopedSourceId, limit ->
                    assertEquals(sourceId, scopedSourceId)
                    importLimit = limit
                    1
                },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.sync)

            assertEquals(IMPORT_RETRY_EXPECTED_LIMIT, importLimit)
        }
    }

    @Test
    fun syncRefreshesAuthBeforeFetchAndPersistsUpdatedAuth() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources, authJson = """{"access_token":"old"}""")
            val connector = RefreshingConnector(
                refreshedAuthJson = """{"access_token":"new"}""",
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-1")), null)),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.recent)

            assertEquals(1, connector.refreshCalls)
            assertEquals(listOf("""{"access_token":"new"}"""), connector.fetchAuthJsons)
            assertEquals("""{"access_token":"new"}""", sources.getById(sourceId)!!.auth_json)
        }
    }

    @Test
    fun importerFailureAfterSuccessfulFetchDoesNotFailSourceSync() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = FakeConnector(
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-1")), null)),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPendingForSource = { _, _ -> error("import failed after fetch") },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.recent)

            sources.getById(sourceId)!!.let { source ->
                assertEquals("idle", source.status)
                assertEquals(1, source.last_items_seen_count)
                assertEquals(1, source.last_pages_seen_count)
                assertTrue(source.last_success_at != null)
                assertEquals("", source.last_error_code)
            }
        }
    }

    @Test
    fun sourceRemainsSyncingAndReportsLocalWorkBeforeFinalSuccess() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = FakeConnector(
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-1")), null)),
            )
            val progress = mutableListOf<FavoriteSyncProgress>()
            val statusesDuringLocalWork = mutableListOf<String>()
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPendingForSource = { scopedSourceId, _ ->
                    assertEquals(sourceId, scopedSourceId)
                    statusesDuringLocalWork += sources.getById(sourceId)!!.status
                    1
                },
                organizePendingForSource = { scopedSourceId, _ ->
                    assertEquals(sourceId, scopedSourceId)
                    statusesDuringLocalWork += sources.getById(sourceId)!!.status
                    1
                },
            )

            service.syncSource(sourceId, FavoriteSyncMode.recent) { progress += it }

            assertEquals(listOf("syncing", "syncing"), statusesDuringLocalWork)
            assertTrue(progress.any { it.phase == "import" })
            assertTrue(progress.any { it.phase == "organize" })
            sources.getById(sourceId)!!.let { source ->
                assertEquals("idle", source.status)
                assertEquals(1, source.last_items_seen_count)
                assertEquals(1, source.last_pages_seen_count)
                assertTrue(source.last_success_at != null)
            }
        }
    }

    @Test
    fun organizerFailureAfterSuccessfulFetchDoesNotFailSourceSync() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = FakeConnector(
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-1")), null)),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPendingForSource = { _, _ -> 0 },
                organizePending = { error("organizer failed after fetch") },
            )

            service.syncSource(sourceId, FavoriteSyncMode.recent)

            sources.getById(sourceId)!!.let { source ->
                assertEquals("idle", source.status)
                assertEquals(1, source.last_items_seen_count)
                assertEquals(1, source.last_pages_seen_count)
                assertTrue(source.last_success_at != null)
                assertEquals("", source.last_error_code)
            }
        }
    }

    @Test
    fun cancellationDuringLocalWorkIsRethrownWithoutMarkingSourceSucceeded() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = FakeConnector(
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-1")), null)),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPendingForSource = { _, _ -> 1 },
                organizePendingForSource = { _, _ -> throw CancellationException("cancel local work") },
            )

            assertFailsWith<CancellationException> {
                service.syncSource(sourceId, FavoriteSyncMode.recent)
            }

            sources.getById(sourceId)!!.let { source ->
                assertEquals("syncing", source.status)
                assertEquals(null, source.last_success_at)
                assertEquals("", source.last_error_code)
            }
        }
    }

    @Test
    fun rateLimitFailureStoresResetTimeOnSource() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val resetAt = 1_780_272_000_000L
            val connector = RateLimitedConnector(resetAt)
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            assertFailsWith<XFavoriteRateLimitException> {
                service.syncSource(sourceId, FavoriteSyncMode.recent)
            }

            sources.getById(sourceId)!!.let { source ->
                assertEquals("rate_limited", source.status)
                assertEquals("rate_limited", source.last_error_code)
                assertEquals(resetAt, source.rate_limit_reset_at)
            }
        }
    }

    @Test
    fun rateLimitAfterPartialFetchStoresBackfillCursorBeforeFailing() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val resetAt = 1_780_272_000_000L
            val connector = FakeConnector(
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-1")), "cursor-2")),
                failWithRateLimitOnFetch = 2,
                rateLimitResetAt = resetAt,
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            assertFailsWith<XFavoriteRateLimitException> {
                service.syncSource(sourceId, FavoriteSyncMode.full_rescan)
            }

            sources.getById(sourceId)!!.let { source ->
                assertEquals("rate_limited", source.status)
                assertTrue(source.config_json.contains(""""history_cursor":"cursor-2""""))
                assertTrue(source.config_json.contains(""""history_complete":false"""))
                assertEquals(resetAt, source.rate_limit_reset_at)
            }
        }
    }

    @Test
    fun cancellationDuringFetchIsRethrownWithoutMarkingSourceFailed() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = CancellingConnector()
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            assertFailsWith<CancellationException> {
                service.syncSource(sourceId, FavoriteSyncMode.recent)
            }

            sources.getById(sourceId)!!.let { source ->
                assertEquals("syncing", source.status)
                assertEquals("", source.last_error_code)
                assertEquals("", source.last_error_message)
            }
        }
    }

    @Test
    fun disabledSourceDoesNotRefreshFetchImportOrOrganize() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(
                sources = sources,
                enabled = false,
            )
            val connector = RefreshingConnector(
                refreshedAuthJson = """{"access_token":"new"}""",
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-1")), null)),
            )
            var imported = 0
            var organized = 0
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPendingForSource = { _, _ ->
                    imported += 1
                    0
                },
                organizePending = {
                    organized += 1
                    0
                },
            )

            service.syncSource(sourceId, FavoriteSyncMode.recent)

            assertEquals(0, connector.refreshCalls)
            assertEquals(0, connector.fetchCalls)
            assertEquals(0, imported)
            assertEquals(0, organized)
            assertEquals(emptyList(), items.getBySource(sourceId))
            sources.getById(sourceId)!!.let { source ->
                assertEquals(0L, source.enabled)
                assertEquals("paused", source.status)
                assertEquals(null, source.last_sync_started_at)
                assertEquals(0, source.last_items_seen_count)
                assertEquals(0, source.last_pages_seen_count)
            }
        }
    }

    @Test
    fun recentUsesPolicyItemCapForFetchPageSize() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 3, maxItemsPerRun = 1),
                pages = listOf(FavoriteFetchPage(listOf(xDraft("post-1"), xDraft("post-2")), "cursor-2")),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.recent)

            assertEquals(listOf(1), connector.pageSizes)
            assertEquals(listOf("post-1"), items.getBySource(sourceId).map { it.external_id })
            assertEquals(1, sources.getById(sourceId)!!.last_items_seen_count)
        }
    }

    @Test
    fun syncUsesConfiguredMaxItemsPerRunWhenProvided() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(
                sources = sources,
                configJson = """{"max_items_per_sync":2}""",
            )
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 10, maxItemsPerRun = 100),
                pages = listOf(
                    FavoriteFetchPage(
                        listOf(xDraft("post-1"), xDraft("post-2"), xDraft("post-3")),
                        "cursor-2",
                    ),
                    FavoriteFetchPage(listOf(xDraft("post-4")), null),
                ),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPending = { 0 },
                organizePending = { 0 },
            )

            service.syncSource(sourceId, FavoriteSyncMode.sync)

            assertEquals(listOf(2), connector.pageSizes)
            assertEquals(listOf("post-1", "post-2"), items.getBySource(sourceId).map { it.external_id }.sorted())
            sources.getById(sourceId)!!.let { source ->
                assertEquals(2, source.last_items_seen_count)
                assertFalse(source.config_json.contains("history_cursor"))
                assertTrue(source.config_json.contains(""""max_items_per_sync":2"""))
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
    fun historyUsesUnifiedBackfillPolicyWhenLatestPageIsUnchanged() = runBlocking {
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
    fun historyNoLongerFetchesAllPagesInOneRun() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val connector = FakeConnector(
                capabilities = xCapabilities(maxPagesPerRun = 2, maxItemsPerRun = 10),
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

            assertEquals(listOf(null, "cursor-2"), connector.cursors)
            assertEquals(listOf("post-1", "post-2"), items.getBySource(sourceId).map { it.external_id }.sorted())
            sources.getById(sourceId)!!.let { source ->
                assertEquals(2, source.last_pages_seen_count)
                assertTrue(source.config_json.contains(""""history_cursor":"cursor-3""""))
            }
        }
    }

    @Test
    fun fullRescanUsesUnifiedBackfillPolicyWhenLatestPageIsUnchanged() = runBlocking {
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
    fun fullRescanIgnoresSavedCompleteStateAndStartsHistoryAgain() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(
                sources = sources,
                configJson = """{"history_complete":true,"history_complete_anchor_cursor":"cursor-2"}""",
            )
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
            sources.getById(sourceId)!!.let { source ->
                assertTrue(source.config_json.contains(""""history_complete":true"""))
                assertTrue(source.config_json.contains(""""history_complete_anchor_cursor":"cursor-2""""))
            }
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
    fun syncRepairsOldPlaceholderBeforeOrganizingSoItIsProcessedInSameRun() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceId = saveXSource(sources)
            val (item, _) = items.upsertDraft(sourceId, xDraft("post-placeholder", text = "旧占位原文"))
            val articleId = articles.insert(
                title = "X 收藏",
                aiContent = "旧占位原文",
                aiMarkdownContent = """
                    # X 收藏

                    ## 原文

                    - 作者：Author
                    - 时间：2023-11-14T22:13:20Z
                    - 链接：https://x.com/daily/status/post-placeholder

                    旧占位原文

                    ## AI 整理

                    待整理
                """.trimIndent(),
                url = "https://x.com/daily/status/post-placeholder",
                isFavorite = 0,
                status = "completed",
            )
            items.markImported(item.id, articleId, duplicateLinked = false)
            items.markAiState(item.id, ExternalItemAiStatus.completed.name)
            val connector = FakeConnector(
                pages = listOf(FavoriteFetchPage(emptyList(), null)),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importer = ExternalFavoriteImporter(items, articles),
                organizer = ExternalFavoriteAiOrganizer(
                    itemRepo = items,
                    articleRepo = articles,
                    generateAnalysis = {
                        ExternalFavoriteAiAnalysis("AI 标题", "AI 摘要", "AI 正文")
                    },
                ),
            )

            service.syncSource(sourceId, FavoriteSyncMode.sync)

            assertEquals("completed", items.getBySource(sourceId).single().ai_status)
            assertTrue(articles.getById(articleId)!!.ai_markdown_content.orEmpty().contains("AI 正文"))
        }
    }

    @Test
    fun retryFailedOrganizesPendingAiOnlyForSyncedSource() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceA = saveXSource(sources, accountId = "acct-a")
            val sourceB = saveXSource(sources, accountId = "acct-b")
            val (itemA, _) = items.upsertDraft(sourceA, xDraft("post-a", text = "source A text"))
            val (itemB, _) = items.upsertDraft(sourceB, xDraft("post-b", text = "source B text"))
            val articleA = articles.insert(
                title = "A",
                aiContent = "source A text",
                aiMarkdownContent = "# X 收藏\n\n## 原文\n\nsource A text\n\n## AI 整理\n\n待整理",
                url = "https://x.com/daily/status/post-a",
                status = "completed",
            )
            val articleB = articles.insert(
                title = "B",
                aiContent = "source B text",
                aiMarkdownContent = "# X 收藏\n\n## 原文\n\nsource B text\n\n## AI 整理\n\n待整理",
                url = "https://x.com/daily/status/post-b",
                status = "completed",
            )
            items.markImported(itemA.id, articleA, duplicateLinked = false)
            items.markImported(itemB.id, articleB, duplicateLinked = false)
            val connector = FakeConnector(
                pages = listOf(FavoriteFetchPage(emptyList(), null)),
            )
            val service = FavoriteSyncService(
                sourceRepo = sources,
                itemRepo = items,
                registry = FavoriteConnectorRegistry(listOf(connector)),
                importPendingForSource = { _, _ -> 0 },
                organizePendingForSource = { scopedSourceId, limit ->
                    assertEquals(sourceA, scopedSourceId)
                    ExternalFavoriteAiOrganizer(
                        itemRepo = items,
                        articleRepo = articles,
                        generateAnalysis = {
                            ExternalFavoriteAiAnalysis("AI ${it.text}", "摘要 ${it.text}", "正文 ${it.text}")
                        },
                    ).organizePendingForSource(scopedSourceId, limit)
                },
            )

            service.syncSource(sourceA, FavoriteSyncMode.retry_failed)

            assertEquals("completed", items.getBySource(sourceA).single().ai_status)
            assertEquals("pending", items.getBySource(sourceB).single().ai_status)
            assertTrue(articles.getById(articleA)!!.ai_markdown_content.orEmpty().contains("source A text"))
            assertEquals("# X 收藏\n\n## 原文\n\nsource B text\n\n## AI 整理\n\n待整理", articles.getById(articleB)!!.ai_markdown_content)
        }
    }

    @Test
    fun organizerUsesExternalFavoriteContentForAiAndCompletesLinkedArticle() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceId = saveXSource(sources)
            val (item, _) = items.upsertDraft(sourceId, xDraft("post-1", text = "原文内容"))
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

            val organized = ExternalFavoriteAiOrganizer(
                itemRepo = items,
                articleRepo = articles,
                generateAnalysis = { input ->
                    assertEquals("原文内容", input.text)
                    ExternalFavoriteAiAnalysis(
                        title = "AI 标题",
                        summary = "AI 摘要",
                        markdown = "AI 正文整理",
                    )
                },
            ).organizePending(limit = 10)

            assertEquals(1, organized)
            assertEquals("completed", items.getBySource(sourceId).single().ai_status)
            articles.getById(articleId)!!.let { article ->
                assertEquals("AI 标题", article.ai_title)
                assertEquals("AI 摘要", article.ai_content)
                assertEquals("completed", article.status)
                assertTrue(article.ai_markdown_content.orEmpty().contains("## 原文"))
                assertTrue(article.ai_markdown_content.orEmpty().contains("原文内容"))
                assertTrue(article.ai_markdown_content.orEmpty().contains("## AI 整理"))
                assertTrue(article.ai_markdown_content.orEmpty().contains("AI 正文整理"))
            }
        }
    }

    @Test
    fun organizerSkipsSupplementFetchWhenExistingTextIsLongEnough() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceId = saveXSource(sources)
            val longText = "这是一段已经足够完整的收藏正文，长度超过二十个字。"
            val (item, _) = items.upsertDraft(
                sourceId,
                xDraft(
                    "post-long",
                    text = longText,
                    normalizedJson = """{"id":"post-long","primary_url":"https://example.com/full"}""",
                ),
            )
            val articleId = articles.insert(
                title = "Imported favorite",
                aiContent = longText,
                aiMarkdownContent = "# X 收藏\n\n## 原文\n\n$longText\n\n## AI 整理\n\n待整理",
                url = "https://x.com/daily/status/post-long",
                status = "completed",
            )
            items.markImported(item.id, articleId, duplicateLinked = false)
            var supplementCalls = 0

            ExternalFavoriteAiOrganizer(
                itemRepo = items,
                articleRepo = articles,
                supplementResolver = object : ExternalFavoriteSupplementResolver {
                    override suspend fun resolve(
                        item: com.dailysatori.shared.db.External_favorite_item,
                        input: ExternalFavoriteAiInput,
                        httpLogger: FavoriteSyncHttpLogger,
                        taskId: Long?,
                    ): ExternalFavoriteSupplement? {
                        supplementCalls += 1
                        return ExternalFavoriteSupplement(
                            url = "https://example.com/full",
                            title = "补充标题",
                            text = "不应该被抓取的补充正文",
                            sourceType = "web",
                        )
                    }
                },
                generateAnalysis = { input ->
                    assertEquals(longText, input.text)
                    assertEquals(null, input.supplementText)
                    ExternalFavoriteAiAnalysis("AI 标题", "AI 摘要", "AI 正文整理")
                },
            ).organizePending(limit = 10)

            assertEquals(0, supplementCalls)
        }
    }

    @Test
    fun organizerAddsSupplementContentWhenExistingTextIsTooShort() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceId = saveXSource(sources)
            val (item, _) = items.upsertDraft(
                sourceId,
                xDraft(
                    "post-short",
                    text = "看这个",
                    normalizedJson = """{"id":"post-short","primary_url":"https://example.com/full","url_title":"卡片标题"}""",
                ),
            )
            val articleId = articles.insert(
                title = "Imported favorite",
                aiContent = "看这个",
                aiMarkdownContent = "# X 收藏\n\n## 原文\n\n看这个\n\n## AI 整理\n\n待整理",
                url = "https://x.com/daily/status/post-short",
                status = "completed",
            )
            items.markImported(item.id, articleId, duplicateLinked = false)
            var supplementCalls = 0

            ExternalFavoriteAiOrganizer(
                itemRepo = items,
                articleRepo = articles,
                supplementResolver = object : ExternalFavoriteSupplementResolver {
                    override suspend fun resolve(
                        item: com.dailysatori.shared.db.External_favorite_item,
                        input: ExternalFavoriteAiInput,
                        httpLogger: FavoriteSyncHttpLogger,
                        taskId: Long?,
                    ): ExternalFavoriteSupplement? {
                        supplementCalls += 1
                        return ExternalFavoriteSupplement(
                            url = "https://example.com/full",
                            title = "补充标题",
                            text = "这是从远程页面抓取到的补充正文，应该提供给 AI 一起整理。",
                            sourceType = "web",
                        )
                    }
                },
                generateAnalysis = { input ->
                    assertEquals("看这个\n\n卡片标题", input.text)
                    assertEquals("https://example.com/full", input.supplementUrl)
                    assertEquals("补充标题", input.supplementTitle)
                    assertEquals("这是从远程页面抓取到的补充正文，应该提供给 AI 一起整理。", input.supplementText)
                    assertEquals("web", input.supplementSourceType)
                    ExternalFavoriteAiAnalysis("AI 标题", "AI 摘要", "AI 正文整理")
                },
            ).organizePending(limit = 10)

            assertEquals(1, supplementCalls)
        }
    }

    @Test
    fun supplementResolverRoutesWebAndXArticleUrlsToMatchingFetcher() = runBlocking {
        withRepositories { _, sources, items, _ ->
            val sourceId = saveXSource(sources)
            val (webItem, _) = items.upsertDraft(
                sourceId,
                xDraft(
                    "post-web",
                    text = "短",
                    normalizedJson = """{"id":"post-web","primary_url":"https://example.com/article"}""",
                ),
            )
            val (xArticleItem, _) = items.upsertDraft(
                sourceId,
                xDraft(
                    "post-article",
                    text = "短",
                    normalizedJson = """{"id":"post-article","primary_url":"https://x.com/i/article/1234567890"}""",
                ),
            )
            val calls = mutableListOf<String>()
            val resolver = DefaultExternalFavoriteSupplementResolver(
                fetchWebSupplement = { url, _, _ ->
                    calls += "web:$url"
                    ExternalFavoriteSupplement(url, "Web", "网页正文", "web")
                },
                fetchXStatusSupplement = { url, _, _ ->
                    calls += "status:$url"
                    ExternalFavoriteSupplement(url, "Status", "推文正文", "x_status")
                },
                fetchXArticleSupplement = { url, _, _ ->
                    calls += "article:$url"
                    ExternalFavoriteSupplement(url, "Article", "X 文章正文", "x_article")
                },
            )

            val web = resolver.resolve(webItem, webItem.toTestAiInput(), NoopFavoriteSyncHttpLogger, null)
            val article = resolver.resolve(xArticleItem, xArticleItem.toTestAiInput(), NoopFavoriteSyncHttpLogger, null)

            assertEquals("web", web?.sourceType)
            assertEquals("x_article", article?.sourceType)
            assertEquals(
                listOf(
                    "web:https://example.com/article",
                    "article:https://x.com/i/article/1234567890",
                ),
                calls,
            )
        }
    }

    @Test
    fun organizerWritesAiRequestAndResponseDiagnosticsWhenTaskLoggerIsProvided() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceId = saveXSource(sources)
            val (item, _) = items.upsertDraft(sourceId, xDraft("post-1", text = "原文内容"))
            val articleId = articles.insert(
                title = "Imported favorite",
                aiContent = "summary",
                aiMarkdownContent = "# X 收藏\n\n## 原文\n\nBody post-1\n\n## AI 整理\n\n待整理",
                url = "https://x.com/daily/status/post-1",
                isFavorite = 1,
                status = "completed",
            )
            items.markImported(item.id, articleId, duplicateLinked = false)
            val logger = RecordingHttpLogger()

            ExternalFavoriteAiOrganizer(
                itemRepo = items,
                articleRepo = articles,
                generateAnalysis = {
                    ExternalFavoriteAiAnalysis("AI 标题", "AI 摘要", "AI 正文整理")
                },
            ).organizePendingForSource(
                sourceId = sourceId,
                limit = 10,
                httpLogger = logger,
                taskId = 42,
            )

            assertTrue(logger.entries.any { it.contains("request:42:external_favorite_ai") && it.contains("externalId=post-1") })
            assertTrue(logger.entries.any { it.contains("response:42:external_favorite_ai:200") && it.contains("AI 正文整理") })
        }
    }

    @Test
    fun organizerDoesNotRetryFailedAiItemsDuringNormalSyncBudget() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceId = saveXSource(sources)
            val (item, _) = items.upsertDraft(sourceId, xDraft("post-failed", text = "failed text"))
            val articleId = articles.insert(
                title = "Failed favorite",
                aiContent = "failed text",
                aiMarkdownContent = "# X 收藏\n\n## 原文\n\nfailed text\n\n## AI 整理\n\n待整理",
                url = "https://x.com/daily/status/post-failed",
                status = "completed",
            )
            items.markImported(item.id, articleId, duplicateLinked = false)
            items.markAiState(item.id, ExternalItemAiStatus.failed.name, "ai_failed", "previous failure")
            var aiCalls = 0

            val organized = ExternalFavoriteAiOrganizer(
                itemRepo = items,
                articleRepo = articles,
                generateAnalysis = {
                    aiCalls += 1
                    ExternalFavoriteAiAnalysis("AI title", "AI summary", "AI markdown")
                },
            ).organizePendingForSource(sourceId, limit = 10, includeFailed = false)

            assertEquals(0, organized)
            assertEquals(0, aiCalls)
            assertEquals("failed", items.getBySource(sourceId).single().ai_status)
        }
    }

    @Test
    fun organizerRetriesFailedAiItemsWhenExplicitlyRequested() = runBlocking {
        withRepositories { _, sources, items, articles ->
            val sourceId = saveXSource(sources)
            val (item, _) = items.upsertDraft(sourceId, xDraft("post-failed-retry", text = "retry text"))
            val articleId = articles.insert(
                title = "Retry favorite",
                aiContent = "retry text",
                aiMarkdownContent = "# X 收藏\n\n## 原文\n\nretry text\n\n## AI 整理\n\n待整理",
                url = "https://x.com/daily/status/post-failed-retry",
                status = "completed",
            )
            items.markImported(item.id, articleId, duplicateLinked = false)
            items.markAiState(item.id, ExternalItemAiStatus.failed.name, "ai_failed", "previous failure")

            val organized = ExternalFavoriteAiOrganizer(
                itemRepo = items,
                articleRepo = articles,
                generateAnalysis = {
                    ExternalFavoriteAiAnalysis("Retry AI title", "Retry AI summary", "Retry AI markdown")
                },
            ).organizePendingForSource(sourceId, limit = 10, includeFailed = true)

            assertEquals(1, organized)
            assertEquals("completed", items.getBySource(sourceId).single().ai_status)
            assertTrue(articles.getById(articleId)!!.ai_markdown_content.orEmpty().contains("Retry AI markdown"))
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
        authJson: String = """{"access_token":"secret"}""",
        enabled: Boolean = true,
        configJson: String = "",
    ): Long = sources.save(
        provider = ExternalFavoriteProvider.X.id,
        displayName = "X Favorites",
        accountId = accountId,
        accountName = "@daily",
        authJson = authJson,
        enabled = enabled,
        configJson = configJson,
    )

    private companion object {
        fun com.dailysatori.shared.db.External_favorite_item.toTestAiInput(): ExternalFavoriteAiInput =
            ExternalFavoriteAiInput(
                provider = provider,
                title = title,
                text = text,
                authorName = author_name,
                sourceCreatedAt = source_created_at,
                canonicalUrl = canonical_url.orEmpty(),
            )

        fun xDraft(
            externalId: String,
            text: String = "Body $externalId",
            normalizedJson: String = """{"id":"$externalId"}""",
        ): ExternalFavoriteItemDraft = ExternalFavoriteItemDraft(
            provider = ExternalFavoriteProvider.X.id,
            externalId = externalId,
            canonicalUrl = "https://x.com/daily/status/$externalId",
            title = "Title $externalId",
            text = text,
            authorName = "Author",
            sourceCreatedAt = 1_700_000_000_000,
            favoritedAt = 1_700_000_100_000,
            normalizedJson = normalizedJson,
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

        const val IMPORT_RETRY_EXPECTED_LIMIT = 50L
    }

    private open class FakeConnector(
        override val capabilities: FavoriteConnectorCapabilities = xCapabilities(),
        private val pages: List<FavoriteFetchPage>,
        private val failOnFetch: Boolean = false,
        private val failWithRateLimitOnFetch: Int? = null,
        private val rateLimitResetAt: Long? = null,
    ) : FavoriteConnector {
        override val provider: String = ExternalFavoriteProvider.X.id
        val cursors = mutableListOf<String?>()
        val pageSizes = mutableListOf<Int>()
        val sinceExternalIds = mutableListOf<String?>()
        val fetchAuthJsons = mutableListOf<String>()
        var fetchCalls = 0

        override suspend fun fetchPage(
            source: External_favorite_source,
            cursor: String?,
            pageSize: Int,
            httpLogger: FavoriteSyncHttpLogger,
            taskId: Long?,
            shouldFetchDetail: FavoriteFetchDetailPolicy,
            sinceExternalId: String?,
        ): FavoriteFetchPage {
            fetchCalls += 1
            cursors += cursor
            pageSizes += pageSize
            sinceExternalIds += sinceExternalId
            fetchAuthJsons += source.auth_json
            if (failWithRateLimitOnFetch == fetchCalls) {
                throw XFavoriteRateLimitException(statusCode = 429, rateLimitResetAt = rateLimitResetAt)
            }
            if (failOnFetch) error("retry_failed must not fetch provider pages")
            return pages.getOrElse(fetchCalls - 1) { FavoriteFetchPage(emptyList(), null) }
        }
    }

    private class RefreshingConnector(
        private val refreshedAuthJson: String,
        pages: List<FavoriteFetchPage>,
    ) : FakeConnector(pages = pages) {
        var refreshCalls = 0

        override suspend fun refreshAuth(source: External_favorite_source): External_favorite_source {
            refreshCalls += 1
            return source.copy(auth_json = refreshedAuthJson)
        }
    }

    private class RateLimitedConnector(
        private val resetAt: Long,
    ) : FavoriteConnector {
        override val provider: String = ExternalFavoriteProvider.X.id
        override val capabilities: FavoriteConnectorCapabilities = xCapabilities()

        override suspend fun fetchPage(
            source: External_favorite_source,
            cursor: String?,
            pageSize: Int,
            httpLogger: FavoriteSyncHttpLogger,
            taskId: Long?,
            shouldFetchDetail: FavoriteFetchDetailPolicy,
            sinceExternalId: String?,
        ): FavoriteFetchPage {
            throw XFavoriteRateLimitException(statusCode = 429, rateLimitResetAt = resetAt)
        }
    }

    private class CancellingConnector : FavoriteConnector {
        override val provider: String = ExternalFavoriteProvider.X.id
        override val capabilities: FavoriteConnectorCapabilities = xCapabilities()

        override suspend fun fetchPage(
            source: External_favorite_source,
            cursor: String?,
            pageSize: Int,
            httpLogger: FavoriteSyncHttpLogger,
            taskId: Long?,
            shouldFetchDetail: FavoriteFetchDetailPolicy,
            sinceExternalId: String?,
        ): FavoriteFetchPage {
            throw CancellationException("sync cancelled")
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
            httpLogger: FavoriteSyncHttpLogger,
            taskId: Long?,
            shouldFetchDetail: FavoriteFetchDetailPolicy,
            sinceExternalId: String?,
        ): FavoriteFetchPage {
            activeFetches += 1
            if (activeFetches > 1) overlapped = true
            delay(25)
            fetchCalls += 1
            activeFetches -= 1
            return FavoriteFetchPage(listOf(xDraft("post-$fetchCalls")), null)
        }
    }

    private class RecordingHttpLogger : FavoriteSyncHttpLogger {
        val entries = mutableListOf<String>()

        override fun logRequest(
            taskId: Long?,
            label: String,
            method: String,
            url: String,
            parameters: Map<String, String>,
        ) {
            entries += "request:$taskId:$label:$method:$url:${parameters.entries.joinToString("&") { "${it.key}=${it.value}" }}"
        }

        override fun logResponse(
            taskId: Long?,
            label: String,
            statusCode: Int,
            headers: Map<String, String>,
            body: String,
        ) {
            entries += "response:$taskId:$label:$statusCode:${headers.entries.joinToString(",") { "${it.key}=${it.value}" }}:$body"
        }
    }
}
