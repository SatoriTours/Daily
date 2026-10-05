package com.dailysatori.ui.feature.phone

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.bookkeeping.*
import com.dailysatori.core.bookkeeping.*
import com.dailysatori.core.reminder.ReminderCoordinator
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.*
import com.dailysatori.service.phone.*
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.sms.*
import com.dailysatori.ui.feature.bookkeeping.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*

data class PhoneUiState(
    val preferences: PhonePreferences = PhonePreferences(PhoneOptions(), PhoneOptions(), emptySet()),
    val messages: List<PhoneMessage> = emptyList(), val smsRecords: List<SmsSourceRecord> = emptyList(),
    val history: List<PhoneMessage> = emptyList(), val historyHasMore: Boolean = false,
    val reminders: List<Reminder> = emptyList(), val bookkeeping: BookkeepingUiState = BookkeepingUiState(),
    val smsGranted: Boolean = false, val canNotify: Boolean = true, val exactAlarms: Boolean = true,
    val busy: Boolean = false, val failed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneAssistantViewModel(
    private val context: Context, private val service: PhoneAssistantService,
    private val messages: PhoneMessageRepository, private val ledger: BookkeepingRepository,
    private val smsSources: SmsSourceRepository, private val reminders: ReminderRepository,
    private val coordinator: ReminderCoordinator, private val monitor: BookkeepingCaptureMonitor,
    private val scheduler: AsyncTaskScheduler, private val legacy: SmsReminderService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PhoneUiState())
    val state = mutableState.asStateFlow()
    private val engine = LedgerEngine()
    private val historyLimit = MutableStateFlow(50)
    init {
        refreshAccess()
        viewModelScope.launch(Dispatchers.IO) { messages.observe().catch { fail() }.collect { rows -> mutableState.update { it.copy(messages = rows) } } }
        viewModelScope.launch(Dispatchers.IO) {
            historyLimit.flatMapLatest { limit -> messages.observeHistory(limit + 1).map { limit to it } }
                .catch { fail() }.collect { (limit, rows) ->
                    mutableState.update { it.copy(history = rows.take(limit), historyHasMore = rows.size > limit) }
                }
        }
        viewModelScope.launch(Dispatchers.IO) { smsSources.observe().catch { fail() }.collect { rows -> mutableState.update { it.copy(smsRecords = rows) } } }
        viewModelScope.launch(Dispatchers.IO) { reminders.observeAll().catch { fail() }.collect { rows -> mutableState.update { it.copy(reminders = rows) } } }
        viewModelScope.launch(Dispatchers.IO) { ledger.observe().catch { fail() }.collect { rows -> mutableState.update { it.copy(bookkeeping = withLedger(it.bookkeeping, rows)) } } }
        viewModelScope.launch { monitor.state.collect { capture -> mutableState.update { it.copy(bookkeeping = it.bookkeeping.copy(capture = capture)) } } }
    }
    fun refreshAccess() = action {
        val component = ComponentName(context, BookkeepingNotificationListener::class.java)
        val granted = if (Build.VERSION.SDK_INT >= 27) context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
            else context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        val prefs = service.preferences()
        mutableState.update { it.copy(preferences = prefs,
            smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED,
            canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarms = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            bookkeeping = withLedger(it.bookkeeping.copy(granted = granted, sources = availableSources(prefs.sources),
                selectedSources = prefs.sources, enabled = prefs.notification.enabled), ledger.snapshot())) }
    }
    fun configure(channel: PhoneChannel, options: PhoneOptions) = action {
        service.configure(channel, options)
        mutableState.update { it.copy(preferences = service.preferences()) }
        if (channel == PhoneChannel.NOTIFICATION && options.enabled && state.value.bookkeeping.granted)
            NotificationListenerService.requestRebind(ComponentName(context, BookkeepingNotificationListener::class.java))
    }
    fun selectSource(source: String, selected: Boolean) = action {
        service.selectSource(source, selected)
        val prefs = service.preferences()
        mutableState.update { it.copy(preferences = prefs, bookkeeping = it.bookkeeping.copy(selectedSources = prefs.sources)) }
    }
    fun retry(row: PhoneMessage) = action { service.retry(row.id)?.let(scheduler::enqueue) }
    fun loadMoreHistory() { if (state.value.historyHasMore) historyLimit.update { it + 50 } }
    fun ignoreTodo(row: PhoneMessage, todo: PhoneTodo) = action { service.ignoreTodo(row.id, todo.id) }
    fun clearText(row: PhoneMessage) = action { service.clearText(row.id) }
    fun blockSource(row: PhoneMessage) = action {
        service.blockSource(row.id)
        val prefs = service.preferences()
        mutableState.update { it.copy(preferences = prefs, bookkeeping = it.bookkeeping.copy(selectedSources = prefs.sources)) }
    }
    fun confirmTodo(row: PhoneMessage, todo: PhoneTodo?, draft: SmsReminderDraft, onSaved: () -> Unit) = action {
        val id = if (todo == null) service.addTodo(row.id, draft) else service.confirmTodo(row.id, todo.id, draft)
        id?.let(coordinator::recompute)
        withContext(Dispatchers.Main) { onSaved() }
    }
    fun complete(id: String) = action { reminders.complete(id); coordinator.recompute(id) }
    fun editLedger(entry: LedgerEntry, amount: String, currency: String, kind: LedgerKind, merchant: String, onSaved: () -> Unit) = action {
        service.editLedger(entry.id, amount, currency, kind, merchant)
        withContext(Dispatchers.Main) { onSaved() }
    }
    fun addLedger(row: PhoneMessage, amount: String, currency: String, kind: LedgerKind, merchant: String, onSaved: () -> Unit) = action {
        service.addLedger(row.id, amount, currency, kind, merchant)
        withContext(Dispatchers.Main) { onSaved() }
    }
    fun dismissLedger(id: String, status: LedgerStatus) = action { service.dismissLedger(id, status) }
    fun retryLegacy(row: SmsSourceRecord) = action {
        val reminderId = row.reminderId
        if (reminderId != null) coordinator.recompute(reminderId) else legacy.retry(row.id)?.let(scheduler::enqueue)
    }
    fun ignoreLegacy(row: SmsSourceRecord) = action { legacy.ignore(row.id) }
    fun blockLegacy(row: SmsSourceRecord) = action { legacy.blockSender(row.source.sender) }
    fun changeMonth(delta: Int) {
        mutableState.update { current ->
            val month = current.bookkeeping.month.let { LocalDate(it.year, it.monthNumber, 1).plus(delta, DateTimeUnit.MONTH) }
            current.copy(bookkeeping = withLedger(current.bookkeeping.copy(month = month), current.bookkeeping.ledger))
        }
    }
    private fun withLedger(state: BookkeepingUiState, ledger: LedgerState): BookkeepingUiState {
        val zone = TimeZone.currentSystemDefault()
        val start = LocalDate(state.month.year, state.month.monthNumber, 1)
        return state.copy(ledger = ledger, totals = engine.totals(ledger, start.atStartOfDayIn(zone).toEpochMilliseconds(),
            start.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(zone).toEpochMilliseconds()))
    }
    private fun availableSources(selected: Set<String>): List<BookkeepingSource> {
        val manager = context.packageManager
        val installed = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map {
            BookkeepingSource(it.activityInfo.packageName, manager.getApplicationLabel(it.activityInfo.applicationInfo).toString())
        }.filterNot { it.packageName == context.packageName }.distinctBy { it.packageName }
        return (installed + selected.minus(installed.map { it.packageName }.toSet()).map { BookkeepingSource(it, it) })
            .sortedWith(compareByDescending<BookkeepingSource> { it.packageName in selected }.thenBy { it.label })
    }
    private fun fail() = mutableState.update { it.copy(failed = true) }
    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail() }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}
