package com.dailysatori.core.startup

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StartupCoordinatorTest {
    @Test
    fun concurrentConsumersCannotAccessBusinessRuntimeUntilPreparationFinishes() = runTest {
        val release = CompletableDeferred<Unit>()
        var preparations = 0
        var consumers = 0
        val startup = StartupCoordinator(backgroundScope, StandardTestDispatcher(testScheduler)) {
            preparations++
            release.await()
        }
        repeat(20) { backgroundScope.launch { if (startup.awaitReady()) consumers++ } }
        repeat(20) { startup.start() }
        runCurrent()
        assertEquals(1, preparations)
        assertEquals(0, consumers)
        advanceTimeBy(120_000)
        assertEquals(StartupState.Preparing, startup.state.value)
        assertEquals(0, consumers)
        release.complete(Unit)
        runCurrent()
        assertEquals(StartupState.Ready, startup.state.value)
        assertEquals(20, consumers)
        startup.start()
        runCurrent()
        assertEquals(1, preparations)
    }

    @Test
    fun failureKeepsConsumersClosedAndExplicitRetryHasOnlyOneOwner() = runTest {
        var attempts = 0
        var failures = 0
        val startup = StartupCoordinator(backgroundScope, StandardTestDispatcher(testScheduler), { failures++ }) {
            if (++attempts == 1) throw java.io.IOException("fixture failure")
        }
        startup.start()
        runCurrent()
        assertEquals(StartupState.Failed, startup.state.value)
        assertFalse(startup.awaitReady())
        assertEquals(1, attempts)
        assertEquals(1, failures)
        repeat(20) { startup.start() }
        runCurrent()
        assertTrue(startup.awaitReady())
        assertEquals(2, attempts)
    }

    @Test
    fun cancellingAWorkerOrRecreatedActivityDoesNotCancelTheUpgrade() = runTest {
        val release = CompletableDeferred<Unit>()
        var completed = false
        val startup = StartupCoordinator(backgroundScope, StandardTestDispatcher(testScheduler)) {
            release.await()
            completed = true
        }
        startup.start()
        val first = launch { startup.awaitReady() }
        runCurrent()
        first.cancelAndJoin()
        assertEquals(StartupState.Preparing, startup.state.value)
        release.complete(Unit)
        runCurrent()
        assertTrue(completed)
        assertTrue(startup.awaitReady())
    }

    @Test
    fun startNeverRunsSlowPreparationOnCallingThread() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        var initializerThread: Thread? = null
        val caller = Thread.currentThread()
        val startup = StartupCoordinator(scope) {
            initializerThread = Thread.currentThread()
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            finished.countDown()
        }
        try {
            startup.start()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            assertNotSame(caller, initializerThread, "Database preparation must leave the application main thread")
            assertEquals(1L, finished.count, "start must return before slow preparation finishes")
            assertEquals(StartupState.Preparing, startup.state.value)
        } finally {
            release.countDown()
            assertTrue(finished.await(3, TimeUnit.SECONDS))
            scope.cancel()
        }
    }
}
