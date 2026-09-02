package com.dailysatori.core.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dailysatori.MainActivity
import com.dailysatori.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ReminderAiParseNotifier {
    fun notifyReady(batchId: String)
    fun notifyFailed(batchId: String)

    companion object {
        const val ACTION_VIEW_BATCH = "com.dailysatori.action.VIEW_REMINDER_AI_BATCH"
        const val EXTRA_BATCH_ID = "reminder_ai_batch_id"
    }
}

object NoOpReminderAiParseNotifier : ReminderAiParseNotifier {
    override fun notifyReady(batchId: String) = Unit
    override fun notifyFailed(batchId: String) = Unit
}

data class ReminderAiParseNotificationIdentity(val batchId: String) {
    val uri: String = "dailysatori://reminder-ai/batch/${java.net.URLEncoder.encode(batchId, Charsets.UTF_8.name())}"
}

data class ReminderAiParseNotificationCopy(val title: String, val text: String)

fun reminderAiParseNotificationCopy(ready: Boolean) = if (ready) {
    ReminderAiParseNotificationCopy("提醒已解析，等待确认", "点击确认提醒草稿")
} else {
    ReminderAiParseNotificationCopy("提醒解析失败，点击处理", "查看原文并重新解析")
}

const val ReminderAiParseNotificationPendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

data class ReminderAiParseNotificationPost(
    val batchId: String,
    val copy: ReminderAiParseNotificationCopy,
    val identity: ReminderAiParseNotificationIdentity,
    val action: String = ReminderAiParseNotifier.ACTION_VIEW_BATCH,
    val extras: Map<String, String> = mapOf(ReminderAiParseNotifier.EXTRA_BATCH_ID to batchId),
    val pendingIntentFlags: Int = ReminderAiParseNotificationPendingIntentFlags,
)

interface ReminderAiParseNotificationPoster {
    fun post(post: ReminderAiParseNotificationPost)
}

class AndroidReminderAiParseNotification(
    private val poster: ReminderAiParseNotificationPoster,
) : ReminderAiParseNotifier {
    constructor(context: Context) : this(AndroidReminderAiParseNotificationPoster(context))

    override fun notifyReady(batchId: String) = post(batchId, ready = true)

    override fun notifyFailed(batchId: String) = post(batchId, ready = false)

    private fun post(batchId: String, ready: Boolean) {
        poster.post(ReminderAiParseNotificationPost(
            batchId = batchId,
            copy = reminderAiParseNotificationCopy(ready),
            identity = ReminderAiParseNotificationIdentity(batchId),
        ))
    }
}

private class AndroidReminderAiParseNotificationPoster(private val context: Context) : ReminderAiParseNotificationPoster {
    override fun post(post: ReminderAiParseNotificationPost) {
        ensureChannel()
        if (!canPostNotifications()) return
        NotificationManagerCompat.from(context).notify(
            "reminder-ai:${post.batchId}",
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(post.copy.title)
                .setContentText(post.copy.text)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(viewIntent(post))
                .build(),
        )
    }

    private fun viewIntent(post: ReminderAiParseNotificationPost): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(post.action)
                .setData(Uri.parse(post.identity.uri))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { post.extras.forEach(::putExtra) },
            post.pendingIntentFlags,
        )
    }

    private fun ensureChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "提醒解析", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "reminder-ai-parse-v1"
        const val NOTIFICATION_ID = 1
    }
}

class ReminderAiBatchOpenRequestState {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun open(batchId: String) {
        if (batchId.isNotBlank()) _pending.value = batchId
    }

    fun consume(batchId: String) {
        _pending.compareAndSet(batchId, null)
    }
}

object ReminderAiBatchOpenRequest {
    val state = ReminderAiBatchOpenRequestState()
}

fun handleReminderAiBatchViewIntent(
    intent: Intent?,
    target: ReminderAiBatchOpenRequestState = ReminderAiBatchOpenRequest.state,
) {
    handleReminderAiBatchViewIntent(intent?.action, intent?.getStringExtra(ReminderAiParseNotifier.EXTRA_BATCH_ID), target)
}

fun handleReminderAiBatchViewIntent(action: String?, batchId: String?, target: ReminderAiBatchOpenRequestState) {
    reminderAiBatchIdForViewIntent(action, batchId)?.let(target::open)
}

fun reminderAiBatchIdForViewIntent(action: String?, batchId: String?): String? =
    batchId?.takeIf { action == ReminderAiParseNotifier.ACTION_VIEW_BATCH && it.isNotBlank() }

fun consumeReminderAiBatchOpenRequest(
    request: ReminderAiBatchOpenRequestState,
    navigate: (String) -> Unit,
): Boolean {
    val batchId = request.pending.value ?: return false
    navigate(batchId)
    request.consume(batchId)
    return true
}
