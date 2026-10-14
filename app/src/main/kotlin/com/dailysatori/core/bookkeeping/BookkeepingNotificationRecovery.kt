package com.dailysatori.core.bookkeeping

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.dailysatori.service.phone.PhoneAssistantService
import kotlinx.coroutines.*

internal fun hasNotificationListenerAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 27) {
    context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(
        ComponentName(context, BookkeepingNotificationListener::class.java))
} else context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

/** One application-scoped recovery survives service recreation and leaving the authorization page. */
class BookkeepingNotificationRecovery internal constructor(
    private val monitor: BookkeepingCaptureMonitor,
    private val scope: CoroutineScope,
    private val allowed: () -> Boolean,
    private val rebind: () -> Unit,
) {
    constructor(context: Context, service: PhoneAssistantService, monitor: BookkeepingCaptureMonitor) : this(
        monitor, CoroutineScope(SupervisorJob() + Dispatchers.IO),
        allowed = {
            val preferences = service.preferences()
            val options = preferences.notification
            options.enabled && (options.todos || options.ledger) && preferences.sources.isNotEmpty() &&
                hasNotificationListenerAccess(context)
        },
        rebind = {
            NotificationListenerService.requestRebind(ComponentName(context, BookkeepingNotificationListener::class.java))
        },
    )

    private var retryJob: Job? = null

    @Synchronized
    fun request() {
        if (!canReconnect()) {
            cancel()
            return
        }
        if (retryJob?.isActive == true) return
        // Assign before starting: an immediate connection callback must be able to cancel this job.
        retryJob = scope.launch(start = CoroutineStart.LAZY) {
            for (waitMs in listOf(0L, 1_000L, 5_000L)) {
                delay(waitMs)
                if (!canReconnect()) return@launch
                try { rebind() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { monitor.failed() }
            }
        }
        retryJob?.start()
    }

    @Synchronized
    fun connected() {
        monitor.connected(true)
        cancel()
    }

    private fun canReconnect(): Boolean = try {
        !monitor.state.value.connected && allowed()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        monitor.failed()
        false
    }

    private fun cancel() {
        retryJob?.cancel()
        retryJob = null
    }
}
