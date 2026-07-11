package com.dailysatori.core.recording

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

class DiaryRecordingHostAttachment internal constructor(
    internal val generation: Long,
)

interface DiaryRecordingAndroidHost {
    fun enterForeground(state: DiaryRecordingState): String?
    fun stateChanged(state: DiaryRecordingState)
    fun stopForeground()
    fun stopSelfResult(startId: Int): Boolean
}

class DiaryRecordingRuntime(
    private val store: DiaryRecordingStore,
    recorder: DiaryRecorder,
    persistence: DiaryRecordingPersistence,
    outputFile: (diaryId: Long, attachmentId: Long, sessionToken: String) -> File,
    scope: CoroutineScope,
    actorDispatcher: CoroutineDispatcher,
    recorderDispatcher: CoroutineDispatcher,
    ioDispatcher: CoroutineDispatcher,
    monotonicNowMs: () -> Long,
    nextSessionToken: () -> String,
    retryDelaysMs: List<Long> = listOf(1_000, 2_000, 4_000),
    tickerIntervalMs: Long = 1_000,
) {
    private val hosts = HostRouter(store)
    private val actor = DiaryRecordingActor(
        store = store,
        recorder = recorder,
        persistence = persistence,
        outputFile = outputFile,
        host = hosts,
        scope = scope,
        actorDispatcher = actorDispatcher,
        recorderDispatcher = recorderDispatcher,
        ioDispatcher = ioDispatcher,
        monotonicNowMs = monotonicNowMs,
        nextSessionToken = nextSessionToken,
        retryDelaysMs = retryDelaysMs,
        tickerIntervalMs = tickerIntervalMs,
    )

    fun attachHost(host: DiaryRecordingAndroidHost): DiaryRecordingHostAttachment = hosts.attach(host)

    fun detachHost(attachment: DiaryRecordingHostAttachment) {
        hosts.detach(attachment)
    }

    fun isAttached(attachment: DiaryRecordingHostAttachment): Boolean = hosts.isAttached(attachment)

    fun submit(
        attachment: DiaryRecordingHostAttachment,
        startId: Int,
        command: DiaryRecordingCommand,
    ): Deferred<DiaryRecordingCommandResult> {
        if (!hosts.isAttached(attachment)) {
            return completedResult(DiaryRecordingCommandResult.Ignored)
        }
        return actor.submit(command, startId)
    }

    internal fun submit(result: DiaryRecordingResult) {
        actor.submit(result)
    }

    fun shutdown(): Deferred<DiaryRecordingCommandResult> =
        actor.submit(DiaryRecordingCommand.Shutdown)

    private fun completedResult(result: DiaryRecordingCommandResult): Deferred<DiaryRecordingCommandResult> =
        kotlinx.coroutines.CompletableDeferred(result)

    private class HostRouter(
        private val store: DiaryRecordingStore,
    ) : DiaryRecordingActorHost {
        private val lock = Any()
        private val effectLock = Any()
        private val generations = AtomicLong()
        private var current: AttachedHost? = null

        fun attach(host: DiaryRecordingAndroidHost): DiaryRecordingHostAttachment {
            val attached = AttachedHost(generations.incrementAndGet(), host)
            synchronized(lock) {
                current = attached
            }
            synchronized(effectLock) {
                val state = store.state.value
                if (state !is DiaryRecordingState.Idle) {
                    invokeCurrent(attached.generation) { it.enterForeground(state) }
                }
                invokeCurrent(attached.generation) {
                    it.stateChanged(store.state.value)
                    Unit
                }
            }
            return DiaryRecordingHostAttachment(attached.generation)
        }

        fun detach(attachment: DiaryRecordingHostAttachment) {
            synchronized(lock) {
                if (current?.generation == attachment.generation) current = null
            }
        }

        fun isAttached(attachment: DiaryRecordingHostAttachment): Boolean =
            synchronized(lock) { current?.generation == attachment.generation }

        override fun enterForeground(state: DiaryRecordingState): String? {
            synchronized(effectLock) {
                repeat(2) {
                    val attached = snapshot()
                        ?: return DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED
                    val result = attached.host.enterForeground(state)
                    if (isCurrent(attached.generation)) return result
                }
                return DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED
            }
        }

        override fun stateChanged(state: DiaryRecordingState) {
            synchronized(effectLock) {
                invokeLatest {
                    it.stateChanged(state)
                    Unit
                }
            }
        }

        override fun finishService(startId: Int): Boolean {
            synchronized(effectLock) {
                val attached = snapshot() ?: return false
                val stoppedCurrent = attached.host.stopSelfResult(startId) &&
                    isCurrent(attached.generation)
                if (stoppedCurrent) attached.host.stopForeground()
                return stoppedCurrent
            }
        }

        private fun <T> invokeLatest(block: (DiaryRecordingAndroidHost) -> T): T? {
            repeat(2) {
                val attached = snapshot() ?: return null
                val result = block(attached.host)
                if (isCurrent(attached.generation)) return result
            }
            return null
        }

        private fun <T> invokeCurrent(
            generation: Long,
            block: (DiaryRecordingAndroidHost) -> T,
        ): T? {
            val attached = snapshot()?.takeIf { it.generation == generation } ?: return null
            val result = block(attached.host)
            return result.takeIf { isCurrent(generation) }
        }

        private fun snapshot(): AttachedHost? = synchronized(lock) { current }

        private fun isCurrent(generation: Long): Boolean =
            synchronized(lock) { current?.generation == generation }

        private data class AttachedHost(
            val generation: Long,
            val host: DiaryRecordingAndroidHost,
        )
    }
}
