package com.dailysatori.service.remotenews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.RemoteFeedSyncStateRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real engine, real sockets: download limits, redirect bases and credential scoping are only believable
 * against an actual HTTP stack, not a mock transport.
 */
class RemoteFeedNetworkLimitsTest {
    @Test
    fun anOversizedDocumentStopsDuringTheDownload() = runBlocking {
        val server = TestFeedServer { request ->
            if (request.path == "/feed") TestResponse.Padded(totalBytes = 400_000, body = feed(listOf(1, 2, 3)))
            else TestResponse.NotFound
        }
        server.use {
            withSync(server) { service, sourceId, syncRepo, stateRepo ->
                val started = System.currentTimeMillis()
                val outcome = service.sync(
                    remoteSourceId = sourceId,
                    entryUrl = server.url("/feed"),
                    config = RemoteNewsConfigValues(server.url("/feed"), ""),
                    sourceDate = "2026-11-15",
                    now = 1_000L,
                    budget = RemoteFeedSyncBudget(maxBytes = 20_000),
                )
                val elapsed = System.currentTimeMillis() - started

                assertFalse(outcome.entryRead)
                assertFalse(outcome.transient, "an oversized document does not heal by waiting")
                assertTrue(outcome.message.orEmpty().contains("上限"), outcome.message)
                assertEquals(0L, syncRepo.countBySource(sourceId))
                assertNull(stateRepo.find(sourceId), "the position is never advanced past an unread document")
                assertTrue(elapsed < 5_000, "the read must abort instead of buffering the whole document: " + elapsed + " ms")
            }
        }
    }

    @Test
    fun theByteBudgetCountsDecodedBytesNotCharacters() = runBlocking {
        val server = TestFeedServer { TestResponse.Body(feed(listOf(1, 2, 3), padding = "汉".repeat(1_500))) }
        server.use {
            withSync(server) { service, sourceId, syncRepo, _ ->
                val tooSmall = service.sync(
                    remoteSourceId = sourceId,
                    entryUrl = server.url("/feed"),
                    config = RemoteNewsConfigValues(server.url("/feed"), ""),
                    sourceDate = "2026-11-15",
                    now = 1_000L,
                    budget = RemoteFeedSyncBudget(maxBytes = 3_000),
                )
                assertFalse(tooSmall.entryRead, "3000 bytes cannot hold 1500 three-byte characters")
                assertEquals(0L, syncRepo.countBySource(sourceId))

                val enough = service.sync(
                    remoteSourceId = sourceId,
                    entryUrl = server.url("/feed"),
                    config = RemoteNewsConfigValues(server.url("/feed"), ""),
                    sourceDate = "2026-11-15",
                    now = 2_000L,
                    budget = RemoteFeedSyncBudget(maxBytes = 20_000),
                )
                assertTrue(enough.entryRead)
                assertEquals(3L, syncRepo.countBySource(sourceId))
            }
        }
    }

    @Test
    fun aSlowDocumentConsumesTheRunClockAndKeepsStoredPages() = runBlocking {
        val server = TestFeedServer { request ->
            when (request.path) {
                "/feed" -> TestResponse.Body(
                    if (request.count <= 1) pagedFeed(listOf(3, 2, 1), next = "/page2")
                    else pagedFeed(listOf(5, 4), next = "/page2"),
                )
                "/page2" -> TestResponse.Body(pagedFeed(listOf(0), next = "/page3"), delayMillis = 3_000)
                else -> TestResponse.NotFound
            }
        }
        server.use {
            withSync(server) { service, sourceId, syncRepo, stateRepo ->
                service.sync(
                    remoteSourceId = sourceId,
                    entryUrl = server.url("/feed"),
                    config = RemoteNewsConfigValues(server.url("/feed"), ""),
                    sourceDate = "2026-11-15",
                    now = 500L,
                )
                val started = System.currentTimeMillis()
                val outcome = service.sync(
                    remoteSourceId = sourceId,
                    entryUrl = server.url("/feed"),
                    config = RemoteNewsConfigValues(server.url("/feed"), ""),
                    sourceDate = "2026-11-15",
                    now = 1_000L,
                    budget = RemoteFeedSyncBudget(maxDurationMillis = 1_200),
                )
                val elapsed = System.currentTimeMillis() - started

                assertTrue(outcome.entryRead)
                assertTrue(outcome.transient, "a timed out document is retryable")
                assertTrue(outcome.pending)
                assertEquals(5L, syncRepo.countBySource(sourceId), "the finished pages are not lost")
                val cursor = stateRepo.find(sourceId)?.cursorUrl.orEmpty()
                assertTrue(cursor.endsWith("/page2"), cursor)
                assertTrue(elapsed < 3_500, "one slow document may not outlive the run clock: " + elapsed + " ms")
            }
        }
    }

