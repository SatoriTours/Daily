package com.dailysatori.ui.feature.myspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.task.NewsOpportunityTaskHandler
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.service.opportunity.NewsOpportunityService
import com.dailysatori.service.opportunity.ReadNewsArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MySpaceViewModel(
    private val service: NewsOpportunityService,
    private val tasks: AsyncTaskRepository,
    private val scheduler: AsyncTaskScheduler,
    private val reminders: ReminderRepository,
) : ViewModel() {
    val state = service.state
    private val _operationFailed = MutableStateFlow(false)
    val operationFailed = _operationFailed.asStateFlow()
    val task = tasks.observeLatestByUniqueKey(NewsOpportunityTaskHandler.TYPE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val activeReminderIds = reminders.observeAll().map { entries -> entries.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init { refresh() }

    fun refresh() = mutate { service.refresh() }
    fun saveFocus(text: String, onSaved: () -> Unit) = mutate {
        service.saveFocus(text)
        enqueueAnalysis(automatic = true)
        withContext(Dispatchers.Main) { onSaved() }
    }
    fun setSaved(id: String, value: Boolean) = mutate { service.setSaved(id, value) }
    fun setIgnored(id: String, value: Boolean) = mutate { service.setIgnored(id, value) }
    fun markRead(article: ReadNewsArticle, onSaved: () -> Unit) = mutate {
        service.markRead(article)
        withContext(Dispatchers.Main) { onSaved() }
    }
    fun linkReminder(id: String, reminderId: String, onSaved: () -> Unit) = mutate {
        service.linkReminder(id, reminderId)
        withContext(Dispatchers.Main) { onSaved() }
    }
    fun analyze() = mutate { enqueueAnalysis(automatic = false) }
    fun recommend() = mutate { enqueueAnalysis(automatic = true) }

    private fun enqueueAnalysis(automatic: Boolean) {
        val id = tasks.enqueue(NewsOpportunityTaskHandler.TYPE, "{\"automatic\":$automatic}", uniqueKey = NewsOpportunityTaskHandler.TYPE, maxAttempts = 1)
        scheduler.enqueue(id)
    }
    fun clearError() { _operationFailed.value = false }

    private fun mutate(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        _operationFailed.value = false
        try { block() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { _operationFailed.value = true }
    }
}
