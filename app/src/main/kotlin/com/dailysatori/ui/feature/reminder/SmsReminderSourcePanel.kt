package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.SmsSourceRepository
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.sms.SmsSourceRecord
import com.dailysatori.ui.feature.settings.sms.formatSmsTime
import com.dailysatori.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

class SmsReminderSourceViewModel(private val sources: SmsSourceRepository) : ViewModel() {
    private val mutableSource = MutableStateFlow<SmsSourceRecord?>(null)
    val source = mutableSource.asStateFlow()
    fun load(id: String) {
        mutableSource.value = null
        viewModelScope.launch(Dispatchers.IO) { mutableSource.value = runCatching { sources.forReminder(id) }.getOrNull() }
    }
}

@Composable
fun SmsReminderSourcePanel(reminderId: String, viewModel: SmsReminderSourceViewModel = koinViewModel()) {
    val source by viewModel.source.collectAsState()
    val i18n: I18nService = koinInject()
    var showOriginal by remember(reminderId) { mutableStateOf(false) }
    LaunchedEffect(reminderId) { viewModel.load(reminderId) }
    val record = source?.takeIf { it.reminderId == reminderId } ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(record.source.sender + " · " + formatSmsTime(record.receivedAt), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showOriginal = !showOriginal }) { Text(i18n.t("sms.source")) }
            if (showOriginal) Text(record.source.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
