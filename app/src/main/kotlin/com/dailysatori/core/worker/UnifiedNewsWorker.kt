package com.dailysatori.core.worker

import android.content.Context
import com.dailysatori.core.startup.StartupWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailysatori.core.task.UnifiedNewsGenerateTaskHandler
import com.dailysatori.core.task.enqueueUnifiedNewsRefresh
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLevel
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.unifiednews.UnifiedNewsGenerationResult
import com.dailysatori.service.unifiednews.UnifiedNewsTickDecision
import com.dailysatori.service.unifiednews.UnifiedNewsWindow
import com.dailysatori.service.unifiednews.dueUnifiedNewsWindows
import com.dailysatori.service.unifiednews.nextUnifiedNewsWindow
import com.dailysatori.service.unifiednews.unifiedNewsTickDecision
import com.dailysatori.shared.db.Unified_news_summary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.context.GlobalContext
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * One unique work per window. The running window work never shares its name with the work this
 * scheduler arms next, so arming the next window can use KEEP and cannot cancel the caller.
 */
internal enum class UnifiedNewsEnqueueOutcome { SUCCESS, TIMEOUT, FAILED }

class UnifiedNewsScheduler(
    private val context: Context,
    private val enqueueTimeoutMs: Long = ENQUEUE_TIMEOUT_MS,
    private val enqueueOperation: suspend (String, OneTimeWorkRequest) -> androidx.work.Operation = { name, request ->
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    },
) {
    suspend fun ensureScheduled(
        now: Instant = Clock.System.now(),
        latestSuccessful: () -> Unified_news_summary? = { null },
    ): Boolean {
        cancelLegacyScheduling()
        val armedNext = enqueueUnique(
            unifiedNewsWindowWorkName(nextUnifiedNewsWindow(now)),
            buildUnifiedNewsNextWorkRequest(now),
        )
        val armedDue = if (unifiedNewsNeedsCatchUp(now, latestSuccessful())) {
            enqueueUnique(
                unifiedNewsCatchUpWorkName(latestDueUnifiedNewsWindow(now)),
                buildUnifiedNewsCatchUpWorkRequest(latestDueUnifiedNewsWindow(now).endMs),
            )
        } else {
            true
        }
        return armedNext && armedDue
    }

    suspend fun scheduleNext(now: Instant = Clock.System.now()): Boolean = enqueueUnique(
        unifiedNewsWindowWorkName(nextUnifiedNewsWindow(now)),
        buildUnifiedNewsNextWorkRequest(now),
    )

    /** Suspends on the Operation result: enqueue is asynchronous, so the call alone proves nothing. */
    private suspend fun enqueueUnique(name: String, request: OneTimeWorkRequest): Boolean {
        val outcome = try {
            val operation = enqueueOperation(name, request)
            awaitOperationWithTimeout(operation, enqueueTimeoutMs)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            recordEnqueueFailure(name, UnifiedNewsEnqueueOutcome.FAILED, error)
            return false
        }
        if (outcome == UnifiedNewsEnqueueOutcome.SUCCESS) return true
        // Timeout and transport failure share one redacted exit.
        recordEnqueueFailure(name, outcome, null)
        return false
    }

    /** Pre-5.1.66 builds armed a single shared name; drop it so it cannot fire a second window. */
    private fun cancelLegacyScheduling() {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(LegacyUnifiedNewsWorkName) }
    }

    private fun recordEnqueueFailure(
        target: String,
        outcome: UnifiedNewsEnqueueOutcome,
        error: Throwable?,
    ) {
        DiagnosticLog.diagnostics.emit(
            DiagnosticCode.OPERATION_FAILED, DiagnosticSource.APP, DiagnosticLevel.ERROR,
            fields = mapOf(
                "unifiedNewsStage" to "enqueue",
                "unifiedNewsTarget" to if (target.startsWith("unified-news-window")) "window" else "due",
                "unifiedNewsFailure" to if (outcome == UnifiedNewsEnqueueOutcome.TIMEOUT) "timeout" else "error",
            ),
            error = error,
        )
    }

    private companion object {
        const val ENQUEUE_TIMEOUT_MS = 10_000L
    }
}

/**
 * Cancellable bridge to the enqueue result. A timeout and a failed future are distinguishable,
 * while a caller cancellation propagates instead of being recorded as a timeout.
 */
