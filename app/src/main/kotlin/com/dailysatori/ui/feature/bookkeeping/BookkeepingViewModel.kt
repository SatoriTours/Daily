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
    val ledger: LedgerState = LedgerState(), val period: LedgerPeriod = LedgerPeriod.DAY,
    val anchor: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val totals: List<LedgerTotal> = emptyList(), val buckets: List<LedgerBucket> = emptyList(),
    val busy: Boolean = false, val error: Boolean = false,
)
