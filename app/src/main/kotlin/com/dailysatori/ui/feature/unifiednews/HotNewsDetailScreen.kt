package com.dailysatori.ui.feature.unifiednews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.R
import com.dailysatori.service.hotnews.HotNewsItem
import com.dailysatori.data.repository.articleOriginalMarkdown
import com.dailysatori.service.parser.articleTranslationMarkdown
import com.dailysatori.service.opportunity.newsArticleKey
import com.dailysatori.ui.component.news.ArticleReaderBody
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.*

@Composable
internal fun HotNewsDetailScreen(state: UnifiedNewsState, item: HotNewsItem, onBack: () -> Unit, onRetry: () -> Unit, onFavorite: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var bodySelected by remember(item.identity) { mutableStateOf(false) }
    val article = state.selectedHotNewsArticle
    val summary = article?.ai_content?.takeIf { it.isNotBlank() } ?: item.displaySummary
    val original = articleOriginalMarkdown(article?.original_markdown_content, article?.ai_markdown_content,
        article?.source_type == "remote_news") ?: item.originalContent
    val translation = articleTranslationMarkdown(original, article?.ai_markdown_content)
    val body = translation ?: original
    val appearances = state.hotNewsBoards.flatMap { it.items }.filter { newsArticleKey(it.url, "") == newsArticleKey(item.url, "") }
        .ifEmpty { listOf(item) }
    AppScaffold(title = stringResource(R.string.news_hotnews_detail), onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().padding(bottom = Height.navBar + Spacing.m)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(article?.ai_title?.takeIf { it.isNotBlank() && it != item.originalTitle } ?: item.displayTitle ?: item.originalTitle,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (appearances.any { it.rank > 0 }) Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        appearances.filter { it.rank > 0 }.forEach { appearance ->
                            Text(stringResource(R.string.news_hotnews_rank, appearance.platform.displayName, appearance.rank),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (state.hotNewsDetailLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.news_hotnews_processing), style = MaterialTheme.typography.bodySmall)
                }
                if (state.hotNewsDetailFailed) {
                    Text(stringResource(R.string.news_hotnews_detail_failed), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.news_hotnews_retry)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    TextButton(onClick = { bodySelected = false }) { Text(stringResource(R.string.news_hotnews_summary),
                        color = if (!bodySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { bodySelected = true }) { Text(stringResource(R.string.news_hotnews_body),
                        color = if (bodySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val content = if (bodySelected) body else summary
                if (bodySelected && translation == null && body.isNotBlank() && state.hotNewsDetailFailed) {
                    Text(stringResource(R.string.news_hotnews_original_fallback), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (content.isNotBlank()) {
                    ArticleReaderBody(content,
                        typography = MarkdownStyles.cardTypography(), padding = MarkdownStyles.cardPadding())
                } else {
                    Text(stringResource(if (bodySelected) R.string.news_hotnews_body_missing else R.string.news_hotnews_summary_missing),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { openArticleUrl(context, item.url) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.news_hotnews_original))
                }
                item.discussionUrl?.takeIf { it != item.url }?.let { url ->
                    TextButton(onClick = { openArticleUrl(context, url) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.news_hotnews_discussion))
                    }
                }
                TextButton(onClick = onFavorite, enabled = article != null, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (article?.is_favorite == 1L) R.string.news_hotnews_favorited else R.string.news_hotnews_favorite))
                }
            }
        }
    }
}
