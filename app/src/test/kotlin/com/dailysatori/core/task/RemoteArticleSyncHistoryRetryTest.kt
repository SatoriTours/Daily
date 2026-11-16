package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteFeedHistoryRepository
import com.dailysatori.data.repository.RemoteFeedSyncStateRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskRunOutcome
import com.dailysatori.service.asynctask.AsyncTaskRunner
import com.dailysatori.service.asynctask.NoopAsyncTaskLogger
import com.dailysatori.service.remotenews.RemoteArticleSyncService
import com.dailysatori.service.remotenews.RemoteFeedHistoryImportService
import com.dailysatori.service.remotenews.RemoteFeedHistoryStatus
import com.dailysatori.service.remotenews.RemoteFeedHistoryStrategy
import com.dailysatori.service.remotenews.RemoteFeedSourceSyncService
import com.dailysatori.service.remotenews.RemoteNewsService
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A readable entry document with a failing older page must keep the walk recoverable: a transient
 * page failure drives the ordinary bounded task retry, a permanent one blocks without retrying, and a
 * paused walk is never advanced by a retry.
 */
class RemoteArticleSyncHistoryRetryTest {
    @Test
    fun aLaterPageTemporaryFailureRetriesTheTaskWithoutUserSync() = runBlocking {
        Fixture(initialPageStatus = { if (it == 2) 503 else 200 }).use { f ->
            f.executeManual().also { result ->
                assertIs<AsyncTaskExecutionResult.RetryableFailure>(
                    result,
                    "a transient history page failure must retry instead of ending as success",
                )
            }
            val failed = assertNotNull(f.historyRepo.find(f.sourceId))
            assertEquals(RemoteFeedHistoryStatus.RUNNING, failed.status)
            assertTrue(failed.lastError.isNotBlank(), "the real reason is persisted")
            assertTrue(f.storedPages() >= 1, "pages before the failure are stored")
            assertTrue(f.requested.none { it == "/page3" }, "the walk stops at the failed page")

            f.pageStatus = { 200 }
            val retried = f.executeManual()
            assertIs<AsyncTaskExecutionResult.Success>(retried)
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(f.historyRepo.find(f.sourceId)).status)
            assertEquals(2, f.requested.count { it == "/page2" }, "one failed attempt plus the successful retry")
            // The stored continuation is re-verified once before trusting it; no page restarts the walk.
            assertEquals(2, f.requested.count { it == "/page1" }, "the last stored page is verified, not re-imported")
        }
    }

    @Test
    fun aPersistentTemporaryFailureStopsAtTheTaskAttemptBudget() = runBlocking {
        Fixture(initialPageStatus = { if (it == 2) 503 else 200 }).use { f ->
            val payload = remoteArticleSyncTaskPayloadJson(
                mode = RemoteArticleSyncTaskHandler.MODE_MANUAL_SOURCE, sourceId = f.sourceId,
            )
            val taskId = f.tasks.enqueue(RemoteArticleSyncTaskHandler.TYPE, payload, maxAttempts = 3)
            var runs = 0
            var outcome: AsyncTaskRunOutcome = AsyncTaskRunOutcome.Skipped
            while (runs < 6) {
                outcome = f.runner.run(taskId)
                runs += 1
                if (outcome == AsyncTaskRunOutcome.Failed) break
                f.advance(20 * 60_000L)
            }
            assertEquals(AsyncTaskRunOutcome.Failed, outcome, "the retry budget is finite, not an infinite chain")
            assertEquals("failed", f.tasks.getById(taskId)?.status)
            assertTrue(
                (f.tasks.getById(taskId)?.attempt_count ?: 0) <= 2,
                "each retry consumes the existing attempt budget",
            )
            assertEquals(RemoteFeedHistoryStatus.RUNNING, assertNotNull(f.historyRepo.find(f.sourceId)).status)
            assertTrue(
                f.tasks.runnableTasksByType(RemoteArticleSyncTaskHandler.TYPE, Long.MAX_VALUE).isEmpty(),
                "no extra task is created that would reset the attempt budget",
            )
        }
    }

    @Test
    fun aPermanentPageFailureBlocksWithoutRetrying() = runBlocking {
        Fixture(initialPageStatus = { if (it == 2) 401 else 200 }).use { f ->
            val result = f.executeManual()

            assertIs<AsyncTaskExecutionResult.Success>(result, "a permission failure is not retried in the background")
            val blocked = assertNotNull(f.historyRepo.find(f.sourceId))
            assertEquals(RemoteFeedHistoryStatus.BLOCKED, blocked.status)
            assertTrue(blocked.blockedReason.contains("401"), blocked.blockedReason)

            val before = f.requested.size
            f.executeManual()
            assertEquals(listOf("/feed"), f.requested.drop(before), "a blocked walk is only refreshed at the entry")
        }
    }

    @Test
    fun aPausedWalkIsNotAdvancedByARetry() = runBlocking {
        Fixture(initialPageStatus = { if (it == 2) 503 else 200 }).use { f ->
            assertIs<AsyncTaskExecutionResult.RetryableFailure>(f.executeManual())
            assertTrue(f.historyRepo.pause(f.sourceId, f.now()))

            val before = f.requested.size
            assertIs<AsyncTaskExecutionResult.Success>(f.executeManual(), "a paused walk ends the retry chain")
            assertEquals(listOf("/feed"), f.requested.drop(before), "no history page is fetched while paused")
            assertEquals(RemoteFeedHistoryStatus.PAUSED, assertNotNull(f.historyRepo.find(f.sourceId)).status)
        }
    }

    private class Fixture(initialPageStatus: (Int) -> Int) : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val db: DailySatoriDatabase
        private val http: HttpClient
        private val handler: RemoteArticleSyncTaskHandler
        val tasks: AsyncTaskRepository
        val historyRepo: RemoteFeedHistoryRepository
        val runner: AsyncTaskRunner
        val sourceId: Long
        val requested = mutableListOf<String>()
        var pageStatus: (Int) -> Int = initialPageStatus
        private var now = 1_000_000L

        init {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(
                null, "来源一", URL, "token", enabled = true,
                historyPolicy = RemoteFeedHistoryStrategy.ALL_HISTORY.dbValue,
            )
            sourceId = sources.getAll().single().id
            tasks = AsyncTaskRepository(db)
            historyRepo = RemoteFeedHistoryRepository(db)
            http = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                requested += path
                if (path == "/feed") {
                    return@MockEngine respond(feed(listOf(100, 99), "/page1"), HttpStatusCode.OK, XML)
                }
                val page = path.removePrefix("/page").removeSuffix("").toIntOrNull()
                if (page == null || page > 3) return@MockEngine respond("", HttpStatusCode.NotFound)
                val status = pageStatus(page)
                if (status != 200) return@MockEngine respond("", HttpStatusCode.fromValue(status))
                val older = if (page < 3) "/page" + (page + 1) else null
                respond(feed(listOf(101 - page * 2, 100 - page * 2), older), HttpStatusCode.OK, XML)
            })
            val syncRepo = RemoteArticleSyncRepository(db)
            val articleSync = RemoteArticleSyncService(ArticleRepository(db), syncRepo)
            val news = RemoteNewsService(http)
            handler = RemoteArticleSyncTaskHandler(
                sourceRepo = sources,
                remoteNewsService = news,
                remoteFeedSyncService = RemoteFeedSourceSyncService(
                    RemoteFeedSyncStateRepository(db),
                    articleSync,
                    news,
                    RemoteFeedHistoryImportService(historyRepo, syncRepo, articleSync, news),
                ),
                clock = object : kotlinx.datetime.Clock {
                    override fun now() = kotlinx.datetime.Instant.fromEpochMilliseconds(now)
                },
                taskRepo = tasks,
                historyRepo = historyRepo,
            )
            runner = AsyncTaskRunner(
                repository = tasks,
                registry = AsyncTaskHandlerRegistry(listOf(handler)),
                logger = NoopAsyncTaskLogger,
                leaseOwnerProvider = { "retry-test" },
                nowMs = { now },
                executionTimeoutMs = { 60_000L },
            )
        }

        fun now(): Long = now

        fun advance(millis: Long) {
            now += millis
        }

        fun storedPages(): Int = requested.count { it.startsWith("/page") }

        suspend fun executeManual(): AsyncTaskExecutionResult {
            val payload = remoteArticleSyncTaskPayloadJson(
                mode = RemoteArticleSyncTaskHandler.MODE_MANUAL_SOURCE, sourceId = sourceId,
            )
            val taskId = tasks.enqueue(RemoteArticleSyncTaskHandler.TYPE, payload)
            assertTrue(tasks.claimForRun(taskId, "owner-" + taskId, Long.MAX_VALUE))
            return handler.execute(taskId, payload, "", reporter)
        }

        private fun feed(items: List<Int>, next: String?): String {
            val head = next?.let { "<atom:link rel=\"next\" href=\"" + it + "\"/>" }.orEmpty()
            val body = items.joinToString("") { id ->
                "<item><title>文章" + id + "</title><link>" + URL + "/a/" + id + "</link>" +
                    "<guid isPermaLink=\"false\">id-" + id + "</guid><description>正文" + id + "</description></item>"
            }
            return "<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\"><channel><title>示例</title>" +
                head + body + "</channel></rss>"
        }

        override fun close() {
            http.close()
            driver.close()
        }
    }

    private companion object {
        const val URL = "https://feed.test/feed"
        val XML = headersOf(HttpHeaders.ContentType, "application/rss+xml")
        val reporter = object : AsyncTaskProgressReporter {
            override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = Unit
        }
    }
}

