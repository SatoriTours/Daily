package com.dailysatori.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal suspend fun <T> retryTransientFailure(
    maxAttempts: Int,
    initialDelayMs: Long,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend () -> T,
): T {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    var nextDelayMs = initialDelayMs.coerceAtLeast(0L)
    repeat(maxAttempts - 1) {
        try {
            return block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!shouldRetry(error)) throw error
            if (nextDelayMs > 0) delay(nextDelayMs)
            nextDelayMs = (nextDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        }
    }
    return block()
}

internal suspend fun <T, R> Iterable<T>.mapConcurrently(
    maxConcurrency: Int,
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
    map { item -> async { semaphore.withPermit { transform(item) } } }.map { it.await() }
}

private const val MAX_RETRY_DELAY_MS = 5_000L
