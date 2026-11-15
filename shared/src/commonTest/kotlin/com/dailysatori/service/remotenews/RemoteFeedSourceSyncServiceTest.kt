package com.dailysatori.service.remotenews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteFeedSyncStateRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract of the RFC 5005 sync state machine: a confirmed checkpoint, an active gap generation with its
 * own cumulative budget, staged candidates that never become articles before closure, and an explicit
 * blocked state. The minimal counterexamples of the review (R1-R4) are encoded here as behaviour.
 */
class RemoteFeedSourceSyncServiceTest {
    @Test
    fun firstRoundStoresOnlyTheEntryDocument() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.page(P2, paged(listOf(0)))

            val outcome = h.round()

            assertEquals(listOf(ENTRY), h.requested)
            assertEquals(3, outcome.inserted)
            assertFalse(outcome.gap)
            assertFalse(outcome.pending)
            assertEquals(3L, h.mappings())
            assertFalse(h.hasStaged())
            assertEquals(listOf(idOf(3), idOf(2), idOf(1)), assertNotNull(h.state()).checkpointIds)
        }
    }

    /** Review counterexample: a pinned checkpoint entry above the hole must not close it. */
    @Test
    fun pinnedCheckpointEntryAboveTheHoleDoesNotCloseTheGap() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(PINNED, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(PINNED) + (30 downTo 22).toList(), next = P2))
            h.page(P2, paged(listOf(PINNED) + (21 downTo 13).toList(), next = P3))
            h.page(P3, paged((12 downTo 3).toList() + 0))

            val outcome = h.round()

            assertEquals(listOf(ENTRY, ENTRY, P2, P3), h.requested, "the page without a reliable boundary must be requested")
            assertTrue(outcome.gap, "an unproven closure is reported instead of claimed: " + outcome.message)
            assertFalse(h.stored(0), "content after the unconfirmed tail is never promoted")
            (13..21).forEach {
                assertFalse(h.stored(it), "candidate " + it + " is not an article")
                assertNull(h.mappingOf(it), "candidate " + it + " queues no AI work")
            }
            assertEquals(12L, h.mappings(), "only the entry documents are articles")
            assertTrue(h.stagedUrls().containsAll((13..21).map(::urlOf)))
        }
    }

    /** The same layout where the page really contains the confirmed boundary closes and promotes. */
    @Test
    fun aContiguousConfirmedBoundaryClosesAndPromotesTheHole() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(PINNED, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(PINNED) + (30 downTo 22).toList(), next = P2))
            h.page(P2, paged(listOf(PINNED) + (21 downTo 13).toList(), next = P3))
            h.page(P3, paged((12 downTo 1).toList() + 0))

            val outcome = h.round()

            assertFalse(outcome.gap, outcome.message)
            (3..30).forEach { assertTrue(h.stored(it), "confirmed new entry " + it + " is promoted") }
            assertFalse(h.stored(0), "content after the confirmed boundary is not promoted")
            assertNull(h.mappingOf(0), "pre-subscription content never queues AI work")
            assertEquals(0, h.stagedCount())
            assertEquals(31L, h.mappings(), "baseline plus the whole filled hole")
            assertEquals(listOf(idOf(PINNED)) + (30 downTo 22).map(::idOf), assertNotNull(h.state()).checkpointIds)
            assertTrue(h.processingStates().isNotEmpty(), "promoted entries reach the existing AI queue")
        }
    }

    /** One old entry in any position proves nothing; small checkpoints stay conservative. */
    @Test
    fun oneOldEntryNeverClosesTheGapInAnyPosition() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged((5 downTo 3).toList(), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(9, 8), next = P2))
            h.page(P2, paged(listOf(7, 4, 6), next = P3))
            h.page(P3, paged(listOf(2, 1)))

            val outcome = h.round()

            assertEquals(listOf(ENTRY, ENTRY, P2, P3), h.requested)
            assertTrue(outcome.gap, "a mid-page old entry above unknown content closes nothing")
            assertFalse(h.stored(6), "the unknown content below the old entry is not promoted")
            assertFalse(h.stored(7))
            assertTrue(h.stagedUrls().contains(urlOf(6)))
        }

        Harness().use { h ->
            h.page(ENTRY, paged((5 downTo 3).toList(), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(9, 8), next = P2))
            h.page(P2, paged(listOf(8, 6)))

            val outcome = h.round()

            assertTrue(outcome.gap, "an isolated checkpoint entry near the page tail is not a boundary")
            assertFalse(h.stored(6), "unknown content beside an old entry is not promoted")
            assertTrue(h.stagedUrls().contains(urlOf(6)))
        }

        // A single-entry checkpoint only closes when the match reaches the end of the page.
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(2), next = P2))
            h.page(P2, paged(listOf(1, 0)))

            val outcome = h.round()

            assertTrue(outcome.gap, "an unknown tail after a one-entry boundary keeps the gap open")
            assertFalse(h.stored(0))
        }

        Harness().use { h ->
            h.page(ENTRY, paged(listOf(1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(2), next = P2))
            h.page(P2, paged(listOf(1)))

            val outcome = h.round()

            assertFalse(outcome.gap, outcome.message)
            assertTrue(h.stored(2))
            assertEquals(0, h.stagedCount())
        }
    }

    @Test
    fun unchangedRoundsNeverWalkHistoryAndKeepAiState() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.page(P2, paged(listOf(0)))
            h.round()
            val processing = h.processingStates()

            h.round()
            h.round()

            assertEquals(listOf(ENTRY, ENTRY, ENTRY), h.requested)
            assertEquals(3L, h.mappings())
            assertEquals(processing, h.processingStates(), "no AI work is queued again for stored entries")
            assertFalse(h.hasStaged())
        }
    }

    /** R3: a pinned old article must not close the hole; the missing pages are really filled. */
    @Test
    fun pinnedOldEntryDoesNotCloseTheGap() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(100, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(100) + (30 downTo 22).toList(), next = P2))
            h.page(P2, paged((21 downTo 12).toList(), next = P3))
            h.page(P3, paged((11 downTo 1).toList() + 0, next = P4))

            val outcome = h.round()

            assertEquals(listOf(ENTRY, ENTRY, P2, P3), h.requested)
            assertFalse(outcome.gap, "the hole is closed, not reported: " + outcome.message)
            (12..21).forEach { assertTrue(h.stored(it), "missing page entry " + it + " is filled") }
            (3..11).forEach { assertTrue(h.stored(it), "missing entry " + it + " is filled") }
            assertFalse(h.stored(0), "content below the confirmed checkpoint is never imported")
            assertFalse(h.hasStaged())
            assertEquals(idOf(22), assertNotNull(h.state()).checkpointIds.firstOrNull { it == idOf(22) })
        }
    }

    /** R1/R2: with the default budget the cumulative cap is reachable, and nothing pre-subscription lands. */
    @Test
    fun rotatedPagedChainNeverImportsPreSubscriptionHistory() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(6, 5), next = P2))
            h.page(P2, paged(listOf(4), next = pathOfPage(3)))
            (3..16).forEach { index ->
                h.page(pathOfPage(index), paged(listOf(20 - index), next = pathOfPage(index + 1).takeIf { index < 16 }))
            }

            var outcome = h.round()
            var rounds = 1
            while (!outcome.blocked && rounds < 20) {
                outcome = h.round()
                rounds += 1
            }

            assertTrue(outcome.blocked, "an unclosable gap must block: " + outcome)
            assertTrue(outcome.gap)
            assertTrue(outcome.message.orEmpty().isNotBlank(), outcome.message)
            assertEquals(5L, h.mappings(), "only the entry documents are articles")
            assertFalse(h.stored(4), "a candidate is not an article before closure")
            (7..9).forEach { assertFalse(h.stored(it), "pre-subscription entry " + it + " never becomes an article") }
            assertTrue(h.stagedUrls().contains(urlOf(4)), "the post-baseline candidate is kept in staging")
            val before = h.requested.size
            val state = assertNotNull(h.state())
            assertTrue(state.scanPages >= 10, "the cap is cumulative across rounds: " + state.scanPages)

            h.round()
            val after = h.requested.drop(before)
            assertTrue(after.size <= 2, "a blocked gap is not crawled again automatically: " + after)
            assertEquals(ENTRY, after.first(), "the entry document is always refreshed")
            assertFalse(after.contains(pathOfPage(5)), "chain pages behind the blocked gap are not re-crawled")
        }
    }

    @Test
    fun stagedCandidatesNeverReachTheNormalStoreOrTheAiQueue() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            val processing = h.processingStates()
            h.page(ENTRY, paged(listOf(6, 5), next = P2))
            h.page(P2, paged(listOf(9), next = P3))

            val outcome = h.round(RemoteFeedSyncBudget(maxGapPages = 1))

            assertTrue(outcome.blocked)
            assertEquals(5L, h.mappings())
            assertEquals(processing.size + 2, h.processingStates().size, "only the entry document queues AI work")
            assertNull(h.mappingOf(9), "a staged candidate owns no article mapping")
            assertTrue(h.stagedUrls().contains(urlOf(9)))
            assertFalse(h.stored(9))
        }
    }

    /** R4: a cycle across rounds is detected, blocks the generation and does not pollute data. */
    @Test
    fun crossRoundCycleIsBlockedAndNotRecrawled() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(5, 4), next = P2))
            h.page(P2, paged(listOf(9), next = P3))
            h.page(P3, paged(listOf(8), next = P2))

            val outcome = h.round()

            assertTrue(outcome.blocked)
            assertTrue(outcome.gap)
            assertEquals(5L, h.mappings(), "cycle candidates stay staged")
            assertFalse(h.stored(9))
            assertFalse(h.stored(8))
            assertEquals(listOf(ENTRY, ENTRY, P2, P3), h.requested)

            h.page(ENTRY, paged(listOf(6, 5), next = P2))
            val next = h.round()

            assertTrue(next.blocked)
            assertEquals(listOf(ENTRY, ENTRY, P2, P3, ENTRY, P2), h.requested, "only the linkage check runs")
        }
    }

    @Test
    fun anInterruptedGapResumesAfterVerifyingItsLastPage() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(6, 5), next = P2))
            h.page(P2, paged(listOf(4), next = P3))
            h.page(P3, paged((3 downTo 1).toList(), next = P4))
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(6, 5), next = P2))

            val first = h.round(RemoteFeedSyncBudget(maxRequests = 2))

            assertTrue(first.pending)
            assertEquals(5L, h.mappings(), "entry document stored, candidates staged")
            assertTrue(h.hasStaged())

            val second = h.round()

            assertEquals(listOf(ENTRY, ENTRY, P2, ENTRY, P2, P3), h.requested)
            assertFalse(second.pending)
            assertFalse(second.gap)
            assertTrue(h.stored(4), "the resumed scan promotes its candidates")
            assertEquals(0, h.stagedCount())
        }
    }

    @Test
    fun aChangedPageReestablishesTheScanWithoutSkipping() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(8, 7), next = P2))
            h.page(P2, paged(listOf(6, 5), next = P3))
            h.round(RemoteFeedSyncBudget(maxRequests = 2))
            assertTrue(h.hasStaged())

            // The publisher pushed new entries into the second page; resuming blind would skip them.
            h.page(P2, paged(listOf(9, 8), next = P3))
            h.page(P3, paged((6 downTo 1).toList(), next = P4))

            val outcome = h.round()

            assertTrue(h.stored(9), "content pushed onto the second page is not skipped")
            assertTrue(h.stored(4), "the hole below the re-read pages is still filled")
            assertFalse(outcome.gap, outcome.message)
            assertEquals(0, h.stagedCount())
        }
    }

    @Test
    fun aPartialPromotionKeepsTheGapOpenAndRetriesIdempotently() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            h.failMappingFor(4)
            h.page(ENTRY, paged(listOf(6), next = P2))
            h.page(P2, paged((5 downTo 1).toList(), next = P3))

            val first = h.round()

            assertTrue(first.pending, "a failed promotion is not a closure: " + first.message)
            assertTrue(h.stored(5))
            assertFalse(h.stored(4))
            assertEquals(listOf(urlOf(4)), h.stagedUrls(), "the failed candidate stays staged")

            h.dropFailMapping()
            val second = h.round()

            assertTrue(h.stored(4))
            assertFalse(second.gap, second.message)
            assertEquals(0, h.stagedCount())
        }
    }

    @Test
    fun aPromotionRetryOnlyConfirmsTheGenerationTarget() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            h.failMappingFor(4)
            h.page(ENTRY, paged(listOf(6), next = P2))
            h.page(P2, paged((5 downTo 1).toList(), next = P3))
            val first = h.round()

            assertTrue(first.pending, "a failed promotion is not a closure: " + first.message)
            assertEquals(listOf(idOf(6)), assertNotNull(h.state()).targetEntryIds)

            // New entries arrive before the retry: they were never scanned, so the retry may not claim them.
            h.dropFailMapping()
            h.page(ENTRY, paged(listOf(8, 7), next = P2))
            val second = h.round()

            assertTrue(h.stored(4))
            assertEquals(listOf(idOf(6)), assertNotNull(h.state()).checkpointIds, "only the generation target is confirmed")

            val third = h.round()
            assertTrue(
                third.pending || third.gap || h.hasStaged(),
                "the next round still detects the gap above the confirmed target",
            )
        }
    }

    @Test
    fun aModeChangeDiscardsIncompatibleProgress() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(5, 4), next = P2))
            h.page(P2, paged(listOf(9), next = P3))
            h.round()
            assertTrue(h.stagedUrls().contains(urlOf(9)))

            h.page(ENTRY, archiveFeed(listOf(5, 4), prevArchive = A1))

            val outcome = h.round()

            assertTrue(outcome.entryRead)
            assertEquals(0, h.stagedCount(), "the paged cursor and candidates are incompatible with the new mode")
            assertFalse(h.stored(9))
            assertEquals(A1, pathOf(assertNotNull(h.state()).confirmedAnchorUrl))
            assertFalse(h.requested.contains(A1), "the archive behind the new baseline is not history to import")
        }
    }

    @Test
    fun anArchiveChainAdvancesItsAnchorWithoutRepeatingDocuments() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, archiveFeed(listOf(5), prevArchive = A1))
            h.page(A1, archiveFeed(listOf(1), archive = true))
            h.round()
            assertEquals(A1, pathOf(assertNotNull(h.state()).confirmedAnchorUrl))

            h.round()
            assertEquals(listOf(ENTRY, ENTRY), h.requested)

            h.page(ENTRY, archiveFeed(listOf(5), prevArchive = A2))
            h.page(A2, archiveFeed(listOf(4), prevArchive = A1, archive = true))
            val third = h.round()

            assertFalse(third.gap)
            assertTrue(h.stored(4))
            assertEquals(A2, pathOf(assertNotNull(h.state()).confirmedAnchorUrl))

            h.round()
            assertEquals(listOf(ENTRY, ENTRY, ENTRY, A2, ENTRY), h.requested, "a processed archive is not read again")
            assertFalse(h.requested.contains(A1))
        }
    }

    @Test
    fun aLostArchiveAnchorBlocksInsteadOfImportingOlderDocuments() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, archiveFeed(listOf(5), prevArchive = A1))
            h.page(A1, archiveFeed(listOf(1), archive = true))
            h.round()

            h.page(ENTRY, archiveFeed(listOf(5), prevArchive = A2))
            h.page(A2, archiveFeed(listOf(4), prevArchive = null, archive = true))

            val outcome = h.round()

            assertTrue(outcome.blocked, "a chain that ends without the anchor cannot be closed")
            assertFalse(h.stored(4), "the candidate is not an article")
            assertTrue(h.stagedUrls().contains(urlOf(4)))
            assertFalse(h.requested.contains(A1))

            val before = h.requested.size
            val again = h.round()
            assertTrue(again.blocked)
            assertEquals(listOf(ENTRY), h.requested.drop(before), "the same blocked archive chain is not re-crawled")
        }
    }

    @Test
    fun aStagedBytesLimitBlocksTheGeneration() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(6), next = P2))
            h.page(P2, paged(listOf(9), next = P3))

            val outcome = h.round(RemoteFeedSyncBudget(maxStagedBytes = 1))

            assertTrue(outcome.blocked)
            assertTrue(outcome.message.orEmpty().contains("上限"), outcome.message)
            assertEquals(4L, h.mappings())
            assertFalse(h.stored(9))
        }
    }

    @Test
    fun aReconfiguredEntryAddressNeverReusesOldCandidates() = runBlocking {
        Harness().use { h ->
            h.page(ENTRY, paged(listOf(3, 2, 1), next = P2))
            h.round()
            h.page(ENTRY, paged(listOf(5, 4), next = P2))
            h.page(P2, paged(listOf(9), next = P3))
            h.round()
            assertTrue(h.hasStaged())

            h.renameSource(NEW_ENTRY)
            h.page(NEW_ENTRY, archiveFeed(listOf(7), prevArchive = A1))

            val outcome = h.round(entryPath = NEW_ENTRY)

            assertTrue(outcome.entryRead)
            assertFalse(h.stored(9), "candidates of the old address are never promoted")
            assertEquals(0, h.stagedCount(), "the old generation is dropped with the old address")
            assertEquals(6L, h.mappings(), "old articles stay, the new entry document is the new baseline")
            assertEquals(A1, pathOf(assertNotNull(h.state()).confirmedAnchorUrl))
        }
    }

    private class Harness : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val db: DailySatoriDatabase
        private val sourceRepo: RemoteNewsSourceRepository
        private val syncRepo: RemoteArticleSyncRepository
        private val stateRepo: RemoteFeedSyncStateRepository
        private val articles: ArticleRepository
        private val http: HttpClient
        private val service: RemoteFeedSourceSyncService
        val requested = mutableListOf<String>()
        private val pages = mutableMapOf<String, PageResponse>()
        private var sourceId: Long
        private var now = 1_000L

        init {
            DailySatoriDatabase.Schema.create(driver)
            db = DailySatoriDatabase(driver)
            sourceRepo = RemoteNewsSourceRepository(db)
            sourceRepo.save(null, "示例", urlOfPath(ENTRY), "", enabled = true)
            sourceId = sourceRepo.getAll().single().id
            syncRepo = RemoteArticleSyncRepository(db)
            stateRepo = RemoteFeedSyncStateRepository(db)
            articles = ArticleRepository(db)
            http = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                requested += path
                val response = pages[path] ?: PageResponse(404)
                if (response.status != 200) {
                    respond("", HttpStatusCode.fromValue(response.status))
                } else {
                    respond(response.body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml"))
                }
            })
            service = RemoteFeedSourceSyncService(
                stateRepo,
                RemoteArticleSyncService(articles, syncRepo),
                RemoteNewsService(http),
            )
        }

        fun page(path: String, body: String) {
            pages[path] = PageResponse(200, body)
        }

        fun page(path: String, status: Int) {
            pages[path] = PageResponse(status)
        }

        fun renameSource(path: String) {
            sourceRepo.save(sourceId, "示例", urlOfPath(path), "", enabled = true)
        }

        suspend fun round(
            budget: RemoteFeedSyncBudget = RemoteFeedSyncBudget(),
            entryPath: String = ENTRY,
        ): RemoteFeedSyncOutcome {
            now += 1_000L
            val address = urlOfPath(entryPath)
            return service.sync(
                remoteSourceId = sourceId,
                entryUrl = address,
                config = RemoteNewsConfigValues(address, ""),
                sourceDate = "2026-11-15",
                now = now,
                budget = budget,
            )
        }

        fun state(): RemoteFeedSyncState? = stateRepo.find(sourceId)

        fun mappings(): Long = syncRepo.countBySource(sourceId)

        fun stored(id: Int): Boolean = articles.getByUrl(urlOf(id)) != null

        fun mappingOf(id: Int) = syncRepo.findByRemoteIdentity(sourceId, idOf(id))

        fun stagedUrls(): List<String> = state()?.let { state ->
            stateRepo.stagedArticles(sourceId, state.generation, includeStale = true).map { it.article.url.orEmpty() }
        }.orEmpty()

        fun stagedCount(): Int = state()?.let { state ->
            stateRepo.stagedArticles(sourceId, state.generation).size
        } ?: 0

        fun hasStaged(): Boolean = stagedCount() > 0

        fun processingStates(): List<String> =
            syncRepo.getLatestMappingsBySource(sourceId, 5000).map { it.processing_state }.sorted()

        fun failMappingFor(id: Int) {
            driver.execute(
                null,
                "CREATE TRIGGER fail_mapping BEFORE INSERT ON remote_article_sync_item " +
                    "WHEN NEW.url = '" + urlOf(id) + "' BEGIN SELECT RAISE(ABORT, 'test failure'); END;",
                0,
            )
        }

        fun dropFailMapping() {
            driver.execute(null, "DROP TRIGGER IF EXISTS fail_mapping", 0)
        }

        override fun close() {
            http.close()
            driver.close()
        }
    }

    private class PageResponse(val status: Int, val body: String = "")

    private companion object {
        const val ENTRY = "/feed"
        const val NEW_ENTRY = "/feed-v2"
        const val P2 = "/page2"
        const val P3 = "/page3"
        const val P4 = "/page4"
        const val PINNED = 100
        const val A1 = "/archive/2026-09"
        const val A2 = "/archive/2026-10"
    }
}