    @Test
    fun aRedirectedDocumentResolvesRelativeLinksAgainstTheFinalAddress() = runBlocking {
        val server = TestFeedServer { request ->
            when (request.path) {
                "/feed" -> TestResponse.Redirect("/feeds/v1/index.rss")
                "/feeds/v1/index.rss" -> TestResponse.Body(
                    if (request.count <= 1) {
                        archiveFeed(items = listOf(3, 2, 1), prevArchive = "archive/2026-09.rss")
                    } else {
                        archiveFeed(items = listOf(5, 4), prevArchive = "archive/2026-10.rss")
                    },
                )
                "/feeds/v1/archive/2026-10.rss" -> TestResponse.Body(archiveFeed(listOf(1), archive = true))
                else -> TestResponse.NotFound
            }
        }
        server.use {
            withSync(server) { service, sourceId, _, _ ->
                service.sync(
                    remoteSourceId = sourceId,
                    entryUrl = server.url("/feed"),
                    config = RemoteNewsConfigValues(server.url("/feed"), ""),
                    sourceDate = "2026-11-15",
                    now = 1_000L,
                )
                val outcome = service.sync(
                    remoteSourceId = sourceId,
                    entryUrl = server.url("/feed"),
                    config = RemoteNewsConfigValues(server.url("/feed"), ""),
                    sourceDate = "2026-11-15",
                    now = 2_000L,
                )

                assertTrue(outcome.entryRead)
                assertEquals(
                    listOf("/feed", "/feeds/v1/index.rss", "/feed", "/feeds/v1/index.rss", "/feeds/v1/archive/2026-10.rss"),
                    server.requestedPaths(),
                    "the declared archive is resolved against the document that answered, not the configured address",
                )
            }
        }
    }

    @Test
    fun aCrossOriginRedirectNeverCarriesTheToken() = runBlocking {
        val other = TestFeedServer { TestResponse.Body(archiveFeed(listOf(7))) }
        val entry = TestFeedServer { request ->
            when (request.path) {
                "/feed" -> TestResponse.Redirect(other.url("/moved/feed.rss"))
                else -> TestResponse.NotFound
            }
        }
        entry.use {
            other.use {
                withSync(entry) { service, sourceId, syncRepo, _ ->
                    val outcome = service.sync(
                        remoteSourceId = sourceId,
                        entryUrl = entry.url("/feed"),
                        config = RemoteNewsConfigValues(entry.url("/feed"), "secret-token"),
                        sourceDate = "2026-11-15",
                        now = 1_000L,
                    )

                    assertTrue(outcome.entryRead)
                    assertEquals(1L, syncRepo.countBySource(sourceId))
                    assertEquals("Bearer secret-token", entry.authorizations().single())
                    assertNull(other.authorizations().singleOrNull(), "a redirect to another origin never receives the token")
                }
            }
        }
    }

    @Test
    fun aDeclaredCrossOriginArchiveIsReadWithoutTheToken() = runBlocking {
        val other = TestFeedServer { TestResponse.Body(archiveFeed(listOf(1), archive = true)) }
        val entry = TestFeedServer { request ->
            when (request.path) {
                "/feed" -> TestResponse.Body(
                    if (request.count <= 1) {
                        archiveFeed(items = listOf(5), prevArchive = other.url("/archive/2026-09.rss"))
                    } else {
                        archiveFeed(items = listOf(5), prevArchive = other.url("/archive/2026-10.rss"))
                    },
                )
                else -> TestResponse.NotFound
            }
        }
        entry.use {
            other.use {
                withSync(entry) { service, sourceId, syncRepo, _ ->
                    service.sync(
                        remoteSourceId = sourceId,
                        entryUrl = entry.url("/feed"),
                        config = RemoteNewsConfigValues(entry.url("/feed"), "secret-token"),
                        sourceDate = "2026-11-15",
                        now = 1_000L,
                    )
                    val outcome = service.sync(
                        remoteSourceId = sourceId,
                        entryUrl = entry.url("/feed"),
                        config = RemoteNewsConfigValues(entry.url("/feed"), "secret-token"),
                        sourceDate = "2026-11-15",
                        now = 2_000L,
                    )

                    assertTrue(outcome.entryRead)
                    assertTrue(other.requestedPaths().contains("/archive/2026-10.rss"), "a standard cross-site archive is followed")
                    assertNull(other.authorizations().firstOrNull(), "the token stays on the subscription origin")
                    assertEquals("Bearer secret-token", entry.authorizations().first())
                    assertTrue(syncRepo.countBySource(sourceId) >= 1L)
                }
            }
        }
    }

