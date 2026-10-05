package com.dailysatori.core.bookkeeping

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dailysatori.core.phone.PhoneIntake
import com.dailysatori.service.phone.*
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.koin.android.ext.android.inject

data class BookkeepingCaptureStatus(val connected: Boolean = false, val failed: Boolean = false, val lastStoredAt: Long? = null)

class BookkeepingCaptureMonitor {
    private val mutableState = MutableStateFlow(BookkeepingCaptureStatus())
    val state: StateFlow<BookkeepingCaptureStatus> = mutableState.asStateFlow()
    fun connected(value: Boolean) = mutableState.update { it.copy(connected = value) }
    fun stored(at: Long) = mutableState.update { it.copy(failed = false, lastStoredAt = at) }
    fun failed() = mutableState.update { it.copy(failed = true) }
}

class BookkeepingNotificationListener : NotificationListenerService() {
    private val intake: PhoneIntake by inject()
    private val monitor: BookkeepingCaptureMonitor by inject()
    private val identity: PhoneNotificationIdentity by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captures = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (capture in captures) {
                try { capture() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { monitor.failed() }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        monitor.connected(true)
        val activeEvents = try {
            activeNotifications.orEmpty().associate { it.key to (it.notification.`when`.takeIf { time -> time > 0 } ?: it.postTime) }
        } catch (_: Exception) { monitor.failed(); return }
        captures.trySend { identity.reconcile(activeEvents) }
    }
    override fun onListenerDisconnected() { monitor.connected(false); super.onListenerDisconnected() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE)).toStringOrEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        phoneNotificationText(sbn.packageName, title, body.toString(),
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0, packageName) ?: return
        val source = sbn.packageName
        val notificationKey = sbn.key
        val postedAt = sbn.postTime
        val mirrorsSms = source == Telephony.Sms.getDefaultSmsPackage(this) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val text = body.toString()
        val zone = TimeZone.currentSystemDefault().id
        captures.trySend {
            val event = PhoneEvent(PhoneChannel.NOTIFICATION, source, identity.key(notificationKey, postedAt), title, text,
                postedAt, zone, mirrorsSms)
            if (intake.accept(event)) monitor.stored(postedAt)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = sbn.key
        captures.trySend { identity.removed(key) }
    }

    override fun onDestroy() { monitor.connected(false); captures.close(); scope.cancel(); super.onDestroy() }

    private fun CharSequence?.toStringOrEmpty(): String = this?.toString().orEmpty()
}
