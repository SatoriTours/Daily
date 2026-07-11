package com.dailysatori.core.recording

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DiaryRecordingCommand {
    data class Start(
        val diaryId: Long,
        val attachmentId: Long,
    ) : DiaryRecordingCommand

    data object Pause : DiaryRecordingCommand
    data object Resume : DiaryRecordingCommand
    data object Stop : DiaryRecordingCommand
    data object RetryPersistence : DiaryRecordingCommand
    data object Shutdown : DiaryRecordingCommand
}

enum class DiaryRecordingCommandResult {
    Accepted,
    AlreadyActive,
    Busy,
    Invalid,
    Ignored,
}

internal sealed interface DiaryRecordingResult {
    val sessionToken: String

    data class RecorderStarted(
        override val sessionToken: String,
    ) : DiaryRecordingResult

    data class RecorderStartFailed(
        override val sessionToken: String,
        val errorCode: String,
    ) : DiaryRecordingResult

    data class RecorderPaused(
        override val sessionToken: String,
    ) : DiaryRecordingResult

    data class RecorderPauseFailed(
        override val sessionToken: String,
        val errorCode: String,
    ) : DiaryRecordingResult

    data class RecorderResumed(
        override val sessionToken: String,
    ) : DiaryRecordingResult

    data class RecorderResumeFailed(
        override val sessionToken: String,
        val errorCode: String,
    ) : DiaryRecordingResult

    data class RecorderStopped(
        override val sessionToken: String,
        val output: DiaryRecordingOutput,
    ) : DiaryRecordingResult

    data class RecorderStopFailed(
        override val sessionToken: String,
        val errorCode: String,
    ) : DiaryRecordingResult

    data class RecorderReleased(
        override val sessionToken: String,
        val output: DiaryRecordingOutput?,
        val errorCode: String,
        val decision: ReleaseDecision,
        val shutdown: Boolean = false,
    ) : DiaryRecordingResult

    data class Tick(
        override val sessionToken: String,
    ) : DiaryRecordingResult
}

interface DiaryRecordingPersistence {
    suspend fun begin(diaryId: Long, attachmentId: Long)

    suspend fun complete(
        diaryId: Long,
        attachmentId: Long,
        output: DiaryRecordingOutput,
    )

    suspend fun fail(
        diaryId: Long,
        attachmentId: Long,
        output: DiaryRecordingOutput?,
        errorCode: String,
    )
}

internal interface DiaryRecordingActorHost {
    fun enterForeground(state: DiaryRecordingState): String?
    fun stateChanged(state: DiaryRecordingState)
    fun finishService(startId: Int): Boolean
}

internal enum class ReleaseDecision {
    CompleteUsableOutput,
    CompleteIfStopRequested,
    Fail,
}

