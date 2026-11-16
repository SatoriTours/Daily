package com.dailysatori.service.remotenews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteFeedHistoryRepository
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
 * First-import history of one subscription. The walk is newest-first, independent from the incremental
 * gap state, bounded per round, resumable, and never turns unselected older content into articles.
 */
class RemoteFeedHistoryImportTest {
    @Test
    fun latestFiveHundredStopsAtTheDedupedTarget() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.LATEST_500).use { h ->
            h.chain(newest = 950, totalPages = 12)
            h.page(ENTRY, paged((1000 downTo 951).toList(), next = PAGE1))

            val rounds = h.drain()

            val history = assertNotNull(h.history())
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, history.status)
            assertEquals(500, h.count(), "the newest 500 entries are the whole first import")
            assertEquals(500, history.importedCount, "the count covers the newest entry document too")
            assertTrue(h.requested.contains(PAGE9), "the target needs nine older pages")
            assertFalse(h.requested.contains(PAGE10), "content below the chosen range is never fetched")
            assertTrue(rounds.size >= 2, "a bounded round continues with another bounded round")
            assertTrue(assertNotNull(h.history()).lastError.isEmpty())
        }
    }

    @Test
    fun anEntryDocumentAboveTheTargetIsTruncatedAndLaterRunsNeverBackfillTheRest() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.LATEST_500).use { h ->
            h.page(ENTRY, paged((1000 downTo 401).toList(), next = PAGE1))

            h.round()

            assertEquals(500, h.count(), "the entry document is capped at the chosen range")
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(h.history()).status)
            assertEquals(listOf(ENTRY), h.requested, "no older page is read once the range is full")

            // The publisher adds one new entry; the 100 unselected entries below the range stay out.
            h.page(ENTRY, paged((1001 downTo 401).toList(), next = PAGE1))
            h.page(PAGE1, paged((400 downTo 351).toList()))

            h.round()

            assertEquals(501, h.count(), "only the genuinely new entry is added")
            assertTrue(h.stored(1001))
            (401..500).forEach { assertFalse(h.stored(it), "unselected older entry " + it + " is never backfilled") }
            assertFalse(h.requested.contains(PAGE1), "a finished range is not walked again")
        }
    }

    @Test
    fun allHistoryWalksTheWholeChainOnceAndStillTakesNewEntries() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged((80 downTo 61).toList(), next = PAGE2))
            h.page(PAGE2, paged((60 downTo 41).toList()))

            h.drain()

            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(h.history()).status)
            assertEquals(60, h.count())
            (41..100).forEach { assertTrue(h.stored(it), "history entry " + it + " is stored") }
            assertEquals(listOf(ENTRY, PAGE1, PAGE2), h.requested, "each document is read once")

            h.page(ENTRY, paged((101..101).toList() + (100 downTo 81), next = PAGE1))
            h.round()

            assertTrue(h.stored(101))
            assertEquals(61, h.count(), "later increments are never capped by the first-import range")
            assertFalse(h.requested.drop(3).contains(PAGE2), "the finished walk stays finished")
        }
    }

    @Test
    fun aSingleDocumentIsCompletedAsWindowOnlyNeverAsFullHistory() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.page(ENTRY, paged((50 downTo 31).toList()))

            h.round()

            val history = assertNotNull(h.history())
            assertEquals(RemoteFeedHistoryStatus.COMPLETE_WINDOW, history.status)
            assertEquals(20, h.count())
            assertEquals(listOf(ENTRY), h.requested)
        }
    }

    @Test
    fun anExistingSourceWithoutAPolicyNeverDownloadsHistory() = runBlocking {
        Harness(null).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged((80 downTo 61).toList()))

            h.round()

            assertEquals(20, h.count())
            assertNull(h.history(), "no history walk is opened for an existing source")
            assertEquals(listOf(ENTRY), h.requested)

            h.page(ENTRY, paged((101 downTo 81).toList(), next = PAGE1))
            h.round()

            assertEquals(21, h.count(), "the daily increment keeps working without history")
            assertFalse(h.requested.contains(PAGE1))
        }
    }

    @Test
    fun anExplicitBackfillKeepsExistingArticlesAndTheirAiState() = runBlocking {
        Harness(null).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged((80 downTo 61).toList()))
            h.round()
            val article = assertNotNull(h.articles.getByUrl(h.urlOf(100)))
            h.articles.updateAiContent(article.id, "AI 摘要", "AI 标题", null)
            h.articles.toggleFavorite(article.id)

            h.historyRepo.start(h.sourceId, RemoteFeedHistoryStrategy.ALL_HISTORY, h.nowForStart())
            h.round()

            val kept = assertNotNull(h.articles.getByUrl(h.urlOf(100)))
            assertEquals("AI 摘要", kept.ai_content, "a backfill never overwrites existing AI output")
            assertEquals(1L, kept.is_favorite, "a backfill never clears the user's favorite")
            assertEquals(40, h.count())
            assertTrue(h.stored(61))
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(h.history()).status)
        }
    }

    @Test
    fun repeatedEntriesAreStoredOnceAndNeverJoinTheDailyWindow() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged(listOf(80, 79, 78) + (80 downTo 61).toList(), next = PAGE2))
            h.page(PAGE2, paged((60 downTo 51).toList() + 79))

            h.drain()

            assertEquals(50, h.count(), "duplicated entries are stored exactly once")
            val historyMapping = assertNotNull(h.mappingOf(70))
            assertEquals(REMOTE_FEED_HISTORY_SOURCE_DATE, historyMapping.source_date,
                "history entries stay outside the daily news window")
            assertEquals(20, h.articlesOfToday().size, "only the entry document belongs to today's window")
            assertTrue(h.aiTasks().isEmpty(), "a history import never queues bulk AI work")
        }
    }

    @Test
    fun aFailedPageKeepsItsCursorAndTheNextRoundCompletes() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged((80 downTo 61).toList(), next = PAGE2))
            h.page(PAGE2, paged((60 downTo 41).toList()))
            h.failMappingFor(50)

            val first = h.round()

            val afterFirst = assertNotNull(h.history())
            assertEquals(RemoteFeedHistoryStatus.RUNNING, afterFirst.status, "a failure never completes the walk")
            assertTrue(afterFirst.lastError.isNotBlank())
            assertFalse(h.stored(50))
            assertTrue(first.historyMessage != null)
            assertFalse(first.historyPending, "a failure does not spin an immediate retry loop")

            h.dropFailMapping()
            h.round()

            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(h.history()).status)
            assertTrue(h.stored(50))
            assertEquals(60, h.count())
        }
    }

    @Test
    fun aCyclicChainBlocksInsteadOfCompleting() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged((80 downTo 61).toList(), next = PAGE2))
            h.page(PAGE2, paged((60 downTo 41).toList(), next = PAGE1))

            val outcome = h.round()

            val history = assertNotNull(h.history())
            assertEquals(RemoteFeedHistoryStatus.BLOCKED, history.status)
            assertTrue(history.blockedReason.contains("循环"), history.blockedReason)
            assertEquals(history.blockedReason, outcome.historyMessage)
            assertFalse(outcome.historyPending)
        }
    }

    @Test
    fun pauseStopsTheWalkAndResumeContinuesFromTheStoredCursor() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged((80 downTo 61).toList(), next = PAGE2))
            h.page(PAGE2, paged((60 downTo 41).toList()))

            h.round(RemoteFeedHistoryBudget(maxPages = 1))
            assertEquals(RemoteFeedHistoryStatus.RUNNING, assertNotNull(h.history()).status)
            assertTrue(h.historyRepo.pause(h.sourceId, h.nowForStart()))

            val before = h.requested.size
            val paused = h.round()

            assertEquals(listOf(ENTRY), h.requested.drop(before), "a paused walk reads nothing")
            assertEquals(RemoteFeedHistoryStatus.PAUSED, paused.historyStatus)
            assertFalse(paused.historyPending)

            h.historyRepo.resume(h.sourceId, h.nowForStart())
            h.round()

            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(h.history()).status)
            assertEquals(60, h.count())
            assertTrue(h.requested.count { it == PAGE1 } <= 2, "resume re-verifies the last page once instead of restarting")
            assertEquals(1, h.requested.count { it == PAGE2 }, "the walk continues from the stored cursor")
        }
    }

    @Test
    fun shiftedPagesAreReWalkedInsteadOfSkipped() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged((100 downTo 81).toList(), next = PAGE2))
            h.page(PAGE2, paged((80 downTo 61).toList()))

            h.round(RemoteFeedHistoryBudget(maxPages = 1))

            // The publisher pushes five new entries into the paginated window between rounds.
            h.page(PAGE1, paged((105 downTo 86).toList(), next = PAGE2))
            h.page(PAGE2, paged((85 downTo 66).toList(), next = PAGE3))
            h.page(PAGE3, paged((65 downTo 46).toList()))

            h.drain()

            (46..105).forEach { assertTrue(h.stored(it), "entry " + it + " survives the shifted pagination") }
            assertEquals(60, h.count(), "the re-walk stores no duplicates")
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(h.history()).status)
        }
    }

    @Test
    fun aChangedAddressStartsItsOwnWalkWithoutReusingTheOldCursor() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged((80 downTo 61).toList()))
            h.round()
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(h.history()).status)

            h.renameSource(NEW_ENTRY)
            h.page(NEW_ENTRY, paged((300 downTo 281).toList(), next = PAGE4))
            h.page(PAGE4, paged((280 downTo 261).toList()))
            h.round(entryPath = NEW_ENTRY)

            val history = assertNotNull(h.history())
            assertEquals(h.urlOfPath(NEW_ENTRY), history.entryUrl, "the walk follows the current address")
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, history.status)
            assertEquals(80, h.count(), "the old walk's articles stay and the new address is imported")
            (261..300).forEach { assertTrue(h.stored(it), "new address entry " + it + " is imported") }
            assertEquals(1, h.requested.count { it == PAGE1 }, "the old walk is not repeated")
            assertEquals(1, h.requested.count { it == PAGE4 }, "the new address is walked from its own start")
        }
    }

    @Test
    fun aModeSwitchReopensAWindowOnlyWalkOnTheNewLayout() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.page(ENTRY, paged((100 downTo 81).toList()))
            h.round()
            assertEquals(RemoteFeedHistoryStatus.COMPLETE_WINDOW, assertNotNull(h.history()).status)

            h.page(ENTRY, archiveFeed((110 downTo 91).toList(), prevArchive = ARCHIVE1))
            h.page(ARCHIVE1, archiveFeed((90 downTo 71).toList(), archive = true))
            h.round()

            val history = assertNotNull(h.history())
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, history.status)
            assertTrue(h.stored(71), "the archived history is imported through prev-archive")
            assertEquals(40, h.count(), "the new entry document overlaps the old one; only new ids are stored")
            assertEquals(1, h.requested.count { it == ARCHIVE1 })
        }
    }

    @Test
    fun theHistoryBudgetIsPerRoundAndNeverTheCumulativeGapCeiling() = runBlocking {
        Harness(RemoteFeedHistoryStrategy.ALL_HISTORY).use { h ->
            h.chain(newest = 950, totalPages = 12)
            h.page(ENTRY, paged((1000 downTo 951).toList(), next = PAGE1))

            val rounds = h.drain(RemoteFeedHistoryBudget(maxPages = 3))

            assertTrue(rounds.size >= 4, "a 12-page chain needs several bounded rounds")
            assertTrue(rounds.count { it.historyPending } >= 2, "each exhausted round asks for a continuation")
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(h.history()).status)
            assertEquals(650, h.count())
            assertEquals(1, h.requested.count { it == PAGE1 }, "the continued walk never restarts a finished page")
            assertEquals(1, h.requested.count { it == PAGE12 })
        }
    }

    private class Harness(policy: RemoteFeedHistoryStrategy?) : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val db: DailySatoriDatabase
        val articles: ArticleRepository
        private val syncRepo: RemoteArticleSyncRepository
        val historyRepo: RemoteFeedHistoryRepository
        private val http: HttpClient
        private val service: RemoteFeedSourceSyncService
        private val tasks: AsyncTaskRepository
        private val sourceRepo: RemoteNewsSourceRepository
        val sourceId: Long
        val requested = mutableListOf<String>()
        private val pages = mutableMapOf<String, PageResponse>()
        private var now = 1_000L

        init {
            DailySatoriDatabase.Schema.create(driver)
            db = DailySatoriDatabase(driver)
            sourceRepo = RemoteNewsSourceRepository(db)
            sourceRepo.save(
                null, "示例", urlOfPath(ENTRY), "", enabled = true,
                historyPolicy = policy?.dbValue ?: RemoteFeedHistoryPolicy.NONE,
            )
            sourceId = sourceRepo.getAll().single().id
            syncRepo = RemoteArticleSyncRepository(db)
            historyRepo = RemoteFeedHistoryRepository(db)
            articles = ArticleRepository(db)
            tasks = AsyncTaskRepository(db)
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
            val news = RemoteNewsService(http)
            val articleSync = RemoteArticleSyncService(articles, syncRepo)
            service = RemoteFeedSourceSyncService(
                RemoteFeedSyncStateRepository(db),
                articleSync,
                news,
                RemoteFeedHistoryImportService(historyRepo, syncRepo, articleSync, news),
            )
        }

        fun urlOf(id: Int): String = "https://feed.test/a/" + id

        fun urlOfPath(path: String): String = "https://feed.test" + path

        fun renameSource(path: String) {
            sourceRepo.save(sourceId, "示例", urlOfPath(path), "", enabled = true)
        }

        fun page(path: String, body: String) {
            pages[path] = PageResponse(200, body)
        }

        /** Descending entries split over [totalPages] documents, each linked to the next one. */
        fun chain(newest: Int, totalPages: Int, pageSize: Int = 50) {
            var next = newest
            for (index in 1..totalPages) {
                val block = (next downTo next - pageSize + 1).toList()
                page(pathOfPage(index), paged(block, next = pathOfPage(index + 1).takeIf { index < totalPages }))
                next -= pageSize
            }
        }

        fun nowForStart(): Long {
            now += 1_000L
            return now
        }

        suspend fun round(
            budget: RemoteFeedHistoryBudget = RemoteFeedHistoryBudget(),
            entryPath: String = ENTRY,
        ): RemoteFeedSyncOutcome {
            nowForStart()
            val address = urlOfPath(entryPath)
            val policy = RemoteFeedHistoryPolicy.strategyOf(sourcePolicyValue())
            return service.sync(
                remoteSourceId = sourceId,
                entryUrl = address,
                config = RemoteNewsConfigValues(address, ""),
                sourceDate = TODAY,
                now = now,
                historyPolicy = policy,
                historyBudget = budget,
            )
        }

        suspend fun drain(budget: RemoteFeedHistoryBudget = RemoteFeedHistoryBudget()): List<RemoteFeedSyncOutcome> {
            val results = mutableListOf<RemoteFeedSyncOutcome>()
            repeat(30) {
                val outcome = round(budget)
                results += outcome
                if (!outcome.historyPending) return results
            }
            return results
        }

        private fun sourcePolicyValue(): String? =
            db.dailySatoriQueries.selectRemoteNewsSourceById(sourceId).executeAsOneOrNull()?.history_policy

        fun history(): RemoteFeedHistoryState? = historyRepo.find(sourceId)

        fun count(): Long = syncRepo.countBySource(sourceId)

        fun stored(id: Int): Boolean = articles.getByUrl(urlOf(id)) != null

        fun mappingOf(id: Int) = syncRepo.findByRemoteIdentity(sourceId, idOf(id))

        fun articlesOfToday() = syncRepo.getArticlesBySourceDate(sourceId, TODAY)

        fun aiTasks() = tasks.runnableTasksByType("remote_article_reprocess", Long.MAX_VALUE)

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
        const val ARCHIVE1 = "/archive/2026-10"
        const val PAGE1 = "/page1"
        const val PAGE2 = "/page2"
        const val PAGE3 = "/page3"
        const val PAGE9 = "/page9"
        const val PAGE4 = "/page4"
        const val PAGE10 = "/page10"
        const val PAGE12 = "/page12"
        const val TODAY = "2026-11-16"

        fun pathOfPage(index: Int): String = "/page" + index

        fun idOf(id: Int): Long = remoteFeedArticleId("id-" + id, "https://feed.test/a/" + id, null)
    }
}

