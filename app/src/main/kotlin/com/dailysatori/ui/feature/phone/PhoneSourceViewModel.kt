package com.dailysatori.ui.feature.phone

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.PhoneMessageRepository
import com.dailysatori.data.repository.SmsSourceRepository
import com.dailysatori.service.phone.PhoneChannel
import com.dailysatori.service.phone.PhoneMessage
import com.dailysatori.service.sms.SmsSourceRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class PhoneSourceState(
    val messages: List<PhoneMessage> = emptyList(), val legacy: List<SmsSourceRecord> = emptyList(),
    val labels: Map<String, String> = emptyMap(), val loaded: Boolean = false, val failed: Boolean = false,
)

internal class PhoneSourceViewModel(private val messages: PhoneMessageRepository,
    private val sms: SmsSourceRepository, private val context: Context) : ViewModel() {
    private val mutableState = MutableStateFlow(PhoneSourceState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    init { reload() }

    fun reload() {
        observation?.cancel()
        mutableState.update { it.copy(loaded = false, failed = false) }
        observation = viewModelScope.launch(Dispatchers.IO) {
            combine(messages.observe(), sms.observe()) { rows, legacy ->
                PhoneSourceState(rows, legacy, phoneApplicationLabels(context, rows), loaded = true)
            }.catch { mutableState.update { it.copy(loaded = true, failed = true) } }
                .collect { mutableState.value = it }
        }
    }
}

internal fun phoneApplicationLabels(context: Context, messages: List<PhoneMessage>): Map<String, String> = messages
    .filter { it.event.channel == PhoneChannel.NOTIFICATION }.map { it.event.origin }.distinct().associateWith { origin ->
        runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(origin, 0)).toString()
        }.getOrDefault(origin)
    }
