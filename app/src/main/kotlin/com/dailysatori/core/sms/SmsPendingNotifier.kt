package com.dailysatori.core.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dailysatori.MainActivity
import com.dailysatori.R
import com.dailysatori.data.repository.SmsSourceRepository
import com.dailysatori.service.i18n.I18nService
import kotlinx.coroutines.flow.MutableStateFlow

object SmsOpenRequest {
    const val ACTION = "com.dailysatori.sms.OPEN"
    val pending = MutableStateFlow(false)
    fun handle(intent: Intent?) { if (intent?.action == ACTION) pending.value = true }
}

class SmsPendingNotifier(private val context: Context, private val sources: SmsSourceRepository, private val i18n: I18nService) {
    fun post(id: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || !sources.claimNotification(id)) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, i18n.t("sms.title"), NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).setAction(SmsOpenRequest.ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 910, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(i18n.t("sms.notification_title")).setContentText(i18n.t("sms.notification_body"))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).setAutoCancel(true).setContentIntent(open).build()
        try { manager.notify("sms:$id", 910, notification) }
        catch (_: SecurityException) { sources.resetNotification(id) }
    }
    fun cancel(id: String) = NotificationManagerCompat.from(context).cancel("sms:$id", 910)
    private companion object { const val CHANNEL = "sms_reminder_candidates" }
}
