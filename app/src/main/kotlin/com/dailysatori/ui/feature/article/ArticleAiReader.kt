package com.dailysatori.ui.feature.article

import com.dailysatori.core.diagnostics.performanceTab
import com.dailysatori.service.diagnostics.PerformanceResult
import com.dailysatori.service.diagnostics.PerformanceScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleAiRepository
import com.dailysatori.data.repository.ArticleAiView
import com.dailysatori.service.parser.articleReaderSummary
import com.dailysatori.service.parser.tidyArticleTranslation
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.service.articleai.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.news.ArticleReaderBody
import com.dailysatori.ui.component.news.articleReaderMetadata
import com.dailysatori.ui.component.card.articleDisplayDomain
import androidx.compose.ui.text.font.FontWeight
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

internal fun ArticleReadMode.performanceScreen(): PerformanceScreen = when (this) {
    ArticleReadMode.ORIGINAL -> PerformanceScreen.ARTICLE_READ_BODY
    ArticleReadMode.SUMMARY -> PerformanceScreen.ARTICLE_READ_SUMMARY
    ArticleReadMode.TRANSLATION -> PerformanceScreen.ARTICLE_READ_TRANSLATION
}

internal fun initialArticleReadMode(view: ArticleAiView) =
    if (view.summary != null) ArticleReadMode.SUMMARY else ArticleReadMode.ORIGINAL

internal fun ArticleAiView.hasPartialTranslation(): Boolean =
    translationPartial || kind != ArticleBodyKind.BODY

internal fun ArticleAiView.canRequestReading(): Boolean {
    if (kind !in setOf(ArticleBodyKind.BODY, ArticleBodyKind.PREVIEW, ArticleBodyKind.UNVERIFIED)) return false
    return (language == ArticleLanguage.FOREIGN && (translation == null || translationPartial)) ||
        (!shortBody && (summary == null || summaryPartial) && language != ArticleLanguage.UNCERTAIN)
}

internal fun savedReadingRetry(key: String, process: (() -> Unit)?, retranslate: (() -> Unit)?): (() -> Unit)? =
    when (key) {
        "retry_saved_reading", "retry_saved_summary", "retry_saved_translation", "reading_failed", "reading_busy" -> process
        "retranslation_failed" -> retranslate
        else -> null
    }

internal fun remoteReadingRetry(
    view: ArticleAiView,
    busy: Boolean,
    process: () -> Unit,
    retranslate: (() -> Unit)?,
): (() -> Unit)? {
    if (busy || view.blocked.isNotEmpty() || view.jobState != "failed") return null
    if (view.canRequestReading()) return process
    return retranslate.takeIf { view.jobMode == ArticleAiMode.TRANSLATION }
}

@Composable
internal fun ArticleAiReader(
    view: ArticleAiView,
    title: String,
    original: String,
    modifier: Modifier = Modifier,
    failureKey: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val originalBody = view.original ?: original
    val summary = view.summary.takeUnless { view.shortBody && !view.original.isNullOrBlank() }
    val body = remember(view.translation, title, originalBody) {
        view.translation?.let { tidyArticleTranslation(it, title) } ?: originalBody
    }
    LazyColumn(modifier = modifier.fillMaxSize().performanceTab(ArticleReadMode.ORIGINAL.performanceScreen(),
        if (body.isBlank() && summary == null) PerformanceResult.EMPTY else PerformanceResult.OK)) {
        item(key = "reader-title") {
            ArticleReadingTitle(title)
        }
        item(key = "reader-content") {
            val stateKey = when {
                view.jobState == "running" -> "processing"
                view.blocked.isNotEmpty() -> articleAiPauseKey(view.blocked)
                view.jobState == "queued" -> "queued_status"
                view.jobState == "failed" -> "reading_failed"
                else -> null
            }
            if (view.jobState in setOf("queued", "running") && view.blocked.isEmpty()) {
                stateKey?.let { Column(Modifier.padding(horizontal = Spacing.l)) { ReadingNote(it) } }
            } else (failureKey ?: stateKey)?.let { key ->
                ArticleRefreshError(key, onRetry.takeIf { view.blocked.isEmpty() })
            }
            ArticleReadingContent(summary, body, translated = view.translation != null,
                summaryNotes = articleReadingNotes(view, ArticleReadMode.SUMMARY).takeIf { summary != null }.orEmpty(),
                bodyNotes = articleReadingNotes(view, if (view.translation == null) ArticleReadMode.ORIGINAL else ArticleReadMode.TRANSLATION))
        }
    }
}

@Composable
internal fun ArticleReadingTitle(title: String) {
    val i18n = koinInject<I18nService>()
    Text(
        text = title.ifBlank { i18n.t("article.detail_title") },
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = Spacing.xs),
        style = MaterialTheme.typography.headlineSmall.editorial(),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun ArticleOriginalMetadata(source: String?, url: String?, meta: List<String>, modifier: Modifier = Modifier) {
    val i18n = koinInject<I18nService>()
    val domain = articleDisplayDomain(url).takeUnless { it == "文章详情" }
    val sources = listOfNotNull(source?.takeIf { it.isNotBlank() }, domain).joinToString(" · ")
    val metadata = articleReaderMetadata(sources, meta, i18n.t("article.source_unknown"))
    Text(
        text = "${i18n.t("article.source")}: $metadata",
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun ArticleReadingContent(
    summary: String?, body: String, translated: Boolean = false,
    summaryNotes: List<String> = emptyList(), bodyNotes: List<String> = emptyList(),
) {
    val i18n = koinInject<I18nService>()
    val distinctSummary = remember(summary, body) {
        summary?.let(::articleReaderSummary)?.trim()?.takeIf { it.isNotBlank() && it != body.trim() }
    }
    Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        if (distinctSummary != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m),
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xxs)) {
                        Box(Modifier.size(Spacing.xs + Spacing.xxs).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Text(
                            i18n.t("article_ai.summary_title"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    summaryNotes.forEach { ReadingNote(it) }
                    ArticleReaderBody(articleDetailPageContent(0, distinctSummary, null),
                        typography = MarkdownStyles.readingTypography(), padding = MarkdownStyles.summaryPadding(),
                        modifier = Modifier.performanceTab(ArticleReadMode.SUMMARY.performanceScreen(), PerformanceResult.OK))
                }
            }
        }
        bodyNotes.forEach { ReadingNote(it) }
        if (body.isNotBlank()) ArticleReaderBody(body, typography = MarkdownStyles.readingTypography(),
            padding = if (translated) MarkdownStyles.translationPadding() else MarkdownStyles.readingPadding(),
            modifier = if (translated) Modifier.performanceTab(ArticleReadMode.TRANSLATION.performanceScreen(), PerformanceResult.OK) else Modifier)
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
