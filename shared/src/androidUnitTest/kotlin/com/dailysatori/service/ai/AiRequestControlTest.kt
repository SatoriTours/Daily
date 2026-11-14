package com.dailysatori.service.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
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

    @Test fun queuedRequestFailsVisiblyInsteadOfWaitingForMinutes() = runBlocking {
        val gate = AiRequestCoordinator(backgroundIntervalMs = 0, maxQueueWaitMs = 150)
        val release = CompletableDeferred<Unit>()
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val first = launch { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {
            firstStarted.complete(Unit); release.await()
        } }
        firstStarted.await()
        val second = launch { gate.withRequest("https://example.test/v1", AiPurpose.INTERACTIVE) {
            secondStarted.complete(Unit); release.await()
        } }
        secondStarted.await()
        // Both permits are held by long requests: a third must fail instead of blocking its caller forever.
        var sent = false
        val failure = assertFailsWith<AiQueueWaitTimeout> {
            gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) { sent = true }
        }
        assertFalse(sent)
        assertTrue(failure.waitedMs in 100..5_000, "waitedMs=" + failure.waitedMs)
        release.complete(Unit)
        first.join()
        second.join()
        withTimeout(1_000) { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {} }
    }

    @Test fun congestionIsDeferredWithinTheCallersOwnAdmissionBudget() = runBlocking {
        val gate = AiRequestCoordinator(backgroundIntervalMs = 0)
        val release = CompletableDeferred<Unit>()
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val first = launch { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {
            firstStarted.complete(Unit); release.await()
        } }
        firstStarted.await()
        val second = launch { gate.withRequest("https://example.test/v1", AiPurpose.INTERACTIVE) {
            secondStarted.complete(Unit); release.await()
        } }
        secondStarted.await()
        var sent = false
        val startedAt = System.currentTimeMillis()
        // A short guard keeps the RED run honest without waiting for the gate's five minute hard bound.
        val outcome = runCatching {
            withTimeout(5_000) {
                withAiCallDeadline(200) {
                    gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) { sent = true }
                }
            }
        }
        val failure = outcome.exceptionOrNull()
        assertIs<AiQueueWaitTimeout>(failure, "congestion must surface as a deferral, was " + failure)
        assertFalse(sent)
        assertTrue(System.currentTimeMillis() - startedAt < 4_000)
        release.complete(Unit)
        first.join()
        second.join()
    }

    @Test fun aFailingSettingsWriteNeverLeaksAPermit() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val settings = SettingRepository(DailySatoriDatabase(driver))
        val gate = AiRequestCoordinator(settings, backgroundIntervalMs = 0)
        withTimeout(2_000) { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {} }
        // Every later persistence write fails now. The bookkeeping write happens after the permit is granted,
        // so a leaked permit would leave background == 1 and block this request until the hard bound.
        driver.close()
        withTimeout(2_000) { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {} }
        withTimeout(2_000) { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {} }
    }

    @Test fun rateLimitCooldownSurvivesAFailingSettingsWrite() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val gate = AiRequestCoordinator(SettingRepository(DailySatoriDatabase(driver)), backgroundIntervalMs = 0)
        withTimeout(2_000) { gate.withRequest("https://example.test/v1", AiPurpose.INTERACTIVE) {} }
        driver.close()
        val limited = assertFailsWith<AiHttpFailure> {
            gate.withRequest("https://example.test/v1", AiPurpose.INTERACTIVE) {
                throw AiHttpFailure(429, System.currentTimeMillis() + 60_000)
            }
        }
        assertEquals(429, limited.status)
        var sent = false
        val blocked = assertFailsWith<AiHttpFailure> {
            gate.withRequest("https://example.test/v1", AiPurpose.INTERACTIVE) { sent = true }
        }
        assertEquals(429, blocked.status, "the in-memory cooldown must not depend on a successful write")
        assertFalse(sent)
    }

    @Test fun congestionDefersAfterRealPreWorkInsteadOfRacingTheOuterTimeout() = runBlocking {
        val gate = AiRequestCoordinator(backgroundIntervalMs = 0)
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var holders = 0
        val held = listOf(
            launch { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {
                holders++; if (holders == 2) started.complete(Unit); release.await()
            } },
            launch { gate.withRequest("https://example.test/v1", AiPurpose.INTERACTIVE) {
                holders++; if (holders == 2) started.complete(Unit); release.await()
            } },
        )
        started.await()
        val budget = 600L
        var sent = false
        val began = System.currentTimeMillis()
        val outcome = runCatching {
            withTimeout(budget) {
                withAiCallDeadline(budget) {
                    delay(100) // real pre-work before the request reaches the gate
                    gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) { sent = true }
                }
            }
        }
        val failure = outcome.exceptionOrNull()
        assertIs<AiQueueWaitTimeout>(failure, "deferral must win even after pre-work, was " + failure)
        assertFalse(sent)
        assertTrue(System.currentTimeMillis() - began < budget, "the deferral must exit before the outer timeout")
        release.complete(Unit)
        held.forEach { it.cancel() }
    }

    @Test fun aShortBudgetStillSendsWhenAPermitIsFree() = runBlocking {
        val gate = AiRequestCoordinator(backgroundIntervalMs = 0)
        var sent = false
        withTimeout(1_000) {
            withAiCallDeadline(20) { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) { sent = true } }
        }
        assertTrue(sent, "a tiny budget must not be swallowed by the send reserve")
    }

    @Test fun aPathAdmissionCapIsOneAbsoluteDeadlineCountedFromContextCreation() {
        // 150s budget, 15s reserve, 15s cap: the cap wins while plenty of budget is left.
        assertEquals(15_000L, AiCallDeadline({ 0 }, 150_000L, 15_000L).remainingForAdmissionMs())
        // Cumulative from creation: 10s in, only 5s of cap is left (not another 15s).
        assertEquals(5_000L, AiCallDeadline({ 10_000L }, 150_000L, 15_000L).remainingForAdmissionMs())
        // Past the cap there is no admission waiting left, even though 115s of budget remains.
        assertEquals(0L, AiCallDeadline({ 20_000L }, 150_000L, 15_000L).remainingForAdmissionMs())
        // The tighter of the two still wins: without a cap the budget alone decides.
        assertEquals(0L, AiCallDeadline({ 140_000L }, 150_000L, 15_000L).remainingForAdmissionMs())
        assertEquals(5_000L, AiCallDeadline({ 130_000L }, 150_000L).remainingForAdmissionMs())
        assertEquals(135_000L, AiCallDeadline({ 0 }, 150_000L).remainingForAdmissionMs())
        assertEquals(15_000L, com.dailysatori.service.unifiednews.AI_SUMMARY_ADMISSION_CAP_MS)
    }

    @Test fun repeatedGateWakeUpsCannotRestartTheAdmissionCap() = runBlocking {
        val gate = AiRequestCoordinator(backgroundIntervalMs = 0)
        val release = CompletableDeferred<Unit>()
        val held = CompletableDeferred<Unit>()
        var holders = 0
        val scope = CoroutineScope(currentCoroutineContext() + Job())
        listOf(
            scope.launch { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) {
                holders++; if (holders == 2) held.complete(Unit); release.await() } },
            scope.launch { gate.withRequest("https://example.test/v1", AiPurpose.INTERACTIVE) {
                holders++; if (holders == 2) held.complete(Unit); release.await() } },
        )
        held.await()
        // Churn: every ticket add/remove signals group.changed, which must never reset the absolute cap.
        val churn = scope.launch {
            repeat(40) {
                val waiter = launch { runCatching {
                    gate.withRequest("https://example.test/v1", AiPurpose.INTERACTIVE) { }
                } }
                delay(25)
                waiter.cancelAndJoin()
            }
        }
        var sent = false
        val began = System.currentTimeMillis()
        val outcome = runCatching {
            withAiCallDeadline(budgetMs = 30_000, admissionCapMs = 300) {
                gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) { sent = true }
            }
        }
        val elapsed = System.currentTimeMillis() - began
        release.complete(Unit)
        churn.cancelAndJoin()
        scope.cancel()

        assertIs<AiQueueWaitTimeout>(outcome.exceptionOrNull(), "an unsent request defers, it never sends late")
        assertFalse(sent)
        assertTrue(elapsed < 800, "the cap is one absolute deadline across wake-ups: " + elapsed + "ms")
    }

    @Test fun advisoryToleranceNeverSwallowsCancellation() {
        assertNull(tolerateAdvisory { throw IllegalStateException("database locked") })
        assertEquals("ok", tolerateAdvisory { "ok" })
        val cancelled = assertFailsWith<kotlinx.coroutines.CancellationException> {
            tolerateAdvisory { throw kotlinx.coroutines.CancellationException("stop") }
        }
        assertEquals("stop", cancelled.message)
    }

    @Test fun numericSettingHighWaterNeverRegressesOnReorderedWrites() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            settings.upsertNumericFloor("ai.cooldown.k", 200)
            settings.upsertNumericFloor("ai.cooldown.k", 100) // a delayed, older write
            assertEquals("200", settings.get("ai.cooldown.k"))
            settings.upsertNumericFloor("ai.cooldown.k", 300)
            assertEquals("300", settings.get("ai.cooldown.k"))
            settings.upsertNumericFloor("ai.next-background.k", 50)
            assertEquals("50", settings.get("ai.next-background.k"), "a missing key is created once")
            settings.upsertNumericFloor("ai.next-background.k", 40)
            assertEquals("50", settings.get("ai.next-background.k"))
        }
    }

    @Test fun cancellationWhilePersistingAnAdmissionNeverLeaksThePermit() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        var cancelPersistence = true
        val cancellable = object : SqlDriver by driver {
            override fun execute(
                identifier: Int?,
                sql: String,
                parameters: Int,
                binders: (SqlPreparedStatement.() -> Unit)?,
            ): QueryResult<Long> {
                if (cancelPersistence && sql.contains("UPDATE setting")) {
                    throw kotlinx.coroutines.CancellationException("cancelled while persisting admission")
                }
                return driver.execute(identifier, sql, parameters, binders)
            }
        }
        val gate = AiRequestCoordinator(SettingRepository(DailySatoriDatabase(cancellable)), backgroundIntervalMs = 0)

        // The admission is granted, then the spacing write is cancelled: the permit must still be released.
        val cancelled = runCatching {
            withTimeout(2_000) { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) { fail("must not send") } }
        }
        assertIs<kotlinx.coroutines.CancellationException>(cancelled.exceptionOrNull(), "cancellation is never swallowed")

        cancelPersistence = false
        var sent = false
        // A leaked permit would leave background == 1 and block this request until the hard bound.
        withTimeout(2_000) { gate.withRequest("https://example.test/v1", AiPurpose.EXTERNAL_CONTENT) { sent = true } }
        assertTrue(sent, "the permit taken before the cancelled write must be released")
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
