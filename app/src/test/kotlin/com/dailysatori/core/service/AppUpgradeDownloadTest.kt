package com.dailysatori.core.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ClosedWriteChannelException
import io.ktor.utils.io.writeFully
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpgradeDownloadTest {
    private val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
    private val chunks = payload.asList().chunked(payload.size / 4).map { it.toByteArray() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }

    @Test
    fun progressStartsBeforeProducerFinishesAndVerifiedFileIsDelivered() = runBlocking {
        withTempDir { dir ->
            val channel = ByteChannel(autoFlush = true)
            val firstProgress = CompletableDeferred<Unit>()
            var producerSawProgress = false
            var producerFinished = false
            val client = HttpClient(MockEngine {
                respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, payload.size.toString()))
            })
            val progress = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
            var progressAtFirstCallbackBeforeProducerFinished = false
            var verifiedAtBytes = -1L
            val target = File(dir, "DailySatori-9.9.9.apk")
            coroutineScope {
                val producer = launch(Dispatchers.IO) {
                    try {
                        channel.writeFully(chunks.first())
                        producerSawProgress = withTimeoutOrNull(2_000) { firstProgress.await() } != null
                        chunks.drop(1).forEach { chunk ->
                            channel.writeFully(chunk)
                            delay(20)
                        }
                        channel.close()
                        producerFinished = true
                    } catch (_: ClosedWriteChannelException) {
                        // Consumer stopped early; the test's scope owns this producer.
                    }
                }
                try {
                    client.use {
                        downloadApkToFile(it, url(), payload.size.toLong(), sha, target,
                            onProgress = { done, total ->
                                progress += done to total
                                if (done > 0) {
                                    if (!producerFinished) progressAtFirstCallbackBeforeProducerFinished = true
                                    firstProgress.complete(Unit)
                                }
                            },
                            onVerifying = { verifiedAtBytes = progress.last().first })
                    }
                } finally {
                    producer.cancelAndJoin()
                    client.close()
                }
            }
            assertTrue(producerSawProgress, "producer waited for client progress and timed out")
            assertTrue(progressAtFirstCallbackBeforeProducerFinished)
            assertTrue(progress.size >= 2, "expected incremental callbacks, got \${progress.size}")
            assertTrue(progress.all { it.second == payload.size.toLong() }, "totals: \$progress")
            assertTrue(progress.first().first < payload.size, "first callback already complete: \$progress")
            assertEquals(payload.size.toLong(), progress.last().first)
            assertEquals(payload.size.toLong(), verifiedAtBytes)
            assertTrue(target.exists())
            assertTrue(target.readBytes().contentEquals(payload))
            assertEquals(sha, sha256(target))
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") })
        }
    }

    @Test
    fun unknownContentLengthUsesManifestFallbackTotal() = runBlocking {
        withTempDir { dir ->
            val progress = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
            val target = File(dir, "app.apk")
            stream(chunks, contentLength = null) { client, url ->
                downloadApkToFile(client, url, payload.size.toLong(), sha, target,
                    onProgress = { done, total -> progress += done to total })
            }
            assertTrue(progress.isNotEmpty())
            assertTrue(progress.all { it.second == payload.size.toLong() }, "totals: \$progress")
            assertEquals(payload.size.toLong(), progress.last().first)
            assertTrue(target.readBytes().contentEquals(payload))
        }
    }

    @Test
    fun unknownContentLengthWithoutFallbackKeepsUnknownTotal() = runBlocking {
        withTempDir { dir ->
            val progress = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
            stream(chunks, contentLength = null) { client, url ->
                downloadApkToFile(client, url, expectedSize = null, expectedSha256 = sha, targetFile = File(dir, "app.apk"),
                    onProgress = { done, total -> progress += done to total })
            }
            assertTrue(progress.isNotEmpty())
            assertTrue(progress.all { it.second == -1L }, "totals: \$progress")
            assertEquals(payload.size.toLong(), progress.last().first)
        }
    }

    @Test
    fun declaredLengthDifferentFromManifestFailsBeforeDownloading() = runBlocking {
        withTempDir { dir ->
            val target = File(dir, "app.apk")
            var verified = false
            val progress = mutableListOf<Pair<Long, Long>>()
            stream(chunks, contentLength = payload.size.toLong() + 100) { client, url ->
                assertFailsWith<IllegalArgumentException> {
                    downloadApkToFile(client, url, payload.size.toLong(), sha, target,
                        onProgress = { done, total -> progress += done to total },
                        onVerifying = { verified = true })
                }
            }
            assertFalse(verified)
            assertTrue(progress.isEmpty(), "length mismatch should fail before progress: \$progress")
            assertFalse(target.exists())
        }
    }

    @Test
    fun realBytesBeyondDeclaredTotalAreReportedThenRejected() = runBlocking {
        withTempDir { dir ->
            val smallTotal = (payload.size / 2).toLong()
            val progress = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
            stream(chunks, contentLength = smallTotal) { client, url ->
                assertFailsWith<IllegalArgumentException> {
                    downloadApkToFile(client, url, smallTotal, sha, File(dir, "app.apk"),
                        onProgress = { done, total -> progress += done to total })
                }
            }
            assertTrue(progress.any { it.first > smallTotal }, "raw overrun bytes must remain visible: \$progress")
            assertTrue(progress.last().first > smallTotal)
            assertFalse(File(dir, "app.apk").exists())
        }
    }

    @Test
    fun nonSuccessNeverVerifiesOrDeliversAFile() = runBlocking {
        withTempDir { dir ->
            var verified = false
            val target = File(dir, "app.apk")
            HttpClient(MockEngine { respond("gone", HttpStatusCode.InternalServerError) }).use { client ->
                assertFailsWith<IllegalStateException> {
                    downloadApkToFile(client, url(), payload.size.toLong(), sha, target, onVerifying = { verified = true })
                }
            }
            assertFalse(verified)
            assertFalse(target.exists())
        }
    }

    @Test
    fun checksumFailureKeepsPreviousFileAndRemovesTemp() = runBlocking {
        withTempDir { dir ->
            val target = File(dir, "app.apk").apply { writeText("old-valid-package") }
            stream(chunks, contentLength = payload.size.toLong()) { client, url ->
                assertFailsWith<IllegalArgumentException> {
                    downloadApkToFile(client, url, payload.size.toLong(), "0".repeat(64), target)
                }
            }
            assertEquals("old-valid-package", target.readText())
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") })
        }
    }

    @Test
    fun moveFailureKeepsTargetAndRemovesTemp() = runBlocking {
        withTempDir { dir ->
            val target = File(dir, "app.apk").apply { mkdirs() }
            stream(chunks, contentLength = payload.size.toLong()) { client, url ->
                assertFailsWith<java.io.IOException> {
                    downloadApkToFile(client, url, payload.size.toLong(), sha, target)
                }
            }
            assertTrue(target.isDirectory, "failed move deleted or replaced the existing target")
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") })
        }
    }

    @Test
    fun successfulMoveReplacesExistingPackage() = runBlocking {
        withTempDir { dir ->
            val target = File(dir, "app.apk").apply { writeText("old-package") }
            stream(chunks, contentLength = payload.size.toLong()) { client, url ->
                downloadApkToFile(client, url, payload.size.toLong(), sha, target)
            }
            assertTrue(target.isFile)
            assertTrue(target.readBytes().contentEquals(payload))
            assertEquals(sha, sha256(target))
        }
    }

    @Test
    fun missingChecksumNeverDeliversAFile() = runBlocking {
        withTempDir { dir ->
            val target = File(dir, "app.apk")
            stream(chunks, contentLength = payload.size.toLong()) { client, url ->
                assertFailsWith<IllegalArgumentException> {
                    downloadApkToFile(client, url, payload.size.toLong(), expectedSha256 = null, targetFile = target)
                }
            }
            assertFalse(target.exists())
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") })
        }
    }

    @Test
    fun cancellationRemovesTempWithoutDeliveringFile() = runBlocking {
        withTempDir { dir ->
            val channel = ByteChannel(autoFlush = true)
            val started = CompletableDeferred<Unit>()
            val target = File(dir, "app.apk")
            val client = HttpClient(MockEngine {
                respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "1000000"))
            })
            coroutineScope {
                val producer = launch(Dispatchers.IO) {
                    try {
                        repeat(100) {
                            channel.writeFully(chunks.first())
                            delay(20)
                        }
                        channel.close()
                    } catch (_: ClosedWriteChannelException) {
                        // Cancelled consumer closes the channel; cleanup is owned by this scope.
                    }
                }
                val job = launch {
                    downloadApkToFile(client, url(), 1_000_000, sha, target,
                        onProgress = { done, _ -> if (done > 0) started.complete(Unit) })
                }
                try {
                    assertTrue(withTimeoutOrNull(4_000) { started.await() } != null, "download never started")
                    job.cancelAndJoin()
                } finally {
                    producer.cancelAndJoin()
                    client.close()
                }
            }
            assertFalse(target.exists())
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") })
        }
    }

    private fun url() = "https://github.com/SatoriTours/Daily/releases/download/v9.9.9/app.apk"

    private suspend fun stream(
        bodyChunks: List<ByteArray>,
        contentLength: Long?,
        block: suspend (HttpClient, String) -> Unit,
    ) = coroutineScope {
        val channel = ByteChannel(autoFlush = true)
        val producer = launch(Dispatchers.IO) {
            try {
                bodyChunks.forEach { chunk ->
                    channel.writeFully(chunk)
                    delay(10)
                }
                channel.close()
            } catch (_: ClosedWriteChannelException) {
                // The consumer intentionally stopped reading; this producer ends with its owner.
            }
        }
        val headers = if (contentLength != null) headersOf(HttpHeaders.ContentLength, contentLength.toString()) else headersOf()
        val client = HttpClient(MockEngine { respond(channel, HttpStatusCode.OK, headers) })
        try {
            block(client, url())
        } finally {
            producer.cancelAndJoin()
            client.close()
        }
    }

    private suspend fun withTempDir(block: suspend (File) -> Unit) {
        val dir = Files.createTempDirectory("apk-download-test").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
