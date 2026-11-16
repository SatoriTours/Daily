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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A bounded history round must not block the daily increment and must not lose its own continuation:
 * one source-scoped task chain carries the walk, a repeated daily trigger reuses it, and the same task
 * yields until the chain really ends.
 */
class RemoteArticleSyncHistoryContinuationTest {
    private val reporter = object : AsyncTaskProgressReporter {
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = Unit
    }

    @Test
    fun aBoundedHistoryRoundContinuesInItsOwnSourceScopedTask() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(
                null, "来源一", URL, "token", enabled = true,
                historyPolicy = RemoteFeedHistoryStrategy.ALL_HISTORY.dbValue,
            )
            val sourceId = sources.getAll().single().id
            val pages = LinkedHashMap<String, String>()
            for (index in 1..20) {
                pages["/page$index"] = pagedFeed(
                    (21 - index) * 20 downTo (20 - index) * 20 + 1,
                    "/page" + (index + 1),
                ).let { body -> if (index == 20) body.replace("<atom:link rel=\"next\" href=\"/page21\"/>", "") else body }
            }
            pages[URL_PATH] = pagedFeed(420 downTo 401, "/page1")
            val requested = mutableListOf<String>()
            val http = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                requested += path
                pages[path]?.let { body ->
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/rss+xml"))
                } ?: respond("", HttpStatusCode.NotFound)
            })
            http.use {
                val syncRepo = RemoteArticleSyncRepository(db)
                val historyRepo = RemoteFeedHistoryRepository(db)
                val articleSync = RemoteArticleSyncService(ArticleRepository(db), syncRepo)
                val news = RemoteNewsService(http)
                val tasks = AsyncTaskRepository(db)
                val scheduled = mutableListOf<Long>()
                val handler = RemoteArticleSyncTaskHandler(
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
                    historyRepo = historyRepo,
                )
                val dailyPayload = remoteArticleSyncTaskPayloadJson(mode = "manual")
                val dailyTask = tasks.enqueue(RemoteArticleSyncTaskHandler.TYPE, dailyPayload, uniqueKey = "daily:1")
                assertTrue(tasks.claimForRun(dailyTask, "owner-daily", Long.MAX_VALUE))
                assertIs<AsyncTaskExecutionResult.Success>(handler.execute(dailyTask, dailyPayload, "", reporter))

                val continuation = scheduled.singleOrNull()
                val historyTask = assertNotNull(continuation, "an unfinished history walk asks for its next round")
                val historyRow = assertNotNull(tasks.getById(historyTask))
                assertEquals(
                    remoteArticleSyncHistoryTaskKey(sourceId, assertNotNull(historyRepo.find(sourceId)).controlEpoch),
                    historyRow.unique_key,
                    "the continuation is scoped to one source and its ownership epoch",
                )
                assertEquals(
                    RemoteArticleSyncTaskHandler.MODE_HISTORY,
                    Json.parseToJsonElement(historyRow.payload_json).jsonObject["mode"]?.jsonPrimitive?.content,
                )
                assertEquals(RemoteFeedHistoryStatus.RUNNING, assertNotNull(historyRepo.find(sourceId)).status)
                assertEquals(8 * 20 + 20, syncRepo.countBySource(sourceId).toInt())

                val historyPayload = remoteArticleSyncTaskPayloadJson(
                    mode = RemoteArticleSyncTaskHandler.MODE_HISTORY, sourceId = sourceId,
                )
                assertTrue(tasks.claimForRun(historyTask, "owner-history-1", Long.MAX_VALUE))
                val firstRound = handler.execute(historyTask, historyPayload, "", reporter)
                assertIs<AsyncTaskExecutionResult.Yield>(firstRound, "a bounded round continues itself")
                tasks.markRetry(historyTask, "", "", 0L, consumeAttempt = false)

                // A daily trigger while the continuation is still pending reuses that task.
                val secondDaily = tasks.enqueue(RemoteArticleSyncTaskHandler.TYPE, dailyPayload, uniqueKey = "daily:2")
                assertTrue(tasks.claimForRun(secondDaily, "owner-daily-2", Long.MAX_VALUE))
                handler.execute(secondDaily, dailyPayload, "", reporter)
                assertEquals(1, tasks.runnableTasksByType(RemoteArticleSyncTaskHandler.TYPE, Long.MAX_VALUE)
                    .count { it.unique_key == historyRow.unique_key },
                    "a repeated trigger never duplicates the history chain",
                )
                assertEquals(historyTask, scheduled.last())

                assertTrue(tasks.claimForRun(historyTask, "owner-history-2", Long.MAX_VALUE))
                val lastRound = handler.execute(historyTask, historyPayload, "", reporter)
                assertIs<AsyncTaskExecutionResult.Success>(lastRound, "the finished walk stops the chain")
                assertEquals(RemoteFeedHistoryStatus.COMPLETE, assertNotNull(historyRepo.find(sourceId)).status)
                assertEquals(420, syncRepo.countBySource(sourceId).toInt(), "twenty pages plus the entry document")
                assertTrue(requested.count { it == "/page1" } == 1)
            }
        }
    }

    @Test
    fun historyTaskKeysNeverCollideBetweenSourcesOrIntents() {
        assertNotEquals(
            remoteArticleSyncHistoryTaskKey(5, 1), remoteArticleSyncHistoryTaskKey(50, 1),
            "source 5 must not absorb the task of source 50",
        )
        assertTrue(remoteArticleSyncHistoryTaskKey(5, 1).startsWith(remoteArticleSyncHistoryTaskPrefix(5, 1)))
        assertTrue(!remoteArticleSyncHistoryTaskKey(50, 1).startsWith(remoteArticleSyncHistoryTaskPrefix(5, 1)))

        assertNotEquals(
            remoteArticleSyncHistoryTaskKey(5, 1), remoteArticleSyncHistoryTaskKey(5, 2),
            "a newer user control action must never reuse the task of the previous intent",
        )
        assertTrue(remoteArticleSyncHistoryTaskKey(5, 2).startsWith(remoteArticleSyncHistoryTaskPrefix(5, 2)))
        assertTrue(!remoteArticleSyncHistoryTaskKey(5, 1).startsWith(remoteArticleSyncHistoryTaskPrefix(5, 2)))
    }

    private fun pagedFeed(items: IntProgression, next: String?): String {
        val head = next?.let { "<atom:link rel=\"next\" href=\"" + it + "\"/>" }.orEmpty()
        val body = items.joinToString("") { id ->
            "<item><title>文章" + id + "</title><link>" + URL + "/a/" + id + "</link>" +
                "<guid isPermaLink=\"false\">id-" + id + "</guid><description>正文" + id + "</description></item>"
        }
        return "<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\"><channel><title>示例</title>" +
            head + body + "</channel></rss>"
    }

    private companion object {
        const val URL = "https://feed.test/feed"
        const val URL_PATH = "/feed"
    }
}

