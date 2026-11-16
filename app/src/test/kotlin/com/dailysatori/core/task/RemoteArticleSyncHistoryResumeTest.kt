package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteFeedHistoryRepository
import com.dailysatori.data.repository.RemoteFeedSyncStateRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
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
 * A user control command must always get a run: pausing and resuming right after a successful sync may
 * not be absorbed by the short "just fetched" reuse window, and a pending walk must get its next bounded
 * round without the user pressing sync again.
 */
class RemoteArticleSyncHistoryResumeTest {

    @Test
    fun resumeRightAfterASuccessfulSyncStillGetsItsOwnRun() = runBlocking {
        Fixture(pages = 1).use { f ->
            f.executeManual()
            assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(f.historyRepo.find(f.sourceId)).status,
                "state=" + f.historyRepo.find(f.sourceId) + " requested=" + f.requested)
            val beforeResume = f.requested.size

            f.historyRepo.start(f.sourceId, RemoteFeedHistoryStrategy.ALL_HISTORY, f.now())
            assertTrue(f.historyRepo.pause(f.sourceId, f.now()))
            assertTrue(f.historyRepo.resume(f.sourceId, f.now()))
            assertEquals(RemoteFeedHistoryStatus.PENDING, assertNotNull(f.historyRepo.find(f.sourceId)).status)

            f.executeManual()

            val after = assertNotNull(f.historyRepo.find(f.sourceId))
            assertTrue(
                after.status in setOf(RemoteFeedHistoryStatus.RUNNING, RemoteFeedHistoryStatus.COMPLETE),
                "the resumed walk really ran, status=" + after.status,
            )
            assertTrue(
                after.pagesRead > 0 || after.status == RemoteFeedHistoryStatus.COMPLETE,
                "the resumed walk stored a page instead of being merged into the previous sync",
            )
            assertTrue(f.requested.drop(beforeResume).contains(f.pagePath(1)), "the resumed run fetched the walk")
        }
    }

    @Test
    fun anOpenWalkAlwaysHasOnePendingContinuationAndNoDuplicatePages() = runBlocking {
        Fixture(pages = 20).use { f ->
            f.executeManual()
            val afterFirst = assertNotNull(f.historyRepo.find(f.sourceId))
            assertEquals(RemoteFeedHistoryStatus.RUNNING, afterFirst.status)

            assertTrue(f.historyRepo.pause(f.sourceId, f.now()))
            assertTrue(f.historyRepo.resume(f.sourceId, f.now()))
            f.executeManual()
            f.executeManual()

            // The last stored page is re-read once to verify the continuation; every other page is
            // fetched exactly once, so a repeated command cannot duplicate the walk.
            assertEquals(1, f.requested.count { it == f.pagePath(1) }, "the resumed walk does not restart")
            assertEquals(1, f.requested.count { it == f.pagePath(9) }, "the walk continues after the stored cursor")
            assertEquals(2, f.requested.count { it == f.pagePath(8) }, "only the stored continuation is re-verified")
            val active = f.tasks.runnableTasksByType(RemoteArticleSyncTaskHandler.TYPE, Long.MAX_VALUE)
                .filter { it.unique_key?.startsWith("remote_article_sync:history:") == true }
            assertTrue(active.size <= 1, "one source keeps at most one pending history round, found " + active.map { it.unique_key })
        }
    }

    private class Fixture(pages: Int) : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val db: DailySatoriDatabase
        private val http: HttpClient
        private val handler: RemoteArticleSyncTaskHandler
        val tasks: AsyncTaskRepository
        val historyRepo: RemoteFeedHistoryRepository
        val sourceId: Long
        val requested = mutableListOf<String>()
        private val bodies = mutableMapOf<String, String>()
        private val scheduled = mutableListOf<Long>()
        private var now = 1_000L

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
            var id = 100
            bodies["/feed"] = pagedFeed(listOf(id - 1, id - 2), "/page1")
            id -= 2
            for (index in 1..pages) {
                bodies["/page$index"] = pagedFeed(
                    listOf(id - 1, id - 2),
                    ("/page" + (index + 1)).takeIf { index < pages },
                )
                id -= 2
            }
            http = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                requested += path
                bodies[path]?.let { body ->
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml"))
                } ?: respond("", HttpStatusCode.NotFound)
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
                taskRepo = tasks,
                scheduleTask = { scheduled += it },
            )
        }

        fun pagePath(index: Int): String = "/page$index"

        fun now(): Long {
            now += 1_000L
            return now
        }

        suspend fun executeManual() {
            val payload = remoteArticleSyncTaskPayloadJson(mode = "manual_source", sourceId = sourceId)
            val taskId = tasks.enqueue(RemoteArticleSyncTaskHandler.TYPE, payload)
            assertTrue(tasks.claimForRun(taskId, "owner-" + taskId, Long.MAX_VALUE))
            val result = handler.execute(taskId, payload, "", reporter)
            assertIs<AsyncTaskExecutionResult.Success>(result)
        }

        private fun pagedFeed(items: List<Int>, next: String?): String {
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

        val reporter = object : AsyncTaskProgressReporter {
            override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = Unit
        }
    }
}

