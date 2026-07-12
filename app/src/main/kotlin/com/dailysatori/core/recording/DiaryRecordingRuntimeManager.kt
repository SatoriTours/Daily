package com.dailysatori.core.recording

import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryRecordingRuntimeManager(
    private val rejectionScope: CoroutineScope,
    private val rejectStart: suspend (DiaryRecordingCommand.Start, errorCode: String) -> Unit,
    private val createRuntime: (onClosed: () -> Unit) -> DiaryRecordingRuntime,
) {
    private val lock = Any()
    private var current: DiaryRecordingRuntime? = null
    private var currentHost: AttachedHost? = null
    private var pendingHost: DiaryRecordingAndroidHost? = null
    private val pendingCommands = ArrayDeque<PendingCommand>()

    fun attachAndSubmit(
        host: DiaryRecordingAndroidHost,
        startId: Int,
        command: DiaryRecordingCommand,
    ): Deferred<DiaryRecordingCommandResult> = synchronized(lock) {
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
            val errorCode = try {
                host.enterForeground(
                    DiaryRecordingState.Starting(
                        diaryId = start.diaryId,
                        attachmentId = start.attachmentId,
                    ),
                )
            } catch (error: Throwable) {
                placeholderForegroundFailureCode(error)
            }
            if (errorCode != null) return rejectPendingStart(host, startId, start, errorCode)
        }
        pendingHost = host
        return CompletableDeferred<DiaryRecordingCommandResult>().also { completion ->
            pendingCommands.addLast(PendingCommand(startId, command, completion))
        }
    }

    private fun rejectPendingStart(
        host: DiaryRecordingAndroidHost,
        startId: Int,
        start: DiaryRecordingCommand.Start,
        errorCode: String,
    ): Deferred<DiaryRecordingCommandResult> {
        val completion = CompletableDeferred<DiaryRecordingCommandResult>()
        rejectionScope.launch {
            try {
                rejectStart(start, errorCode)
            } finally {
                runCatching { host.stopSelfResult(startId) }
                completion.complete(DiaryRecordingCommandResult.ForegroundRejected)
            }
        }
        return completion
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
}

private fun placeholderForegroundFailureCode(error: Throwable): String =
    if (error is SecurityException) {
        DiaryRecordingErrorCode.FOREGROUND_SECURITY_DENIED
    } else {
        DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED
    }
