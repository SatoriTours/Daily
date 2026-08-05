package com.dailysatori.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetrySupportTest {
    @Test
    fun transientFailureRetriesUntilSuccess() = runBlocking {
        var attempts = 0

        val result = retryTransientFailure(maxAttempts = 3, initialDelayMs = 0) {
            attempts += 1
            if (attempts < 3) error("temporary")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun permanentAndCancellationFailuresAreNotRetried() = runBlocking {
        var permanentAttempts = 0
        assertFailsWith<IllegalArgumentException> {
            retryTransientFailure(
                maxAttempts = 3,
                initialDelayMs = 0,
                shouldRetry = { it !is IllegalArgumentException },
            ) {
                permanentAttempts += 1
                throw IllegalArgumentException("permanent")
            }
        }
        assertEquals(1, permanentAttempts)

        var cancellationAttempts = 0
        assertFailsWith<CancellationException> {
            retryTransientFailure(maxAttempts = 3, initialDelayMs = 0) {
                cancellationAttempts += 1
                throw CancellationException("cancelled")
            }
        }
        assertEquals(1, cancellationAttempts)
    }

    @Test
    fun fatalErrorsAreNotCaughtOrRetried() = runBlocking {
        var attempts = 0

        assertFailsWith<AssertionError> {
            retryTransientFailure(maxAttempts = 3, initialDelayMs = 0) {
                attempts += 1
                throw AssertionError("fatal")
            }
        }

        assertEquals(1, attempts)
    }

    @Test
    fun concurrentMapHonorsConfiguredLimitAndPreservesOrder() = runBlocking {
        val lock = Mutex()
        var active = 0
        var maxActive = 0

        val result = (1..5).mapConcurrently(maxConcurrency = 2) { value ->
            lock.withLock {
                active += 1
                maxActive = maxOf(maxActive, active)
            }
            delay(20)
            lock.withLock { active -= 1 }
            value * 2
        }

        assertEquals(listOf(2, 4, 6, 8, 10), result)
        assertEquals(2, maxActive)
    }
}
