package com.dailysatori.core.bookkeeping

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BookkeepingNotificationRecoveryTest {
    @Test fun retriesAreBoundedAndRepeatedRequestsShareOneRecovery() = runTest {
        val monitor = BookkeepingCaptureMonitor()
        val attempts = mutableListOf<Long>()
        val recovery = BookkeepingNotificationRecovery(monitor, backgroundScope, { true }) {
            attempts += testScheduler.currentTime
        }

        repeat(3) { recovery.request() }
        runCurrent()
        assertEquals(listOf(0L), attempts)
        advanceTimeBy(1_000)
        runCurrent()
        recovery.request()
        advanceTimeBy(5_000)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(listOf(0L, 1_000L, 6_000L), attempts)
        assertFalse(monitor.state.value.connected)
    }

    @Test fun actualConnectionCancelsRetriesAndUpdatesStatus() = runTest {
        val monitor = BookkeepingCaptureMonitor()
        var attempts = 0
        val recovery = BookkeepingNotificationRecovery(monitor, backgroundScope, { true }) { attempts++ }
        recovery.request()
        runCurrent()

        recovery.connected()
        recovery.request()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, attempts)
        assertTrue(monitor.state.value.connected)
    }

    @Test fun unavailableAccessDoesNotRequestRebind() = runTest {
        var attempts = 0
        val recovery = BookkeepingNotificationRecovery(BookkeepingCaptureMonitor(), backgroundScope, { false }) {
            attempts++
        }
        recovery.request()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, attempts)
    }

    @Test fun revocationDuringRecoveryStopsFurtherAttempts() = runTest {
        var allowed = true
        var attempts = 0
        val recovery = BookkeepingNotificationRecovery(BookkeepingCaptureMonitor(), backgroundScope, { allowed }) {
            attempts++
        }
        recovery.request()
        runCurrent()
        allowed = false
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, attempts)
    }

    @Test fun disablingReceptionCancelsAPendingRecoveryImmediately() = runTest {
        var allowed = true
        var attempts = 0
        val recovery = BookkeepingNotificationRecovery(BookkeepingCaptureMonitor(), backgroundScope, { allowed }) {
            attempts++
        }
        recovery.request()
        runCurrent()
        allowed = false
        recovery.request()
        allowed = true
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, attempts)
    }

    @Test fun systemRebindFailureDoesNotCrashOrPretendToBeConnected() = runTest {
        val monitor = BookkeepingCaptureMonitor()
        var attempts = 0
        val recovery = BookkeepingNotificationRecovery(monitor, backgroundScope, { true }) {
            attempts++
            throw SecurityException("access changed")
        }
        recovery.request()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(3, attempts)
        assertTrue(monitor.state.value.failed)
        assertFalse(monitor.state.value.connected)
    }

    @Test fun returningToSettingsCanRetryAfterThePreviousBatchFinished() = runTest {
        var attempts = 0
        val recovery = BookkeepingNotificationRecovery(BookkeepingCaptureMonitor(), backgroundScope, { true }) {
            attempts++
        }
        recovery.request()
        runCurrent()
        advanceTimeBy(6_001)
        runCurrent()
        assertEquals(3, attempts)

        recovery.request()
        runCurrent()
        assertEquals(4, attempts)
    }
}
