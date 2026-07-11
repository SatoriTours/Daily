package com.dailysatori.core.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dailysatori.MainActivity

class DiaryRecordingNotification(
    private val context: Context,
) {
    init {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Voice diary recording", NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun build(state: DiaryRecordingState, diaryId: Long): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(titleFor(state))
            .setContentText(formatElapsed(state.elapsedMs))
            .setContentIntent(openDiaryIntent(diaryId))
            .setOngoing(state.isActive())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        when (state) {
            is DiaryRecordingState.Recording -> builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                serviceIntent(DiaryRecordingService.ACTION_PAUSE),
            )
            is DiaryRecordingState.Paused -> builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                serviceIntent(DiaryRecordingService.ACTION_RESUME),
            )
            else -> Unit
        }
        if (state.isActive()) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                serviceIntent(DiaryRecordingService.ACTION_STOP),
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_view,
            "Open diary",
            openDiaryIntent(diaryId),
        )
        return builder.build()
    }

    fun notify(state: DiaryRecordingState, diaryId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, build(state, diaryId))
    }

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, DiaryRecordingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openDiaryIntent(diaryId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_REQUEST_CODE,
        Intent(context, MainActivity::class.java).apply {
            action = DiaryRecordingService.ACTION_OPEN
            putExtra(DiaryRecordingService.EXTRA_DIARY_ID, diaryId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun titleFor(state: DiaryRecordingState): String = when (state) {
        is DiaryRecordingState.Starting -> "Starting voice diary"
        is DiaryRecordingState.Recording -> "Recording voice diary"
        is DiaryRecordingState.Paused -> "Voice diary paused"
        is DiaryRecordingState.Stopping -> "Saving voice diary"
        is DiaryRecordingState.Failed -> "Voice diary recording failed"
        DiaryRecordingState.Idle -> "Voice diary"
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1_000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun DiaryRecordingState.isActive(): Boolean =
        this is DiaryRecordingState.Starting ||
            this is DiaryRecordingState.Recording ||
            this is DiaryRecordingState.Paused ||
            this is DiaryRecordingState.Stopping

    companion object {
        const val NOTIFICATION_ID = 2_003
        private const val CHANNEL_ID = "diary_recording"
        private const val OPEN_REQUEST_CODE = 20_030
    }
}