    private suspend fun withSync(
        server: TestFeedServer,
        block: suspend (
            RemoteFeedSourceSyncService,
            Long,
            RemoteArticleSyncRepository,
            RemoteFeedSyncStateRepository,
        ) -> Unit,
    ) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "本地服务", server.url("/feed"), "", enabled = true)
            val sourceId = sources.getAll().single().id
            val syncRepo = RemoteArticleSyncRepository(db)
            HttpClient(OkHttp) { install(HttpTimeout) { connectTimeoutMillis = 5_000 } }.use { client ->
                val service = RemoteFeedSourceSyncService(
                    RemoteFeedSyncStateRepository(db),
                    RemoteArticleSyncService(ArticleRepository(db), syncRepo),
                    RemoteNewsService(client),
                )
                block(service, sourceId, syncRepo, RemoteFeedSyncStateRepository(db))
            }
        }
    }

    private fun pagedFeed(items: List<Int>, next: String?): String =
        archiveFeed(items).replace("<channel>", "<channel>" + (next?.let { "<atom:link rel=\"next\" href=\"" + it + "\"/>" } ?: ""))

    private fun archiveFeed(items: List<Int>, prevArchive: String? = null, archive: Boolean = false): String {
        val head = buildString {
            if (archive) append("<fh:archive/>")
            prevArchive?.let { append("<atom:link rel=\"prev-archive\" href=\"" + it + "\"/>") }
        }
        val body = items.joinToString("") { id ->
            "<item><title>文章" + id + "</title><link>https://feed.test/a/" + id +
                "</link><guid isPermaLink=\"false\">id-" + id + "</guid><description>正文" + id + "</description></item>"
        }
        return "<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\" " +
            "xmlns:fh=\"http://purl.org/syndication/history/1.0\"><channel><title>示例</title>" + head + body + "</channel></rss>"
    }

    private fun feed(items: List<Int>, padding: String = ""): String =
        archiveFeed(items).replace("<channel>", "<channel><description>" + padding + "</description>")

    private class TestRequest(val path: String, val headers: Map<String, String>, val count: Int)

    private sealed interface TestResponse {
        class Body(val body: String, val delayMillis: Long = 0) : TestResponse
        class Redirect(val location: String) : TestResponse
        data object NotFound : TestResponse
        class Padded(val totalBytes: Int, val body: String) : TestResponse
    }

    /** Minimal HTTP/1.1 server: full control over status, streaming, delay and connection shutdown. */
    private class TestFeedServer(
        private val handler: (TestRequest) -> TestResponse,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val requests = java.util.Collections.synchronizedList(mutableListOf<TestRequest>())
        private val hits = java.util.concurrent.ConcurrentHashMap<String, Int>()
        private val worker = thread(isDaemon = true, name = "test-feed-server") {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching { serve(client) }
            }
        }

        val port: Int get() = socket.localPort
        fun url(path: String): String = "http://127.0.0.1:" + port + path
        fun requestedPaths(): List<String> = requests.map { it.path }
        fun authorizations(): List<String?> = requests.map { it.headers["authorization"] }

        private fun serve(client: Socket) {
            client.use { connection ->
                val reader = BufferedReader(InputStreamReader(connection.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val path = requestLine.split(' ').getOrElse(1) { "/" }
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
                }
                val count = hits.merge(path, 1, Int::plus) ?: 1
                val request = TestRequest(path, headers, count)
                requests += request
                val output = connection.getOutputStream()
                when (val response = handler(request)) {
                    is TestResponse.Body -> {
                        if (response.delayMillis > 0) Thread.sleep(response.delayMillis)
                        writeBody(output, response.body)
                    }
                    is TestResponse.Redirect -> {
                        output.write(("HTTP/1.1 302 Found\r\nLocation: " + response.location +
                            "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                        output.flush()
                    }
                    is TestResponse.Padded -> {
                        val payload = response.body.toByteArray()
                        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/rss+xml\r\nContent-Length: " +
                            response.totalBytes + "\r\nConnection: close\r\n\r\n"
                        output.write(header.toByteArray())
                        output.write(payload)
                        val chunk = ByteArray(16 * 1024)
                        var written = payload.size
                        while (written < response.totalBytes) {
                            val size = minOf(chunk.size, response.totalBytes - written)
                            // Writing into a closed connection throws: that is the abort being observed.
                            output.write(chunk, 0, size)
                            written += size
                        }
                        output.flush()
                    }
                    TestResponse.NotFound -> {
                        output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        output.flush()
                    }
                }
            }
        }

        private fun writeBody(output: OutputStream, body: String) {
            val payload = body.toByteArray()
            val header = "HTTP/1.1 200 OK\r\nContent-Type: application/rss+xml\r\nContent-Length: " +
                payload.size + "\r\nConnection: close\r\n\r\n"
            output.write(header.toByteArray())
            output.write(payload)
            output.flush()
        }

        override fun close() {
            socket.close()
            worker.interrupt()
        }
    }
}