private fun idOf(id: Int): Long = remoteFeedArticleId("id-" + id, urlOf(id), null)

private fun urlOf(id: Int): String = "https://feed.test/a/" + id

private fun urlOfPath(path: String): String = "https://feed.test" + path

private fun pathOf(url: String?): String? = url?.substringAfter("https://feed.test")

private fun pathOfPage(index: Int): String = "/chain" + index

/** RSS 2.0 document with RFC 5005 paged relations. */
internal fun paged(items: List<Int>, next: String? = null, padding: Int = 0): String {
    val head = next?.let { "<atom:link rel=\"next\" href=\"" + it + "\"/>" }.orEmpty()
    return feedDocument(items, head, historyNamespace = false, padding = padding)
}

/** RSS 2.0 subscription or archive document with RFC 5005 archive relations. */
internal fun archiveFeed(items: List<Int>, prevArchive: String? = null, archive: Boolean = false): String {
    val head = buildString {
        if (archive) append("<fh:archive/>")
        prevArchive?.let { append("<atom:link rel=\"prev-archive\" href=\"" + it + "\"/>") }
    }
    return feedDocument(items, head, historyNamespace = archive)
}

private fun feedDocument(items: List<Int>, head: String, historyNamespace: Boolean, padding: Int = 0): String {
    val namespaces = " xmlns:atom=\"http://www.w3.org/2005/Atom\"" +
        if (historyNamespace) " xmlns:fh=\"http://purl.org/syndication/history/1.0\"" else ""
    val filler = if (padding > 0) "<description>" + "x".repeat(padding) + "</description>" else ""
    val body = items.joinToString("") { id ->
        "<item><title>文章" + id + "</title><link>" + urlOf(id) + "</link>" +
            "<guid isPermaLink=\"false\">id-" + id + "</guid><description>正文" + id + "</description></item>"
    }
    return "<rss version=\"2.0\"" + namespaces + "><channel><title>示例</title>" + head + filler + body + "</channel></rss>"
}
