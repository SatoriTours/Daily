package com.dailysatori.core.recording

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryRecordingRuntimeTest {
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
    fun shutdownPersistenceFailureStaysRetryableOrAbandonableAfterRecorderExecutorCloses() = runTest {
        val executor = Executors.newSingleThreadExecutor()
        val persistence = FakePersistence().apply { failWrites = true }
        withRuntime(
            persistence = persistence,
            recorderExecutor = executor,
            retryDelaysMs = emptyList(),
        ) { fixture ->
            val host = FakeAndroidHost()
            val attachment = fixture.runtime.attachHost(host)
            fixture.startRecording(attachment)

            fixture.runtime.shutdown().await()
            fixture.awaitState { it is DiaryRecordingState.PersistenceFailed }
            assertTrue(executor.isShutdown)

            persistence.failWrites = false
            assertEquals(
                DiaryRecordingCommandResult.Accepted,
                fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.RetryPersistence).await(),
            )
            runCurrent()

            assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
            assertEquals(2, persistence.completions.size)
        }

        val discardExecutor = Executors.newSingleThreadExecutor()
        val discardPersistence = FakePersistence().apply { failWrites = true }
        withRuntime(
            persistence = discardPersistence,
            recorderExecutor = discardExecutor,
            retryDelaysMs = emptyList(),
        ) { fixture ->
            val host = FakeAndroidHost()
            val attachment = fixture.runtime.attachHost(host)
            fixture.startRecording(attachment)
            val output = checkNotNull(fixture.recorder.currentOutput).file

            fixture.runtime.shutdown().await()
            fixture.awaitState { it is DiaryRecordingState.PersistenceFailed }
            assertTrue(discardExecutor.isShutdown)

            discardPersistence.failWrites = false
            assertEquals(
                DiaryRecordingCommandResult.Accepted,
                fixture.runtime.submit(attachment, 2, DiaryRecordingCommand.Stop).await(),
            )
            runCurrent()

            assertFalse(output.exists())
            assertEquals(
                DiaryRecordingErrorCode.USER_CANCELLED,
                discardPersistence.failures.last().errorCode,
            )
            assertEquals(DiaryRecordingState.Idle, fixture.store.state.value)
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

    private suspend fun TestScope.withRuntime(
        recorder: FakeRecorder = FakeRecorder(),
        persistence: FakePersistence = FakePersistence(),
        recorderExecutor: ExecutorService? = null,
        realRecorderThread: Boolean = false,
        retryDelaysMs: List<Long> = listOf(1_000, 2_000, 4_000),
        monotonicNowMs: () -> Long = { 1_000L },
        nextSessionToken: () -> String = { "session-token" },
        outputFile: ((Long, Long, String) -> File)? = null,
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
        var onStart: (String, File) -> Unit = { token, output ->
            output.parentFile?.mkdirs()
            output.writeBytes(byteArrayOf(1, 2, 3))
            currentOutput = DiaryRecordingOutput(token, output, 3_000)
        }
        var onRelease: () -> DiaryRecordingOutput? = { currentOutput }

        override fun start(sessionToken: String, outputFile: File) {
            onStart(sessionToken, outputFile)
        }

        override fun pause() = Unit
        override fun resume() = Unit
        override fun stop(): DiaryRecordingOutput = stopOutput ?: checkNotNull(currentOutput)
        override fun releasePreservingOutput(): DiaryRecordingOutput? = onRelease()
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
        val stopSelfResultCalls = mutableListOf<Int>()
        var latestStartId = 0
        var stopForegroundCalls = 0

        override fun enterForeground(state: DiaryRecordingState): String? = null

        override fun stateChanged(state: DiaryRecordingState) {
            states += state
        }

        override fun stopForeground() {
            stopForegroundCalls++
        }

        override fun stopSelfResult(startId: Int): Boolean {
            stopSelfResultCalls += startId
            return startId >= latestStartId
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
