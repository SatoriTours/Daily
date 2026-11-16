package com.dailysatori.service.remotenews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One owner at a time: the user owns the control state (pause / resume / restart) and a running walk
 * only advances while it still holds the same control epoch. A pause during an in-flight page must stop
 * the walk before the next request, and a stale snapshot must never revive a paused, restarted or
 * deleted walk.
 */
class RemoteFeedHistoryControlTest {
    @Test
    fun pauseDuringAPageStopsBeforeTheNextRequest() = runBlocking {
        Harness().use { h ->
            h.feed(pages = 4)
            val inFlight = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            h.gate = { path -> if (path == PAGE2) { inFlight.complete(Unit); gate.await() } }

            val round = async { h.round(RemoteFeedHistoryBudget(maxPages = 8)) }
            withTimeout(10_000) { inFlight.await() }
            assertTrue(h.historyRepo.pause(h.sourceId, h.nowForStart()))
            gate.complete(Unit)
            val outcome = withTimeout(10_000) { round.await() }

            assertEquals(RemoteFeedHistoryStatus.PAUSED, assertNotNull(h.history()).status)
            assertEquals(listOf(ENTRY, PAGE1, PAGE2), h.requested, "no page is requested after the pause")
            assertFalse(outcome.historyPending, "a paused walk never asks for a continuation")
            // The rejected progress write keeps the cursor on the in-flight page: a resume re-reads it and
            // de-duplicates, so only the fully written pages are counted.
            assertEquals(1, assertNotNull(h.history()).pagesRead)
        }
    }

    @Test
    fun pauseOnTheLastPageIsNotOverwrittenByCompletion() = runBlocking {
        Harness().use { h ->
            h.feed(pages = 2)
            val inFlight = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            h.gate = { path -> if (path == PAGE2) { inFlight.complete(Unit); gate.await() } }

            val round = async { h.round() }
            withTimeout(10_000) { inFlight.await() }
            assertTrue(h.historyRepo.pause(h.sourceId, h.nowForStart()))
            gate.complete(Unit)
            val outcome = withTimeout(10_000) { round.await() }

            val history = assertNotNull(h.history())
            assertEquals(RemoteFeedHistoryStatus.PAUSED, history.status, "the last page may not complete a paused walk")
            assertEquals(PAGE2, h.pathOf(history.cursorUrl), "the rejected page keeps the stored cursor")
            assertEquals(1, history.pagesRead, "the rejected last page is not counted as completed")
            assertFalse(outcome.historyPending)
        }
    }

    @Test
    fun restartDuringAPageKeepsTheNewIntentAndDropsTheOldSnapshot() = runBlocking {
        Harness().use { h ->
            h.feed(pages = 4)
            val inFlight = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            h.gate = { path -> if (path == PAGE2) { inFlight.complete(Unit); gate.await() } }

            val round = async { h.round() }
            withTimeout(10_000) { inFlight.await() }
            h.historyRepo.start(h.sourceId, RemoteFeedHistoryStrategy.ALL_HISTORY, h.nowForStart())
            gate.complete(Unit)
            val outcome = withTimeout(10_000) { round.await() }

            val history = assertNotNull(h.history())
            assertEquals(RemoteFeedHistoryStatus.PENDING, history.status, "the restart intent is preserved")
            assertEquals(0, history.importedCount)
            assertEquals(0, history.pagesRead)
            assertEquals(listOf(ENTRY, PAGE1, PAGE2), h.requested, "the restarted walk is not continued by the old round")
            assertFalse(outcome.historyPending)
        }
    }

    @Test
    fun aDeletedSourceIsNeverResurrectedByTheRunningWalk() = runBlocking {
        Harness().use { h ->
            h.feed(pages = 4)
            val inFlight = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            h.gate = { path -> if (path == PAGE2) { inFlight.complete(Unit); gate.await() } }

            val round = async { h.round() }
            withTimeout(10_000) { inFlight.await() }
            h.sourceRepo.delete(h.sourceId)
            gate.complete(Unit)
            val outcome = withTimeout(10_000) { round.await() }

            assertNull(h.history(), "a removed subscription keeps no walk state")
            assertFalse(outcome.historyPending)
        }
    }

