package com.dailysatori.core.worker

import androidx.work.ListenableWorker
import androidx.work.Operation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.ListenableFuture
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSink
import com.dailysatori.service.diagnostics.Diagnostics
import com.dailysatori.service.diagnostics.SafeDiagnosticEvent
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The enqueue exit is shared by timeout and transport failure; the run budget is finite and a
 * cancellation must never be recorded as an enqueue timeout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class UnifiedNewsFailureExitTest {
    private val events = mutableListOf<SafeDiagnosticEvent>()
    private var previous = DiagnosticLog.diagnostics

    @Before
    fun installSink() {
        events.clear()
        DiagnosticLog.diagnostics = Diagnostics(DiagnosticSink { event -> events += event; true })
    }

    @After
    fun restoreSink() {
        DiagnosticLog.diagnostics = previous
    }

    @Test
    fun enqueueTimeoutIsRecordedAsATimeoutAndReturnsFalse() = runBlocking {
        val scheduler = UnifiedNewsScheduler(
            context = RuntimeEnvironment.getApplication(),
            enqueueTimeoutMs = 30,
            enqueueOperation = { _, _ -> FakeOperation(NeverCompletingFuture()) },
        )

        assertFalse(scheduler.scheduleNext(Clock.System.now()))

        val event = events.single { it.attributes["unifiedNewsStage"] == "enqueue" }
        assertEquals("timeout", event.attributes["unifiedNewsFailure"])
        assertEquals("window", event.attributes["unifiedNewsTarget"])
        assertEquals(DiagnosticCode.OPERATION_FAILED, event.eventCode)
    }

    @Test
    fun cancellationIsNotRecordedAsAnEnqueueTimeout() = runBlocking {
        val scheduler = UnifiedNewsScheduler(
            context = RuntimeEnvironment.getApplication(),
            enqueueTimeoutMs = 5_000,
            enqueueOperation = { _, _ -> suspendCancellableCoroutine<Operation> { } },
        )

        val job = launch { scheduler.scheduleNext(Clock.System.now()) }
        yield()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(events.none { it.attributes["unifiedNewsStage"] == "enqueue" })
    }

    @Test
    fun runBudgetAllowsFourRetriesAndFailsTheFifthAttempt() {
        assertEquals(ListenableWorker.Result.retry(), unifiedNewsFailureResult(0, "run"))
        assertEquals(ListenableWorker.Result.retry(), unifiedNewsFailureResult(3, "run"))
        assertEquals(ListenableWorker.Result.failure(), unifiedNewsFailureResult(4, "run"))

        val exhausted = events.single { it.attributes["unifiedNewsFailure"] == "budget_exhausted" }
        assertEquals("5", exhausted.attributes["attempt"])
        assertEquals("due", exhausted.attributes["unifiedNewsTarget"])
    }

    private class NeverCompletingFuture : ListenableFuture<Operation.State.SUCCESS> {
        override fun cancel(mayInterruptIfRunning: Boolean) = false
        override fun isCancelled() = false
        override fun isDone() = false
        override fun get(): Operation.State.SUCCESS = throw java.util.concurrent.CancellationException()
        override fun get(timeout: Long, unit: TimeUnit): Operation.State.SUCCESS = get()
        override fun addListener(listener: Runnable, executor: Executor) = Unit
    }

    private class FakeOperation(private val result: ListenableFuture<Operation.State.SUCCESS>) : Operation {
        override fun getState(): androidx.lifecycle.LiveData<Operation.State> =
            androidx.lifecycle.MutableLiveData<Operation.State>()
        override fun getResult(): ListenableFuture<Operation.State.SUCCESS> = result
    }
}
