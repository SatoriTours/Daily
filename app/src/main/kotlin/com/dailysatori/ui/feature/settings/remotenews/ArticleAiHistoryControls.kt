package com.dailysatori.ui.feature.settings.remotenews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.articleai.ArticleAiHistoryStatus
import com.dailysatori.service.articleai.ArticleAiQueue
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.feature.article.articleAiPauseKey
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
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

    init {
        calculate()
    }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArticleAiHistoryControls(onOpenTasks: (() -> Unit)? = null) {
    val vm: ArticleAiHistoryViewModel = koinViewModel()
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val i18n = koinInject<I18nService>()
    Surface(
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ArticleAiHistoryHeader(status = status, busy = busy, onRefresh = vm::calculate, i18n = i18n)
            ArticleAiHistoryBody(status = status, i18n = i18n)
            status?.let { current ->
                ArticleAiHistoryActions(
                    status = current,
                    busy = busy,
                    onStart = vm::start,
                    onPause = vm::pause,
                    onContinue = vm::continueQueued,
                    onResumeQueue = vm::resumeQueue,
                    onOpenTasks = onOpenTasks,
                    i18n = i18n,
                )
            }
        }
    }
}

@Composable
private fun ArticleAiHistoryHeader(
    status: ArticleAiHistoryStatus?,
    busy: Boolean,
    onRefresh: () -> Unit,
    i18n: I18nService,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            text = i18n.t("article_ai.history"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        status?.let { ArticleAiHistoryBadge(it, i18n) }
        IconButton(
            onClick = onRefresh,
            enabled = !busy,
            modifier = Modifier.size(Height.touchTarget),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = i18n.t("article_ai.history_recount"),
                modifier = Modifier.size(IconSize.m),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleAiHistoryBadge(status: ArticleAiHistoryStatus, i18n: I18nService) {
    val (labelKey, containerColor, contentColor) = when {
        status.blocked.isNotBlank() -> Triple(
            "article_ai.history_paused",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        status.running -> Triple(
            "article_ai.history_running",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        else -> Triple(
            "article_ai.history_idle",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(shape = RoundedCornerShape(Radius.circular), color = containerColor) {
        Text(
            text = i18n.t(labelKey),
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun ArticleAiHistoryBody(status: ArticleAiHistoryStatus?, i18n: I18nService) {
    if (status == null) {
        Text(
            text = i18n.t("article_ai.processing"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val summaryText = if (status.pending == 0 && status.waiting == 0 && status.blocked.isBlank()) {
        i18n.t("article_ai.history_empty")
    } else {
        "${i18n.t("article_ai.pending")}: ${status.pending} · ${i18n.t("article_ai.waiting")}: ${status.waiting}"
    }
    Text(
        text = summaryText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (status.blocked.isNotBlank()) {
        Text(
            text = i18n.t("article_ai.${articleAiPauseKey(status.blocked)}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleAiHistoryActions(
    status: ArticleAiHistoryStatus,
    busy: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onContinue: () -> Unit,
    onResumeQueue: () -> Unit,
    onOpenTasks: (() -> Unit)?,
    i18n: I18nService,
) {
    val hasPrimary = status.blocked.isNotBlank() || status.pending > 0 || status.running || status.waiting > 0
    if (!hasPrimary && onOpenTasks == null) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (status.blocked.isNotBlank()) {
            FilledTonalButton(
                onClick = onResumeQueue,
                enabled = !busy,
                modifier = Modifier.heightIn(min = Height.touchTarget),
                contentPadding = PaddingValues(horizontal = Spacing.m),
            ) {
                Text(i18n.t("article_ai.resume_queue"))
            }
        }
        if (status.pending > 0) {
            FilledTonalButton(
                onClick = onStart,
                enabled = !busy,
                modifier = Modifier.heightIn(min = Height.touchTarget),
                contentPadding = PaddingValues(horizontal = Spacing.m),
            ) {
                Text(i18n.t("article_ai.start"))
            }
        } else if (!status.running && status.waiting > 0) {
            FilledTonalButton(
                onClick = onContinue,
                enabled = !busy,
                modifier = Modifier.heightIn(min = Height.touchTarget),
                contentPadding = PaddingValues(horizontal = Spacing.m),
            ) {
                Text(i18n.t("article_ai.start"))
            }
        }
        if (status.running) {
            TextButton(
                onClick = onPause,
                enabled = !busy,
                modifier = Modifier.heightIn(min = Height.touchTarget),
            ) {
                Text(i18n.t("article_ai.pause"))
            }
        }
        if (onOpenTasks != null) {
            TextButton(
                onClick = onOpenTasks,
                modifier = Modifier.heightIn(min = Height.touchTarget),
            ) {
                Text(i18n.t("article_ai.view_tasks"))
            }
        }
    }
}
