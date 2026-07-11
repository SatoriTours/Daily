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
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.koin.android.ext.android.inject

class DiaryRecordingService : Service() {
    private val store: DiaryRecordingStore by inject()
    private val recorder: DiaryRecorder by inject()
    private val attachmentRepository: DiaryAttachmentRepository by inject()
    private val actorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionTokens = AtomicLong()

    private lateinit var notification: DiaryRecordingNotification
    private lateinit var actor: DiaryRecordingActor
    @Volatile private var androidResourcesOpen = true
    @Volatile private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        notification = DiaryRecordingNotification(this)
        val recorderDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "diary-recorder")
        }.asCoroutineDispatcher()
        actor = DiaryRecordingActor(
            store = store,
            recorder = recorder,
            persistence = object : DiaryRecordingPersistence {
                override suspend fun begin(diaryId: Long, attachmentId: Long) {
                    attachmentRepository.beginRecording(diaryId = diaryId, id = attachmentId)
                }

                override suspend fun complete(
                    diaryId: Long,
                    attachmentId: Long,
                    output: DiaryRecordingOutput,
                ) {
                    attachmentRepository.completeRecording(
                        diaryId = diaryId,
                        id = attachmentId,
                        localPath = output.file.absolutePath,
                        sizeBytes = output.file.length(),
                        durationMs = output.durationMs,
                    )
                }

                override suspend fun fail(
                    diaryId: Long,
                    attachmentId: Long,
                    output: DiaryRecordingOutput?,
                    errorCode: String,
                ) {
                    attachmentRepository.failRecording(
                        diaryId = diaryId,
                        id = attachmentId,
                        localPath = output?.file?.absolutePath,
                        sizeBytes = output?.file?.length() ?: 0,
                        durationMs = output?.durationMs ?: 0,
                        errorCode = errorCode,
                    )
                }
            },
            outputFile = { diaryId, attachmentId ->
                File(filesDir, "DailySatori/diary/audio/$diaryId/$attachmentId.m4a")
            },
            host = object : DiaryRecordingActorHost {
                override fun enterForeground(state: DiaryRecordingState): String? =
                    this@DiaryRecordingService.enterForeground(state)

                override fun stateChanged(state: DiaryRecordingState) {
                    if (!androidResourcesOpen || !foregroundStarted) return
                    val diaryId = state.diaryId ?: return
                    runCatching { notification.notify(state, diaryId) }
                        .onFailure { Log.e(TAG, "Unable to update recording notification", it) }
                }

                override fun stopForeground() {
                    this@DiaryRecordingService.stopRecordingForeground()
                }

                override fun stopService() {
                    if (androidResourcesOpen) stopSelf()
                }
            },
            scope = actorScope,
            actorDispatcher = Dispatchers.Default,
            recorderDispatcher = recorderDispatcher,
            ioDispatcher = Dispatchers.IO,
            nowMs = System::currentTimeMillis,
            nextSessionToken = sessionTokens::incrementAndGet,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val diaryId = intent.getLongExtra(EXTRA_DIARY_ID, 0)
                val attachmentId = intent.getLongExtra(EXTRA_ATTACHMENT_ID, 0)
                if (diaryId > 0 && attachmentId > 0) {
                    actor.submit(DiaryRecordingCommand.Start(diaryId, attachmentId))
                } else {
                    Log.w(TAG, "Rejected invalid recording start")
                }
            }
            ACTION_PAUSE -> actor.submit(DiaryRecordingCommand.Pause)
            ACTION_RESUME -> actor.submit(DiaryRecordingCommand.Resume)
            ACTION_STOP -> actor.submit(DiaryRecordingCommand.Stop)
            ACTION_RETRY_PERSIST -> actor.submit(DiaryRecordingCommand.RetryPersistence)
            ACTION_OPEN -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        actor.submit(DiaryRecordingCommand.Shutdown)
        androidResourcesOpen = false
        stopRecordingForeground()
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
