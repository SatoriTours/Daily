package com.dailysatori.core.task

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 1482 (remote_article_sync 0/1, no HTTP, no progress) turned into a controllable fake-source contract:
 * ownership must always be released and a waiter must learn the real outcome instead of hanging or
 * mistaking a cancelled owner for success.
 */
class RemoteArticleSyncOwnershipTest {
    @Test
    fun aWaiterTakesOverWhenTheOwnerIsCancelled() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val ownerStarted = CompletableDeferred<Unit>()
        var waiterFetched = false
        val owner = launch {
            coordinator.share(7L) { ownerStarted.complete(Unit); awaitCancellation() }
        }
        ownerStarted.await()
        val waiter = async { coordinator.share(7L) { waiterFetched = true; "value" } }
        delay(100)
        owner.cancelAndJoin()

        val shared = withTimeout(2_000) { waiter.await() }

        assertTrue(waiterFetched, "a cancelled owner must release the source: " + shared)
        assertIs<NewsSourceFetchShare.Owned<String>>(shared)
        assertEquals("value", shared.value)
    }

    @Test
    fun aWaiterIsReleasedWhenTheOwnerFailsAndOwnsTheRetry() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val ownerStarted = CompletableDeferred<Unit>()
        val owner = launch {
            runCatching { coordinator.share<String>(9L) { ownerStarted.complete(Unit); error("boom") } }
        }
        ownerStarted.await()
        val waiter = async { coordinator.share(9L) { "retried" } }

        val shared = withTimeout(2_000) { waiter.await() }

        assertEquals("retried", (shared as NewsSourceFetchShare.Owned).value)
        owner.join()
    }

    @Test
    fun aWaiterCancelledWhileTheOwnerRunsLeavesTheOwnerUsable() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val ownerStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val owner = launch {
            coordinator.share(11L) { ownerStarted.complete(Unit); release.await(); "owner-value" }
        }
        ownerStarted.await()
        val waiter = launch { coordinator.share(11L) { "waiter-value" } }
        delay(100)
        waiter.cancelAndJoin()
        release.complete(Unit)
        owner.join()

        // The owner's permit is released and the next call is served (fresh reuse or a new fetch), never stuck.
        val next = withTimeout(2_000) { coordinator.share(11L) { "next-value" } }
        assertTrue(next !is NewsSourceFetchShare.Failed, "source must be usable after a cancelled waiter: " + next)
    }
}