internal suspend fun awaitOperationWithTimeout(
    operation: androidx.work.Operation,
    timeoutMs: Long,
): UnifiedNewsEnqueueOutcome = withTimeoutOrNull(timeoutMs) {
    if (awaitOperationResult(operation)) UnifiedNewsEnqueueOutcome.SUCCESS else UnifiedNewsEnqueueOutcome.FAILED
} ?: UnifiedNewsEnqueueOutcome.TIMEOUT

/** Cancellable bridge to WorkManager's async enqueue result; no blocking get(). */
private suspend fun awaitOperationResult(operation: androidx.work.Operation): Boolean =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { operation.result.cancel(false) }
        operation.result.addListener(
            {
                val succeeded = runCatching { operation.result.get() }.isSuccess
                if (continuation.isActive) continuation.resume(succeeded)
            },
            Executor { it.run() },
        )
    }

internal const val LegacyUnifiedNewsWorkName = "unified-news-next"

internal fun unifiedNewsWindowWorkName(next: com.dailysatori.service.unifiednews.NextUnifiedNewsWindow): String =
    "unified-news-window-${next.key.value}-${next.dueAt.toLocalDateTime(TimeZone.currentSystemDefault()).date}"

internal fun unifiedNewsCatchUpWorkName(window: UnifiedNewsWindow): String =
    "unified-news-due-${window.key.value}-${window.summaryDate}"

/** Latest window whose due time already passed; the only window a startup check may catch up. */
fun latestDueUnifiedNewsWindow(
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): UnifiedNewsWindow = dueUnifiedNewsWindows(now, timeZone).maxByOrNull { it.endMs }
    ?: error("No unified news window is due yet")

/**
 * True when the newest due window produced no summary yet. Only the most recent window is
 * considered, so a long outage cannot replay historical windows.
 */
