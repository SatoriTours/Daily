package com.dailysatori.core.recording

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.dailysatori.data.repository.DiaryAttachmentRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject

class DiaryRecordingService : Service() {
    private val store: DiaryRecordingStore by inject()
    private val recorder: DiaryRecorder by inject()
    private val attachmentRepository: DiaryAttachmentRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorderMutex = Mutex()
    private lateinit var notification: DiaryRecordingNotification
    private var ticker: Job? = null
    private var activeAttachmentId: Long? = null
    private var activeOutputFile: File? = null
    private var normalStop = false

    override fun onCreate() {
        super.onCreate()
        notification = DiaryRecordingNotification(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val diaryId = intent.getLongExtra(EXTRA_DIARY_ID, 0)
                val attachmentId = intent.getLongExtra(EXTRA_ATTACHMENT_ID, 0)
                enterForeground(diaryId)
                startRecording(diaryId, attachmentId)
            }
            ACTION_PAUSE -> scope.launch { recorderMutex.withLock { pauseRecording() } }
            ACTION_RESUME -> scope.launch { recorderMutex.withLock { resumeRecording() } }
            ACTION_STOP -> scope.launch { recorderMutex.withLock { stopRecording() } }
            ACTION_OPEN -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ticker?.cancel()
        if (!normalStop) preserveInterruptedRecording()
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(diaryId: Long) {
        val starting = DiaryRecordingState.Starting(diaryId.coerceAtLeast(0), 0)
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
    }

    private fun startRecording(diaryId: Long, attachmentId: Long) {
        scope.launch {
            recorderMutex.withLock {
                if (!store.start(diaryId, attachmentId)) {
                    handleFailure(DiaryRecordingErrorCode.INVALID_STATE, attachmentId.takeIf { it > 0 })
                    return@withLock
                }
                activeAttachmentId = attachmentId
                val output = File(filesDir, "DailySatori/diary/audio/$diaryId/$attachmentId.m4a")
                activeOutputFile = output
                try {
                    recorder.start(output)
                    store.markRecording()
                    notification.notify(store.state.value, diaryId)
                    startTicker(diaryId)
                } catch (error: DiaryRecorderException) {
                    handleFailure(error.errorCode, attachmentId)
                } catch (_: Exception) {
                    handleFailure(DiaryRecordingErrorCode.START_FAILED, attachmentId)
                }
            }
        }
    }

    private fun pauseRecording() {
        if (store.state.value !is DiaryRecordingState.Recording) return
        try {
            recorder.pause()
            store.pause()
            notifyCurrent()
        } catch (error: DiaryRecorderException) {
            handleFailure(error.errorCode, activeAttachmentId)
        }
    }

    private fun resumeRecording() {
        if (store.state.value !is DiaryRecordingState.Paused) return
        try {
            recorder.resume()
            store.resume()
            notifyCurrent()
        } catch (error: DiaryRecorderException) {
            handleFailure(error.errorCode, activeAttachmentId)
        }
    }

    private fun stopRecording() {
        if (!store.stop()) return
        ticker?.cancel()
        notifyCurrent()
        val attachmentId = activeAttachmentId ?: return handleFailure(DiaryRecordingErrorCode.PERSIST_FAILED, null)
        try {
            val output = recorder.stop()
            attachmentRepository.completeRecording(
                id = attachmentId,
                localPath = output.file.absolutePath,
                sizeBytes = output.file.length(),
                durationMs = output.durationMs,
            )
            normalStop = true
            activeAttachmentId = null
            activeOutputFile = null
            store.complete()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (error: DiaryRecorderException) {
            handleFailure(error.errorCode, attachmentId)
        } catch (_: Exception) {
            handleFailure(DiaryRecordingErrorCode.PERSIST_FAILED, attachmentId)
        }
    }

    private fun startTicker(diaryId: Long) {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                delay(1_000)
                store.refreshElapsed()
                notification.notify(store.state.value, diaryId)
            }
        }
    }

    private fun notifyCurrent() {
        val state = store.state.value
        notification.notify(state, state.diaryId ?: return)
    }

    private fun handleFailure(errorCode: String, attachmentId: Long?) {
        ticker?.cancel()
        val partial = recorder.releasePreservingOutput()
        if (attachmentId != null) {
            runCatching {
                attachmentRepository.failRecording(
                    id = attachmentId,
                    localPath = partial?.file?.absolutePath ?: activeOutputFile?.absolutePath.orEmpty(),
                    sizeBytes = partial?.file?.length() ?: activeOutputFile?.takeIf(File::exists)?.length() ?: 0,
                    durationMs = partial?.durationMs ?: 0,
                    errorCode = errorCode,
                )
            }
        }
        store.fail(errorCode)
        notifyCurrent()
        activeAttachmentId = null
        activeOutputFile = null
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun preserveInterruptedRecording() {
        val attachmentId = activeAttachmentId ?: return
        val partial = recorder.releasePreservingOutput()
        runCatching {
            attachmentRepository.failRecording(
                id = attachmentId,
                localPath = partial?.file?.absolutePath ?: activeOutputFile?.absolutePath.orEmpty(),
                sizeBytes = partial?.file?.length() ?: activeOutputFile?.takeIf(File::exists)?.length() ?: 0,
                durationMs = partial?.durationMs ?: 0,
                errorCode = DiaryRecordingErrorCode.FINALIZE_FAILED,
            )
        }
        store.fail(DiaryRecordingErrorCode.FINALIZE_FAILED)
    }

    companion object {
        const val ACTION_START = "com.dailysatori.recording.START"
        const val ACTION_PAUSE = "com.dailysatori.recording.PAUSE"
        const val ACTION_RESUME = "com.dailysatori.recording.RESUME"
        const val ACTION_STOP = "com.dailysatori.recording.STOP"
        const val ACTION_OPEN = "com.dailysatori.recording.OPEN"
        const val EXTRA_DIARY_ID = "diaryId"
        const val EXTRA_ATTACHMENT_ID = "attachmentId"

        fun startFromUserAction(context: Context, diaryId: Long, attachmentId: Long) {
            require(diaryId > 0) { "diaryId must be positive" }
            require(attachmentId > 0) { "attachmentId must be positive" }
            val intent = Intent(context, DiaryRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DIARY_ID, diaryId)
                putExtra(EXTRA_ATTACHMENT_ID, attachmentId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
