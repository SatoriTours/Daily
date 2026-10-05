package com.dailysatori.ui.feature.settings.sms

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.reminder.ReminderCoordinator
import com.dailysatori.core.sms.SmsPendingNotifier
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.SmsSourceRepository
import com.dailysatori.service.sms.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SmsSettingsState(
    val enabled: Boolean = false, val cloudAllowed: Boolean = false,
    val smsPermission: Boolean = false, val notifications: Boolean = true, val exactAlarms: Boolean = true,
    val records: List<SmsSourceRecord> = emptyList(), val busy: Boolean = false, val messageKey: String? = null,
)

class SmsSettingsViewModel(
    private val context: Context, private val sources: SmsSourceRepository,
    private val service: SmsReminderService, private val settings: SettingRepository,
    private val scheduler: AsyncTaskScheduler,
    private val coordinator: ReminderCoordinator, private val notifier: SmsPendingNotifier,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SmsSettingsState())
    val state = mutableState.asStateFlow()
    init {
        refreshAccess()
        viewModelScope.launch(Dispatchers.IO) {
            sources.all().filter { it.status in setOf(SmsSourceStatus.PENDING, SmsSourceStatus.LOCAL_ONLY, SmsSourceStatus.EXPIRED) }.forEach {
                service.createLocal(it.id)?.let(coordinator::recompute)
                notifier.cancel(it.id)
            }
        }
        viewModelScope.launch(Dispatchers.IO) { sources.observe().collect { records -> mutableState.update { it.copy(records = records) } } }
    }

    fun refreshAccess() {
        val sms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val exact = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.update { it.copy(enabled = settings.get(SmsReminderService.ENABLED_KEY) == "true", cloudAllowed = settings.get(SmsReminderService.CLOUD_KEY) == "true",
                smsPermission = sms, notifications = notifications, exactAlarms = exact, records = sources.all()) }
        }
    }

    fun setMonitoring(enabled: Boolean) = runAction {
        settings.upsert(SmsReminderService.ENABLED_KEY, enabled.toString())
        mutableState.update { it.copy(enabled = enabled) }
    }
    fun setCloudAllowed(allowed: Boolean) = runAction {
        service.setCloudAllowed(allowed)
        mutableState.update { it.copy(cloudAllowed = allowed) }
    }
    fun syncPending() = runAction {
        sources.all().forEach { record ->
            when (record.status) {
                SmsSourceStatus.QUEUED -> scheduler.enqueue(sources.enqueue(record.id))
                SmsSourceStatus.FAILED -> service.retry(record.id)?.let(scheduler::enqueue)
                SmsSourceStatus.LOCAL_ONLY, SmsSourceStatus.PENDING, SmsSourceStatus.EXPIRED -> {
                    service.createLocal(record.id)?.let(coordinator::recompute)
                    notifier.cancel(record.id)
                }
                else -> Unit
            }
        }
        mutableState.update { it.copy(messageKey = "sms.sync_started") }
    }
    fun retry(record: SmsSourceRecord) = runAction {
        if (record.status == SmsSourceStatus.CREATED) record.reminderId?.let(coordinator::recompute)
        else service.retry(record.id)?.let(scheduler::enqueue)
    }
    fun ignore(record: SmsSourceRecord) = runAction { service.ignore(record.id); notifier.cancel(record.id) }
    fun block(record: SmsSourceRecord) = runAction { service.blockSender(record.source.sender); notifier.cancel(record.id) }
    fun clearBlockedSenders() = runAction { service.clearBlockedSenders() }

    private fun runAction(action: () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, messageKey = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(messageKey = "sms.action_failed") } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}
