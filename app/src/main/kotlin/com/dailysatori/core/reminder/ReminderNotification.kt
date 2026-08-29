package com.dailysatori.core.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dailysatori.MainActivity
import com.dailysatori.R
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

data class ReminderNotificationPolicy(
    val visible: Boolean,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val lockScreenText: String,
    val importance: ReminderImportance,
    val lockScreenVisibility: ReminderLockScreenVisibility,
) {
    companion object {
        fun forDelivery(reminder: Reminder, at: Instant): ReminderNotificationPolicy {
            val local = at.toLocalDateTime(reminder.timeZone)
            val workHours = local.date.dayOfWeek in reminder.profile.workDays &&
                local.time >= reminder.profile.workStart && local.time < reminder.profile.workEnd
            return ReminderNotificationPolicy(
                visible = true,
                soundEnabled = reminder.profile.soundEnabled && !workHours,
                vibrationEnabled = reminder.profile.vibrationEnabled && !workHours,
                lockScreenText = "You have a reminder",
                importance = reminder.profile.importance,
                lockScreenVisibility = reminder.profile.lockScreenVisibility,
            )
        }
    }
}

data class ReminderNotificationPost(
    val reminder: Reminder,
    val policy: ReminderNotificationPolicy,
)

interface ReminderNotifier {
    fun post(post: ReminderNotificationPost)
    fun cancel(id: String)
}

class AndroidReminderNotification(
    private val context: Context,
) : ReminderNotifier {
    override fun post(post: ReminderNotificationPost) {
        ensureChannels()
        if (!canPostNotifications()) return
        NotificationManagerCompat.from(context).notify(notificationId(post.reminder.id), build(post))
    }

    override fun cancel(id: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(id))
    }

    private fun build(post: ReminderNotificationPost): Notification {
        val reminder = post.reminder
        val policy = post.policy
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Reminder")
            .setContentText(policy.lockScreenText)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val builder = NotificationCompat.Builder(context, channelId(policy))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Reminder")
            .setContentText(reminder.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.content))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(policy.lockScreenVisibility.compatValue())
            .setContentIntent(viewIntent(reminder))
            .setDeleteIntent(receiverIntent(ReminderReceiver.ACTION_DISMISS, reminder, ReminderIntentIdentity.dismiss(reminder.id, reminder.version)))
            .addAction(0, "Complete", receiverIntent(ReminderReceiver.ACTION_COMPLETE, reminder, ReminderIntentIdentity.complete(reminder.id, reminder.version)))
            .setAutoCancel(false)
        if (policy.lockScreenVisibility == ReminderLockScreenVisibility.PRIVATE) builder.setPublicVersion(publicVersion)
        return builder.build()
    }

    private fun viewIntent(reminder: Reminder): PendingIntent {
        val identity = ReminderIntentIdentity.view(reminder.id, reminder.version)
        return PendingIntent.getActivity(
            context,
            identity.requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(ReminderCoordinator.ACTION_VIEW_REMINDER)
                .setData(Uri.parse(identity.uri))
                .putExtra(ReminderCoordinator.EXTRA_REMINDER_ID, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun receiverIntent(action: String, reminder: Reminder, identity: ReminderIntentIdentity): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse(identity.uri))
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            .putExtra(ReminderReceiver.EXTRA_EXPECTED_VERSION, reminder.version)
        return PendingIntent.getBroadcast(
            context,
            identity.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        channelDefinitions.forEach { definition ->
            ReminderImportance.entries.forEach { importance ->
                val channel = NotificationChannel(definition.idFor(importance), definition.name, importance.androidValue())
                channel.enableVibration(definition.vibration)
                channel.setSound(if (definition.sound) sound else null, if (definition.sound) audio else null)
                // Allow each reminder's per-notification visibility to decide what the lock screen reveals.
                channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channelId(policy: ReminderNotificationPolicy): String = (when {
        policy.soundEnabled && policy.vibrationEnabled -> CHANNEL_SOUND_VIBRATION
        policy.soundEnabled -> CHANNEL_SOUND
        policy.vibrationEnabled -> CHANNEL_VIBRATION
        else -> CHANNEL_SILENT
    }) + policy.importance.channelSuffix()

    private fun notificationId(id: String) = id.hashCode()

    private data class ChannelDefinition(val id: String, val name: String, val sound: Boolean, val vibration: Boolean)

    private fun ChannelDefinition.idFor(importance: ReminderImportance) = id + importance.channelSuffix()
    private fun ReminderImportance.channelSuffix() = when (this) { ReminderImportance.HIGH -> ""; ReminderImportance.DEFAULT -> "-default"; ReminderImportance.LOW -> "-low" }
    private fun ReminderImportance.androidValue() = when (this) { ReminderImportance.HIGH -> NotificationManager.IMPORTANCE_HIGH; ReminderImportance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT; ReminderImportance.LOW -> NotificationManager.IMPORTANCE_LOW }
    private fun ReminderLockScreenVisibility.compatValue() = when (this) { ReminderLockScreenVisibility.PUBLIC -> NotificationCompat.VISIBILITY_PUBLIC; ReminderLockScreenVisibility.PRIVATE -> NotificationCompat.VISIBILITY_PRIVATE; ReminderLockScreenVisibility.SECRET -> NotificationCompat.VISIBILITY_SECRET }

    private companion object {
        const val CHANNEL_SOUND_VIBRATION = "reminder-sound-vibration-v1"
        const val CHANNEL_SOUND = "reminder-sound-v1"
        const val CHANNEL_VIBRATION = "reminder-vibration-v1"
        const val CHANNEL_SILENT = "reminder-silent-v1"
        val channelDefinitions = listOf(
            ChannelDefinition(CHANNEL_SOUND_VIBRATION, "Reminders: sound and vibration", sound = true, vibration = true),
            ChannelDefinition(CHANNEL_SOUND, "Reminders: sound", sound = true, vibration = false),
            ChannelDefinition(CHANNEL_VIBRATION, "Reminders: vibration", sound = false, vibration = true),
            ChannelDefinition(CHANNEL_SILENT, "Reminders: silent", sound = false, vibration = false),
        )
    }
}
