package com.dailysatori.core.bookkeeping

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dailysatori.service.bookkeeping.BookkeepingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.android.ext.android.inject
import java.security.MessageDigest

data class BookkeepingCaptureStatus(val connected: Boolean = false, val failed: Boolean = false, val lastStoredAt: Long? = null)

class BookkeepingCaptureMonitor {
    private val mutableState = MutableStateFlow(BookkeepingCaptureStatus())
    val state: StateFlow<BookkeepingCaptureStatus> = mutableState.asStateFlow()
    fun connected(value: Boolean) = mutableState.update { it.copy(connected = value) }
    fun stored(at: Long) = mutableState.update { it.copy(failed = false, lastStoredAt = at) }
    fun failed() = mutableState.update { it.copy(failed = true) }
}

class BookkeepingNotificationListener : NotificationListenerService() {
    private val service: BookkeepingService by inject()
    private val monitor: BookkeepingCaptureMonitor by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() { super.onListenerConnected(); monitor.connected(true) }
    override fun onListenerDisconnected() { monitor.connected(false); super.onListenerDisconnected() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE)).toStringOrEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        val text = financialNotificationText(sbn.packageName, title, body.toString(),
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) ?: return
        val source = sbn.packageName
        val eventKey = MessageDigest.getInstance("SHA-256").digest("${sbn.key}\n${sbn.postTime}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val postedAt = sbn.postTime
        scope.launch {
            try {
                if (service.accept(source, eventKey, text, postedAt)) monitor.stored(postedAt)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { monitor.failed() }
        }
    }

    override fun onDestroy() { monitor.connected(false); scope.cancel(); super.onDestroy() }

    private fun CharSequence?.toStringOrEmpty(): String = this?.toString().orEmpty()
}
