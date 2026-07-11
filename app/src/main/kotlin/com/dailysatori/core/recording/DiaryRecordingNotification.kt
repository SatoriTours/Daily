package com.dailysatori.core.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dailysatori.MainActivity
import com.dailysatori.R

class DiaryRecordingNotification(
    private val context: Context,
) {
    init {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.diary_recording_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = context.getString(R.string.diary_recording_channel_description)
        manager.createNotificationChannel(
            channel,
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
                context.getString(R.string.diary_recording_action_pause),
                serviceIntent(DiaryRecordingService.ACTION_PAUSE),
            )
            is DiaryRecordingState.Paused -> builder.addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.diary_recording_action_resume),
                serviceIntent(DiaryRecordingService.ACTION_RESUME),
            )
            else -> Unit
        }
        if (state.isControllableRecording()) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.diary_recording_action_stop),
                serviceIntent(DiaryRecordingService.ACTION_STOP),
            )
        }
        if (state is DiaryRecordingState.PersistenceFailed) {
            builder.addAction(
                android.R.drawable.ic_popup_sync,
                context.getString(R.string.diary_recording_action_retry),
                serviceIntent(DiaryRecordingService.ACTION_RETRY_PERSIST),
            )
            builder.addAction(
                android.R.drawable.ic_menu_delete,
                context.getString(R.string.diary_recording_action_discard),
                serviceIntent(DiaryRecordingService.ACTION_STOP),
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_view,
            context.getString(R.string.diary_recording_action_open),
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
        is DiaryRecordingState.Starting -> context.getString(R.string.diary_recording_starting)
        is DiaryRecordingState.Recording -> context.getString(R.string.diary_recording_active)
        is DiaryRecordingState.Paused -> context.getString(R.string.diary_recording_paused)
        is DiaryRecordingState.Stopping -> context.getString(R.string.diary_recording_stopping)
        is DiaryRecordingState.Failed -> context.getString(R.string.diary_recording_failed)
        is DiaryRecordingState.PersistenceFailed -> context.getString(R.string.diary_recording_failed)
        DiaryRecordingState.Idle -> context.getString(R.string.diary_recording_idle)
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1_000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun DiaryRecordingState.isActive(): Boolean =
        this is DiaryRecordingState.Starting ||
            this is DiaryRecordingState.Recording ||
            this is DiaryRecordingState.Paused ||
            this is DiaryRecordingState.Stopping ||
            this is DiaryRecordingState.PersistenceFailed

    private fun DiaryRecordingState.isControllableRecording(): Boolean =
        this is DiaryRecordingState.Starting ||
            this is DiaryRecordingState.Recording ||
            this is DiaryRecordingState.Paused

    companion object {
        const val NOTIFICATION_ID = 2_003
        private const val CHANNEL_ID = "diary_recording"
        private const val OPEN_REQUEST_CODE = 20_030
    }
}
