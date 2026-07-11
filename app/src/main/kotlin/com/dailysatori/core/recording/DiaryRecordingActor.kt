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
    val sessionToken: Long

    data class RecorderStarted(
        override val sessionToken: Long,
    ) : DiaryRecordingResult

    data class RecorderStartFailed(
        override val sessionToken: Long,
        val errorCode: String,
    ) : DiaryRecordingResult

    data class RecorderPaused(
        override val sessionToken: Long,
    ) : DiaryRecordingResult

    data class RecorderPauseFailed(
        override val sessionToken: Long,
        val errorCode: String,
    ) : DiaryRecordingResult

    data class RecorderResumed(
        override val sessionToken: Long,
    ) : DiaryRecordingResult

    data class RecorderResumeFailed(
        override val sessionToken: Long,
        val errorCode: String,
    ) : DiaryRecordingResult

    data class RecorderStopped(
        override val sessionToken: Long,
        val output: DiaryRecordingOutput,
    ) : DiaryRecordingResult

    data class RecorderStopFailed(
        override val sessionToken: Long,
        val errorCode: String,
    ) : DiaryRecordingResult

    data class RecorderReleased(
        override val sessionToken: Long,
        val output: DiaryRecordingOutput?,
        val errorCode: String,
        val completeUsableOutput: Boolean,
        val shutdown: Boolean = false,
    ) : DiaryRecordingResult

    data class Tick(
        override val sessionToken: Long,
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

interface DiaryRecordingActorHost {
    fun enterForeground(state: DiaryRecordingState): String?
    fun stateChanged(state: DiaryRecordingState)
    fun stopForeground()
    fun stopService()
}

class DiaryRecordingActor(
    private val store: DiaryRecordingStore,
    private val recorder: DiaryRecorder,
    private val persistence: DiaryRecordingPersistence,
    private val outputFile: (diaryId: Long, attachmentId: Long) -> File,
    private val host: DiaryRecordingActorHost,
    private val scope: CoroutineScope,
    private val actorDispatcher: CoroutineDispatcher,
    private val recorderDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMs: () -> Long,
    private val nextSessionToken: () -> Long,
    private val retryDelaysMs: List<Long> = listOf(1_000, 2_000, 4_000),
    private val tickerIntervalMs: Long = 1_000,
) {
    private val mailbox = Channel<Message>(Channel.UNLIMITED)
    @Volatile private var acceptingCommands = true
    private var activeSession: Session? = null
    private var ticker: Job? = null
    private var shutdownToken: Long? = null

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

    fun submit(command: DiaryRecordingCommand): Deferred<DiaryRecordingCommandResult> {
        val completion = CompletableDeferred<DiaryRecordingCommandResult>()
        if (!acceptingCommands && command !is DiaryRecordingCommand.Shutdown) {
            completion.complete(DiaryRecordingCommandResult.Ignored)
            return completion
        }
        if (mailbox.trySend(Message.Command(command, completion)).isFailure) {
            completion.complete(DiaryRecordingCommandResult.Ignored)
        }
        return completion
    }

    internal fun submit(result: DiaryRecordingResult) {
        mailbox.trySend(Message.Result(result))
    }

    private suspend fun processCommand(message: Message.Command) {
        if (!acceptingCommands && message.value !is DiaryRecordingCommand.Shutdown) {
            message.completion.complete(DiaryRecordingCommandResult.Ignored)
            return
        }
        val result = try {
            when (val command = message.value) {
                is DiaryRecordingCommand.Start -> start(command)
                DiaryRecordingCommand.Pause -> pause()
                DiaryRecordingCommand.Resume -> resume()
                DiaryRecordingCommand.Stop -> stop()
                DiaryRecordingCommand.RetryPersistence -> retryPersistence()
                DiaryRecordingCommand.Shutdown -> shutdown()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DiaryRecordingCommandResult.Invalid
        }
        message.completion.complete(result)
    }

    private suspend fun start(command: DiaryRecordingCommand.Start): DiaryRecordingCommandResult {
        if (command.diaryId <= 0 || command.attachmentId <= 0) {
            return DiaryRecordingCommandResult.Invalid
        }
        activeSession?.let { active ->
            return if (active.diaryId == command.diaryId && active.attachmentId == command.attachmentId) {
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
            outputFile = outputFile(command.diaryId, command.attachmentId),
            createdAtMs = nowMs(),
        )
        activeSession = session
        publish(session.startingState())

        val foregroundError = host.enterForeground(store.state.value)
        if (foregroundError != null) {
            session.pendingPersistence = TerminalPersistence.Failure(null, foregroundError)
            publish(session.failedState(foregroundError, null))
            host.stopService()
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
        } catch (_: Exception) {
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
                recorder.start(session.outputFile)
                DiaryRecordingResult.RecorderStarted(session.token)
            } catch (error: Exception) {
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
        session.captureElapsed(nowMs())
        ticker?.cancel()
        ticker = null
        publish(session.pausedState())
        launchRecorder(session.token) {
            try {
                recorder.pause()
                DiaryRecordingResult.RecorderPaused(session.token)
            } catch (error: Exception) {
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
        session.runningSinceMs = nowMs()
        publish(session.recordingState())
        startTicker(session)
        launchRecorder(session.token) {
            try {
                recorder.resume()
                DiaryRecordingResult.RecorderResumed(session.token)
            } catch (error: Exception) {
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
        session.captureElapsed(nowMs())
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
        if (!acceptingCommands) return DiaryRecordingCommandResult.AlreadyActive
        acceptingCommands = false
        ticker?.cancel()
        ticker = null
        val session = activeSession
        activeSession = null
        publish(DiaryRecordingState.Idle)
        if (session == null) {
            closeRecorderBoundary()
            mailbox.close()
        } else {
            shutdownToken = session.token
            launchRecorder(session.token) {
                val output = runCatching { recorder.releasePreservingOutput() }.getOrNull()
                DiaryRecordingResult.RecorderReleased(
                    sessionToken = session.token,
                    output = output,
                    errorCode = DiaryRecordingErrorCode.FINALIZE_FAILED,
                    completeUsableOutput = false,
                    shutdown = true,
                )
            }
        }
        return DiaryRecordingCommandResult.Accepted
    }

    private suspend fun processResult(result: DiaryRecordingResult) {
        if (result is DiaryRecordingResult.RecorderReleased && result.shutdown) {
            if (shutdownToken == result.sessionToken) {
                shutdownToken = null
                closeRecorderBoundary()
                mailbox.close()
            }
            return
        }
        val session = activeSession ?: return
        if (session.token != result.sessionToken) return

        when (result) {
            is DiaryRecordingResult.RecorderStarted -> {
                session.recorderStarted = true
                if (session.stopRequested) {
                    launchStop(session)
                } else {
                    session.runningSinceMs = nowMs()
                    publish(session.recordingState())
                    startTicker(session)
                }
            }
            is DiaryRecordingResult.RecorderStartFailed -> launchRelease(
                session = session,
                errorCode = if (session.stopRequested) {
                    DiaryRecordingErrorCode.USER_CANCELLED
                } else {
                    result.errorCode
                },
                completeUsableOutput = session.stopRequested,
            )
            is DiaryRecordingResult.RecorderPaused,
            is DiaryRecordingResult.RecorderResumed,
            -> Unit
            is DiaryRecordingResult.RecorderPauseFailed -> {
                if (store.state.value !is DiaryRecordingState.Stopping) {
                    launchRelease(session, result.errorCode, completeUsableOutput = false)
                }
            }
            is DiaryRecordingResult.RecorderResumeFailed -> {
                if (store.state.value !is DiaryRecordingState.Stopping) {
                    launchRelease(session, result.errorCode, completeUsableOutput = false)
                }
            }
            is DiaryRecordingResult.RecorderStopped -> completeRecorderOutput(session, result.output)
            is DiaryRecordingResult.RecorderStopFailed -> launchRelease(
                session,
                result.errorCode,
                completeUsableOutput = false,
            )
            is DiaryRecordingResult.RecorderReleased -> {
                val usableOutput = usableOutput(session, result.output)
                session.pendingPersistence = if (result.completeUsableOutput && usableOutput != null) {
                    TerminalPersistence.Completion(usableOutput)
                } else {
                    TerminalPersistence.Failure(usableOutput, result.errorCode)
                }
                publish(session.stoppingState())
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
            } catch (error: Exception) {
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
        completeUsableOutput: Boolean,
    ) {
        launchRecorder(session.token) {
            val output = runCatching { recorder.releasePreservingOutput() }.getOrNull()
            DiaryRecordingResult.RecorderReleased(
                sessionToken = session.token,
                output = output,
                errorCode = errorCode,
                completeUsableOutput = completeUsableOutput,
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
            } catch (_: Exception) {
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
        host.stopForeground()
        host.stopService()
    }

    private fun prepareOutput(session: Session): Boolean {
        val output = session.outputFile
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
        val expected = session.outputFile.absoluteFile
        val actual = file.absoluteFile
        return candidate.takeIf {
            session.outputPrepared &&
                actual == expected &&
                actual.isFile &&
                actual.length() > 0 &&
                actual.lastModified() >= session.createdAtMs
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
        sessionToken: Long,
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
        (recorderDispatcher as? ExecutorCoroutineDispatcher)?.close()
    }

    private fun recorderErrorCode(error: Exception, fallback: String): String = when (error) {
        is DiaryRecorderException -> error.errorCode
        is SecurityException -> DiaryRecordingErrorCode.PERMISSION_DENIED
        else -> fallback
    }

    private sealed interface Message {
        data class Command(
            val value: DiaryRecordingCommand,
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
        val token: Long,
        val diaryId: Long,
        val attachmentId: Long,
        val outputFile: File,
        val createdAtMs: Long,
        var outputPrepared: Boolean = false,
        var recorderStarted: Boolean = false,
        var stopRequested: Boolean = false,
        var stopLaunched: Boolean = false,
        var accumulatedMs: Long = 0,
        var runningSinceMs: Long? = null,
        var pendingPersistence: TerminalPersistence? = null,
    ) {
        fun elapsed(atMs: Long = nowMs()): Long =
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
}
