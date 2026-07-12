package com.dailysatori.core.recording

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryRecordingRuntimeTest {
    @Test
    fun closingWindowPlaceholderForegroundReturnFailureRejectsStart() = runTest {
        verifyClosingPlaceholderFailure(
            configureHost = {
                foregroundError = DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED
            },
            expectedErrorCode = DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED,
        )
    }

    @Test
    fun closingWindowPlaceholderForegroundExceptionRejectsStartWithStableCode() = runTest {
        verifyClosingPlaceholderFailure(
            configureHost = {
                foregroundFailure = SecurityException("notifications denied")
            },
            expectedErrorCode = DiaryRecordingErrorCode.FOREGROUND_SECURITY_DENIED,
        )
    }

    @Test
    fun closingWindowStartUsesPlaceholderAndCreatesFreshRuntimeOnlyAfterOldClosed() = runTest {
        val store = DiaryRecordingStore()
        val persistence = FakePersistence()
        val root = createTempDirectory("diary-recording-runtime-closing-test").toFile()
        val executors = mutableListOf<CountingExecutorService>()
        val recorders = mutableListOf<FakeRecorder>()
        val releaseStarted = CountDownLatch(1)
        val releaseMayFinish = CountDownLatch(1)
        var nextToken = 0
        val manager = DiaryRecordingRuntimeManager(backgroundScope, { _, _ -> }) { onClosed ->
            val executor = CountingExecutorService().also(executors::add)
            val recorder = FakeRecorder().also(recorders::add).apply {
                if (recorders.size == 1) {
                    onRelease = {
                        releaseStarted.countDown()
                        check(releaseMayFinish.await(2, TimeUnit.SECONDS))
                        currentOutput
                    }
                }
            }
            DiaryRecordingRuntime(
                store = store,
                recorder = recorder,
                persistence = persistence,
                outputFile = { diaryId, attachmentId, token ->
                    File(root, "$diaryId/$attachmentId-$token.m4a")
                },
                scope = backgroundScope,
                actorDispatcher = StandardTestDispatcher(testScheduler),
                recorderDispatcher = executor.asCoroutineDispatcher(),
                ioDispatcher = StandardTestDispatcher(testScheduler),
                monotonicNowMs = { testScheduler.currentTime },
                nextSessionToken = { "session-${++nextToken}" },
                retryDelaysMs = emptyList(),
                tickerIntervalMs = 1_000_000,
                onClosed = onClosed,
            )
        }

        val firstHost = FakeAndroidHost()
        assertEquals(
            DiaryRecordingCommandResult.Accepted,
            manager.attachAndSubmit(
                firstHost,
                1,
                DiaryRecordingCommand.Start(11, 101),
            ).await(),
        )
        var attempts = 0
        while (store.state.value !is DiaryRecordingState.Recording && attempts++ < 200) {
            runCurrent()
            Thread.sleep(5)
        }
        assertIs<DiaryRecordingState.Recording>(store.state.value)
        manager.detachHost(firstHost)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(releaseStarted.await(2, TimeUnit.SECONDS))

        val replacementHost = FakeAndroidHost()
        val pendingStart = manager.attachAndSubmit(
            replacementHost,
            2,
            DiaryRecordingCommand.Start(22, 202),
        )
        val placeholder = assertIs<DiaryRecordingState.Starting>(replacementHost.foregroundStates.single())
        assertEquals(22, placeholder.diaryId)
        assertEquals(202, placeholder.attachmentId)
        assertEquals(1, executors.size)
        assertEquals(1, recorders.size)
        assertFalse(pendingStart.isCompleted)

        releaseMayFinish.countDown()
        attempts = 0
        while (
            (executors.size != 2 || store.state.value !is DiaryRecordingState.Recording) &&
            attempts++ < 200
        ) {
            runCurrent()
            Thread.sleep(5)
        }
        assertTrue(executors.first().isShutdown)
        assertEquals(DiaryRecordingCommandResult.Accepted, pendingStart.await())
        assertEquals(2, executors.size)
        assertEquals(2, recorders.size)

        val recording = assertIs<DiaryRecordingState.Recording>(store.state.value)
        assertEquals(22, recording.diaryId)
        assertEquals(202, recording.attachmentId)
        runCurrent()
        assertIs<DiaryRecordingState.Recording>(store.state.value)

        manager.detachHost(replacementHost)
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun pendingPauseAndStopReplayInFifoAfterOldRuntimeCloses() = runTest {
        val store = DiaryRecordingStore()
        val persistence = FakePersistence()
        val root = createTempDirectory("diary-recording-runtime-manager-test").toFile()
        val executors = mutableListOf<CountingExecutorService>()
        val recorders = mutableListOf<FakeRecorder>()
        val releaseStarted = CountDownLatch(1)
        val releaseMayFinish = CountDownLatch(1)
        var nextToken = 0
        val manager = DiaryRecordingRuntimeManager(backgroundScope, { _, _ -> }) { onClosed ->
            val executor = CountingExecutorService().also(executors::add)
            val recorder = FakeRecorder().also(recorders::add).apply {
                if (recorders.size == 1) {
                    onRelease = {
                        releaseStarted.countDown()
                        check(releaseMayFinish.await(2, TimeUnit.SECONDS))
                        currentOutput
                    }
                }
            }
            DiaryRecordingRuntime(
                store = store,
                recorder = recorder,
                persistence = persistence,
                outputFile = { diaryId, attachmentId, token ->
                    File(root, "$diaryId/$attachmentId-$token.m4a")
                },
                scope = backgroundScope,
                actorDispatcher = StandardTestDispatcher(testScheduler),
                recorderDispatcher = executor.asCoroutineDispatcher(),
                ioDispatcher = StandardTestDispatcher(testScheduler),
                monotonicNowMs = { testScheduler.currentTime },
                nextSessionToken = { "session-${++nextToken}" },
                retryDelaysMs = emptyList(),
                tickerIntervalMs = 1_000_000,
                onClosed = onClosed,
            )
        }

        val firstHost = FakeAndroidHost()
        assertEquals(
            DiaryRecordingCommandResult.Accepted,
            manager.attachAndSubmit(
                firstHost,
                1,
                DiaryRecordingCommand.Start(11, 101),
            ).await(),
        )
        var attempts = 0
        while (store.state.value !is DiaryRecordingState.Recording && attempts++ < 200) {
            runCurrent()
            Thread.sleep(5)
        }
        manager.detachHost(firstHost)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(releaseStarted.await(2, TimeUnit.SECONDS))

        val replacementHost = FakeAndroidHost()
        val completionOrder = CopyOnWriteArrayList<String>()
        val start = manager.attachAndSubmit(
            replacementHost,
            2,
            DiaryRecordingCommand.Start(22, 202),
        )
        val pause = manager.submit(DiaryRecordingCommand.Pause, startId = 3)
        val stop = manager.submit(DiaryRecordingCommand.Stop, startId = 4)
        start.invokeOnCompletion { completionOrder += "start" }
        pause.invokeOnCompletion { completionOrder += "pause" }
        stop.invokeOnCompletion { completionOrder += "stop" }
        assertFalse(start.isCompleted)
        assertFalse(pause.isCompleted)
        assertFalse(stop.isCompleted)
        assertEquals(1, recorders.size)

        releaseMayFinish.countDown()
        attempts = 0
        while (!stop.isCompleted && attempts++ < 200) {
            runCurrent()
            Thread.sleep(5)
        }

        assertEquals(DiaryRecordingCommandResult.Accepted, start.await())
        assertEquals(DiaryRecordingCommandResult.Ignored, pause.await())
        assertEquals(DiaryRecordingCommandResult.Accepted, stop.await())
        assertEquals(listOf("start", "pause", "stop"), completionOrder)
        assertEquals(2, executors.size)
        assertEquals(2, recorders.size)
        attempts = 0
        while (recorders[1].stopCalls == 0 && attempts++ < 200) {
            runCurrent()
            Thread.sleep(5)
        }
        assertEquals(1, recorders[1].stopCalls)

        manager.detachHost(replacementHost)
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun shutdownCannotSplitOpenCheckFromAttachAndSubmit() = runTest {
        val store = DiaryRecordingStore()
        val root = createTempDirectory("diary-recording-runtime-attach-race-test").toFile()
        val runtimeExecutors = mutableListOf<CountingExecutorService>()
        val runtimes = mutableListOf<DiaryRecordingRuntime>()
        val manager = DiaryRecordingRuntimeManager(backgroundScope, { _, _ -> }) { onClosed ->
            val executor = CountingExecutorService().also(runtimeExecutors::add)
            DiaryRecordingRuntime(
                store = store,
                recorder = FakeRecorder(),
                persistence = FakePersistence(),
                outputFile = { diaryId, attachmentId, token ->
                    File(root, "$diaryId/$attachmentId-$token.m4a")
                },
                scope = backgroundScope,
                actorDispatcher = StandardTestDispatcher(testScheduler),
                recorderDispatcher = executor.asCoroutineDispatcher(),
                ioDispatcher = StandardTestDispatcher(testScheduler),
                monotonicNowMs = { testScheduler.currentTime },
                nextSessionToken = { "session-token" },
                retryDelaysMs = emptyList(),
                tickerIntervalMs = 1_000_000,
                onClosed = onClosed,
            ).also(runtimes::add)
        }
        val firstHost = FakeAndroidHost()
        assertEquals(
            DiaryRecordingCommandResult.Accepted,
            manager.attachAndSubmit(
                firstHost,
                1,
                DiaryRecordingCommand.Start(11, 101),
            ).await(),
        )
        var attempts = 0
        while (store.state.value !is DiaryRecordingState.Recording && attempts++ < 200) {
            runCurrent()
            Thread.sleep(5)
        }

        val attachEntered = CountDownLatch(1)
        val attachMayFinish = CountDownLatch(1)
        val replacement = FakeAndroidHost().apply {
            onStateChanged = {
                attachEntered.countDown()
                check(attachMayFinish.await(2, TimeUnit.SECONDS))
            }
        }
        val callers = Executors.newFixedThreadPool(2)
        try {
            val attachCall = callers.submit<kotlinx.coroutines.Deferred<DiaryRecordingCommandResult>> {
                manager.attachAndSubmit(
                    replacement,
                    2,
                    DiaryRecordingCommand.Start(11, 101),
                )
            }
            assertTrue(attachEntered.await(2, TimeUnit.SECONDS))
            val shutdownCall = callers.submit<kotlinx.coroutines.Deferred<DiaryRecordingCommandResult>> {
                runtimes.single().shutdown()
            }
            Thread.sleep(50)
            assertFalse(shutdownCall.isDone)

            attachMayFinish.countDown()
            val attachedResult = attachCall.get(2, TimeUnit.SECONDS)
            shutdownCall.get(2, TimeUnit.SECONDS)
            runCurrent()

            assertEquals(DiaryRecordingCommandResult.AlreadyActive, attachedResult.await())
            assertEquals(1, runtimeExecutors.size)
        } finally {
            attachMayFinish.countDown()
            callers.shutdownNow()
        }
    }

    @Test
    fun detachedPersistenceFailedShutdownClosesMailboxExecutorAndNotifiesManager() = runTest {
        val executor = CountingExecutorService()
        val persistence = FakePersistence()
        val closed = AtomicInteger()
        withRuntime(
            persistence = persistence,
            recorderExecutor = executor,
            retryDelaysMs = emptyList(),
            onClosed = { closed.incrementAndGet() },
        ) { fixture ->
            val host = FakeAndroidHost()
            val attachment = fixture.runtime.attachHost(host)
            fixture.startRecording(attachment)
            persistence.failWrites = true
            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            fixture.awaitState { it is DiaryRecordingState.PersistenceFailed }

            fixture.runtime.detachHost(attachment)
            advanceTimeBy(1_000)
            fixture.awaitCondition { executor.isShutdown && closed.get() == 1 }

            assertEquals(
                DiaryRecordingCommandResult.Ignored,
                fixture.runtime.submit(
                    attachment,
                    3,
                    DiaryRecordingCommand.RetryPersistence,
                ).await(),
            )
            assertEquals(1, executor.shutdownCalls.get())
        }
    }

    @Test
    fun lastHostDetachShutsDownAndFinalizesActiveSessionAfterOneSecond() = runTest {
        val executor = CountingExecutorService()
        withRuntime(recorderExecutor = executor) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(attachment)

            fixture.runtime.detachHost(attachment)
            advanceTimeBy(999)
            runCurrent()
            assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)
            assertFalse(executor.isShutdown)

            advanceTimeBy(1)
            fixture.awaitState { it == DiaryRecordingState.Idle }
            fixture.awaitCondition { executor.isShutdown }

            assertEquals(1, fixture.recorder.releaseCalls)
            assertEquals(1, fixture.persistence.completions.size)
            assertEquals(1, executor.shutdownCalls.get())
        }
    }

    @Test
    fun replacementAttachCancelsShutdownAndKeepsTheSameRuntimeSession() = runTest {
        val executor = CountingExecutorService()
        withRuntime(recorderExecutor = executor) { fixture ->
            val oldAttachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(oldAttachment)
            val sessionCreatedAt = fixture.store.state.value.createdAtMonotonicMs

            fixture.runtime.detachHost(oldAttachment)
            advanceTimeBy(500)
            val replacement = fixture.runtime.attachHost(FakeAndroidHost())
            advanceTimeBy(500)
            runCurrent()

            assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)
            assertEquals(sessionCreatedAt, fixture.store.state.value.createdAtMonotonicMs)
            assertEquals(0, fixture.recorder.releaseCalls)
            assertFalse(executor.isShutdown)

            fixture.runtime.detachHost(replacement)
            advanceTimeBy(999)
            runCurrent()
            assertFalse(executor.isShutdown)
            advanceTimeBy(1)
            fixture.awaitState { it == DiaryRecordingState.Idle }
            fixture.awaitCondition { executor.isShutdown }
        }
    }

    @Test
    fun lateOldGenerationTimerCannotCloseReplacementHostOrItsSession() = runTest {
        val executor = CountingExecutorService()
        withRuntime(recorderExecutor = executor) { fixture ->
            val oldAttachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.runtime.detachHost(oldAttachment)
            advanceTimeBy(999)
            val replacement = fixture.runtime.attachHost(FakeAndroidHost())

            advanceTimeBy(1)
            runCurrent()
            assertFalse(executor.isShutdown)
            fixture.startRecording(replacement)
            assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)

            fixture.runtime.detachHost(replacement)
            advanceTimeBy(1_000)
            fixture.awaitState { it == DiaryRecordingState.Idle }
            fixture.awaitCondition { executor.isShutdown }

            assertEquals(1, fixture.persistence.completions.size)
            assertEquals(1, executor.shutdownCalls.get())
        }
    }

    @Test
    fun stopFailureReleasesOncePersistsPartialAndHandsOffWithoutShutdown() = runTest {
        val executor = CountingExecutorService()
        val recorder = FakeRecorder().apply {
            onStop = { throw DiaryRecorderException(DiaryRecordingErrorCode.FINALIZE_FAILED) }
        }
        withRuntime(recorder = recorder, recorderExecutor = executor) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(attachment)

            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            fixture.awaitState { it == DiaryRecordingState.Idle }

            assertEquals(1, recorder.releaseCalls)
            val failure = fixture.persistence.failures.single()
            assertEquals(DiaryRecordingErrorCode.FINALIZE_FAILED, failure.errorCode)
            assertEquals(recorder.currentOutput, failure.output)
            assertFalse(executor.isShutdown)
        }
    }

    @Test
    fun stopFailureDuringShutdownReleasesPersistsAndClosesRecorderExecutorOnce() = runTest {
        val enteredStop = CountDownLatch(1)
        val finishStop = CountDownLatch(1)
        val executor = CountingExecutorService()
        val recorder = FakeRecorder().apply {
            onStop = {
                enteredStop.countDown()
                check(finishStop.await(2, TimeUnit.SECONDS))
                throw DiaryRecorderException(DiaryRecordingErrorCode.FINALIZE_FAILED)
            }
        }
        withRuntime(recorder = recorder, recorderExecutor = executor) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(attachment)

            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            assertTrue(enteredStop.await(2, TimeUnit.SECONDS))
            fixture.runtime.shutdown().await()
            finishStop.countDown()
            fixture.awaitState { it == DiaryRecordingState.Idle }
            fixture.awaitCondition { executor.isShutdown }

            assertEquals(1, recorder.releaseCalls)
            val failure = fixture.persistence.failures.single()
            assertEquals(DiaryRecordingErrorCode.FINALIZE_FAILED, failure.errorCode)
            assertEquals(recorder.currentOutput, failure.output)
            assertEquals(1, executor.shutdownCalls.get())
        }
    }

    @Test
    fun shutdownWhileStopIsInFlightDoesNotReleaseAgainAndClosesRecorderExecutorOnce() = runTest {
        val enteredStop = CountDownLatch(1)
        val finishStop = CountDownLatch(1)
        val executor = CountingExecutorService()
        val recorder = FakeRecorder().apply {
            onStop = {
                enteredStop.countDown()
                check(finishStop.await(2, TimeUnit.SECONDS))
                checkNotNull(currentOutput)
            }
        }
        withRuntime(recorder = recorder, recorderExecutor = executor) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(attachment)

            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            assertTrue(enteredStop.await(2, TimeUnit.SECONDS))
            fixture.runtime.shutdown().await()
            finishStop.countDown()
            fixture.awaitState { it == DiaryRecordingState.Idle }
            fixture.awaitCondition { executor.isShutdown }

            assertEquals(0, recorder.releaseCalls)
            assertEquals(1, fixture.persistence.completions.size)
            assertEquals(1, executor.shutdownCalls.get())
        }
    }

    @Test
    fun replacementHostOwnsActiveSessionAndLateOldDetachCannotPublishIdle() = runTest {
        val enteredStart = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val recorder = FakeRecorder().apply {
            onStart = { token, output ->
                enteredStart.countDown()
                check(releaseStart.await(2, TimeUnit.SECONDS))
                output.parentFile?.mkdirs()
                output.writeBytes(byteArrayOf(1, 2, 3))
                currentOutput = DiaryRecordingOutput(token, output, 3_000)
            }
        }
        withRuntime(recorder = recorder, realRecorderThread = true) { fixture ->
            val oldHost = FakeAndroidHost()
            val oldAttachment = fixture.runtime.attachHost(oldHost)
            fixture.runtime.submit(oldAttachment, 1, DiaryRecordingCommand.Start(11, 101)).await()
            assertTrue(enteredStart.await(2, TimeUnit.SECONDS))

            val newHost = FakeAndroidHost()
            val newAttachment = fixture.runtime.attachHost(newHost)
            fixture.runtime.detachHost(oldAttachment)
            releaseStart.countDown()
            fixture.awaitState { it is DiaryRecordingState.Recording }

            fixture.runtime.detachHost(oldAttachment)
            fixture.runtime.submit(
                DiaryRecordingResult.RecorderStarted(sessionToken = "late-old-session"),
            )
            runCurrent()

            assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)
            assertTrue(newHost.states.any { it is DiaryRecordingState.Recording })
            assertFalse(oldHost.states.any { it is DiaryRecordingState.Recording })
            assertEquals(0, oldHost.stopSelfResultCalls.size)
            assertTrue(fixture.runtime.isAttached(newAttachment))
        }
    }

    @Test
    fun replacementHostForegroundFailureTerminatesAndPersistsCurrentSession() = runTest {
        withRuntime { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(attachment)

            fixture.runtime.attachHost(
                FakeAndroidHost().apply {
                    foregroundError = DiaryRecordingErrorCode.FOREGROUND_SECURITY_DENIED
                },
            )
            fixture.awaitState { it == DiaryRecordingState.Idle }

            assertEquals(1, fixture.recorder.releaseCalls)
            assertEquals(
                DiaryRecordingErrorCode.FOREGROUND_SECURITY_DENIED,
                fixture.persistence.failures.single().errorCode,
            )
        }
    }

    @Test
    fun lateForegroundFailureFromReplacedHostCannotStopCurrentGeneration() = runTest {
        withRuntime { fixture ->
            val oldAttachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(oldAttachment)
            fixture.runtime.attachHost(FakeAndroidHost())

            fixture.runtime.submit(
                DiaryRecordingResult.ForegroundEntryFinished(
                    sessionToken = "session-token",
                    sessionCreatedAtMonotonicMs = 1_000L,
                    hostGeneration = oldAttachment.generation,
                    errorCode = DiaryRecordingErrorCode.FOREGROUND_SECURITY_DENIED,
                ),
            )
            runCurrent()

            assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)
            assertTrue(fixture.persistence.failures.isEmpty())
            assertEquals(0, fixture.recorder.releaseCalls)
        }
    }

    @Test
    fun sameSessionStartForegroundFailureCannotLeaveRecorderRunning() = runTest {
        withRuntime { fixture ->
            val host = FakeAndroidHost()
            val attachment = fixture.runtime.attachHost(host)
            fixture.startRecording(attachment)
            host.foregroundError = DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED

            assertEquals(
                DiaryRecordingCommandResult.AlreadyActive,
                fixture.runtime.submit(
                    attachment,
                    2,
                    DiaryRecordingCommand.Start(11, 101),
                ).await(),
            )
            fixture.awaitState { it == DiaryRecordingState.Idle }

            assertEquals(1, fixture.recorder.releaseCalls)
            assertEquals(
                DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED,
                fixture.persistence.failures.single().errorCode,
            )
        }
    }

    @Test
    fun terminalUsesItsStopStartIdAndCannotStopHostAfterNewerStartId() = runTest {
        val persistenceGate = CompletableDeferred<Unit>()
        val persistence = FakePersistence().apply {
            onComplete = { persistenceGate.await() }
        }
        withRuntime(persistence = persistence) { fixture ->
            val host = FakeAndroidHost()
            val attachment = fixture.runtime.attachHost(host)
            fixture.startRecording(attachment, startId = 1)

            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            runCurrent()
            host.latestStartId = 3
            val queuedStart = fixture.runtime.submit(
                attachment,
                3,
                DiaryRecordingCommand.Start(22, 202),
            )
            runCurrent()
            persistenceGate.complete(Unit)
            runCurrent()

            assertEquals(listOf(2), host.stopSelfResultCalls)
            assertEquals(0, host.stopForegroundCalls)
            assertEquals(DiaryRecordingCommandResult.Accepted, queuedStart.await())
            runCurrent()
            assertEquals(22, fixture.store.state.value.diaryId)
        }
    }

    @Test
    fun finishServiceRetriesReplacementHostCreatedDuringStopSelfResult() = runTest {
        withRuntime { fixture ->
            val replacement = FakeAndroidHost()
            val original = FakeAndroidHost()
            val attachment = fixture.runtime.attachHost(original)
            fixture.startRecording(attachment)
            original.onStopSelfResult = {
                fixture.runtime.attachHost(replacement)
                true
            }

            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            fixture.awaitState { it == DiaryRecordingState.Idle }

            assertEquals(listOf(2), original.stopSelfResultCalls)
            assertEquals(listOf(2), replacement.stopSelfResultCalls)
            assertEquals(1, replacement.stopForegroundCalls)
        }
    }

    @Test
    fun serviceDetachDoesNotShutdownRuntimeButExplicitShutdownFinalizesAndClosesExecutor() = runTest {
        val executor = Executors.newSingleThreadExecutor()
        withRuntime(recorderExecutor = executor) { fixture ->
            val host = FakeAndroidHost()
            val attachment = fixture.runtime.attachHost(host)
            fixture.startRecording(attachment)

            fixture.runtime.detachHost(attachment)
            runCurrent()
            assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)
            assertFalse(executor.isShutdown)

            fixture.runtime.shutdown().await()
            fixture.awaitCondition { fixture.persistence.completions.size == 1 }

            assertEquals(1, fixture.persistence.completions.size)
            assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
            assertTrue(executor.isShutdown)
        }
    }

    @Test
    fun stopQueuedAfterStartFailureCompletesOutputDecidedAtReleaseResultTime() = runTest {
        val enteredRelease = CountDownLatch(1)
        val releaseResult = CountDownLatch(1)
        val recorder = FakeRecorder().apply {
            onStart = { token, output ->
                output.parentFile?.mkdirs()
                output.writeBytes(byteArrayOf(7, 8, 9))
                currentOutput = DiaryRecordingOutput(token, output, 2_000)
                throw DiaryRecorderException(DiaryRecordingErrorCode.START_FAILED)
            }
            onRelease = {
                enteredRelease.countDown()
                check(releaseResult.await(2, TimeUnit.SECONDS))
                currentOutput
            }
        }
        withRuntime(recorder = recorder, realRecorderThread = true) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.runtime.submit(attachment, 1, DiaryRecordingCommand.Start(11, 101)).await()
            fixture.awaitCondition { enteredRelease.count == 0L }

            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            releaseResult.countDown()
            fixture.awaitState { it is DiaryRecordingState.Idle }

            assertEquals(1, fixture.persistence.completions.size)
            assertTrue(fixture.persistence.failures.isEmpty())
        }
    }

    @Test
    fun stopQueuedAfterStartFailureWithoutOutputPersistsUserCancelled() = runTest {
        val enteredRelease = CountDownLatch(1)
        val releaseResult = CountDownLatch(1)
        val recorder = FakeRecorder().apply {
            onStart = { _, _ -> throw DiaryRecorderException(DiaryRecordingErrorCode.START_FAILED) }
            onRelease = {
                enteredRelease.countDown()
                check(releaseResult.await(2, TimeUnit.SECONDS))
                null
            }
        }
        withRuntime(recorder = recorder, realRecorderThread = true) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.runtime.submit(attachment, 1, DiaryRecordingCommand.Start(11, 101)).await()
            fixture.awaitCondition { enteredRelease.count == 0L }

            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            releaseResult.countDown()
            fixture.awaitState { it is DiaryRecordingState.Idle }

            val failure = fixture.persistence.failures.single()
            assertEquals(DiaryRecordingErrorCode.USER_CANCELLED, failure.errorCode)
            assertNull(failure.output)
        }
    }

    @Test
    fun outputRequiresMatchingTokenCanonicalPathRegularFileAndContent() = runTest {
        withRuntime(nextSessionToken = { "session-token-123" }) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(attachment)
            val prepared = fixture.recorder.currentOutput ?: fail("Recorder did not prepare output")
            assertTrue(prepared.file.name.contains("session-token-123"))

            fixture.recorder.stopOutput = prepared.copy(
                file = File(prepared.file.parentFile, "./${prepared.file.name}"),
            )
            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            runCurrent()

            assertEquals("session-token-123", fixture.persistence.completions.single().output.sessionToken)
        }

        withRuntime(nextSessionToken = { "session-token-456" }) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(attachment)
            fixture.recorder.stopOutput = fixture.recorder.currentOutput?.copy(sessionToken = "wrong-token")
            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await()
            runCurrent()

            assertTrue(fixture.persistence.completions.isEmpty())
            assertEquals(DiaryRecordingErrorCode.FINALIZE_FAILED, fixture.persistence.failures.single().errorCode)
        }
    }

    @Test
    fun elapsedUsesInjectedMonotonicClock() = runTest {
        var monotonicMs = 10_000L
        withRuntime(monotonicNowMs = { monotonicMs }) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(attachment)

            monotonicMs = 12_500L
            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Pause).await()
            runCurrent()

            assertEquals(2_500L, assertIs<DiaryRecordingState.Paused>(fixture.store.state.value).elapsedMs)
        }
    }

    @Test
    fun sessionCreationTimestampUsesInjectedMonotonicClockAndSurvivesStateChanges() = runTest {
        var monotonicMs = 41_000L
        withRuntime(monotonicNowMs = { monotonicMs }) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.startRecording(attachment)

            val recording = assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)
            assertEquals(41_000L, recording.createdAtMonotonicMs)

            monotonicMs = 44_000L
            fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Pause).await()
            runCurrent()
            val paused = assertIs<DiaryRecordingState.Paused>(fixture.store.state.value)
            assertEquals(41_000L, paused.createdAtMonotonicMs)
            assertEquals(3_000L, paused.elapsedMs)
        }
    }

    @Test
    fun unexpectedThrowableDuringStartBecomesStablePersistedFailure() = runTest {
        val recorder = FakeRecorder().apply {
            onStart = { _, _ -> throw AssertionError("unexpected recorder failure") }
        }
        withRuntime(recorder = recorder, realRecorderThread = true) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.runtime.submit(attachment, 1, DiaryRecordingCommand.Start(11, 101)).await()
            fixture.awaitState { it is DiaryRecordingState.Idle }

            val failure = fixture.persistence.failures.single()
            assertEquals(DiaryRecordingErrorCode.START_FAILED, failure.errorCode)
            assertFalse(fixture.store.state.value is DiaryRecordingState.Starting)
        }

        val beginFailure = FakePersistence().apply {
            beginThrowable = AssertionError("unexpected persistence failure")
        }
        withRuntime(persistence = beginFailure) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.runtime.submit(attachment, 1, DiaryRecordingCommand.Start(12, 102)).await()
            runCurrent()

            assertEquals(DiaryRecordingErrorCode.START_FAILED, beginFailure.failures.single().errorCode)
            assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
        }

        withRuntime(
            outputFile = { _, _, _ -> throw AssertionError("unexpected output path failure") },
        ) { fixture ->
            val attachment = fixture.runtime.attachHost(FakeAndroidHost())
            fixture.runtime.submit(attachment, 1, DiaryRecordingCommand.Start(13, 103)).await()
            runCurrent()

            assertEquals(DiaryRecordingErrorCode.START_FAILED, fixture.persistence.failures.single().errorCode)
            assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
        }
    }

    private suspend fun TestScope.verifyClosingPlaceholderFailure(
        configureHost: FakeAndroidHost.() -> Unit,
        expectedErrorCode: String,
    ) {
        val store = DiaryRecordingStore()
        val persistence = FakePersistence()
        val root = createTempDirectory("diary-recording-placeholder-failure-test").toFile()
        val releaseStarted = CountDownLatch(1)
        val releaseMayFinish = CountDownLatch(1)
        val recorders = mutableListOf<FakeRecorder>()
        val manager = DiaryRecordingRuntimeManager(
            rejectionScope = backgroundScope,
            rejectStart = { start, errorCode ->
                persistence.fail(
                    diaryId = start.diaryId,
                    attachmentId = start.attachmentId,
                    output = null,
                    errorCode = errorCode,
                )
            },
        ) { onClosed ->
            val recorder = FakeRecorder().also(recorders::add).apply {
                onRelease = {
                    releaseStarted.countDown()
                    check(releaseMayFinish.await(2, TimeUnit.SECONDS))
                    currentOutput
                }
            }
            DiaryRecordingRuntime(
                store = store,
                recorder = recorder,
                persistence = persistence,
                outputFile = { diaryId, attachmentId, token ->
                    File(root, "$diaryId/$attachmentId-$token.m4a")
                },
                scope = backgroundScope,
                actorDispatcher = StandardTestDispatcher(testScheduler),
                recorderDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
                ioDispatcher = StandardTestDispatcher(testScheduler),
                monotonicNowMs = { testScheduler.currentTime },
                nextSessionToken = { "session-${recorders.size}" },
                retryDelaysMs = emptyList(),
                tickerIntervalMs = 1_000_000,
                onClosed = onClosed,
            )
        }

        val firstHost = FakeAndroidHost()
        manager.attachAndSubmit(firstHost, 1, DiaryRecordingCommand.Start(11, 101)).await()
        var attempts = 0
        while (store.state.value !is DiaryRecordingState.Recording && attempts++ < 200) {
            runCurrent()
            Thread.sleep(5)
        }
        manager.detachHost(firstHost)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(releaseStarted.await(2, TimeUnit.SECONDS))

        val failedHost = FakeAndroidHost().apply(configureHost)
        val failedStart = manager.attachAndSubmit(
            failedHost,
            2,
            DiaryRecordingCommand.Start(22, 202),
        )
        assertEquals(DiaryRecordingCommandResult.Ignored, manager.submit(DiaryRecordingCommand.Pause).await())
        assertEquals(DiaryRecordingCommandResult.Ignored, manager.submit(DiaryRecordingCommand.Stop).await())
        runCurrent()

        assertEquals(DiaryRecordingCommandResult.ForegroundRejected, failedStart.await())
        assertEquals(1, recorders.size)
        assertEquals(listOf(2), failedHost.stopSelfResultCalls)
        val failure = persistence.failures.single()
        assertEquals(22, failure.diaryId)
        assertEquals(202, failure.attachmentId)
        assertNull(failure.output)
        assertEquals(expectedErrorCode, failure.errorCode)

        releaseMayFinish.countDown()
        attempts = 0
        while (!recorders.single().let { it.releaseCalls > 0 } && attempts++ < 200) {
            runCurrent()
            Thread.sleep(5)
        }
        assertEquals(1, recorders.size)
    }

    private suspend fun TestScope.withRuntime(
        recorder: FakeRecorder = FakeRecorder(),
        persistence: FakePersistence = FakePersistence(),
        recorderExecutor: ExecutorService? = null,
        realRecorderThread: Boolean = false,
        retryDelaysMs: List<Long> = listOf(1_000, 2_000, 4_000),
        monotonicNowMs: () -> Long = { 1_000L },
        nextSessionToken: () -> String = { "session-token" },
        outputFile: ((Long, Long, String) -> File)? = null,
        onClosed: () -> Unit = {},
        block: suspend (RuntimeFixture) -> Unit,
    ) {
        val ownedExecutor = recorderExecutor ?: if (realRecorderThread) {
            Executors.newSingleThreadExecutor()
        } else {
            null
        }
        val recorderDispatcher: CoroutineDispatcher = ownedExecutor?.asCoroutineDispatcher()
            ?: StandardTestDispatcher(testScheduler)
        val store = DiaryRecordingStore()
        val root = createTempDirectory("diary-recording-runtime-test").toFile()
        val runtime = DiaryRecordingRuntime(
            store = store,
            recorder = recorder,
            persistence = persistence,
            outputFile = outputFile ?: { diaryId, attachmentId, token ->
                File(root, "$diaryId/$attachmentId-$token.m4a")
            },
            scope = backgroundScope,
            actorDispatcher = StandardTestDispatcher(testScheduler),
            recorderDispatcher = recorderDispatcher,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            monotonicNowMs = monotonicNowMs,
            nextSessionToken = nextSessionToken,
            retryDelaysMs = retryDelaysMs,
            tickerIntervalMs = 1_000_000,
            onClosed = onClosed,
        )
        try {
            block(RuntimeFixture(this, runtime, store, recorder, persistence))
        } finally {
            ownedExecutor?.shutdownNow()
        }
    }

    private data class RuntimeFixture(
        val testScope: TestScope,
        val runtime: DiaryRecordingRuntime,
        val store: DiaryRecordingStore,
        val recorder: FakeRecorder,
        val persistence: FakePersistence,
    ) {
        suspend fun startRecording(
            attachment: DiaryRecordingHostAttachment,
            startId: Int = 1,
        ) {
            assertEquals(
                DiaryRecordingCommandResult.Accepted,
                runtime.submit(attachment, startId, DiaryRecordingCommand.Start(11, 101)).await(),
            )
            awaitState { it is DiaryRecordingState.Recording }
        }

        fun awaitState(predicate: (DiaryRecordingState) -> Boolean) {
            awaitCondition { predicate(store.state.value) }
        }

        fun awaitCondition(predicate: () -> Boolean) {
            repeat(300) {
                testScope.runCurrent()
                if (predicate()) return
                Thread.sleep(5)
            }
            fail("Condition did not become true; state=${store.state.value}")
        }
    }

    private class FakeRecorder : DiaryRecorder {
        var currentOutput: DiaryRecordingOutput? = null
        var stopOutput: DiaryRecordingOutput? = null
        var releaseCalls = 0
        var stopCalls = 0
        var onStart: (String, File) -> Unit = { token, output ->
            output.parentFile?.mkdirs()
            output.writeBytes(byteArrayOf(1, 2, 3))
            currentOutput = DiaryRecordingOutput(token, output, 3_000)
        }
        var onRelease: () -> DiaryRecordingOutput? = { currentOutput }
        var onStop: () -> DiaryRecordingOutput = { stopOutput ?: checkNotNull(currentOutput) }

        override fun start(sessionToken: String, outputFile: File) {
            onStart(sessionToken, outputFile)
        }

        override fun pause() = Unit
        override fun resume() = Unit
        override fun stop(): DiaryRecordingOutput {
            stopCalls++
            return onStop()
        }
        override fun releasePreservingOutput(): DiaryRecordingOutput? {
            releaseCalls++
            return onRelease()
        }
    }

    private class FakePersistence : DiaryRecordingPersistence {
        val completions = mutableListOf<PersistenceCompletion>()
        val failures = mutableListOf<PersistenceFailure>()
        var failWrites = false
        var beginThrowable: Throwable? = null
        var onComplete: suspend (PersistenceCompletion) -> Unit = {}

        override suspend fun begin(diaryId: Long, attachmentId: Long) {
            beginThrowable?.let { throw it }
        }

        override suspend fun complete(
            diaryId: Long,
            attachmentId: Long,
            output: DiaryRecordingOutput,
        ) {
            val completion = PersistenceCompletion(diaryId, attachmentId, output)
            completions += completion
            onComplete(completion)
            if (failWrites) error("database locked")
        }

        override suspend fun fail(
            diaryId: Long,
            attachmentId: Long,
            output: DiaryRecordingOutput?,
            errorCode: String,
        ) {
            failures += PersistenceFailure(diaryId, attachmentId, output, errorCode)
            if (failWrites) error("database locked")
        }
    }

    private class FakeAndroidHost : DiaryRecordingAndroidHost {
        val states = mutableListOf<DiaryRecordingState>()
        val foregroundStates = mutableListOf<DiaryRecordingState>()
        val stopSelfResultCalls = mutableListOf<Int>()
        var latestStartId = 0
        var stopForegroundCalls = 0
        var foregroundError: String? = null
        var foregroundFailure: Throwable? = null
        var onStopSelfResult: ((Int) -> Boolean)? = null
        var onStateChanged: (DiaryRecordingState) -> Unit = {}

        override fun enterForeground(state: DiaryRecordingState): String? {
            foregroundStates += state
            foregroundFailure?.let { throw it }
            return foregroundError
        }

        override fun stateChanged(state: DiaryRecordingState) {
            states += state
            onStateChanged(state)
        }

        override fun stopForeground() {
            stopForegroundCalls++
        }

        override fun stopSelfResult(startId: Int): Boolean {
            stopSelfResultCalls += startId
            return onStopSelfResult?.invoke(startId) ?: (startId >= latestStartId)
        }
    }

    private class CountingExecutorService(
        private val delegate: ExecutorService = Executors.newSingleThreadExecutor(),
    ) : ExecutorService by delegate {
        val shutdownCalls = AtomicInteger()

        override fun shutdown() {
            shutdownCalls.incrementAndGet()
            delegate.shutdown()
        }
    }

    private data class PersistenceCompletion(
        val diaryId: Long,
        val attachmentId: Long,
        val output: DiaryRecordingOutput,
    )

    private data class PersistenceFailure(
        val diaryId: Long,
        val attachmentId: Long,
        val output: DiaryRecordingOutput?,
        val errorCode: String,
    )
}
