package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.remotenews.RemoteArticleSyncService
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Value-level failures of the real sync chain (RemoteNewsResult.Failure, SourceFetch.failures) must not
 * be treated as a shared success: no reuse window, no false success count, and the next attempt fetches
 * again.
 */
class RemoteArticleSyncTaskHandlerValueFailureTest {
    private val reporter = object : AsyncTaskProgressReporter {
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = Unit
    }

    @Test
    fun aRemoteFailureNeverOpensTheReuseWindowOrCountsAsSuccess() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "来源一", "https://news.example/v1", "token", enabled = true)
            var requests = 0
            HttpClient(MockEngine {
                requests++
                respond("", HttpStatusCode.InternalServerError)
            }).use { http ->
                val handler = RemoteArticleSyncTaskHandler(
                    sourceRepo = sources,
                    remoteNewsService = RemoteNewsService(http),
                    remoteArticleSyncService = RemoteArticleSyncService(ArticleRepository(db), RemoteArticleSyncRepository(db)),
                    coordinator = RemoteArticleSyncCoordinator(),
                )
                val tasks = AsyncTaskRepository(db)
                val payload = """{"mode":"manual_source","sourceId":1}"""

                repeat(2) { attempt ->
                    val taskId = tasks.enqueue("remote_article_sync", payload, uniqueKey = "ln:source:1:" + attempt)
                    tasks.claimForRun(taskId, "owner-" + attempt, Long.MAX_VALUE)
                    val result = handler.execute(taskId, payload, "", reporter)
                    assertIs<AsyncTaskExecutionResult.RetryableFailure>(result,
                        "a value-level failure must stay a failure, never a success count")
                }
                assertEquals(2, requests, "a failed source may not be reused from a false success")
            }
        }
    }

    @Test
    fun aSuccessfulSyncIsReusedAndCountedOnlyOnce() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "来源一", "https://news.example/v1", "token", enabled = true)
            var requests = 0
            HttpClient(MockEngine {
                requests++
                respond("""{"articles":[]}""", HttpStatusCode.OK)
            }).use { http ->
                val handler = RemoteArticleSyncTaskHandler(
                    sourceRepo = sources,
                    remoteNewsService = RemoteNewsService(http),
                    remoteArticleSyncService = RemoteArticleSyncService(ArticleRepository(db), RemoteArticleSyncRepository(db)),
                    coordinator = RemoteArticleSyncCoordinator(),
                )
                val tasks = AsyncTaskRepository(db)
                val payload = """{"mode":"manual_source","sourceId":1}"""
                val first = tasks.enqueue("remote_article_sync", payload, uniqueKey = "ln:source:1:a")
                tasks.claimForRun(first, "owner-a", Long.MAX_VALUE)
                assertEquals(AsyncTaskExecutionResult.Success("""{"inserted":0,"updated":0,"skipped":0,"failureCount":0}"""),
                    handler.execute(first, payload, "", reporter))

                val second = tasks.enqueue("remote_article_sync", payload, uniqueKey = "ln:source:1:b")
                tasks.claimForRun(second, "owner-b", Long.MAX_VALUE)
                assertIs<AsyncTaskExecutionResult.Success>(handler.execute(second, payload, "", reporter))
                assertEquals(1, requests, "a real success is merged inside the reuse window")
            }
        }
    }
}
