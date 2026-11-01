package com.dailysatori.service.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class AiRequestControlTest {
    @Test fun cancellingAQueuedRequestCannotLeaveAGhostAdmission() = runBlocking {
        val gate = AiRequestCoordinator(backgroundIntervalMs = 0)
        val firstStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cancelledStarted = false
        val first = launch { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {
            firstStarted.complete(Unit); release.await()
        } }
        firstStarted.await()
        val queued = launch(start = CoroutineStart.UNDISPATCHED) { gate.withRequest("https://example.test/other", AiPurpose.EXTERNAL_CONTENT) {
            cancelledStarted = true
        } }
        queued.cancelAndJoin()
        release.complete(Unit); first.join()
        withTimeout(1_000) { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {} }
        assertFalse(cancelledStarted)
    }

    @Test fun cooldownAndBackgroundStartSpacingSurviveReconstruction() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            var now = 10_000L
            val first = AiRequestCoordinator(settings, { now })
            first.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {}
            var started = false
            val next = launch(start = CoroutineStart.UNDISPATCHED) {
                AiRequestCoordinator(settings, { now }).withRequest("https://example.test/v2", AiPurpose.EXTERNAL_CONTENT) { started = true }
            }
            try { delay(30); assertFalse(started) } finally { next.cancelAndJoin() }
            assertFailsWith<AiHttpFailure> { first.withRequest("https://example.test/v1", AiPurpose.INTERACTIVE) { throw AiHttpFailure(429, 60_000) } }
            val reconstructed = AiRequestCoordinator(settings, { now })
            val limited = assertFailsWith<AiHttpFailure> { reconstructed.withRequest("https://example.test/v2", AiPurpose.INTERACTIVE) { fail("Must not send") } }
            assertEquals(60_000, limited.retryAtMs)
            now = 60_001
            reconstructed.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) { started = true }
            assertTrue(started)
        }
    }

    @Test fun retryAfterSupportsSecondsAndServerDateWithoutTrustingThePhonesClock() {
        assertEquals(61_000, aiRetryAtMs("60", null, 1_000))
        assertEquals(121_000, aiRetryAtMs("Sun, 01 Nov 2026 15:22:00 GMT", "Sun, 01 Nov 2026 15:20:00 GMT", 1_000))
        assertEquals(61_000, aiRetryAtMs("invalid", null, 1_000))
    }

    @Test fun backgroundWorkReservesCapacityForTheCurrentConversation() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var starts = 0
        HttpClient(MockEngine {
            starts++
            entered.complete(Unit)
            release.await()
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val service = AiService(client)
            val first = async { request(service, AiPurpose.EXTERNAL_CONTENT) }
            entered.await()
            val second = async { request(service, AiPurpose.REFLECTION) }
            val interactive = async { request(service, AiPurpose.INTERACTIVE) }
            try {
                delay(100)
                assertEquals(2, starts, "Only one background call and the interactive call may be in flight")
            } finally {
                second.cancelAndJoin()
                release.complete(Unit)
                first.await()
                interactive.await()
            }
        }
    }

    @Test fun rateLimitPreventsOtherTasksFromImmediatelyCallingTheSameService() = runBlocking {
        var starts = 0
        HttpClient(MockEngine {
            starts++
            respond("{}", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "60"))
        }).use { client ->
            val service = AiService(client)
            assertFails { request(service, AiPurpose.EXTERNAL_CONTENT) }
            val waiting = launch { runCatching { request(service, AiPurpose.INTERACTIVE) } }
            try {
                delay(100)
                assertEquals(1, starts, "A different task must honor the shared service cooldown")
            } finally { waiting.cancelAndJoin() }
        }
    }

    private suspend fun request(service: AiService, purpose: AiPurpose) = service.complete(
        "hello", "https://example.test/v1", "not-a-real-key", "model", purpose = purpose,
    )

    private val response = """{"choices":[{"message":{"content":"ok"}}]}"""
}
