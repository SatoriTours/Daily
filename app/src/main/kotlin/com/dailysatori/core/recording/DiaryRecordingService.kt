package com.dailysatori.core.recording

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.koin.android.ext.android.inject

class DiaryRecordingService : Service() {
    private val runtimeManager: DiaryRecordingRuntimeManager by inject()

    private lateinit var notification: DiaryRecordingNotification
    @Volatile private var androidResourcesOpen = true
    @Volatile private var foregroundStarted = false
    private var lastNotificationPresentationKey: String? = null
    private var recordingWakeLock: PowerManager.WakeLock? = null

    private val androidHost = object : DiaryRecordingAndroidHost {
        override fun enterForeground(state: DiaryRecordingState): String? =
            this@DiaryRecordingService.enterForeground(state)

        override fun stateChanged(state: DiaryRecordingState) {
            if (!androidResourcesOpen || !foregroundStarted) return
            updateRecordingWakeLockSafely(state)
            val diaryId = state.diaryId ?: return
            val presentationKey = notificationPresentationKey(state)
            if (lastNotificationPresentationKey == presentationKey) return
            runCatching { notification.notify(state, diaryId) }
                .onSuccess { lastNotificationPresentationKey = presentationKey }
                .onFailure { Log.e(TAG, "Unable to update recording notification", it) }
        }

        override fun stopForeground() {
            this@DiaryRecordingService.stopRecordingForeground()
        }

        override fun stopSelfResult(startId: Int): Boolean {
            return androidResourcesOpen && this@DiaryRecordingService.stopSelfResult(startId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notification = DiaryRecordingNotification(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val diaryId = intent.getLongExtra(EXTRA_DIARY_ID, 0)
                val attachmentId = intent.getLongExtra(EXTRA_ATTACHMENT_ID, 0)
                if (diaryId > 0 && attachmentId > 0) {
                    runtimeManager.attachAndSubmit(
                        androidHost,
                        startId,
                        DiaryRecordingCommand.Start(diaryId, attachmentId),
                    )
                } else {
                    Log.w(TAG, "Rejected invalid recording start")
                    stopSelfResult(startId)
                }
            }
            ACTION_PAUSE -> runtimeManager.submit(DiaryRecordingCommand.Pause, startId)
            ACTION_RESUME -> runtimeManager.submit(DiaryRecordingCommand.Resume, startId)
            ACTION_STOP -> runtimeManager.submit(DiaryRecordingCommand.Stop, startId)
            ACTION_RETRY_PERSIST -> runtimeManager.submit(
                DiaryRecordingCommand.RetryPersistence,
                startId,
            )
            ACTION_OPEN -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runtimeManager.detachHost(androidHost)
        androidResourcesOpen = false
        stopRecordingForeground()
        releaseRecordingWakeLock()
        super.onDestroy()
    }

    private fun enterForeground(state: DiaryRecordingState): String? {
        if (!androidResourcesOpen) return DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED
        val diaryId = state.diaryId ?: return DiaryRecordingErrorCode.INVALID_STATE
        return try {
            val foregroundNotification = notification.build(state, diaryId)
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
            lastNotificationPresentationKey = notificationPresentationKey(state)
            updateRecordingWakeLockSafely(state)
            null
        } catch (error: RuntimeException) {
            foregroundLaunchFailureCode(
                sdkInt = Build.VERSION.SDK_INT,
                error = error,
                isApi31ForegroundStartDenied = isApi31ForegroundStartNotAllowed(error),
            )?.also { Log.e(TAG, "Unable to enter microphone foreground service: $it", error) }
                ?: throw error
        }
    }

    private fun stopRecordingForeground() {
        if (!foregroundStarted) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        lastNotificationPresentationKey = null
        releaseRecordingWakeLock()
    }

    private fun updateRecordingWakeLock(state: DiaryRecordingState) {
        val needsWakeLock = state is DiaryRecordingState.Starting || state is DiaryRecordingState.Recording
        if (!needsWakeLock) {
            releaseRecordingWakeLock()
            return
        }
        val lock = recordingWakeLock ?: run {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:diary-recording").apply {
                setReferenceCounted(false)
            }.also { recordingWakeLock = it }
        }
        if (!lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun updateRecordingWakeLockSafely(state: DiaryRecordingState) {
        runCatching { updateRecordingWakeLock(state) }
            .onFailure { Log.e(TAG, "Unable to update recording wake lock", it) }
    }

    private fun releaseRecordingWakeLock() {
        recordingWakeLock?.let { if (it.isHeld) it.release() }
        recordingWakeLock = null
    }

    private fun notificationPresentationKey(state: DiaryRecordingState): String {
        val reminderBucket = if (state is DiaryRecordingState.Recording) {
            state.elapsedMs / LONG_RECORDING_REMINDER_INTERVAL_MS
        } else {
            0
        }
        return "${state.javaClass.name}:$reminderBucket"
    }

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
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1_000L

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
