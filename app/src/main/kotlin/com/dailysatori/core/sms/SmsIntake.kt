package com.dailysatori.core.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.SmsSourceRepository
import com.dailysatori.service.sms.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.koin.core.context.GlobalContext
import java.security.MessageDigest

internal fun smsFingerprint(sender: String, body: String, sentAt: Long, subscription: Int): String {
    val bytes = kotlinx.serialization.json.buildJsonArray {
        add(kotlinx.serialization.json.JsonPrimitive(sender))
        add(kotlinx.serialization.json.JsonPrimitive(body))
        add(kotlinx.serialization.json.JsonPrimitive(sentAt))
        add(kotlinx.serialization.json.JsonPrimitive(subscription))
    }.toString().toByteArray()
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

class SmsIntake(
    private val service: SmsReminderService, private val sources: SmsSourceRepository,
    private val scheduler: AsyncTaskScheduler, private val notifier: SmsPendingNotifier,
    private val coordinator: com.dailysatori.core.reminder.ReminderCoordinator,
) {
    fun accept(id: String, source: SmsSource, received: Instant, zone: TimeZone, manual: Boolean = false) {
        service.accept(id, source, received, zone, manual)?.let(scheduler::enqueue)
        val row = sources.get(id) ?: return
        if (row.status == SmsSourceStatus.CREATED) {
            notifier.cancel(id)
            row.reminderId?.let(coordinator::recompute)
        }
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return
        val receivedAt = Clock.System.now()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        if (messages.isEmpty()) return
        val source = SmsSource(messages.first().originatingAddress.orEmpty(), messages.joinToString("") { it.messageBody.orEmpty() })
        val id = smsFingerprint(source.sender, source.body, messages.first().timestampMillis, intent.getIntExtra("subscription", -1))
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    val koin = GlobalContext.get()
                    if (koin.get<SettingRepository>().get(SmsReminderService.ENABLED_KEY) == "true")
                        koin.get<SmsIntake>().accept(id, source, receivedAt, TimeZone.currentSystemDefault())
                }
            } catch (_: Exception) {
                android.util.Log.w("SmsReceiver", "SMS intake could not finish; no message content logged")
            } finally { pending.finish() }
        }
    }
}
