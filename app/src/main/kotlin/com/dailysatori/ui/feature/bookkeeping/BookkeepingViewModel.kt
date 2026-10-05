package com.dailysatori.ui.feature.bookkeeping

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.bookkeeping.*
import com.dailysatori.core.bookkeeping.*
import com.dailysatori.data.repository.BookkeepingRepository
import com.dailysatori.service.bookkeeping.BookkeepingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*

data class BookkeepingSource(val packageName: String, val label: String)
data class BookkeepingUiState(
    val enabled: Boolean = false, val granted: Boolean = false, val capture: BookkeepingCaptureStatus = BookkeepingCaptureStatus(),
    val selectedSources: Set<String> = emptySet(), val sources: List<BookkeepingSource> = emptyList(),
    val ledger: LedgerState = LedgerState(), val month: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val totals: List<LedgerTotal> = emptyList(), val busy: Boolean = false, val error: Boolean = false,
)

class BookkeepingViewModel(
    private val context: Context, private val repository: BookkeepingRepository,
    private val service: BookkeepingService, private val monitor: BookkeepingCaptureMonitor,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BookkeepingUiState())
    val state = mutableState.asStateFlow()
    private val engine = LedgerEngine()

    init {
        refreshAccess()
        viewModelScope.launch(Dispatchers.IO) {
            repository.observe().catch { mutableState.update { it.copy(error = true) } }.collect { ledger ->
                mutableState.update { it.copy(ledger = ledger, totals = totals(ledger, it.month)) }
            }
        }
        viewModelScope.launch { monitor.state.collect { capture -> mutableState.update { it.copy(capture = capture) } } }
    }

    fun refreshAccess() = action {
        val preferences = service.preferences()
        val sources = availableSources(preferences.sources)
        val component = ComponentName(context, BookkeepingNotificationListener::class.java)
        val granted = if (Build.VERSION.SDK_INT >= 27)
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        else context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        val ledger = repository.snapshot()
        mutableState.update { it.copy(enabled = preferences.enabled, selectedSources = preferences.sources,
            sources = sources, granted = granted, ledger = ledger, totals = totals(ledger, it.month), error = false) }
    }

    fun setEnabled(enabled: Boolean) = action {
        service.setEnabled(enabled)
        mutableState.update { it.copy(enabled = enabled) }
        if (enabled && state.value.granted) NotificationListenerService.requestRebind(ComponentName(context, BookkeepingNotificationListener::class.java))
    }

    fun selectSource(source: String, selected: Boolean) = action {
        service.selectSource(source, selected)
        mutableState.update { it.copy(selectedSources = service.preferences().sources) }
    }

    fun edit(id: String, amount: String, currency: String, kind: LedgerKind, merchant: String, onSaved: () -> Unit) = action {
        service.edit(id, amount, currency, kind, merchant)
        withContext(Dispatchers.Main) { onSaved() }
    }

    fun dismiss(id: String, status: LedgerStatus) = action { service.dismiss(id, status) }

    fun changeMonth(delta: Int) {
        val month = state.value.month.let { LocalDate(it.year, it.monthNumber, 1).plus(delta, DateTimeUnit.MONTH) }
        mutableState.update { it.copy(month = month, totals = totals(it.ledger, month)) }
    }

    private fun totals(ledger: LedgerState, month: LocalDate): List<LedgerTotal> {
        val start = LocalDate(month.year, month.monthNumber, 1)
        val zone = TimeZone.currentSystemDefault()
        return engine.totals(ledger, start.atStartOfDayIn(zone).toEpochMilliseconds(),
            start.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(zone).toEpochMilliseconds())
    }

    private fun availableSources(selected: Set<String>): List<BookkeepingSource> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val manager = context.packageManager
        val installed = manager.queryIntentActivities(intent, 0).map { resolve ->
            val app = resolve.activityInfo.applicationInfo
            BookkeepingSource(app.packageName, manager.getApplicationLabel(app).toString())
        }.filterNot { it.packageName == context.packageName }.distinctBy { it.packageName }
        val missing = selected.minus(installed.map { it.packageName }.toSet()).map { BookkeepingSource(it, it) }
        return (installed + missing).sortedWith(compareByDescending<BookkeepingSource> { it.packageName in selected }.thenBy { it.label })
    }

    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(error = true) } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}
