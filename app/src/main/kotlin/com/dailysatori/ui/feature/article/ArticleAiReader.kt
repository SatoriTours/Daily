package com.dailysatori.ui.feature.article

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleAiRepository
import com.dailysatori.data.repository.ArticleAiView
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.service.articleai.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.news.ArticleReaderBody
import com.dailysatori.ui.component.news.ArticleReaderHeader
import com.dailysatori.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class ArticleAiReaderViewModel(
    private val id: Long,
    articles: ArticleRepository,
    repo: ArticleAiRepository,
    private val queue: ArticleAiQueue,
) : ViewModel() {
    val view = repo.observe(id) { articles.getById(id) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun request(mode: ArticleAiMode? = null, redo: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { queue.manual(id, mode, redo) }
    }
}

internal enum class ArticleReadMode { ORIGINAL, SUMMARY, TRANSLATION }

internal fun initialArticleReadMode(view: ArticleAiView) =
    if (view.summary != null) ArticleReadMode.SUMMARY else ArticleReadMode.ORIGINAL

internal class ArticleReaderSelection(mode: ArticleReadMode) {
    var mode by mutableStateOf(mode)
}

@Composable
internal fun rememberArticleReaderSelection(id: Long, view: ArticleAiView): ArticleReaderSelection {
    var name by rememberSaveable(id) { mutableStateOf(initialArticleReadMode(view).name) }
    return remember(id) { ArticleReaderSelection(ArticleReadMode.valueOf(name)) }.also {
        SideEffect { name = it.mode.name }
    }
}

@Composable
internal fun ArticleAiReader(
    view: ArticleAiView,
    selection: ArticleReaderSelection,
    title: String,
    meta: List<String>,
    source: String?,
    original: String,
    modifier: Modifier = Modifier,
    request: (ArticleAiMode?) -> Unit,
    refreshError: String? = null,
) {
    val i18n = koinInject<I18nService>()
    val mode = selection.mode
    val sourceMarkdown = view.original ?: original
    val markdown = when (mode) {
        ArticleReadMode.ORIGINAL -> sourceMarkdown
        ArticleReadMode.SUMMARY -> view.summary ?: sourceMarkdown
        ArticleReadMode.TRANSLATION -> view.translation ?: sourceMarkdown
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "reader-title") {
            ArticleReaderHeader(title, meta, sourceName = source, sourceAction = {
                ArticleAiSourceAction(view, selection, request)
            })
        }
        item(key = "reader-content") {
            Column(Modifier.padding(horizontal = Spacing.l)) {
                refreshError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = AppTypography.labelMedium) }
                articleReadingNotes(view, mode).forEach { key ->
                    Text(i18n.t("article_ai.$key"), modifier = Modifier.padding(bottom = Spacing.xs),
                        style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ArticleReaderBody(markdown, typography = MarkdownStyles.readingTypography(), padding = MarkdownStyles.summaryPadding())
            }
        }
    }
}

internal fun articleReadingNotes(view: ArticleAiView, mode: ArticleReadMode): List<String> = buildList {
    when {
        mode == ArticleReadMode.SUMMARY && view.summary != null -> {
            add(if (view.sourceSummary) "source_summary" else "ai_summary")
            if (view.summaryPartial) add("partial")
        }
        mode == ArticleReadMode.TRANSLATION && view.translation != null -> {
            add("ai_translation")
            if (view.translationPartial) add("partial")
        }
        view.kind == ArticleBodyKind.PREVIEW -> add("preview")
        view.kind == ArticleBodyKind.SOURCE_SUMMARY -> add("source_summary")
        view.kind == ArticleBodyKind.UNVERIFIED -> add("unverified")
    }
}

@Composable
private fun ArticleAiSourceAction(
    view: ArticleAiView,
    selection: ArticleReaderSelection,
    request: (ArticleAiMode?) -> Unit,
) {
    val i18n = koinInject<I18nService>()
    val key = when {
        view.jobState == "running" -> "processing"
        view.blocked.isNotEmpty() && view.jobState in setOf("queued", "failed") -> articleAiPauseKey(view.blocked)
        view.jobState == "queued" -> "run_now"
        view.jobState == "failed" -> "retry"
        selection.mode != ArticleReadMode.ORIGINAL -> "original"
        view.summary != null -> "ready"
        view.translation != null -> "translation"
        view.kind == ArticleBodyKind.ERROR_PAGE -> "fetch_error"
        view.kind == ArticleBodyKind.UNVERIFIED -> "unverified"
        view.kind != ArticleBodyKind.BODY -> "no_body"
        view.language == ArticleLanguage.UNCERTAIN -> "uncertain"
        view.language == ArticleLanguage.CHINESE && view.units <= 100 -> "force_summary"
        else -> "run_now"
    }
    val action: (() -> Unit)? = when (key) {
        "run_now" -> { { request(null) } }
        "retry" -> { { request(view.jobMode) } }
        "force_summary" -> { { request(ArticleAiMode.SUMMARY) } }
        "original" -> { { selection.mode = ArticleReadMode.ORIGINAL } }
        "summary", "ready" -> { { selection.mode = ArticleReadMode.SUMMARY } }
        "translation" -> { { selection.mode = ArticleReadMode.TRANSLATION } }
        else -> null
    }
    if (action == null) Text(i18n.t("article_ai.$key"), style = AppTypography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    else TextButton(onClick = action) { Text(i18n.t("article_ai.$key")) }
}

internal fun articleAiPauseKey(reason: String): String = when (reason) {
    "configuration" -> "paused_configuration"
    "http_401", "http_403" -> "paused_auth"
    "http_402" -> "paused_billing"
    "http_429" -> "paused_rate"
    else -> "queue_paused"
}

@Composable
internal fun ArticleAiMenu(
    view: ArticleAiView?,
    selection: ArticleReaderSelection?,
    dismiss: () -> Unit,
    onForce: (ArticleAiMode) -> Unit,
) {
    if (view == null || selection == null) return
    val i18n = koinInject<I18nService>()
    fun switch(mode: ArticleReadMode) { selection.mode = mode; dismiss() }
    DropdownMenuItem(text = { Text(i18n.t("article_ai.original")) }, onClick = { switch(ArticleReadMode.ORIGINAL) })
    if (view.summary != null) DropdownMenuItem(text = { Text(i18n.t("article_ai.summary")) },
        onClick = { switch(ArticleReadMode.SUMMARY) })
    if (view.translation != null) DropdownMenuItem(text = { Text(i18n.t("article_ai.translation")) },
        onClick = { switch(ArticleReadMode.TRANSLATION) })
    if (view.kind != ArticleBodyKind.BODY) return
    DropdownMenuItem(text = { Text(i18n.t("article_ai.force_menu")) },
        onClick = { dismiss(); onForce(ArticleAiMode.SUMMARY) })
    DropdownMenuItem(text = { Text(i18n.t("article_ai.force_both")) },
        onClick = { dismiss(); onForce(ArticleAiMode.BOTH) })
}

@Composable
internal fun ArticleAiRedoDialog(mode: ArticleAiMode?, onDismiss: () -> Unit, onRedo: (ArticleAiMode) -> Unit) {
    if (mode == null) return
    val i18n = koinInject<I18nService>()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(i18n.t("article_ai.redo")) },
        text = { Text(i18n.t("article_ai.redo_note")) },
        confirmButton = { TextButton(onClick = { onRedo(mode); onDismiss() }) { Text(i18n.t("article_ai.redo")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t("article_ai.cancel")) } })
}
