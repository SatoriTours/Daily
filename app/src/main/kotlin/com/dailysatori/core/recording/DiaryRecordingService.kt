package com.dailysatori.core.recording

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryAttachmentRecordingTargetException
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject

class DiaryRecordingService : Service() {
    private val store: DiaryRecordingStore by inject()
    private val recorder: DiaryRecorder by inject()
    private val attachmentRepository: DiaryAttachmentRepository by inject()
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val actionMutex = Mutex()
    private val actionChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val recorderMutex = Mutex()
    @Volatile private var acceptingActions = true
    private val actionWorker = scope.launch {
        for (action in actionChannel) {
            actionMutex.withLock {
                if (acceptingActions) action()
            }
        }
    }
    private lateinit var notification: DiaryRecordingNotification
    private var ticker: Job? = null
    private var activeDiaryId: Long? = null
    private var activeAttachmentId: Long? = null
    private var activeOutputFile: File? = null
    private var pendingPersistence: PendingPersistence? = null
    private var persistenceCommitted = false
    private var foregroundStarted = false
    private var session = DiaryRecordingSessionCoordinator()

    override fun onCreate() {
        super.onCreate()
        notification = DiaryRecordingNotification(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!acceptingActions) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_START -> {
                val diaryId = intent.getLongExtra(EXTRA_DIARY_ID, 0)
                val attachmentId = intent.getLongExtra(EXTRA_ATTACHMENT_ID, 0)
                when (store.requestStart(diaryId, attachmentId)) {
                    DiaryRecordingStartResult.Accepted -> {
                        activeDiaryId = diaryId
                        activeAttachmentId = attachmentId
                        activeOutputFile = File(filesDir, "DailySatori/diary/audio/$diaryId/$attachmentId.m4a")
                        persistenceCommitted = false
                        pendingPersistence = null
                        session = DiaryRecordingSessionCoordinator()
                        val foregroundError = enterForeground(diaryId)
                        if (foregroundError == null) {
                            enqueueAction { recorderMutex.withLock { startRecording() } }
                        } else {
                            store.fail(foregroundError)
                            pendingPersistence = PendingPersistence(
                                diaryId = diaryId,
                                attachmentId = attachmentId,
                                output = null,
                                errorCode = foregroundError,
                            )
                            stopSelf()
                        }
                    }
                    DiaryRecordingStartResult.AlreadyActive -> {
                        enterForeground(diaryId)?.let { Log.e(TAG, "Unable to refresh recording foreground: $it") }
                    }
                    DiaryRecordingStartResult.Busy ->
                        Log.w(TAG, "Rejected recording start: ${DiaryRecordingErrorCode.RECORDER_BUSY}")
                    DiaryRecordingStartResult.Invalid ->
                        Log.w(TAG, "Rejected recording start: ${DiaryRecordingErrorCode.INVALID_STATE}")
                }
            }
            ACTION_PAUSE -> enqueueAction { recorderMutex.withLock { pauseRecording() } }
            ACTION_RESUME -> enqueueAction { recorderMutex.withLock { resumeRecording() } }
            ACTION_STOP -> {
                if (store.state.value is DiaryRecordingState.Starting) session.requestUserStop()
                if (store.stop()) {
                    enqueueAction { recorderMutex.withLock { finishStoppingRecording() } }
                }
            }
            ACTION_RETRY_PERSIST -> {
                val pending = pendingPersistence
                if (pending != null) {
                    val foregroundError = enterForeground(pending.diaryId)
                    if (foregroundError == null) {
                        enqueueAction { persistPendingRecording() }
                    } else {
                        Log.e(TAG, "Unable to retry recording persistence in foreground: $foregroundError")
                        stopSelf()
                    }
                } else {
                    stopSelf()
                }
            }
            ACTION_OPEN -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        acceptingActions = false
        ticker?.cancel()
        actionChannel.close()
        val cleanupCompleted = runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(DESTROY_TIMEOUT_MS) {
                serviceJob.cancelAndJoin()
                recorderMutex.withLock {
                    if (!persistenceCommitted && activeAttachmentId != null) {
                        preserveInterruptedRecording()
                    }
                }
                true
            } ?: false
        }
        if (!cleanupCompleted) {
            serviceJob.cancel()
            runCatching { recorder.releasePreservingOutput() }
                .onFailure { Log.e(TAG, "Unable to release recorder after destroy timeout", it) }
        }
        super.onDestroy()
    }

    private fun enterForeground(diaryId: Long): String? = try {
        val starting = store.state.value
        val foregroundNotification = notification.build(starting, diaryId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DiaryRecordingNotification.NOTIFICATION_ID,
                foregroundNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(DiaryRecordingNotification.NOTIFICATION_ID, foregroundNotification)
        }
        foregroundStarted = true
        null
    } catch (error: RuntimeException) {
        foregroundLaunchFailureCode(
            sdkInt = Build.VERSION.SDK_INT,
            error = error,
            isApi31ForegroundStartDenied = isApi31ForegroundStartNotAllowed(error),
        )?.also { Log.e(TAG, "Unable to enter microphone foreground service: $it", error) }
            ?: throw error
    }

    private fun enqueueAction(action: suspend () -> Unit) {
        if (!acceptingActions) return
        actionChannel.trySend(action)
    }

    private suspend fun startRecording() {
        val output = activeOutputFile ?: return handleFailure(DiaryRecordingErrorCode.STORAGE_FAILED)
        if (session.startingStopDecision() == DiaryRecordingStartingStopDecision.CancelWithoutOutput) {
            cancelStartingRecording()
            return
        }
        if (!session.prepareOutput(output)) {
            handleFailure(DiaryRecordingErrorCode.STORAGE_FAILED)
            return
        }
        if (session.startingStopDecision() == DiaryRecordingStartingStopDecision.CancelWithoutOutput) {
            cancelStartingRecording()
            return
        }
        try {
            attachmentRepository.beginRecording(
                diaryId = checkNotNull(activeDiaryId),
                id = checkNotNull(activeAttachmentId),
            )
            session.markRecorderStartAttempted()
            recorder.start(output)
            session.markRecorderStarted()
            when (session.startingStopDecision()) {
                DiaryRecordingStartingStopDecision.FinalizeRecording -> finishStoppingRecording()
                DiaryRecordingStartingStopDecision.ContinueStarting -> {
                    if (store.markRecording()) {
                        notifyCurrent()
                        startTicker(checkNotNull(activeDiaryId))
                    } else if (store.state.value is DiaryRecordingState.Stopping) {
                        finishStoppingRecording()
                    } else {
                        handleFailure(DiaryRecordingErrorCode.INVALID_STATE)
                    }
                }
                DiaryRecordingStartingStopDecision.CancelWithoutOutput -> cancelStartingRecording()
            }
        } catch (error: DiaryAttachmentRecordingTargetException) {
            handleInvalidAttachment(error)
        } catch (error: DiaryRecorderException) {
            if (session.startingStopDecision() == DiaryRecordingStartingStopDecision.FinalizeRecording) {
                completeUsableStartingOutput()
            } else {
                handleFailure(error.errorCode)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start recorder", error)
            if (session.startingStopDecision() == DiaryRecordingStartingStopDecision.FinalizeRecording) {
                completeUsableStartingOutput()
            } else {
                handleFailure(DiaryRecordingErrorCode.START_FAILED)
            }
        }
    }

    private suspend fun completeUsableStartingOutput() {
        val partial = recorder.releasePreservingOutput()
        val usableFile = session.currentUsableOutput(partial?.file)
            ?: session.currentUsableOutput(activeOutputFile)
            ?: return handleFailure(DiaryRecordingErrorCode.FINALIZE_FAILED)
        val diaryId = activeDiaryId ?: return handleFailure(DiaryRecordingErrorCode.PERSIST_FAILED)
        val attachmentId = activeAttachmentId ?: return handleFailure(DiaryRecordingErrorCode.PERSIST_FAILED)
        val durationMs = partial?.takeIf { it.file == usableFile }?.durationMs ?: 0
        val output = DiaryRecordingOutput(usableFile, durationMs)
        activeOutputFile = usableFile
        pendingPersistence = PendingPersistence(diaryId, attachmentId, output, null)
        persistPendingRecording()
    }

    private suspend fun cancelStartingRecording() {
        recorder.releasePreservingOutput()
        store.fail(DiaryRecordingErrorCode.USER_CANCELLED)
        val diaryId = activeDiaryId ?: return markPersistenceFailed()
        val attachmentId = activeAttachmentId ?: return markPersistenceFailed()
        pendingPersistence = PendingPersistence(
            diaryId = diaryId,
            attachmentId = attachmentId,
            output = null,
            errorCode = DiaryRecordingErrorCode.USER_CANCELLED,
        )
        persistPendingRecording()
    }

    private fun handleInvalidAttachment(error: DiaryAttachmentRecordingTargetException) {
        Log.e(TAG, "Invalid recording attachment target: ${error.error}", error)
        recorder.releasePreservingOutput()
        store.fail(DiaryRecordingErrorCode.ATTACHMENT_INVALID)
        store.releaseFailedSession()
        pendingPersistence = null
        persistenceCommitted = true
        activeAttachmentId = null
        activeDiaryId = null
        activeOutputFile = null
        stopForegroundAfterPersistence()
        stopSelf()
    }

    private suspend fun pauseRecording() {
        if (store.state.value !is DiaryRecordingState.Recording) return
        try {
            recorder.pause()
            store.pause()
            ticker?.cancel()
            ticker = null
            notifyCurrent()
        } catch (error: DiaryRecorderException) {
            handleFailure(error.errorCode)
        }
    }

    private suspend fun resumeRecording() {
        if (store.state.value !is DiaryRecordingState.Paused) return
        try {
            recorder.resume()
            store.resume()
            notifyCurrent()
            startTicker(checkNotNull(activeDiaryId))
        } catch (error: DiaryRecorderException) {
            handleFailure(error.errorCode)
        }
    }

    private suspend fun finishStoppingRecording() {
        if (store.state.value !is DiaryRecordingState.Stopping) return
        ticker?.cancel()
        ticker = null
        notifyCurrent()
        val diaryId = activeDiaryId ?: return handleFailure(DiaryRecordingErrorCode.PERSIST_FAILED)
        val attachmentId = activeAttachmentId ?: return handleFailure(DiaryRecordingErrorCode.PERSIST_FAILED)
        try {
            val output = recorder.stop()
            val usableFile = session.currentUsableOutput(output.file)
                ?: throw DiaryRecorderException(DiaryRecordingErrorCode.FINALIZE_FAILED)
            val usableOutput = output.copy(file = usableFile)
            activeOutputFile = usableFile
            pendingPersistence = PendingPersistence(diaryId, attachmentId, usableOutput, null)
            persistPendingRecording()
        } catch (error: DiaryRecorderException) {
            handleFailure(error.errorCode)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to finalize recording", error)
            markPersistenceFailed()
        }
    }

    private suspend fun handleFailure(errorCode: String) {
        ticker?.cancel()
        ticker = null
        val partial = recorder.releasePreservingOutput()
        val actualFile = session.currentUsableOutput(partial?.file)
            ?: session.currentUsableOutput(activeOutputFile)
        val output = actualFile?.let {
            DiaryRecordingOutput(it, partial?.takeIf { candidate -> candidate.file == it }?.durationMs ?: 0)
        }
        activeOutputFile = actualFile
        store.fail(errorCode, actualFile?.absolutePath)
        val diaryId = activeDiaryId
        val attachmentId = activeAttachmentId
        if (diaryId == null || attachmentId == null) {
            markPersistenceFailed()
            return
        }
        pendingPersistence = PendingPersistence(diaryId, attachmentId, output, errorCode)
        persistPendingRecording()
    }

    private suspend fun persistPendingRecording() {
        val pending = pendingPersistence ?: return
        when (val result = retryDiaryRecordingPersistence { writePendingRecording(pending) }) {
            is DiaryRecordingPersistenceResult.Succeeded -> {
                pendingPersistence = null
                persistenceCommitted = true
                if (pending.errorCode == null) {
                    store.complete()
                } else {
                    store.releaseFailedSession()
                }
                activeAttachmentId = null
                activeDiaryId = null
                activeOutputFile = null
                stopForegroundAfterPersistence()
                if (acceptingActions) stopSelf()
            }
            is DiaryRecordingPersistenceResult.Failed -> {
                Log.e(
                    TAG,
                    "Unable to persist recording terminal state after ${result.attempts} attempts",
                    result.cause,
                )
                markPersistenceFailed()
            }
        }
    }

    private fun writePendingRecording(pending: PendingPersistence) {
        if (pending.errorCode == null) {
            val output = checkNotNull(pending.output)
            attachmentRepository.completeRecording(
                diaryId = pending.diaryId,
                id = pending.attachmentId,
                localPath = output.file.absolutePath,
                sizeBytes = output.file.length(),
                durationMs = output.durationMs,
            )
        } else {
            val partial = session.currentUsableOutput(pending.output?.file)
            attachmentRepository.failRecording(
                diaryId = pending.diaryId,
                id = pending.attachmentId,
                localPath = partial?.absolutePath,
                sizeBytes = partial?.length() ?: 0,
                durationMs = pending.output?.takeIf { it.file == partial }?.durationMs ?: 0,
                errorCode = pending.errorCode,
            )
        }
    }

    private fun markPersistenceFailed() {
        val path = session.currentUsableOutput(activeOutputFile)?.absolutePath
        store.fail(DiaryRecordingErrorCode.PERSIST_FAILED, path)
        notifyCurrent()
    }

    private suspend fun preserveInterruptedRecording() {
        if (pendingPersistence == null) {
            val partial = recorder.releasePreservingOutput()
            val actualFile = session.currentUsableOutput(partial?.file)
                ?: session.currentUsableOutput(activeOutputFile)
            activeOutputFile = actualFile
            store.fail(DiaryRecordingErrorCode.FINALIZE_FAILED, actualFile?.absolutePath)
            val diaryId = activeDiaryId ?: return
            val attachmentId = activeAttachmentId ?: return
            pendingPersistence = PendingPersistence(
                diaryId,
                attachmentId,
                actualFile?.let { DiaryRecordingOutput(it, partial?.durationMs ?: 0) },
                DiaryRecordingErrorCode.FINALIZE_FAILED,
            )
        }
        persistPendingRecording()
    }

    private fun startTicker(diaryId: Long) {
        ticker?.cancel()
        ticker = scope.launch {
            while (store.state.value is DiaryRecordingState.Recording) {
                delay(1_000)
                if (store.state.value !is DiaryRecordingState.Recording) break
                store.refreshElapsed()
                notification.notify(store.state.value, diaryId)
            }
        }
    }

    private fun notifyCurrent() {
        val state = store.state.value
        val diaryId = state.diaryId ?: return
        runCatching { notification.notify(state, diaryId) }
            .onFailure { Log.e(TAG, "Unable to update recording notification", it) }
    }

    private fun stopForegroundAfterPersistence() {
        if (!foregroundStarted) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
    }

    private data class PendingPersistence(
        val diaryId: Long,
        val attachmentId: Long,
        val output: DiaryRecordingOutput?,
        val errorCode: String?,
    )

    companion object {
        const val ACTION_START = "com.dailysatori.recording.START"
        const val ACTION_PAUSE = "com.dailysatori.recording.PAUSE"
        const val ACTION_RESUME = "com.dailysatori.recording.RESUME"
        const val ACTION_STOP = "com.dailysatori.recording.STOP"
        const val ACTION_RETRY_PERSIST = "com.dailysatori.recording.RETRY_PERSIST"
        const val ACTION_OPEN = "com.dailysatori.recording.OPEN"
        const val EXTRA_DIARY_ID = "diaryId"
        const val EXTRA_ATTACHMENT_ID = "attachmentId"
        private const val TAG = "DiaryRecordingService"
        private const val DESTROY_TIMEOUT_MS = 1_500L

        fun startFromUserAction(
            context: Context,
            diaryId: Long,
            attachmentId: Long,
        ): DiaryRecordingLaunchResult {
            require(diaryId > 0) { "diaryId must be positive" }
            require(attachmentId > 0) { "attachmentId must be positive" }
            val intent = Intent(context, DiaryRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DIARY_ID, diaryId)
                putExtra(EXTRA_ATTACHMENT_ID, attachmentId)
            }
            return try {
                ContextCompat.startForegroundService(context, intent)
                DiaryRecordingLaunchResult.Started
            } catch (error: RuntimeException) {
                val errorCode = foregroundLaunchFailureCode(
                    sdkInt = Build.VERSION.SDK_INT,
                    error = error,
                    isApi31ForegroundStartDenied = isApi31ForegroundStartNotAllowed(error),
                ) ?: throw error
                Log.e(TAG, "Unable to request microphone foreground service: $errorCode", error)
                DiaryRecordingLaunchResult.Failed(errorCode)
            }
        }

        private fun isApi31ForegroundStartNotAllowed(error: RuntimeException): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error is ForegroundServiceStartNotAllowedException
    }
}

sealed interface DiaryRecordingLaunchResult {
    data object Started : DiaryRecordingLaunchResult
    data class Failed(val errorCode: String) : DiaryRecordingLaunchResult
}
