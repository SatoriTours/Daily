package com.dailysatori.core.task

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * What one call of [RemoteArticleSyncCoordinator.share] did, so callers never mistake another owner's
 * failure for their own success.
 */
sealed interface NewsSourceFetchShare<out T> {
    /** This call owned the fetch and got a value. */
    data class Owned<T>(val value: T) : NewsSourceFetchShare<T>

    /** Another call already fetched this source; read the result from the repository. */
    data object Merged : NewsSourceFetchShare<Nothing>

    /** Neither this call nor a shared owner produced data; keep the caller's error behaviour. */
    data class Failed(val cause: Throwable?) : NewsSourceFetchShare<Nothing>
}

/**
 * Single execution owner per news source, app-process wide. The first caller owns the fetch; concurrent
 * callers wait for that owner and then read the result from the repository instead of fetching again.
 * Ownership is always released and waiters always learn the real outcome, so a failure or cancellation
 * is never reported as success and the next call can retry.
 */
class RemoteArticleSyncCoordinator(
    private val reuseWindowMs: Long = DEFAULT_REUSE_WINDOW_MS,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private enum class OwnerOutcome { SUCCESS, FAILURE, CANCELLED }

    private sealed interface Claim {
        class Owner(val ticket: CompletableDeferred<OwnerOutcome>) : Claim
        class Busy(val ticket: CompletableDeferred<OwnerOutcome>) : Claim
        object Reuse : Claim
    }

    private class Slot {
        var running: CompletableDeferred<OwnerOutcome>? = null
        var freshUntil = 0L
    }

    private val lock = Mutex()
    private val slots = mutableMapOf<Long, Slot>()

    /**
     * [isSuccess] decides whether the returned value is a real business success; a value-level failure
     * (for example a RemoteNewsResult.Failure carried back normally) must not open the reuse window and
     * must not be reported to waiters as a reusable success.
     */
    suspend fun <T> share(
        sourceId: Long,
        isSuccess: (T) -> Boolean = { true },
        /**
         * When false, a source that was just fetched successfully is still fetched by this call. Used by
         * a bounded history round, which must observe its own progress instead of reading another run.
         * A concurrently running owner is still awaited, so the source keeps exactly one writer.
         */
        reuseSuccessfulFetch: Boolean = true,
        fetch: suspend () -> T,
    ): NewsSourceFetchShare<T> {
        var takenOver = false
        while (true) {
            val claim = lock.withLock { claimLocked(sourceId, reuseSuccessfulFetch) }
            when (claim) {
                is Claim.Reuse -> return NewsSourceFetchShare.Merged
                is Claim.Owner -> return runOwned(sourceId, claim.ticket, isSuccess, fetch)
                is Claim.Busy -> {
                    val outcome = try {
                        claim.ticket.await()
                    } catch (cancelled: CancellationException) {
                        // Our own cancellation must not disturb the owner; it only ends this call.
                        currentCoroutineContext().ensureActive()
                        OwnerOutcome.CANCELLED
                    }
                    if (outcome == OwnerOutcome.SUCCESS) return NewsSourceFetchShare.Merged
                    // The owner produced nothing: take the work over exactly once, then give up.
                    if (takenOver) return NewsSourceFetchShare.Failed(null)
                    takenOver = true
                }
            }
        }
    }

    private suspend fun <T> runOwned(
        sourceId: Long,
        ticket: CompletableDeferred<OwnerOutcome>,
        isSuccess: (T) -> Boolean,
        fetch: suspend () -> T,
    ): NewsSourceFetchShare<T> {
        var outcome = OwnerOutcome.CANCELLED
        try {
            val value = fetch()
            outcome = if (isSuccess(value)) OwnerOutcome.SUCCESS else OwnerOutcome.FAILURE
            return NewsSourceFetchShare.Owned(value)
        } catch (cancelled: CancellationException) {
            outcome = OwnerOutcome.CANCELLED
            throw cancelled
        } catch (error: Throwable) {
            outcome = OwnerOutcome.FAILURE
            return NewsSourceFetchShare.Failed(error)
        } finally {
            // Cleanup must complete even when this owner was cancelled while contending for the lock.
            withContext(NonCancellable) {
                lock.withLock {
                    slots[sourceId]?.let { slot ->
                        if (slot.running === ticket) slot.running = null
                        if (outcome == OwnerOutcome.SUCCESS) slot.freshUntil = nowMs() + reuseWindowMs
                    }
                }
                // Notified outside the lock so a resumed waiter cannot run inside the critical section.
                ticket.complete(outcome)
            }
        }
    }

    private fun claimLocked(sourceId: Long, reuseSuccessfulFetch: Boolean): Claim {
        val slot = slots.getOrPut(sourceId) { Slot() }
        slot.running?.let { return Claim.Busy(it) }
        if (reuseSuccessfulFetch && slot.freshUntil > nowMs()) return Claim.Reuse
        val ticket = CompletableDeferred<OwnerOutcome>()
        slot.running = ticket
        return Claim.Owner(ticket)
    }

    companion object {
        /** Short window in which a just-fetched source is reused instead of fetched again. */
        const val DEFAULT_REUSE_WINDOW_MS = 60_000L
    }
}

