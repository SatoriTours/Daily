package com.dailysatori.core.task

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoteArticleSyncCoordinatorTest {
    @Test
    fun oneOwnerPerSourceAndConcurrentCallersReuseIt() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var fetches = 0

        coroutineScope {
            val owner = async { coordinator.share(1L) { fetches++; entered.complete(Unit); release.await(); "owner" } }
            entered.await()
            val waiter = async { coordinator.share(1L) { fetches++; "waiter" } }
            yield()
            release.complete(Unit)

            assertEquals(NewsSourceFetchShare.Owned("owner"), owner.await())
            assertEquals(NewsSourceFetchShare.Merged, waiter.await())
            assertEquals(1, fetches)
        }
    }

    @Test
    fun differentSourcesAreNotBlockedByOneAnother() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val release = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        coroutineScope {
            val first = launch { coordinator.share(1L) { release.await() } }
            val second = async { coordinator.share(2L) { secondEntered.complete(Unit); "second" } }
            withTimeout(1_000) { secondEntered.await() }
            assertEquals(NewsSourceFetchShare.Owned("second"), second.await())
            release.complete(Unit)
            first.join()
        }
    }

    @Test
    fun cancellingTheWaiterDoesNotCancelTheOwner() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var ownerFinished = false

        coroutineScope {
            val owner = launch { coordinator.share(3L) { entered.complete(Unit); release.await(); ownerFinished = true } }
            entered.await()
            val waiter = launch { coordinator.share(3L) { error("the waiter must not fetch") } }
            yield()
            waiter.cancel()
            release.complete(Unit)
            owner.join()
        }
        assertTrue(ownerFinished, "cancelling a waiter must not cancel the shared owner")
    }

    @Test
    fun anOwnerFailureIsNotReportedAsSuccessAndTheWaiterTakesOverOnce() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var fetches = 0

        coroutineScope {
            val owner = async { coordinator.share(4L) { fetches++; entered.complete(Unit); release.await(); error("抓取失败") } }
            entered.await()
            val waiter = async { coordinator.share(4L) { fetches++; "retry" } }
            yield()
            release.complete(Unit)

            assertIs<NewsSourceFetchShare.Failed>(owner.await())
            assertEquals(NewsSourceFetchShare.Owned("retry"), waiter.await())
            assertEquals(2, fetches)
        }
    }

    @Test
    fun ownerCancellationReleasesTheSlotSoTheNextCallRetries() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val entered = CompletableDeferred<Unit>()

        coroutineScope {
            val owner = async { coordinator.share(5L) { entered.complete(Unit); awaitCancellation() } }
            entered.await()
            owner.cancel()
            owner.join()
        }

        val recovered = withTimeout(1_000) { coordinator.share(5L) { "recovered" } }
        assertEquals(NewsSourceFetchShare.Owned("recovered"), recovered)
    }

    @Test
    fun aCancelledOwnerUnderContentionNeverStrandsWaitingCallers() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val entered = CompletableDeferred<Unit>()

        coroutineScope {
            val owner = async { coordinator.share(6L) { entered.complete(Unit); awaitCancellation() } }
            entered.await()
            val waiters = (1..4).map { index -> async { coordinator.share(6L) { "w" + index } } }
            yield()
            owner.cancel()
            owner.join()
            val results = withTimeout(2_000) { waiters.awaitAll() }
            // Every waiter ends within the bound; exactly one of them takes the work over, the rest
            // reuse it. None may hang or report the cancelled owner as success by accident.
            assertTrue(results.all { it is NewsSourceFetchShare.Owned || it == NewsSourceFetchShare.Merged },
                "waiters must end as owner or reuse: " + results)
            assertEquals(1, results.count { it is NewsSourceFetchShare.Owned })
        }
    }

    @Test
    fun aValueLevelFailureIsNotSharedAsSuccessAndTheWaiterTakesOver() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        coroutineScope {
            val owner = async {
                coordinator.share(9L, isSuccess = { ok: Boolean -> ok }) {
                    entered.complete(Unit); release.await(); false
                }
            }
            entered.await()
            val waiter = async { coordinator.share(9L, isSuccess = { ok: Boolean -> ok }) { true } }
            yield()
            release.complete(Unit)

            assertEquals(NewsSourceFetchShare.Owned(false), owner.await())
            assertEquals(NewsSourceFetchShare.Owned(true), waiter.await(),
                "a returned failure value must not be reused as success")
        }
    }

    @Test
    fun onlyARealSuccessOpensTheReuseWindow() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        var fetches = 0
        suspend fun share(value: String) =
            coordinator.share(10L, isSuccess = { it == "ok" }) { fetches++; value }

        assertEquals(NewsSourceFetchShare.Owned("bad"), share("bad"))
        assertEquals(NewsSourceFetchShare.Owned("ok"), share("ok"), "a failed value is retried")
        assertEquals(NewsSourceFetchShare.Merged, share("ok"))
        assertEquals(2, fetches)
    }

    @Test
    fun aFinishedFetchIsReusedOnlyInsideTheWindow() = runBlocking {
        var now = 1_000L
        val coordinator = RemoteArticleSyncCoordinator(reuseWindowMs = 5_000L, nowMs = { now })
        var fetches = 0

        assertEquals(NewsSourceFetchShare.Owned(1), coordinator.share(7L) { ++fetches })
        assertEquals(NewsSourceFetchShare.Merged, coordinator.share(7L) { ++fetches })
        assertEquals(1, fetches)
        now = 10_000L
        assertEquals(NewsSourceFetchShare.Owned(2), coordinator.share(7L) { ++fetches })
    }

    @Test
    fun aFailedFetchDoesNotOpenTheReuseWindow() = runBlocking {
        val coordinator = RemoteArticleSyncCoordinator()
        assertIs<NewsSourceFetchShare.Failed>(coordinator.share(8L) { error("offline") })
        assertEquals(NewsSourceFetchShare.Owned("second try"), coordinator.share(8L) { "second try" })
    }
}