    @Test
    fun aStaleEpochCannotWriteOverANewerControlIntent() = runBlocking {
        Harness().use { h ->
            h.feed(pages = 2)
            h.page(ENTRY, paged((100 downTo 81).toList(), next = PAGE1))
            h.page(PAGE1, paged((80 downTo 61).toList(), next = PAGE2))
            h.page(PAGE2, paged((60 downTo 41).toList()))
            h.round(RemoteFeedHistoryBudget(maxPages = 1))
            val running = assertNotNull(h.history())
            assertTrue(h.historyRepo.pause(h.sourceId, h.nowForStart()))
            val paused = assertNotNull(h.history())
            assertTrue(paused.controlEpoch > running.controlEpoch, "a control action changes the ownership epoch")

            val revived = h.historyRepo.compareAndSetProgress(
                sourceId = h.sourceId,
                expectedEpoch = running.controlEpoch,
                state = running.copy(status = RemoteFeedHistoryStatus.COMPLETE, completedAt = h.nowForStart()),
                now = h.nowForStart(),
            )

            assertFalse(revived, "the old snapshot is rejected")
            val after = assertNotNull(h.history())
            assertEquals(RemoteFeedHistoryStatus.PAUSED, after.status)
            assertEquals(paused.controlEpoch, after.controlEpoch)
        }
    }

    @Test
    fun resumeInsideTheSameSecondStillOwnsTheNextRound() = runBlocking {
        Harness().use { h ->
            h.feed(pages = 3)
            h.round(RemoteFeedHistoryBudget(maxPages = 1))
            assertTrue(h.historyRepo.pause(h.sourceId, h.nowForStart()))
            val resumedAt = h.nowForStart()
            assertTrue(h.historyRepo.resume(h.sourceId, resumedAt))
            assertEquals(RemoteFeedHistoryStatus.PENDING, assertNotNull(h.history()).status)

            h.round()

            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(h.history()).status)
            assertEquals(1, h.requested.count { it == PAGE3 }, "every remaining page is read exactly once")
        }
    }

    private class Harness : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val db: DailySatoriDatabase
        private val http: HttpClient
        private val service: RemoteFeedSourceSyncService
        val sourceRepo: RemoteNewsSourceRepository
        val historyRepo: RemoteFeedHistoryRepository
        val sourceId: Long
        val requested = mutableListOf<String>()
        var gate: suspend (String) -> Unit = {}
        private val pages = mutableMapOf<String, String>()
        private var now = 1_000L

        init {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            db = DailySatoriDatabase(driver)
            sourceRepo = RemoteNewsSourceRepository(db)
            sourceRepo.save(
                null, "示例", urlOfPath(ENTRY), "", enabled = true,
                historyPolicy = RemoteFeedHistoryStrategy.ALL_HISTORY.dbValue,
            )
            sourceId = sourceRepo.getAll().single().id
            historyRepo = RemoteFeedHistoryRepository(db)
            val syncRepo = RemoteArticleSyncRepository(db)
            http = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                requested += path
                val body = pages[path] ?: return@MockEngine respond("", HttpStatusCode.NotFound)
                gate(path)
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml"))
            })
            val news = RemoteNewsService(http)
            val articleSync = RemoteArticleSyncService(ArticleRepository(db), syncRepo)
            service = RemoteFeedSourceSyncService(
                RemoteFeedSyncStateRepository(db),
                articleSync,
                news,
                RemoteFeedHistoryImportService(historyRepo, syncRepo, articleSync, news),
            )
        }

        /** Entry document plus [pages] older documents of two entries each, newest first. */
        fun feed(pages: Int) {
            var id = 100
            this.page(ENTRY, paged(listOf(id - 1, id - 2), next = PAGE1))
            id -= 2
            for (index in 1..pages) {
                this.page(
                    pathOfPage(index),
                    paged(listOf(id - 1, id - 2), next = pathOfPage(index + 1).takeIf { index < pages }),
                )
                id -= 2
            }
        }

        fun page(path: String, body: String) {
            pages[path] = body
        }

        fun pathOf(url: String?): String? = url?.substringAfter("https://feed.test")

        fun nowForStart(): Long {
            now += 1_000L
            return now
        }

        suspend fun round(budget: RemoteFeedHistoryBudget = RemoteFeedHistoryBudget()): RemoteFeedSyncOutcome {
            nowForStart()
            val address = urlOfPath(ENTRY)
            return service.sync(
                remoteSourceId = sourceId,
                entryUrl = address,
                config = RemoteNewsConfigValues(address, ""),
                sourceDate = "2026-11-16",
                now = now,
                historyPolicy = RemoteFeedHistoryStrategy.ALL_HISTORY,
                historyBudget = budget,
            )
        }

        fun history(): RemoteFeedHistoryState? = historyRepo.find(sourceId)

        override fun close() {
            http.close()
            driver.close()
        }
    }

    private companion object {
        const val ENTRY = "/feed"
        const val PAGE1 = "/page1"
        const val PAGE2 = "/page2"
        const val PAGE3 = "/page3"

        fun pathOfPage(index: Int): String = "/page" + index

        fun urlOfPath(path: String): String = "https://feed.test" + path
    }
}

