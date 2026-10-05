package com.dailysatori.ui.feature.settings.sms

import androidx.lifecycle.ViewModel
import com.dailysatori.data.repository.SmsSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class SmsCreationNoticeViewModel(private val sources: SmsSourceRepository) : ViewModel() {
    @OptIn(FlowPreview::class)
    val pendingCount = sources.observeCreatedCount().distinctUntilChanged().debounce(600)

    suspend fun takeCreatedCount(): Int = withContext(Dispatchers.IO) { sources.takeCreatedCount() }
}
