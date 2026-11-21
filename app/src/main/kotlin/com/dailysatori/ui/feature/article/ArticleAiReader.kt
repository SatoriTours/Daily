package com.dailysatori.ui.feature.article

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

internal fun ArticleAiView.hasPartialTranslation(): Boolean =
    translationPartial || kind != ArticleBodyKind.BODY

internal fun ArticleAiView.canRequestReading(): Boolean {
    if (kind !in setOf(ArticleBodyKind.BODY, ArticleBodyKind.PREVIEW, ArticleBodyKind.UNVERIFIED)) return false
    return (language == ArticleLanguage.FOREIGN && (translation == null || translationPartial)) ||
        (!shortBody && (summary == null || summaryPartial) && language != ArticleLanguage.UNCERTAIN)
}

@Composable
internal fun ArticleAiReader(
    view: ArticleAiView,
    title: String,
    meta: List<String>,
    source: String?,
    original: String,
    modifier: Modifier = Modifier,
    refreshError: String? = null,
    onOriginalClick: () -> Unit,
) {
    val originalBody = view.original ?: original
    val summary = view.summary.takeUnless { view.shortBody && !view.original.isNullOrBlank() }
    val body = view.translation ?: originalBody
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "reader-title") {
            ArticleReaderHeader(title, meta, sourceName = source, sourceAction = { ArticleOriginalLink(onOriginalClick) })
        }
        item(key = "reader-content") {
            val stateKey = when {
                view.jobState == "running" -> "processing"
                view.blocked.isNotEmpty() -> articleAiPauseKey(view.blocked)
                view.jobState == "queued" -> "queued_status"
                view.jobState == "failed" -> "reading_failed"
                else -> null
            }
            if (stateKey != null || refreshError != null) Column(Modifier.padding(horizontal = Spacing.l)) {
                stateKey?.let { ReadingNote(it) }
                refreshError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = AppTypography.labelMedium) }
            }
            ArticleReadingContent(summary, body,
                summaryNotes = articleReadingNotes(view, ArticleReadMode.SUMMARY).takeIf { summary != null }.orEmpty(),
                bodyNotes = articleReadingNotes(view, if (view.translation == null) ArticleReadMode.ORIGINAL else ArticleReadMode.TRANSLATION))
        }
    }
}

@Composable
internal fun ArticleReadingContent(
    summary: String?, body: String, summaryNotes: List<String> = emptyList(), bodyNotes: List<String> = emptyList(),
) {
    val i18n = koinInject<I18nService>()
    val distinctSummary = summary?.trim()?.takeIf { it.isNotBlank() && it != body.trim() }
    Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        if (distinctSummary != null) {
            Text(i18n.t("article_ai.summary_title"), style = MaterialTheme.typography.titleMedium)
            summaryNotes.forEach { ReadingNote(it) }
            ArticleReaderBody(articleDetailPageContent(0, distinctSummary, null),
                typography = MarkdownStyles.readingTypography(), padding = MarkdownStyles.summaryPadding())
            HorizontalDivider(Modifier.padding(vertical = Spacing.m))
        }
        bodyNotes.forEach { ReadingNote(it) }
        if (body.isNotBlank()) ArticleReaderBody(body, typography = MarkdownStyles.readingTypography(),
            padding = MarkdownStyles.readingPadding())
    }
}

@Composable
internal fun ReadingNote(key: String) {
    val i18n = koinInject<I18nService>()
    Text(i18n.t("article_ai.$key"), modifier = Modifier.padding(bottom = Spacing.xs),
        style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun articleReadingNotes(view: ArticleAiView, mode: ArticleReadMode): List<String> = buildList {
    when {
        mode == ArticleReadMode.SUMMARY && view.summary != null -> {
            add(if (view.sourceSummary) "source_summary" else "ai_summary")
            if (view.summaryPartial) add("partial")
        }
        mode == ArticleReadMode.TRANSLATION && view.translation != null -> {
            add("ai_translation")
            if (view.kind == ArticleBodyKind.PREVIEW || view.kind == ArticleBodyKind.UNVERIFIED) add("translate_excerpt_note")
            if (view.translationPartial) add("partial")
        }
        view.kind == ArticleBodyKind.PREVIEW -> add("preview")
        view.kind == ArticleBodyKind.SOURCE_SUMMARY -> add("source_summary")
        view.kind == ArticleBodyKind.UNVERIFIED -> add("unverified")
    }
}

internal fun articleAiPauseKey(reason: String): String = when (reason) {
    "configuration" -> "paused_configuration"
    "http_401", "http_403" -> "paused_auth"
    "http_402" -> "paused_billing"
    "http_429" -> "paused_rate"
    else -> "queue_paused"
}
