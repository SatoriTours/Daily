package com.dailysatori.ui.feature.settings.remotenews

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.articleai.ArticleAiHistoryStatus
import com.dailysatori.service.articleai.ArticleAiQueue
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

class ArticleAiHistoryViewModel(private val queue: ArticleAiQueue) : ViewModel() {
    private val _status = MutableStateFlow<ArticleAiHistoryStatus?>(null)
    val status = _status.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun calculate() = update {}
    fun start() = update { queue.startHistory() }
    fun pause() = update { queue.pauseHistory() }
    fun continueQueued() = update { queue.resumeHistory() }
    fun resumeQueue() = update { queue.resume() }

    private fun update(action: () -> Unit) {
        if (!_busy.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
                _status.value = queue.historyStatus()
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
internal fun ArticleAiHistoryControls() {
    val vm: ArticleAiHistoryViewModel = koinViewModel()
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val i18n = koinInject<I18nService>()
    Column(Modifier.padding(Spacing.s)) {
        Text(i18n.t("article_ai.history"), style = AppTypography.titleSmall)
        TextButton(onClick = vm::calculate, enabled = !busy) { Text(i18n.t("article_ai.count")) }
        status?.let {
            Text("${i18n.t("article_ai.pending")}: ${it.pending} · ${i18n.t("article_ai.waiting")}: ${it.waiting}")
            if (it.pending > 0) TextButton(onClick = vm::start, enabled = !busy) { Text(i18n.t("article_ai.start")) }
            if (it.running) TextButton(onClick = vm::pause, enabled = !busy) { Text(i18n.t("article_ai.pause")) }
            if (!it.running && it.waiting > 0) TextButton(onClick = vm::continueQueued, enabled = !busy) {
                Text(i18n.t("article_ai.start"))
            }
            if (it.blocked.isNotBlank()) TextButton(onClick = vm::resumeQueue, enabled = !busy) {
                Text(i18n.t("article_ai.resume_queue"))
            }
        }
    }
}
