package com.dailysatori.ui.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.bookkeeping.*
import com.dailysatori.data.repository.BookkeepingRepository
import com.dailysatori.service.bookkeeping.BookkeepingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*

enum class LedgerTab { DETAIL, ANALYSIS }

/** Detail uses day/week/month buckets; analysis compares month, quarter and year. */
val DETAIL_PERIODS = listOf(LedgerPeriod.DAY, LedgerPeriod.WEEK, LedgerPeriod.MONTH)
val ANALYSIS_PERIODS = listOf(LedgerPeriod.MONTH, LedgerPeriod.QUARTER, LedgerPeriod.YEAR)

data class LedgerUiState(
    val tab: LedgerTab = LedgerTab.DETAIL,
    val detail: LedgerPeriod = LedgerPeriod.DAY,
    val analysis: LedgerPeriod = LedgerPeriod.MONTH,
    val anchor: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val ledger: LedgerState = LedgerState(),
    val buckets: List<LedgerBucket> = emptyList(),
    val totals: List<LedgerTotal> = emptyList(),
    val previousExpense: Long = 0L,
    val trend: List<LedgerBucket> = emptyList(),
    val categories: List<LedgerCategoryTotal> = emptyList(),
    val merchants: List<LedgerMerchantTotal> = emptyList(),
    val pending: List<LedgerEntry> = emptyList(),
    val primaryCurrency: String = "CNY",
    val entry: LedgerEntry? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
) {
    val period: LedgerPeriod get() = if (tab == LedgerTab.DETAIL) detail else analysis

    fun expense(currency: String = "CNY"): Long = totals.firstOrNull { it.currency == currency }?.expense ?: 0L
    fun bucketExpense(bucket: LedgerBucket, currency: String = "CNY"): Long =
        bucket.totals.firstOrNull { it.currency == currency }?.expense ?: 0L
}

class LedgerViewModel(
    private val repository: BookkeepingRepository,
    private val service: BookkeepingService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LedgerUiState())
    val state = mutableState.asStateFlow()
    private val engine = LedgerEngine()

    init {
        viewModelScope.launch(Dispatchers.IO) { repository.backfillCategories() }
        viewModelScope.launch(Dispatchers.IO) {
            repository.observe().catch { mutableState.update { it.copy(failed = true) } }
                .collect { ledger -> mutableState.update { summary(it, ledger) } }
        }
    }

    fun selectTab(tab: LedgerTab) = mutableState.update { summary(it.copy(tab = tab), it.ledger) }

    fun selectPeriod(period: LedgerPeriod) = mutableState.update {
        val next = if (it.tab == LedgerTab.DETAIL) it.copy(detail = period) else it.copy(analysis = period)
        summary(next, it.ledger)
    }

    fun shift(delta: Int) = mutableState.update {
        summary(it.copy(anchor = engine.shift(it.anchor, it.period, delta)), it.ledger)
    }

    fun openEntry(id: String) = mutableState.update { it.copy(entry = it.ledger.entries.firstOrNull { entry -> entry.id == id }) }

    fun closeEntry() = mutableState.update { it.copy(entry = null) }

    fun setCategory(id: String, category: LedgerCategory, remember: Boolean) = action { service.setCategory(id, category, remember) }

    fun setNote(id: String, text: String) = action { service.setNote(id, text) }

    fun setExcluded(id: String, excluded: Boolean) = action { service.setExcluded(id, excluded) }

    fun edit(id: String, amount: String, currency: String, kind: LedgerKind, merchant: String, onSaved: () -> Unit) = action {
        service.edit(id, amount, currency, kind, merchant)
        withContext(Dispatchers.Main) { onSaved() }
    }

    fun dismiss(id: String, status: LedgerStatus) = action {
        service.dismiss(id, status)
        mutableState.update { it.copy(entry = null) }
    }

    fun refresh() = action { mutableState.update { summary(it, repository.snapshot()) } }

    /** Everything the list, the summary card and the analysis tab need for the current anchors. */
    private fun summary(state: LedgerUiState, ledger: LedgerState): LedgerUiState {
        val zone = TimeZone.currentSystemDefault()
        val scope = if (state.tab == LedgerTab.DETAIL) state.detail else state.analysis
        val (from, to) = engine.range(scope, state.anchor, zone)
        val totals = engine.totals(ledger, from, to)
        val currency = totals.maxByOrNull { it.expense }?.takeIf { it.expense > 0L }?.currency
            ?: totals.firstOrNull()?.currency ?: "CNY"
        return state.copy(
            ledger = ledger,
            primaryCurrency = currency,
            buckets = if (state.tab == LedgerTab.DETAIL) engine.buckets(ledger, state.detail, state.anchor, zone) else emptyList(),
            trend = if (state.tab == LedgerTab.ANALYSIS) trend(ledger, state.analysis, state.anchor, zone) else emptyList(),
            totals = totals,
            previousExpense = engine.previousExpense(ledger, scope, state.anchor, zone, currency),
            categories = if (state.tab == LedgerTab.ANALYSIS) engine.categoryTotals(ledger, state.analysis, state.anchor, zone, currency) else emptyList(),
            merchants = if (state.tab == LedgerTab.ANALYSIS) engine.merchantTotals(ledger, state.analysis, state.anchor, zone, limit = 5, currency = currency) else emptyList(),
            pending = ledger.entries.filter { it.status == LedgerStatus.PENDING }.sortedByDescending { it.receivedAt },
            entry = state.entry?.let { open -> ledger.entries.firstOrNull { it.id == open.id } },
            failed = false,
        )
    }

    /** Oldest-first buckets for the bar chart: a month shows days, a quarter weeks, a year months. */
    private fun trend(ledger: LedgerState, period: LedgerPeriod, anchor: LocalDate, zone: TimeZone): List<LedgerBucket> = when (period) {
        LedgerPeriod.QUARTER -> engine.buckets(ledger, LedgerPeriod.WEEK, anchor, zone, count = 13)
        LedgerPeriod.YEAR -> engine.buckets(ledger, LedgerPeriod.MONTH, anchor, zone, count = 12)
        else -> engine.buckets(ledger, LedgerPeriod.DAY, anchor, zone, count = anchor.dayOfMonth)
    }.reversed()

    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(failed = true) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
