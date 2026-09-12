package com.dailysatori.core.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateManifestTest {
    private val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val manifest = """{"versionName":"5.1.63-commit.101","versionCode":101,"channel":"commit","schemaVersion":27,"size":3,"sha256":"$hash","apkUrl":"https://github.com/SatoriTours/Daily/releases/download/commit-101/app.apk"}"""

    @Test
    fun manifestCarriesChannelVersionAndExactApkDigest() {
        val result = parseUpdateManifest(manifest, "release")
        assertEquals(101, result.versionCode)
        assertEquals(UpdateChannel.COMMIT, result.channel)
        assertEquals(27, result.schemaVersion)
        assertEquals(hash, result.sha256)
        verifyApkChecksum("abc".toByteArray(), result.sha256)
    }

    @Test
    fun tamperedOrUnverifiedApkIsRejected() {
        assertFailsWith<IllegalArgumentException> { verifyApkChecksum("changed".toByteArray(), hash) }
        assertFailsWith<IllegalArgumentException> { verifyApkChecksum("abc".toByteArray(), null) }
    }

    @Test
    fun foreignRepositoryAndMalformedMetadataAreRejected() {
        assertFailsWith<IllegalArgumentException> { parseUpdateManifest(manifest.replace("SatoriTours/Daily", "foreign/repo"), "") }
        assertFailsWith<IllegalArgumentException> { parseUpdateManifest(manifest.replace(hash, "bad"), "") }
        assertFailsWith<IllegalArgumentException> { parseUpdateManifest(manifest.replace("5.1.63", "5.1.64"), "") }
        assertFailsWith<IllegalArgumentException> { parseUpdateManifest(manifest.replace("\"commit\"", "\"unknown\""), "") }
    }

    @Test
    fun commitChannelFetchesItsPointerAndThenImmutableApkMetadata(): Unit = runBlocking {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            respond(if (paths.size == 1) releaseJson() else manifest)
        })
        try {
            val result = AppUpgradeService(client).checkChannelUpdate(UpdateChannel.COMMIT, InstalledBuild(100, UpdateChannel.COMMIT, 27))
            assertEquals(101, result.release?.versionCode)
            assertEquals(listOf("/repos/SatoriTours/Daily/releases/tags/commit-build", "/SatoriTours/Daily/releases/download/commit-build/update.json"), paths)
        } finally { client.close() }
    }

    @Test
    fun stableChannelUsesLatestAndOldReleasesWaitForMetadata(): Unit = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/repos/SatoriTours/Daily/releases/latest", request.url.encodedPath)
            respond("""{"assets":[],"tag_name":"v5.1.63"}""")
        })
        try {
            val result = AppUpgradeService(client).checkChannelUpdate(UpdateChannel.STABLE, InstalledBuild(100, UpdateChannel.COMMIT, 27))
            assertNull(result.release)
            assertTrue(result.message.contains("等待"))
        } finally { client.close() }
    }

    @Test
    fun missingChannelIsNotANetworkError(): Unit = runBlocking {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })
        try {
            assertNull(AppUpgradeService(client).checkChannelUpdate(UpdateChannel.COMMIT, InstalledBuild(100, UpdateChannel.COMMIT, 27)).release)
        } finally { client.close() }
    }

    @Test
    fun cancellationIsNotConvertedToAnUpdateResult(): Unit = runBlocking {
        val client = HttpClient(MockEngine { throw CancellationException() })
        try {
            assertFailsWith<CancellationException> { AppUpgradeService(client).checkChannelUpdate(UpdateChannel.COMMIT, InstalledBuild(100, UpdateChannel.COMMIT, 27)) }
        } finally { client.close() }
    }

    private fun releaseJson() = """{"html_url":"https://github.com/SatoriTours/Daily/releases/tag/commit-build","assets":[{"name":"update.json","browser_download_url":"https://github.com/SatoriTours/Daily/releases/download/commit-build/update.json"}]}"""
}