internal class DiaryRecordingActor(
    private val store: DiaryRecordingStore,
    private val recorder: DiaryRecorder,
    private val persistence: DiaryRecordingPersistence,
    private val outputFile: (diaryId: Long, attachmentId: Long, sessionToken: String) -> File,
    private val host: DiaryRecordingActorHost,
    private val scope: CoroutineScope,
    private val actorDispatcher: CoroutineDispatcher,
    private val recorderDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val monotonicNowMs: () -> Long,
    private val nextSessionToken: () -> String,
    private val retryDelaysMs: List<Long> = listOf(1_000, 2_000, 4_000),
    private val tickerIntervalMs: Long = 1_000,
) {
    private val mailbox = Channel<Message>(Channel.UNLIMITED)
    @Volatile private var acceptingCommands = true
    private var activeSession: Session? = null
    private var ticker: Job? = null
    private var shutdownRequested = false
    private var recorderBoundaryClosed = false

    init {
        scope.launch(actorDispatcher) {
            for (message in mailbox) {
                when (message) {
                    is Message.Command -> processCommand(message)
                    is Message.Result -> processResult(message.value)
                }
            }
        }
    }

    fun submit(
        command: DiaryRecordingCommand,
        startId: Int = 0,
    ): Deferred<DiaryRecordingCommandResult> {
        val completion = CompletableDeferred<DiaryRecordingCommandResult>()
        if (!accepts(command)) {
            completion.complete(DiaryRecordingCommandResult.Ignored)
            return completion
        }
        if (mailbox.trySend(Message.Command(command, startId, completion)).isFailure) {
            completion.complete(DiaryRecordingCommandResult.Ignored)
        }
        return completion
    }

    internal fun submit(result: DiaryRecordingResult) {
        mailbox.trySend(Message.Result(result))
    }

    private suspend fun processCommand(message: Message.Command) {
        if (!accepts(message.value)) {
            message.completion.complete(DiaryRecordingCommandResult.Ignored)
            return
        }
        if (message.startId > 0) activeSession?.latestStartId = message.startId
        val result = try {
            when (val command = message.value) {
                is DiaryRecordingCommand.Start -> start(command, message.startId)
                DiaryRecordingCommand.Pause -> pause()
                DiaryRecordingCommand.Resume -> resume()
                DiaryRecordingCommand.Stop -> stop()
                DiaryRecordingCommand.RetryPersistence -> retryPersistence()
                DiaryRecordingCommand.Shutdown -> shutdown()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (message.value is DiaryRecordingCommand.Start) {
                terminateUnexpectedStart(message.value, message.startId)
            } else {
                DiaryRecordingCommandResult.Invalid
            }
        }
        message.completion.complete(result)
    }

    private suspend fun start(
        command: DiaryRecordingCommand.Start,
        startId: Int,
    ): DiaryRecordingCommandResult {
        if (command.diaryId <= 0 || command.attachmentId <= 0) {
            return DiaryRecordingCommandResult.Invalid
        }
        activeSession?.let { active ->
            return if (active.diaryId == command.diaryId && active.attachmentId == command.attachmentId) {
                if (startId > 0) active.latestStartId = startId
                host.enterForeground(store.state.value)
                DiaryRecordingCommandResult.AlreadyActive
            } else {
                DiaryRecordingCommandResult.Busy
            }
        }

        val session = Session(
            token = nextSessionToken(),
            diaryId = command.diaryId,
            attachmentId = command.attachmentId,
            latestStartId = startId,
        )
        activeSession = session
        publish(session.startingState())
        session.outputFile = outputFile(command.diaryId, command.attachmentId, session.token)

        val foregroundError = host.enterForeground(store.state.value)
        if (foregroundError != null) {
            session.pendingPersistence = TerminalPersistence.Failure(null, foregroundError)
            publish(session.failedState(foregroundError, null))
            session.serviceFinishRequested = host.finishService(session.latestStartId)
            persistTerminal(session)
            return DiaryRecordingCommandResult.Accepted
        }

        if (!prepareOutput(session)) {
            session.pendingPersistence = TerminalPersistence.Failure(
                output = null,
                errorCode = DiaryRecordingErrorCode.STORAGE_FAILED,
            )
            publish(session.stoppingState())
            persistTerminal(session)
            return DiaryRecordingCommandResult.Accepted
        }

        try {
            withContext(ioDispatcher) {
                persistence.begin(session.diaryId, session.attachmentId)
            }
        } catch (_: com.dailysatori.data.repository.DiaryAttachmentRecordingTargetException) {
            publish(session.failedState(DiaryRecordingErrorCode.ATTACHMENT_INVALID, null))
            handoffTerminal(session)
            return DiaryRecordingCommandResult.Accepted
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            session.pendingPersistence = TerminalPersistence.Failure(
                output = null,
                errorCode = DiaryRecordingErrorCode.START_FAILED,
            )
            publish(session.stoppingState())
            persistTerminal(session)
            return DiaryRecordingCommandResult.Accepted
        }

        launchRecorder(session.token) {
            try {
                recorder.start(session.token, checkNotNull(session.outputFile))
                DiaryRecordingResult.RecorderStarted(session.token)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                DiaryRecordingResult.RecorderStartFailed(
                    sessionToken = session.token,
                    errorCode = recorderErrorCode(error, DiaryRecordingErrorCode.START_FAILED),
                )
            }
        }
        return DiaryRecordingCommandResult.Accepted
    }

    private fun pause(): DiaryRecordingCommandResult {
        val session = activeSession ?: return DiaryRecordingCommandResult.Ignored
        if (store.state.value !is DiaryRecordingState.Recording) return DiaryRecordingCommandResult.Ignored
        session.captureElapsed(monotonicNowMs())
        ticker?.cancel()
        ticker = null
        publish(session.pausedState())
        launchRecorder(session.token) {
            try {
                recorder.pause()
                DiaryRecordingResult.RecorderPaused(session.token)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                DiaryRecordingResult.RecorderPauseFailed(
                    session.token,
                    recorderErrorCode(error, DiaryRecordingErrorCode.INVALID_STATE),
                )
            }
        }
        return DiaryRecordingCommandResult.Accepted
    }

    private fun resume(): DiaryRecordingCommandResult {
        val session = activeSession ?: return DiaryRecordingCommandResult.Ignored
        if (store.state.value !is DiaryRecordingState.Paused) return DiaryRecordingCommandResult.Ignored
        session.runningSinceMs = monotonicNowMs()
        publish(session.recordingState())
        startTicker(session)
        launchRecorder(session.token) {
            try {
                recorder.resume()
                DiaryRecordingResult.RecorderResumed(session.token)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                DiaryRecordingResult.RecorderResumeFailed(
                    session.token,
                    recorderErrorCode(error, DiaryRecordingErrorCode.INVALID_STATE),
                )
            }
        }
        return DiaryRecordingCommandResult.Accepted
    }

    private suspend fun stop(): DiaryRecordingCommandResult {
        val session = activeSession ?: return DiaryRecordingCommandResult.Ignored
        val state = store.state.value
        if (state is DiaryRecordingState.PersistenceFailed) {
            if (!discardPendingOutput(session)) return DiaryRecordingCommandResult.Invalid
            session.pendingPersistence = TerminalPersistence.Failure(
                output = null,
                errorCode = DiaryRecordingErrorCode.USER_CANCELLED,
            )
            persistTerminal(session)
            return DiaryRecordingCommandResult.Accepted
        }
        if (
            state !is DiaryRecordingState.Starting &&
            state !is DiaryRecordingState.Recording &&
            state !is DiaryRecordingState.Paused
        ) {
            return DiaryRecordingCommandResult.Ignored
        }
        session.stopRequested = true
        session.captureElapsed(monotonicNowMs())
        ticker?.cancel()
        ticker = null
        publish(session.stoppingState())
        if (session.recorderStarted) launchStop(session)
        return DiaryRecordingCommandResult.Accepted
    }

    private fun discardPendingOutput(session: Session): Boolean {
        val output = when (val pending = session.pendingPersistence) {
            is TerminalPersistence.Completion -> pending.output
            is TerminalPersistence.Failure -> pending.output
            null -> null
        }
        val file = usableOutput(session, output)?.file ?: return true
        return !file.exists() || file.delete()
    }

    private suspend fun retryPersistence(): DiaryRecordingCommandResult {
        val session = activeSession ?: return DiaryRecordingCommandResult.Ignored
        val failed = store.state.value as? DiaryRecordingState.PersistenceFailed
            ?: return DiaryRecordingCommandResult.Ignored
        if (session.pendingPersistence == null) {
            return DiaryRecordingCommandResult.Ignored
        }
        host.enterForeground(failed)
        persistTerminal(session)
        return DiaryRecordingCommandResult.Accepted
    }

    private fun shutdown(): DiaryRecordingCommandResult {
        if (shutdownRequested) return DiaryRecordingCommandResult.AlreadyActive
        shutdownRequested = true
        acceptingCommands = false
        ticker?.cancel()
        ticker = null
        val session = activeSession
        if (session == null) {
            closeRecorderBoundary()
            mailbox.close()
        } else if (store.state.value is DiaryRecordingState.PersistenceFailed) {
            closeRecorderBoundary()
        } else {
            launchRecorder(session.token) {
                val output = runCatching { recorder.releasePreservingOutput() }.getOrNull()
                DiaryRecordingResult.RecorderReleased(
                    sessionToken = session.token,
                    output = output,
                    errorCode = DiaryRecordingErrorCode.FINALIZE_FAILED,
                    decision = ReleaseDecision.CompleteUsableOutput,
                    shutdown = true,
                )
            }
        }
        return DiaryRecordingCommandResult.Accepted
    }

    private suspend fun processResult(result: DiaryRecordingResult) {
        val session = activeSession ?: return
        if (session.token != result.sessionToken) return

        when (result) {
            is DiaryRecordingResult.RecorderStarted -> {
                session.recorderStarted = true
                if (session.stopRequested) {
                    launchStop(session)
                } else {
                    session.runningSinceMs = monotonicNowMs()
                    publish(session.recordingState())
                    startTicker(session)
                }
            }
            is DiaryRecordingResult.RecorderStartFailed -> launchRelease(
                session = session,
                errorCode = result.errorCode,
                decision = ReleaseDecision.CompleteIfStopRequested,
            )
            is DiaryRecordingResult.RecorderPaused,
            is DiaryRecordingResult.RecorderResumed,
            -> Unit
            is DiaryRecordingResult.RecorderPauseFailed -> {
                if (store.state.value !is DiaryRecordingState.Stopping) {
                    launchRelease(session, result.errorCode, decision = ReleaseDecision.Fail)
                }
            }
            is DiaryRecordingResult.RecorderResumeFailed -> {
                if (store.state.value !is DiaryRecordingState.Stopping) {
                    launchRelease(session, result.errorCode, decision = ReleaseDecision.Fail)
                }
            }
            is DiaryRecordingResult.RecorderStopped -> completeRecorderOutput(session, result.output)
            is DiaryRecordingResult.RecorderStopFailed -> launchRelease(
                session,
                result.errorCode,
                decision = ReleaseDecision.Fail,
            )
            is DiaryRecordingResult.RecorderReleased -> {
                val usableOutput = usableOutput(session, result.output)
                val completeUsableOutput = when (result.decision) {
                    ReleaseDecision.CompleteUsableOutput -> true
                    ReleaseDecision.CompleteIfStopRequested -> session.stopRequested
                    ReleaseDecision.Fail -> false
                }
                val errorCode = if (
                    result.decision == ReleaseDecision.CompleteIfStopRequested && session.stopRequested
                ) {
                    DiaryRecordingErrorCode.USER_CANCELLED
                } else {
                    result.errorCode
                }
                session.pendingPersistence = if (completeUsableOutput && usableOutput != null) {
                    TerminalPersistence.Completion(usableOutput)
                } else {
                    TerminalPersistence.Failure(usableOutput, errorCode)
                }
                publish(session.stoppingState())
                if (result.shutdown) closeRecorderBoundary()
                persistTerminal(session)
            }
            is DiaryRecordingResult.Tick -> {
                if (store.state.value is DiaryRecordingState.Recording) {
                    publish(session.recordingState())
                }
            }
        }
    }

    private fun launchStop(session: Session) {
        if (session.stopLaunched) return
        session.stopLaunched = true
        launchRecorder(session.token) {
            try {
                DiaryRecordingResult.RecorderStopped(session.token, recorder.stop())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                DiaryRecordingResult.RecorderStopFailed(
                    session.token,
                    recorderErrorCode(error, DiaryRecordingErrorCode.FINALIZE_FAILED),
                )
            }
        }
    }

    private fun launchRelease(
        session: Session,
        errorCode: String,
        decision: ReleaseDecision,
    ) {
        launchRecorder(session.token) {
            val output = runCatching { recorder.releasePreservingOutput() }.getOrNull()
            DiaryRecordingResult.RecorderReleased(
                sessionToken = session.token,
                output = output,
                errorCode = errorCode,
                decision = decision,
            )
        }
    }

    private suspend fun completeRecorderOutput(session: Session, candidate: DiaryRecordingOutput) {
        val output = usableOutput(session, candidate)
        session.pendingPersistence = if (output == null) {
            TerminalPersistence.Failure(null, DiaryRecordingErrorCode.FINALIZE_FAILED)
        } else {
            TerminalPersistence.Completion(output)
        }
        persistTerminal(session)
    }

    private suspend fun persistTerminal(session: Session) {
        val pending = session.pendingPersistence ?: return
        repeat(retryDelaysMs.size + 1) { attempt ->
            try {
                withContext(ioDispatcher) {
                    when (pending) {
                        is TerminalPersistence.Completion -> {
                            val output = usableOutput(session, pending.output)
                                ?: throw InvalidRecordingOutputException()
                            persistence.complete(session.diaryId, session.attachmentId, output)
                        }
                        is TerminalPersistence.Failure -> {
                            persistence.fail(
                                diaryId = session.diaryId,
                                attachmentId = session.attachmentId,
                                output = usableOutput(session, pending.output),
                                errorCode = pending.errorCode,
                            )
                        }
                    }
                }
                handoffTerminal(session)
                return
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Keep the session owned and continue the bounded retry cycle.
            }
            if (attempt < retryDelaysMs.size) delay(retryDelaysMs[attempt])
        }
        val localPath = when (pending) {
            is TerminalPersistence.Completion -> usableOutput(session, pending.output)?.file?.absolutePath
            is TerminalPersistence.Failure -> usableOutput(session, pending.output)?.file?.absolutePath
        }
        publish(session.persistenceFailedState(localPath))
    }

    private fun handoffTerminal(session: Session) {
        if (activeSession?.token != session.token) return
        activeSession = null
        publish(DiaryRecordingState.Idle)
        if (!session.serviceFinishRequested) host.finishService(session.latestStartId)
        if (shutdownRequested) mailbox.close()
    }

    private fun prepareOutput(session: Session): Boolean {
        val output = session.outputFile ?: return false
        val parent = output.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        if (output.exists() && !output.delete()) return false
        session.outputPrepared = true
        return true
    }

    private fun usableOutput(
        session: Session,
        candidate: DiaryRecordingOutput?,
    ): DiaryRecordingOutput? {
        val file = candidate?.file ?: return null
        val expected = session.outputFile ?: return null
        return try {
            candidate.takeIf {
                session.outputPrepared &&
                    candidate.sessionToken == session.token &&
                    file.canonicalFile == expected.canonicalFile &&
                    file.isFile &&
                    file.length() > 0
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun startTicker(session: Session) {
        ticker?.cancel()
        ticker = scope.launch(actorDispatcher) {
            while (true) {
                delay(tickerIntervalMs)
                if (mailbox.trySend(Message.Result(DiaryRecordingResult.Tick(session.token))).isFailure) return@launch
            }
        }
    }

    private fun launchRecorder(
        sessionToken: String,
        operation: () -> DiaryRecordingResult,
    ) {
        scope.launch(recorderDispatcher) {
            val result = operation()
            check(result.sessionToken == sessionToken)
            mailbox.trySend(Message.Result(result))
        }
    }

    private fun publish(state: DiaryRecordingState) {
        store.publish(state)
        host.stateChanged(state)
    }

    private fun closeRecorderBoundary() {
        if (recorderBoundaryClosed) return
        recorderBoundaryClosed = true
        (recorderDispatcher as? ExecutorCoroutineDispatcher)?.close()
    }

    private fun recorderErrorCode(error: Throwable, fallback: String): String = when (error) {
        is DiaryRecorderException -> error.errorCode
        is SecurityException -> DiaryRecordingErrorCode.PERMISSION_DENIED
        else -> fallback
    }

    private sealed interface Message {
        data class Command(
            val value: DiaryRecordingCommand,
            val startId: Int,
            val completion: CompletableDeferred<DiaryRecordingCommandResult>,
        ) : Message

        data class Result(val value: DiaryRecordingResult) : Message
    }

    private sealed interface TerminalPersistence {
        data class Completion(val output: DiaryRecordingOutput) : TerminalPersistence

        data class Failure(
            val output: DiaryRecordingOutput?,
            val errorCode: String,
        ) : TerminalPersistence
    }

    private inner class Session(
        val token: String,
        val diaryId: Long,
        val attachmentId: Long,
        var outputFile: File? = null,
        var latestStartId: Int,
        var outputPrepared: Boolean = false,
        var recorderStarted: Boolean = false,
        var stopRequested: Boolean = false,
        var stopLaunched: Boolean = false,
        var accumulatedMs: Long = 0,
        var runningSinceMs: Long? = null,
        var pendingPersistence: TerminalPersistence? = null,
        var serviceFinishRequested: Boolean = false,
    ) {
        fun elapsed(atMs: Long = monotonicNowMs()): Long =
            accumulatedMs + (runningSinceMs?.let { (atMs - it).coerceAtLeast(0) } ?: 0)

        fun captureElapsed(atMs: Long) {
            accumulatedMs = elapsed(atMs)
            runningSinceMs = null
        }

        fun startingState() = DiaryRecordingState.Starting(diaryId, attachmentId)
        fun recordingState() = DiaryRecordingState.Recording(diaryId, attachmentId, elapsed())
        fun pausedState() = DiaryRecordingState.Paused(diaryId, attachmentId, accumulatedMs)
        fun stoppingState() = DiaryRecordingState.Stopping(diaryId, attachmentId, accumulatedMs)
        fun failedState(errorCode: String, localPath: String?) = DiaryRecordingState.Failed(
            diaryId = diaryId,
            attachmentId = attachmentId,
            elapsedMs = elapsed(),
            errorCode = errorCode,
            localPath = localPath,
        )

        fun persistenceFailedState(localPath: String?) = DiaryRecordingState.PersistenceFailed(
            diaryId = diaryId,
            attachmentId = attachmentId,
            elapsedMs = elapsed(),
            localPath = localPath,
        )
    }

    private class InvalidRecordingOutputException : Exception()

    private fun accepts(command: DiaryRecordingCommand): Boolean =
        acceptingCommands ||
            command is DiaryRecordingCommand.Shutdown ||
            (shutdownRequested && (
                command is DiaryRecordingCommand.RetryPersistence ||
                    command is DiaryRecordingCommand.Stop
                ))

    private suspend fun terminateUnexpectedStart(
        command: DiaryRecordingCommand.Start,
        startId: Int,
    ): DiaryRecordingCommandResult {
        val session = activeSession ?: Session(
            token = runCatching { nextSessionToken() }.getOrDefault("failed-session"),
            diaryId = command.diaryId,
            attachmentId = command.attachmentId,
            latestStartId = startId,
        ).also { activeSession = it }
        session.pendingPersistence = TerminalPersistence.Failure(
            output = null,
            errorCode = DiaryRecordingErrorCode.START_FAILED,
        )
        publish(session.stoppingState())
        persistTerminal(session)
        return DiaryRecordingCommandResult.Accepted
    }
}
