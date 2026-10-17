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
    val appearances = state.hotNewsBoards.flatMap { it.items }.filter { newsArticleKey(it.url, "") == newsArticleKey(item.url, "") }
        .ifEmpty { listOf(item) }
    AppScaffold(title = stringResource(R.string.news_hotnews_detail), onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(article?.ai_title ?: item.displayTitle ?: item.originalTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (appearances.any { it.rank > 0 }) Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        appearances.filter { it.rank > 0 }.forEach { appearance ->
                            Text(stringResource(R.string.news_hotnews_rank, appearance.platform.displayName, appearance.rank),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (state.isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.news_hotnews_processing), style = MaterialTheme.typography.bodySmall)
                }
                if (article == null && item.displaySummary.isNotBlank()) {
                    ArticleReaderBody(item.displaySummary, typography = MarkdownStyles.cardTypography(), padding = MarkdownStyles.cardPadding())
                }
                state.error?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.news_hotnews_retry)) }
                }
                if (article != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        TextButton(onClick = { bodySelected = false }) { Text(stringResource(R.string.news_hotnews_summary),
                            color = if (!bodySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = { bodySelected = true }) { Text(stringResource(R.string.news_hotnews_body),
                            color = if (bodySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    ArticleReaderBody(if (bodySelected) article.ai_markdown_content.orEmpty() else article.ai_content.orEmpty(),
                        typography = MarkdownStyles.cardTypography(), padding = MarkdownStyles.cardPadding())
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
