package com.dailysatori.core.diagnostics

import com.dailysatori.core.service.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.*
import kotlin.test.*

class RecoveryUpdateControllerTest {
    private val installed = InstalledBuild(100, UpdateChannel.COMMIT, 27)
    private val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun savedChannelAndHigherDatabaseVersionAreRespected() = runBlocking {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            respond(if (paths.size == 1) releaseJson() else manifest(schema = 28, channel = "stable"))
        })
        try {
            val service = AppUpgradeService(client)
            val controller = RecoveryUpdateController(installed,
                { resolveRecoveryUpdateConfiguration(mapOf("update_channel" to "stable", "schema_version" to "29"), installed) },
                service::checkChannelUpdate, { _, _ -> error("Incompatible build must not download") })
            controller.loadConfiguration()
            assertEquals(UpdateChannel.STABLE, controller.state.value.configuration.channel)
            assertTrue(paths.isEmpty(), "Reading configuration must not make network requests")
            controller.check()
            assertEquals(RecoveryUpdatePhase.IDLE, controller.state.value.phase)
            assertNull(controller.state.value.release)
            assertEquals(29, controller.state.value.configuration.schemaVersion)
            assertEquals("/repos/SatoriTours/Daily/releases/latest", paths.first())
        } finally { client.close() }
    }

    @Test
    fun unreadableConfigurationFallsBackToInstalledChannelWithoutBlockingUpdate() = runBlocking {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            respond(if (paths.size == 1) releaseJson() else manifest())
        })
        try {
            val controller = RecoveryUpdateController(installed, { error("private-configuration-canary") },
                AppUpgradeService(client)::checkChannelUpdate, { _, _ -> error("Not downloading") })
            controller.loadConfiguration()
            assertTrue(controller.state.value.configuration.usedDefaults)
            controller.check()
            assertEquals(UpdateChannel.COMMIT, controller.state.value.configuration.channel)
            assertEquals(101, controller.state.value.release?.versionCode)
            assertEquals(RecoveryUpdatePhase.AVAILABLE, controller.state.value.phase)
            assertEquals("/repos/SatoriTours/Daily/releases/tags/commit-build", paths.first())
            assertFalse(controller.state.value.toString().contains("private-configuration-canary"))
        } finally { client.close() }
    }

    @Test
    fun invalidConfigurationNeverLowersInstalledSchemaOrChangesDefaultChannel() {
        val result = resolveRecoveryUpdateConfiguration(mapOf("update_channel" to "unknown", "schema_version" to "2"), installed)
        assertEquals(UpdateChannel.COMMIT, result.channel)
        assertEquals(27, result.schemaVersion)
        assertTrue(result.usedDefaults)
    }

    @Test
    fun failedDownloadCannotBecomeInstallReadyAndCanBeRetried() = runBlocking {
        val release = parseUpdateManifest(manifest(), "release")
        var attempt = 0
        val controller = RecoveryUpdateController(installed,
            { resolveRecoveryUpdateConfiguration(emptyMap(), installed) }, { _, _ -> UpdateCheck(release) },
            { _, progress ->
                progress(1, 3)
                if (attempt++ == 0) error("private-download-canary")
                ApkDownload(0, "/private/test.apk")
            })
        controller.loadConfiguration()
        controller.check()
        assertNull(controller.download())
        assertEquals(RecoveryUpdateFailure.DOWNLOAD, controller.state.value.failure)
        assertNull(controller.state.value.download)
        assertFalse(controller.state.value.toString().contains("private-download-canary"))
        assertNotNull(controller.download())
        assertEquals(RecoveryUpdatePhase.READY, controller.state.value.phase)
        assertEquals(1f, controller.state.value.progress)
    }

    @Test
    fun overlappingChecksCannotReplaceEachOtherOrStartTwoRequests() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var checks = 0
        val release = parseUpdateManifest(manifest(), "release")
        val controller = RecoveryUpdateController(installed,
            { resolveRecoveryUpdateConfiguration(emptyMap(), installed) },
            { _, _ -> checks++; entered.complete(Unit); finish.await(); UpdateCheck(release) },
            { _, _ -> error("Not downloading") })
        controller.loadConfiguration()
        val first = launch { controller.check() }
        entered.await()
        controller.check()
        finish.complete(Unit)
        first.join()
        assertEquals(1, checks)
        assertEquals(101, controller.state.value.release?.versionCode)
    }

    @Test
    fun cancelledUpdateCheckStaysCancelledAndAllowsRetry() = runBlocking {
        val controller = RecoveryUpdateController(installed,
            { resolveRecoveryUpdateConfiguration(emptyMap(), installed) },
            { _, _ -> throw CancellationException() }, { _, _ -> error("Not downloading") })
        controller.loadConfiguration()
        assertFailsWith<CancellationException> { controller.check() }
        assertEquals(RecoveryUpdatePhase.IDLE, controller.state.value.phase)
        assertNull(controller.state.value.failure)
    }

    private fun releaseJson() = """{"assets":[{"name":"update.json","browser_download_url":"https://github.com/SatoriTours/Daily/releases/download/commit-build/update.json"}]}"""
    private fun manifest(schema: Int = 27, channel: String = "commit") =
        """{"versionName":"5.1.65-commit.101","versionCode":101,"channel":"$channel","schemaVersion":$schema,"size":3,"sha256":"$hash","apkUrl":"https://github.com/SatoriTours/Daily/releases/download/commit-build/app.apk"}"""
}
