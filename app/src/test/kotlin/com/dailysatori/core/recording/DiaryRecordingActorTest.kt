package com.dailysatori.core.recording

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
class DiaryRecordingActorTest {
    @Test
    fun queuedStartRunsOnlyAfterPreviousTerminalPersistenceHandoff() = runTest {
        val persistenceGate = CompletableDeferred<Unit>()
        val persistence = FakePersistence().apply {
            onComplete = { persistenceGate.await() }
        }
        val fixture = fixture(persistence = persistence)

        assertEquals(
            DiaryRecordingCommandResult.Accepted,
            fixture.actor.submit(DiaryRecordingCommand.Start(11, 101)).await(),
        )
        runCurrent()
        assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)

        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        runCurrent()
        val queuedStart = fixture.actor.submit(DiaryRecordingCommand.Start(22, 202))
        runCurrent()

        assertFalse(queuedStart.isCompleted)
        assertEquals(11, fixture.store.state.value.diaryId)
        assertEquals(listOf("begin:11:101", "complete:11:101"), persistence.calls)

        persistenceGate.complete(Unit)
        runCurrent()

        assertEquals(DiaryRecordingCommandResult.Accepted, queuedStart.await())
        runCurrent()
        val recording = assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)
        assertEquals(22, recording.diaryId)
        assertEquals(202, recording.attachmentId)
        assertEquals(
            listOf("begin:11:101", "complete:11:101", "begin:22:202"),
            persistence.calls,
        )
    }

    @Test
    fun stopWhileStartingWithoutUsableOutputPersistsUserCancellationWithoutPath() = runTest {
        val enteredStart = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val recorder = FakeRecorder().apply {
            onStart = {
                enteredStart.countDown()
                check(releaseStart.await(2, TimeUnit.SECONDS))
                throw DiaryRecorderException(DiaryRecordingErrorCode.START_FAILED)
            }
        }
        withRealRecorderFixture(recorder) { fixture ->
            assertEquals(
                DiaryRecordingCommandResult.Accepted,
                fixture.actor.submit(DiaryRecordingCommand.Start(11, 101)).await(),
            )
            assertTrue(enteredStart.await(2, TimeUnit.SECONDS))

            fixture.actor.submit(DiaryRecordingCommand.Stop).await()
            assertIs<DiaryRecordingState.Stopping>(fixture.store.state.value)
            releaseStart.countDown()
            fixture.awaitState { it == DiaryRecordingState.Idle }

            val failure = fixture.persistence.failures.single()
            assertEquals(DiaryRecordingErrorCode.USER_CANCELLED, failure.errorCode)
            assertNull(failure.output)
        }
    }

    @Test
    fun stopWhileStartingWithUsableOutputCompletesAndQueuesTranscription() = runTest {
        val enteredStart = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val recorder = FakeRecorder().apply {
            onStart = { output ->
                output.parentFile?.mkdirs()
                output.writeBytes(byteArrayOf(1, 2, 3))
                enteredStart.countDown()
                check(releaseStart.await(2, TimeUnit.SECONDS))
            }
        }
        withRealRecorderFixture(recorder) { fixture ->
            fixture.actor.submit(DiaryRecordingCommand.Start(11, 101)).await()
            assertTrue(enteredStart.await(2, TimeUnit.SECONDS))

            fixture.actor.submit(DiaryRecordingCommand.Stop).await()
            releaseStart.countDown()
            fixture.awaitState { it == DiaryRecordingState.Idle }

            val completion = fixture.persistence.completions.single()
            assertEquals(11, completion.diaryId)
            assertEquals(101, completion.attachmentId)
            assertTrue(completion.output.file.isFile)
            assertTrue(completion.output.file.length() > 0)
            assertTrue(fixture.persistence.failures.isEmpty())
        }
    }

    @Test
    fun automaticPersistenceUsesInitialAttemptThenOneTwoAndFourSecondRetries() = runTest {
        val persistence = FakePersistence(completeFailuresRemaining = 3)
        val fixture = fixture(persistence = persistence)
        fixture.startRecording()

        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        runCurrent()
        assertEquals(1, persistence.completeAttempts)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, persistence.completeAttempts)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, persistence.completeAttempts)
        advanceTimeBy(4_000)
        runCurrent()

        assertEquals(4, persistence.completeAttempts)
        assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
        assertEquals(1, fixture.host.stopForegroundCalls)
    }

    @Test
    fun explicitPersistenceRetryRunsAnotherBoundedCycleAndKeepsForeground() = runTest {
        val persistence = FakePersistence(completeFailuresRemaining = Int.MAX_VALUE)
        val fixture = fixture(persistence = persistence)
        fixture.startRecording()

        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        advanceTimeBy(7_000)
        runCurrent()

        assertEquals(4, persistence.completeAttempts)
        assertPersistenceFailed(fixture.store.state.value)
        assertEquals(0, fixture.host.stopForegroundCalls)

        fixture.actor.submit(DiaryRecordingCommand.RetryPersistence).await()
        advanceTimeBy(7_000)
        runCurrent()

        assertEquals(8, persistence.completeAttempts)
        assertPersistenceFailed(fixture.store.state.value)
        assertEquals(0, fixture.host.stopForegroundCalls)
    }

    @Test
    fun explicitPersistenceRetryRequiresAttachedHostAndOpenRuntime() = runTest {
        val persistence = FakePersistence(completeFailuresRemaining = Int.MAX_VALUE)
        val fixture = fixture(persistence = persistence)
        fixture.startRecording()
        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        advanceTimeBy(7_000)
        runCurrent()
        assertPersistenceFailed(fixture.store.state.value)
        assertEquals(4, persistence.completeAttempts)

        fixture.host.attached = false
        assertEquals(
            DiaryRecordingCommandResult.Ignored,
            fixture.actor.submit(DiaryRecordingCommand.RetryPersistence).await(),
        )
        advanceTimeBy(7_000)
        runCurrent()

        assertEquals(4, persistence.completeAttempts)
        assertPersistenceFailed(fixture.store.state.value)
    }

    @Test
    fun persistenceFailedShutdownClosesMailboxAndInvokesOnClosed() = runTest {
        val persistence = FakePersistence(completeFailuresRemaining = Int.MAX_VALUE)
        val closed = AtomicInteger()
        val fixture = fixture(
            persistence = persistence,
            onClosed = { closed.incrementAndGet() },
        )
        fixture.startRecording()
        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        advanceTimeBy(7_000)
        runCurrent()
        assertPersistenceFailed(fixture.store.state.value)

        assertEquals(
            DiaryRecordingCommandResult.Accepted,
            fixture.actor.submit(DiaryRecordingCommand.Shutdown).await(),
        )
        runCurrent()

        assertEquals(1, closed.get())
        assertEquals(
            DiaryRecordingCommandResult.Ignored,
            fixture.actor.submit(DiaryRecordingCommand.RetryPersistence).await(),
        )
        assertPersistenceFailed(fixture.store.state.value)
    }

    @Test
    fun stopInPersistenceFailureDiscardsCurrentOutputAndPersistsCancellation() = runTest {
        val persistence = FakePersistence(completeFailuresRemaining = Int.MAX_VALUE)
        val output = tempOutput()
        val fixture = fixture(persistence = persistence, output = output)
        fixture.startRecording()
        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        advanceTimeBy(7_000)
        runCurrent()
        assertPersistenceFailed(fixture.store.state.value)
        assertTrue(output.exists())

        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        runCurrent()

        assertFalse(output.exists())
        val discarded = persistence.failures.single()
        assertEquals(DiaryRecordingErrorCode.USER_CANCELLED, discarded.errorCode)
        assertNull(discarded.output)
        assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
    }

    @Test
    fun completionFileIsRevalidatedAfterDeletionBeforeEveryRetry() = runTest {
        val persistence = FakePersistence().apply {
            onComplete = { completion ->
                completion.output.file.delete()
                error("database locked")
            }
        }
        val fixture = fixture(persistence = persistence)
        fixture.startRecording()

        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        advanceTimeBy(7_000)
        runCurrent()

        assertEquals(1, persistence.completeAttempts)
        assertPersistenceFailed(fixture.store.state.value)
    }

    @Test
    fun completionFileIsRevalidatedAfterTruncationBeforeEveryRetry() = runTest {
        val persistence = FakePersistence().apply {
            onComplete = { completion ->
                completion.output.file.writeBytes(byteArrayOf())
                error("database locked")
            }
        }
        val fixture = fixture(persistence = persistence)
        fixture.startRecording()

        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        advanceTimeBy(7_000)
        runCurrent()

        assertEquals(1, persistence.completeAttempts)
        assertPersistenceFailed(fixture.store.state.value)
    }

    @Test
    fun staleSameNameOutputIsRemovedBeforeRecorderPreparation() = runTest {
        val output = tempOutput().apply {
            parentFile?.mkdirs()
            writeText("stale")
            setLastModified(1)
        }
        var staleFileWasAbsentAtStart = false
        val recorder = FakeRecorder().apply {
            onStart = {
                staleFileWasAbsentAtStart = !it.exists()
                it.writeBytes(byteArrayOf(9))
            }
        }
        val fixture = fixture(recorder = recorder, output = output)

        fixture.startRecording()

        assertTrue(staleFileWasAbsentAtStart)
        assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)
    }

    @Test
    fun staleSameNameOutputIsRejectedWhenItCannotBeRemoved() = runTest {
        val output = tempOutput().apply {
            mkdirs()
            resolve("child").writeText("stale")
        }
        val recorder = FakeRecorder()
        val fixture = fixture(recorder = recorder, output = output)

        fixture.actor.submit(DiaryRecordingCommand.Start(11, 101)).await()
        runCurrent()

        assertTrue(recorder.calls.isEmpty())
        val failure = fixture.persistence.failures.single()
        assertEquals(DiaryRecordingErrorCode.STORAGE_FAILED, failure.errorCode)
        assertNull(failure.output)
        assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
    }

    @Test
    fun shutdownSubmissionDoesNotWaitForBlockedRecorderAndReleaseUsesRecorderThread() = runTest {
        val enteredStart = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val recorder = FakeRecorder().apply {
            onStart = {
                enteredStart.countDown()
                check(releaseStart.await(2, TimeUnit.SECONDS))
            }
        }
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "diary-recorder-test")
        }.asCoroutineDispatcher()
        val fixture = fixture(recorder = recorder, recorderDispatcher = executor)
        try {
            fixture.actor.submit(DiaryRecordingCommand.Start(11, 101)).await()
            assertTrue(enteredStart.await(2, TimeUnit.SECONDS))

            val submittedAt = System.nanoTime()
            val shutdown = fixture.actor.submit(DiaryRecordingCommand.Shutdown)
            val submitElapsedMs = (System.nanoTime() - submittedAt) / 1_000_000
            runCurrent()

            assertTrue(submitElapsedMs < 100, "Shutdown submit blocked for ${submitElapsedMs}ms")
            assertTrue(shutdown.isCompleted)
            assertFalse(recorder.calls.contains("release"))

            releaseStart.countDown()
            fixture.awaitCondition { recorder.calls.contains("release") }
            assertTrue(recorder.operationThreads.all { it.startsWith("diary-recorder-test") })
            assertTrue(recorder.operationThreads.all { it != Thread.currentThread().name })
        } finally {
            releaseStart.countDown()
            executor.close()
        }
    }

    @Test
    fun commandQueuedAfterShutdownCannotStartANewSession() = runTest {
        val fixture = fixture()

        val shutdown = fixture.actor.submit(DiaryRecordingCommand.Shutdown)
        val lateStart = fixture.actor.submit(DiaryRecordingCommand.Start(22, 202))
        runCurrent()

        assertEquals(DiaryRecordingCommandResult.Accepted, shutdown.await())
        assertEquals(DiaryRecordingCommandResult.Ignored, lateStart.await())
        assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
        assertTrue(fixture.recorder.calls.isEmpty())
    }

    @Test
    fun lateRecorderResultWithOldSessionTokenCannotMutateNewSession() = runTest {
        val fixture = fixture()
        fixture.startRecording(diaryId = 11, attachmentId = 101)
        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        runCurrent()
        fixture.startRecording(diaryId = 22, attachmentId = 202)
        val beforeLateResult = fixture.store.state.value

        fixture.actor.submit(
            DiaryRecordingResult.RecorderStarted(
                sessionToken = "1",
            ),
        )
        runCurrent()

        assertEquals(beforeLateResult, fixture.store.state.value)
        assertEquals(22, fixture.store.state.value.diaryId)
        assertEquals(202, fixture.store.state.value.attachmentId)
    }

    @Test
    fun sameSessionStartIsIdempotentAndDifferentSessionStartIsBusy() = runTest {
        val fixture = fixture()

        assertEquals(
            DiaryRecordingCommandResult.Accepted,
            fixture.actor.submit(DiaryRecordingCommand.Start(11, 101)).await(),
        )
        runCurrent()
        assertEquals(
            DiaryRecordingCommandResult.AlreadyActive,
            fixture.actor.submit(DiaryRecordingCommand.Start(11, 101)).await(),
        )
        assertEquals(
            DiaryRecordingCommandResult.Busy,
            fixture.actor.submit(DiaryRecordingCommand.Start(22, 202)).await(),
        )

        assertEquals(1, fixture.recorder.calls.count { it == "start" })
        assertEquals(11, fixture.store.state.value.diaryId)
        assertEquals(101, fixture.store.state.value.attachmentId)
    }

    @Test
    fun pauseResumeAndStopUseSerializedRecorderOperations() = runTest {
        val fixture = fixture()
        fixture.startRecording()

        fixture.actor.submit(DiaryRecordingCommand.Pause).await()
        runCurrent()
        assertIs<DiaryRecordingState.Paused>(fixture.store.state.value)

        fixture.actor.submit(DiaryRecordingCommand.Resume).await()
        runCurrent()
        assertIs<DiaryRecordingState.Recording>(fixture.store.state.value)

        fixture.actor.submit(DiaryRecordingCommand.Stop).await()
        runCurrent()
        assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
        assertEquals(listOf("start", "pause", "resume", "stop"), fixture.recorder.calls)
    }

    private fun TestScope.fixture(
        recorder: FakeRecorder = FakeRecorder(),
        persistence: FakePersistence = FakePersistence(),
        output: File = tempOutput(),
        recorderDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        onClosed: () -> Unit = {},
    ): ActorFixture {
        val store = DiaryRecordingStore()
        val host = FakeHost()
        var nextToken = 0L
        val actor = DiaryRecordingActor(
            store = store,
            recorder = recorder,
            persistence = persistence,
            outputFile = { _, _, _ -> output },
            host = host,
            scope = backgroundScope,
            actorDispatcher = StandardTestDispatcher(testScheduler),
            recorderDispatcher = recorderDispatcher,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            monotonicNowMs = { 1_000 },
            nextSessionToken = { (++nextToken).toString() },
            retryDelaysMs = listOf(1_000, 2_000, 4_000),
            tickerIntervalMs = 1_000_000,
            onClosed = onClosed,
        )
        return ActorFixture(this, actor, store, recorder, persistence, host)
    }

    private suspend fun TestScope.withRealRecorderFixture(
        recorder: FakeRecorder,
        block: suspend (ActorFixture) -> Unit,
    ) {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "diary-recorder-test")
        }.asCoroutineDispatcher()
        try {
            block(fixture(recorder = recorder, recorderDispatcher = executor))
        } finally {
            executor.close()
        }
    }

    private data class ActorFixture(
        val testScope: TestScope,
        val actor: DiaryRecordingActor,
        val store: DiaryRecordingStore,
        val recorder: FakeRecorder,
        val persistence: FakePersistence,
        val host: FakeHost,
    ) {
        suspend fun startRecording(diaryId: Long = 11, attachmentId: Long = 101) {
            assertEquals(
                DiaryRecordingCommandResult.Accepted,
                actor.submit(DiaryRecordingCommand.Start(diaryId, attachmentId)).await(),
            )
            testScope.runCurrent()
            assertIs<DiaryRecordingState.Recording>(store.state.value)
        }

        fun awaitState(predicate: (DiaryRecordingState) -> Boolean) {
            awaitCondition { predicate(store.state.value) }
        }

        fun awaitCondition(predicate: () -> Boolean) {
            repeat(200) {
                testScope.runCurrent()
                if (predicate()) return
                Thread.sleep(5)
            }
            fail("Condition did not become true; state=${store.state.value}")
        }
    }

    private class FakeRecorder : DiaryRecorder {
        val calls = mutableListOf<String>()
        val operationThreads = mutableListOf<String>()
        var onStart: (File) -> Unit = { output ->
            output.parentFile?.mkdirs()
            output.writeBytes(byteArrayOf(1, 2, 3))
        }
        private var sessionToken: String? = null
        private var output: File? = null

        override fun start(sessionToken: String, outputFile: File) {
            record("start")
            this.sessionToken = sessionToken
            output = outputFile
            onStart(outputFile)
        }

        override fun pause() {
            record("pause")
        }

        override fun resume() {
            record("resume")
        }

        override fun stop(): DiaryRecordingOutput {
            record("stop")
            return DiaryRecordingOutput(
                checkNotNull(sessionToken),
                checkNotNull(output),
                durationMs = 3_000,
            )
        }

        override fun releasePreservingOutput(): DiaryRecordingOutput? {
            record("release")
            return output?.takeIf { it.isFile && it.length() > 0 }
                ?.let { DiaryRecordingOutput(checkNotNull(sessionToken), it, durationMs = 3_000) }
        }

        private fun record(operation: String) {
            synchronized(this) {
                calls += operation
                operationThreads += Thread.currentThread().name
            }
        }
    }

    private class FakePersistence(
        var completeFailuresRemaining: Int = 0,
    ) : DiaryRecordingPersistence {
        val calls = mutableListOf<String>()
        val completions = mutableListOf<PersistenceCompletion>()
        val failures = mutableListOf<PersistenceFailure>()
        var completeAttempts = 0
        var onComplete: suspend (PersistenceCompletion) -> Unit = {}

        override suspend fun begin(diaryId: Long, attachmentId: Long) {
            calls += "begin:$diaryId:$attachmentId"
        }

        override suspend fun complete(
            diaryId: Long,
            attachmentId: Long,
            output: DiaryRecordingOutput,
        ) {
            completeAttempts++
            val completion = PersistenceCompletion(diaryId, attachmentId, output)
            completions += completion
            calls += "complete:$diaryId:$attachmentId"
            onComplete(completion)
            if (completeFailuresRemaining > 0) {
                completeFailuresRemaining--
                error("database locked")
            }
        }

        override suspend fun fail(
            diaryId: Long,
            attachmentId: Long,
            output: DiaryRecordingOutput?,
            errorCode: String,
        ) {
            failures += PersistenceFailure(diaryId, attachmentId, output, errorCode)
            calls += "fail:$diaryId:$attachmentId:$errorCode"
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

    private class FakeHost : DiaryRecordingActorHost {
        val states = mutableListOf<DiaryRecordingState>()
        var stopForegroundCalls = 0
        var stopServiceCalls = 0
        var attached = true

        override fun requestForeground(
            sessionToken: String,
            sessionCreatedAtMonotonicMs: Long,
            state: DiaryRecordingState,
        ) = Unit

        override fun isCurrentHostGeneration(generation: Long): Boolean = true

        override fun hasAttachedHost(): Boolean = attached

        override fun stateChanged(state: DiaryRecordingState) {
            states += state
        }

        override fun finishService(startId: Int): Boolean {
            stopForegroundCalls++
            stopServiceCalls++
            return true
        }
    }

    private fun assertPersistenceFailed(state: DiaryRecordingState) {
        assertIs<DiaryRecordingState.PersistenceFailed>(state)
    }

    private fun tempOutput(): File =
        File(createTempDirectory("diary-recording-actor-test").toFile(), "voice.m4a")
}
