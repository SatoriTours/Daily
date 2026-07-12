package com.dailysatori.core.recording

import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryRecordingRuntimeManager(
    private val rejectionScope: CoroutineScope,
    private val rejectStart: suspend (DiaryRecordingCommand.Start, errorCode: String) -> Unit,
    private val rejectionRetryDelaysMs: List<Long> = listOf(1_000, 2_000, 4_000),
    private val logRejectionCleanupFailure: (String, Throwable?) -> Unit = { _, _ -> },
    private val createRuntime: (onClosed: () -> Unit) -> DiaryRecordingRuntime,
) {
    private val lock = Any()
    private var current: DiaryRecordingRuntime? = null
    private var currentHost: AttachedHost? = null
    private var pendingHost: DiaryRecordingAndroidHost? = null
    private var rejectedCleanup: RejectedCleanup? = null
    private val pendingCommands = ArrayDeque<PendingCommand>()

    fun attachAndSubmit(
        host: DiaryRecordingAndroidHost,
        startId: Int,
        command: DiaryRecordingCommand,
    ): Deferred<DiaryRecordingCommandResult> = synchronized(lock) {
        rejectedCleanup?.let { cleanup ->
            if (cleanup.host !== host) {
                return@synchronized completedResult(DiaryRecordingCommandResult.Ignored)
            }
            cleanup.latestStartId = maxOf(cleanup.latestStartId, startId)
            val result = CompletableDeferred<DiaryRecordingCommandResult>()
            cleanup.waiters += CleanupWaiter(result, DiaryRecordingCommandResult.ForegroundRejected)
            if (cleanup.persistenceSucceeded) launchRejectedFinish(cleanup)
            return@synchronized result
        }
        if (current == null) {
            val start = command as? DiaryRecordingCommand.Start
                ?: return@synchronized completedResult(DiaryRecordingCommandResult.Invalid)
            val errorCode = enterPlaceholder(host, start)
            if (errorCode != null) return@synchronized rejectPendingStart(
                host,
                startId,
                start,
                errorCode,
            )
        }
        val runtime = current ?: createManagedRuntime().also { current = it }
        val attachment = currentHost
            ?.takeIf { it.host === host }
            ?.attachment
        val submission = runtime.attachAndSubmitIfOpen(
            host = host,
            existingAttachment = attachment,
            startId = startId,
            command = command,
        )
        if (submission != null) {
            currentHost = AttachedHost(host, submission.attachment)
            submission.result
        } else {
            enqueuePending(host, startId, command, enterPlaceholder = true)
        }
    }

    fun submit(
        command: DiaryRecordingCommand,
        startId: Int = 0,
    ): Deferred<DiaryRecordingCommandResult> = synchronized(lock) {
        rejectedCleanup?.let { cleanup ->
            if (startId > 0) cleanup.latestStartId = maxOf(cleanup.latestStartId, startId)
            return@synchronized when (command) {
                DiaryRecordingCommand.RetryPersistence -> retryRejectedCleanup(cleanup)
                DiaryRecordingCommand.Stop -> waitForOrDiscardRejectedCleanup(cleanup)
                else -> completedResult(DiaryRecordingCommandResult.Ignored)
            }
        }
        val runtime = current
        val attached = currentHost
        if (runtime != null && attached != null) {
            runtime.submitIfOpen(attached.attachment, startId, command)?.let { return@synchronized it }
        }
        val host = pendingHost
        if (host != null && command !is DiaryRecordingCommand.RetryPersistence) {
            enqueuePending(host, startId, command, enterPlaceholder = false)
        } else {
            completedResult(DiaryRecordingCommandResult.Ignored)
        }
    }

    fun detachHost(host: DiaryRecordingAndroidHost) {
        synchronized(lock) {
            rejectedCleanup?.takeIf { it.host === host }?.let { cleanup ->
                cleanup.hostDetached = true
                if (!cleanup.persistenceInProgress || cleanup.persistenceSucceeded) {
                    finishRejectedCleanup(cleanup)
                }
            }
            if (pendingHost === host) {
                pendingHost = null
                completePendingAsIgnored()
            }
            val attached = currentHost?.takeIf { it.host === host } ?: return
            currentHost = null
            current?.detachHost(attached.attachment)
        }
    }

    private fun enqueuePending(
        host: DiaryRecordingAndroidHost,
        startId: Int,
        command: DiaryRecordingCommand,
        enterPlaceholder: Boolean,
    ): Deferred<DiaryRecordingCommandResult> {
        if (enterPlaceholder) {
            val start = command as? DiaryRecordingCommand.Start
            if (start == null) return completedResult(DiaryRecordingCommandResult.Invalid)
            val errorCode = enterPlaceholder(host, start)
            if (errorCode != null) return rejectPendingStart(host, startId, start, errorCode)
        }
        pendingHost = host
        return CompletableDeferred<DiaryRecordingCommandResult>().also { completion ->
            pendingCommands.addLast(PendingCommand(startId, command, completion))
        }
    }

    private fun enterPlaceholder(
        host: DiaryRecordingAndroidHost,
        start: DiaryRecordingCommand.Start,
    ): String? = try {
        host.enterForeground(
            DiaryRecordingState.Starting(
                diaryId = start.diaryId,
                attachmentId = start.attachmentId,
            ),
        )
    } catch (error: Throwable) {
        placeholderForegroundFailureCode(error)
    }

    private fun rejectPendingStart(
        host: DiaryRecordingAndroidHost,
        startId: Int,
        start: DiaryRecordingCommand.Start,
        errorCode: String,
    ): Deferred<DiaryRecordingCommandResult> {
        val completion = CompletableDeferred<DiaryRecordingCommandResult>()
        val cleanup = RejectedCleanup(host, start, errorCode, startId).also {
            it.waiters += CleanupWaiter(completion, DiaryRecordingCommandResult.ForegroundRejected)
            rejectedCleanup = it
        }
        publishRejectedCleanup(cleanup, persistenceFailed = false)
        launchRejectedPersistence(cleanup)
        return completion
    }

    private fun retryRejectedCleanup(cleanup: RejectedCleanup): Deferred<DiaryRecordingCommandResult> {
        if (cleanup.persistenceSucceeded) {
            launchRejectedFinish(cleanup)
            return completedResult(DiaryRecordingCommandResult.Accepted)
        }
        if (cleanup.persistenceInProgress) return completedResult(DiaryRecordingCommandResult.Ignored)
        val completion = CompletableDeferred<DiaryRecordingCommandResult>()
        cleanup.waiters += CleanupWaiter(completion, DiaryRecordingCommandResult.Accepted)
        publishRejectedCleanup(cleanup, persistenceFailed = false)
        launchRejectedPersistence(cleanup)
        return completion
    }

    private fun waitForOrDiscardRejectedCleanup(
        cleanup: RejectedCleanup,
    ): Deferred<DiaryRecordingCommandResult> {
        val completion = CompletableDeferred<DiaryRecordingCommandResult>()
        cleanup.waiters += CleanupWaiter(completion, DiaryRecordingCommandResult.Accepted)
        if (!cleanup.persistenceSucceeded) {
            cleanup.persistenceAbandoned = true
            logRejectionCleanupFailure("Rejected recording cleanup explicitly abandoned", null)
        }
        launchRejectedFinish(cleanup)
        return completion
    }

    private fun launchRejectedPersistence(cleanup: RejectedCleanup) {
        if (cleanup.persistenceInProgress) return
        cleanup.persistenceInProgress = true
        rejectionScope.launch {
            var lastError: Throwable? = null
            repeat(rejectionRetryDelaysMs.size + 1) { attempt ->
                val shouldContinue = synchronized(lock) {
                    rejectedCleanup === cleanup && !cleanup.persistenceAbandoned
                }
                if (!shouldContinue) return@launch
                try {
                    rejectStart(cleanup.start, cleanup.errorCode)
                    val hostDetached = synchronized(lock) {
                        if (rejectedCleanup !== cleanup) return@launch
                        cleanup.persistenceInProgress = false
                        cleanup.persistenceSucceeded = true
                        cleanup.hostDetached
                    }
                    if (hostDetached) finishRejectedCleanup(cleanup)
                    else launchRejectedFinish(cleanup)
                    return@launch
                } catch (error: Throwable) {
                    lastError = error
                    logRejectionCleanupFailure(
                        "Unable to persist rejected recording failure; attempt ${attempt + 1}",
                        error,
                    )
                }
                if (attempt < rejectionRetryDelaysMs.size) delay(rejectionRetryDelaysMs[attempt])
            }
            val hostDetached = synchronized(lock) {
                if (rejectedCleanup !== cleanup) return@launch
                cleanup.persistenceInProgress = false
                cleanup.hostDetached
            }
            if (hostDetached) {
                finishRejectedCleanup(cleanup)
                return@launch
            }
            publishRejectedCleanup(cleanup, persistenceFailed = true)
            logRejectionCleanupFailure(
                "Rejected recording cleanup exhausted; use Retry or discard",
                lastError,
            )
        }
    }

    private fun launchRejectedFinish(cleanup: RejectedCleanup) {
        val shouldLaunch = synchronized(lock) {
            if (rejectedCleanup !== cleanup || cleanup.finishInProgress) false else {
                cleanup.finishInProgress = true
                true
            }
        }
        if (!shouldLaunch) return
        rejectionScope.launch {
            repeat(REJECTION_FINISH_ATTEMPTS) {
                val snapshot = synchronized(lock) {
                    rejectedCleanup?.takeIf { it === cleanup }?.let { it.host to it.latestStartId }
                } ?: return@launch
                val stopped = runCatching { snapshot.first.stopSelfResult(snapshot.second) }
                    .getOrDefault(false)
                val stillOwned = synchronized(lock) { rejectedCleanup === cleanup }
                if (!stillOwned) return@launch
                if (stopped) {
                    runCatching { snapshot.first.stopForeground() }
                    finishRejectedCleanup(cleanup)
                    return@launch
                }
            }
            synchronized(lock) {
                if (rejectedCleanup === cleanup) cleanup.finishInProgress = false
            }
            logRejectionCleanupFailure(
                "Unable to finish rejected recording service; awaiting a newer startId or Stop",
                null,
            )
        }
    }

    private fun finishRejectedCleanup(cleanup: RejectedCleanup) {
        val waiters = synchronized(lock) {
            if (rejectedCleanup !== cleanup) return
            rejectedCleanup = null
            cleanup.waiters.toList()
        }
        waiters.forEach { it.completion.complete(it.result) }
    }

    private fun publishRejectedCleanup(cleanup: RejectedCleanup, persistenceFailed: Boolean) {
        runCatching {
            cleanup.host.stateChanged(
                if (persistenceFailed) DiaryRecordingState.PersistenceFailed(
                    diaryId = cleanup.start.diaryId,
                    attachmentId = cleanup.start.attachmentId,
                    elapsedMs = 0,
                ) else DiaryRecordingState.Starting(
                    diaryId = cleanup.start.diaryId,
                    attachmentId = cleanup.start.attachmentId,
                ),
            )
        }
    }

    private fun createManagedRuntime(): DiaryRecordingRuntime {
        lateinit var runtime: DiaryRecordingRuntime
        runtime = createRuntime { runtimeClosed(runtime) }
        return runtime
    }

    private fun runtimeClosed(runtime: DiaryRecordingRuntime) {
        synchronized(lock) {
            if (current !== runtime) return
            current = null
            currentHost = null
            val host = pendingHost
            if (host == null || pendingCommands.isEmpty()) {
                pendingHost = null
                completePendingAsIgnored()
                return
            }

            val queued = ArrayList<PendingCommand>(pendingCommands.size)
            while (pendingCommands.isNotEmpty()) queued += pendingCommands.removeFirst()
            pendingHost = null

            val fresh = createManagedRuntime()
            current = fresh
            val first = queued.first()
            val firstSubmission = fresh.attachAndSubmitIfOpen(
                host = host,
                existingAttachment = null,
                startId = first.startId,
                command = first.command,
            )
            if (firstSubmission == null) {
                current = null
                queued.forEach { it.completion.complete(DiaryRecordingCommandResult.Ignored) }
                return
            }
            currentHost = AttachedHost(host, firstSubmission.attachment)
            bridge(firstSubmission.result, first.completion)
            queued.drop(1).forEach { pending ->
                val result = fresh.submitIfOpen(
                    firstSubmission.attachment,
                    pending.startId,
                    pending.command,
                )
                if (result == null) {
                    pending.completion.complete(DiaryRecordingCommandResult.Ignored)
                } else {
                    bridge(result, pending.completion)
                }
            }
        }
    }

    private fun bridge(
        source: Deferred<DiaryRecordingCommandResult>,
        target: CompletableDeferred<DiaryRecordingCommandResult>,
    ) {
        source.invokeOnCompletion { error ->
            if (error == null) {
                target.complete(source.getCompleted())
            } else {
                target.completeExceptionally(error)
            }
        }
    }

    private fun completePendingAsIgnored() {
        while (pendingCommands.isNotEmpty()) {
            pendingCommands.removeFirst().completion.complete(DiaryRecordingCommandResult.Ignored)
        }
    }

    private fun completedResult(
        result: DiaryRecordingCommandResult,
    ): Deferred<DiaryRecordingCommandResult> = CompletableDeferred(result)

    private data class AttachedHost(
        val host: DiaryRecordingAndroidHost,
        val attachment: DiaryRecordingHostAttachment,
    )

    private data class PendingCommand(
        val startId: Int,
        val command: DiaryRecordingCommand,
        val completion: CompletableDeferred<DiaryRecordingCommandResult>,
    )

    private class RejectedCleanup(
        val host: DiaryRecordingAndroidHost,
        val start: DiaryRecordingCommand.Start,
        val errorCode: String,
        var latestStartId: Int,
        var persistenceInProgress: Boolean = false,
        var persistenceSucceeded: Boolean = false,
        var persistenceAbandoned: Boolean = false,
        var finishInProgress: Boolean = false,
        var hostDetached: Boolean = false,
        val waiters: MutableList<CleanupWaiter> = mutableListOf(),
    )

    private data class CleanupWaiter(
        val completion: CompletableDeferred<DiaryRecordingCommandResult>,
        val result: DiaryRecordingCommandResult,
    )

    private companion object {
        const val REJECTION_FINISH_ATTEMPTS = 4
    }
}

private fun placeholderForegroundFailureCode(error: Throwable): String =
    if (error is SecurityException) {
        DiaryRecordingErrorCode.FOREGROUND_SECURITY_DENIED
    } else {
        DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED
    }
