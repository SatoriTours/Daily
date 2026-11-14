package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteArticleSyncService
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 1482 (remote_article_sync 0/1) walked through the real chain: real handler, real repositories, real
 * RemoteArticleSyncService, real feed parsing; only HTTP is a controllable response. "0/1" is one source:
 * the handler reports progress only after a whole source finishes.
 */
class RemoteArticleSyncChainTest {
    private fun feed(count: Int = 2): String = buildString {
        append("<rss version=\"2.0\"><channel><title>远程新闻</title>")
        repeat(count) { index ->
            val n = (index + 1).toString()
            append("<item><title>第" + n + "篇</title><link>https://news.example.com/" + n + "</link>")
            append("<description>摘要" + n + "</description>")
            append("<pubDate>Tue, 12 Nov 2026 08:00:00 GMT</pubDate></item>")
        }
        append("</channel></rss>")
    }

    /** Real failure injection without touching production schema: abort only the second article's mapping write. */
    private fun failSecondMapping(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """CREATE TRIGGER fail_second_mapping BEFORE INSERT ON remote_article_sync_item
               WHEN NEW.url LIKE '%/2' BEGIN SELECT RAISE(ABORT, 'test failure'); END;""",
            0,
        )
    }

    private fun mappingCount(driver: JdbcSqliteDriver): Long =
        driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM remote_article_sync_item",
            { cursor -> app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
            0,
        ).value

    private class ProgressLog : AsyncTaskProgressReporter {
        val reports = mutableListOf<String>()
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
            reports += current.toString() + "/" + total
        }
    }

    private fun withChain(
        respondWith: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        block: suspend kotlinx.coroutines.CoroutineScope.(RemoteArticleSyncTaskHandler, ArticleRepository, ProgressLog, JdbcSqliteDriver) -> Unit,
    ) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "远程源", "https://feed.test/rss", "", true)
            val articles = ArticleRepository(db)
            HttpClient(MockEngine { request -> respondWith(request) }).use { client ->
                val handler = RemoteArticleSyncTaskHandler(
                    sourceRepo = sources,
                    remoteNewsService = RemoteNewsService(client),
                    remoteArticleSyncService = RemoteArticleSyncService(articles, RemoteArticleSyncRepository(db)),
                )
                this.block(handler, articles, ProgressLog(), driver)
            }
        }
    }

    @Test
    fun aHealthyFeedFillsProgressAndSavesEveryArticle() {
        withChain({ respond(feed(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml")) }) { handler, articles, log, _ ->
            val result = handler.execute(1L, remoteArticleSyncTaskPayloadJson("manual"), "", log)

            assertIs<AsyncTaskExecutionResult.Success>(result, "a healthy feed completes the source: " + result)
            assertEquals("0/1", log.reports.first(), "the source is not complete while articles are processed")
            assertEquals("1/1", log.reports.last(), "the source completes only at the end")
            assertTrue(log.reports.size >= 4, "each article reports its own stage: " + log.reports)
            assertNotNull(articles.getByUrl("https://news.example.com/1"), "first article saved")
            assertNotNull(articles.getByUrl("https://news.example.com/2"), "second article saved")
        }
    }

    @Test
    fun aPermanentFeedFailureBecomesABoundedRetryableFailure() {
        withChain({ respond("not found", HttpStatusCode.NotFound) }) { handler, _, log, _ ->
            val result = handler.execute(1L, remoteArticleSyncTaskPayloadJson("manual"), "", log)

            val failure = assertIs<AsyncTaskExecutionResult.RetryableFailure>(result, "404 must not hang: " + result)
            assertEquals("remote_article_sync_failed", failure.code)
            assertTrue(failure.message.isNotBlank(), "the user gets a reason")
            assertEquals(listOf("0/1", "1/1"), log.reports)
        }
    }

    @Test
    fun aFailedArticleCommitLeavesNoHalfWrittenStateAndLaterArticlesStillSucceed() {
        withChain({ respond(feed(3), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml")) }) { handler, articles, log, driver ->
            failSecondMapping(driver)

            val result = handler.execute(1L, remoteArticleSyncTaskPayloadJson("manual"), "", log)

            assertIs<AsyncTaskExecutionResult.Success>(result, "one bad article must not fail the whole source: " + result)
            assertNotNull(articles.getByUrl("https://news.example.com/1"), "the committed article stays")
            assertNull(articles.getByUrl("https://news.example.com/2"), "no half-written article for the aborted transaction")
            assertNotNull(articles.getByUrl("https://news.example.com/3"), "the next article still commits")
            assertEquals(2L, mappingCount(driver), "only committed articles own a mapping")
            assertEquals("1/1", log.reports.last(), "the source still completes with the good articles")
        }
    }

    @Test
    fun aRerunAfterAFailureCompletesWithoutDuplicatingMappings() {
        withChain({ respond(feed(3), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml")) }) { handler, articles, log, driver ->
            failSecondMapping(driver)
            handler.execute(1L, remoteArticleSyncTaskPayloadJson("manual"), "", log)
            driver.execute(null, "DROP TRIGGER fail_second_mapping", 0)

            val rerun = handler.execute(2L, remoteArticleSyncTaskPayloadJson("manual"), "", log)

            assertIs<AsyncTaskExecutionResult.Success>(rerun, "the rerun completes: " + rerun)
            assertNotNull(articles.getByUrl("https://news.example.com/2"))
            assertEquals(3L, mappingCount(driver), "a rerun never duplicates mappings")
        }
    }

    @Test
    fun aWaitingFetchHoldsNoDatabaseLock() {
        withChain({ awaitCancellation() }) { handler, _, log, driver ->
            val child = launch { handler.execute(1L, remoteArticleSyncTaskPayloadJson("manual"), "", log) }
            delay(200)
            try {
                // The fetch is still waiting; another writer must not be blocked by any open transaction here.
                withTimeout(1_000) {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.dailysatori.data.repository.SettingRepository(DailySatoriDatabase(driver)).upsert("probe.key", "ok")
                    }
                }
                val stored = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.dailysatori.data.repository.SettingRepository(DailySatoriDatabase(driver)).get("probe.key")
                }
                assertEquals("ok", stored)
            } finally {
                child.cancelAndJoin()
            }
        }
    }

    @Test
    fun theExportRedactorKeepsTheSyncStageField() {
        val kept = com.dailysatori.service.diagnostics.DiagnosticRedactor.fields(mapOf("syncStage" to "sync_start"))
        assertEquals("sync_start", kept["syncStage"], "a stage the export drops is no evidence at all")
        assertTrue(com.dailysatori.service.diagnostics.DiagnosticRedactor.fields(mapOf("syncStage" to "https://secret")).isEmpty())
    }

    @Test
    fun cancellingAtAnArticleBoundaryKeepsCommittedArticlesAndTheNextRunCompletes() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "远程源", "https://feed.test/rss", "", true)
            val sourceId = sources.getAll().first().id
            val articles = ArticleRepository(db)
            val service = RemoteArticleSyncService(articles, RemoteArticleSyncRepository(db))
            val feedArticles = (1..3).map { n ->
                RemoteArticle(id = n.toLong(), title = "第" + n + "篇", url = "https://news.example.com/" + n, summary = "摘要" + n)
            }

            // Cancellation arrives at the article boundary from inside the progress callback (after article 1)
            // and only cancels this syncing coroutine, never the test scope.
            var syncJob: kotlinx.coroutines.Job? = null
            val child = launch {
                syncJob = currentCoroutineContext()[kotlinx.coroutines.Job]
                service.syncSourceArticles(sourceId, "2026-11-14", feedArticles, 1L) { progress ->
                    if (progress.processed == 1) syncJob?.cancel()
                }
            }
            child.join()

            assertNotNull(articles.getByUrl("https://news.example.com/1"), "the committed article survives cancellation")
            assertNull(articles.getByUrl("https://news.example.com/2"), "cancellation starts no new transaction")
            assertEquals(1L, mappingCount(driver))

            val rerun = service.syncSourceArticles(sourceId, "2026-11-14", feedArticles, 1L)
            assertEquals(0, rerun.failures.size)
            assertEquals(3L, mappingCount(driver), "the rerun completes without duplicating mappings")
        }
    }

    @Test
    fun cancellingAStalledFetchPropagatesInsteadOfHangingOrReportingSuccess() {
        withChain({ awaitCancellation() }) { handler, _, log, _ ->
            val started = CompletableDeferred<Unit>()
            val job = launch {
                started.complete(Unit)
                handler.execute(1L, remoteArticleSyncTaskPayloadJson("manual"), "", log)
            }
            started.await()
            delay(200)
            job.cancelAndJoin()

            assertEquals(listOf("0/1"), log.reports, "a cancelled source never reports completion")
        }
    }
}