fun unifiedNewsNeedsCatchUp(
    now: Instant,
    latestSuccessful: Unified_news_summary?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean {
    val due = latestDueUnifiedNewsWindow(now, timeZone)
    if (latestSuccessful == null) return true
    val generatedAt = latestSuccessful.generated_at ?: latestSuccessful.updated_at
    return generatedAt < due.endMs
}

class UnifiedNewsWorker(
    appContext: Context,
    params: WorkerParameters,
) : StartupWorker(appContext, params) {
    override suspend fun doReadyWork(): Result {
        val scheduler = UnifiedNewsScheduler(applicationContext)
        val mode = unifiedNewsWorkerMode(inputData.getString(KEY_MODE))
        val tick = inputData.getLong(KEY_TICK_DUE_AT, -1L).takeIf { it > 0L }
        return try {
            val result = when (mode) {
                UnifiedNewsWorkerMode.DUE -> runDueTick(tick)
                UnifiedNewsWorkerMode.BACKFILL -> enqueueDailySummaryTask(
                    mode = UnifiedNewsWorkerMode.BACKFILL, force = false, tickDueAtMs = null,
                )
                else -> Result.failure()
            }
            // Single owner for arming: the run already handed its tick to the task queue.
            if (!scheduler.scheduleNext(Clock.System.now())) {
                return unifiedNewsFailureResult(runAttemptCount, stage = "schedule_next")
            }
            result
        } catch (cancelled: CancellationException) {
            // A cancelled run must not start more work; startup recovery re-arms the window.
            throw cancelled
        } catch (_: Exception) {
            // Same failure exit as an enqueue timeout; no unbounded retrying.
            unifiedNewsFailureResult(runAttemptCount, stage = "run")
        }
    }

    /**
     * Execution-time idempotency: a late work must not regenerate a tick that already produced a
     * summary, and a work older than the newest due tick is left to that newer tick.
     */
    private fun runDueTick(tick: Long?): Result {
        val now = Clock.System.now()
        val latestDue = latestDueUnifiedNewsWindow(now).endMs
        val latestSuccessful = GlobalContext.get()
            .get<UnifiedNewsSummaryRepository>()
            .getLatestSuccessful()
            ?.let { it.generated_at ?: it.updated_at }
        val decision = unifiedNewsTickDecision(tick, latestDue, latestSuccessful)
        DiagnosticLog.diagnostics.emit(
            DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.APP,
            fields = mapOf(
                "unifiedNewsStage" to "tick",
                "unifiedNewsDecision" to when (decision) {
                    UnifiedNewsTickDecision.GENERATE -> "generate"
                    UnifiedNewsTickDecision.SKIP_STALE -> "skip_stale"
                    UnifiedNewsTickDecision.SKIP_GENERATED -> "skip_generated"
                },
            ),
        )
        if (decision != UnifiedNewsTickDecision.GENERATE) return Result.success()
        return enqueueDailySummaryTask(
            mode = UnifiedNewsWorkerMode.DUE, force = true, tickDueAtMs = tick ?: latestDue,
        )
    }

    private fun enqueueDailySummaryTask(
        mode: UnifiedNewsWorkerMode,
        force: Boolean,
        tickDueAtMs: Long?,
    ): Result {
        val asyncTaskRepo = GlobalContext.get().get<AsyncTaskRepository>()
        val asyncTaskScheduler = AsyncTaskScheduler(applicationContext)
        val modeValue = when (mode) {
            UnifiedNewsWorkerMode.DUE -> UnifiedNewsGenerateTaskHandler.MODE_DUE
            UnifiedNewsWorkerMode.BACKFILL -> UnifiedNewsGenerateTaskHandler.MODE_BACKFILL
        }
        enqueueUnifiedNewsRefresh(
            tasks = asyncTaskRepo,
            force = force,
            ignoreSourceTimeFilter = false,
            mode = modeValue,
            schedule = asyncTaskScheduler::enqueue,
            tickDueAtMs = tickDueAtMs,
            cancelWork = asyncTaskScheduler::cancel,
        )
        return Result.success()
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_TICK_DUE_AT = "tick_due_at_ms"
        const val MODE_DUE = "due"
        const val MODE_BACKFILL = "backfill"
    }
}

internal fun buildUnifiedNewsNextWorkRequest(now: Instant = Clock.System.now()): OneTimeWorkRequest {
    val next = nextUnifiedNewsWindow(now)
    val delayMs = (next.dueAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0L)
    return OneTimeWorkRequestBuilder<UnifiedNewsWorker>()
        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        .setInputData(
            workDataOf(
                UnifiedNewsWorker.KEY_MODE to UnifiedNewsWorker.MODE_DUE,
                UnifiedNewsWorker.KEY_TICK_DUE_AT to next.dueAt.toEpochMilliseconds(),
            ),
        )
        .build()
}

internal fun buildUnifiedNewsCatchUpWorkRequest(tickDueAtMs: Long): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<UnifiedNewsWorker>()
        .setInputData(
            workDataOf(
                UnifiedNewsWorker.KEY_MODE to UnifiedNewsWorker.MODE_DUE,
                UnifiedNewsWorker.KEY_TICK_DUE_AT to tickDueAtMs,
            ),
        )
        .build()

internal fun buildUnifiedNewsBackfillWorkRequest(): OneTimeWorkRequest {
    return OneTimeWorkRequestBuilder<UnifiedNewsWorker>()
        .setInputData(workDataOf(UnifiedNewsWorker.KEY_MODE to UnifiedNewsWorker.MODE_BACKFILL))
        .build()
}

internal enum class UnifiedNewsWorkerMode { DUE, BACKFILL }

internal fun unifiedNewsWorkerMode(value: String?): UnifiedNewsWorkerMode? = when (value) {
    UnifiedNewsWorker.MODE_DUE -> UnifiedNewsWorkerMode.DUE
    UnifiedNewsWorker.MODE_BACKFILL -> UnifiedNewsWorkerMode.BACKFILL
    else -> null
}

/** At most five total attempts (runAttemptCount 0..4); the last one fails and is recorded. */
internal const val UNIFIED_NEWS_MAX_RUN_ATTEMPTS = 5

internal fun unifiedNewsFailureResult(attemptCount: Int, stage: String): ListenableWorker.Result {
    if (attemptCount < UNIFIED_NEWS_MAX_RUN_ATTEMPTS - 1) return ListenableWorker.Result.retry()
    DiagnosticLog.diagnostics.emit(
        DiagnosticCode.OPERATION_FAILED, DiagnosticSource.APP, DiagnosticLevel.ERROR,
        fields = mapOf(
            "unifiedNewsStage" to "run",
            "unifiedNewsFailure" to "budget_exhausted",
            "attempt" to (attemptCount + 1).toString(),
            "unifiedNewsTarget" to if (stage == "schedule_next") "window" else "due",
        ),
    )
    return ListenableWorker.Result.failure()
}

internal fun shouldRetryUnifiedNews(result: UnifiedNewsGenerationResult): Boolean {
    if (result.success) return false
    // The summary service classifies transport, rate-limit and configuration failures by type.
    result.retryable?.let { return it }
    val message = result.message.orEmpty()
    if (message.contains("请先配置")) return false
    if (message.contains("无效引用")) return false
    if (message.startsWith("AI ")) return false
    return true
}
