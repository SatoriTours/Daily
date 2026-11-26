package com.dailysatori.ui.feature.remotenews

import com.dailysatori.ui.component.appbar.PageAction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lightbulb
import com.dailysatori.ui.theme.MoreActionsIcon
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dailysatori.core.service.ClipboardMonitorService
import com.dailysatori.service.parser.normalizeArticleMarkdownImages
import com.dailysatori.service.parser.tidyArticleTranslation
import coil3.compose.AsyncImage
import com.dailysatori.core.diagnostics.performanceImageModel
import coil3.request.ImageRequest
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.ui.component.news.ArticleReaderBody
import com.dailysatori.ui.feature.article.ArticleOriginalMenuItem
import com.dailysatori.ui.feature.article.ArticleOriginalMetadata
import com.dailysatori.ui.feature.article.ArticleReadingTitle
import com.dailysatori.ui.feature.article.ReadingNote
import com.dailysatori.ui.feature.article.copyArticleUrl
import com.dailysatori.ui.feature.article.hasPartialTranslation
import com.dailysatori.ui.theme.SmartReadingIcon
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.articleCoverMaxHeightDp
import com.dailysatori.ui.feature.article.rememberArticleCoverNestedScrollConnection
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.feature.article.ArticleAiReader
import com.dailysatori.ui.feature.article.canRequestReading
import com.dailysatori.service.articleai.ArticleAiMode
import com.dailysatori.service.ideatopic.articleIdeaCaptureInput
import com.dailysatori.ui.feature.article.remoteReadingRetry
import com.dailysatori.ui.feature.ideatopic.IdeaTopicCaptureSheet
import androidx.compose.material.icons.filled.Translate
import com.dailysatori.ui.feature.article.ArticleAiReaderViewModel
import com.dailysatori.ui.component.indicator.LoadingIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.i18n.I18nService
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.ui.theme.editorial
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dailysatori.ui.feature.myspace.toReadNewsArticle
import com.dailysatori.ui.feature.myspace.MarkNewsReadButton

private val RemoteImagePlaceholderRegex = Regex(
    """[!！](?:\[[^]]*]|图片|配图|插图|图像|image|photo|figure)""",
    RegexOption.IGNORE_CASE,
)

@Composable
fun RemoteArticleDetailScreen(
    article: RemoteArticle,
    onBack: () -> Unit,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    showFavoriteAction: Boolean = false,
    sourceIdentity: String = article.domain ?: article.feedName.orEmpty(),
    localArticleId: Long? = null,
    sourceName: String? = null,
    onTopic: ((String) -> Unit)? = null,
) {
    val density = LocalDensity.current
    var showOriginalSheet by remember { mutableStateOf(false) }
    var showCaptureSheet by remember { mutableStateOf(false) }
    var coverHeightDp by remember { mutableIntStateOf(articleCoverMaxHeightDp) }
    val aiViewModel: ArticleAiReaderViewModel? = if (localArticleId != null)
        koinViewModel(key = "ai-reader:$localArticleId") { parametersOf(localArticleId) } else null
    val ai = aiViewModel?.view?.collectAsStateWithLifecycle()?.value
    val busy = ai?.jobState in setOf("queued", "running")
    val i18n = koinInject<I18nService>()
    val processSavedBody: () -> Unit = { aiViewModel?.request() }
    val retranslate: (() -> Unit)? = if (!busy && ai?.translation != null && !ai.original.isNullOrBlank() && ai.blocked.isEmpty())
        ({ aiViewModel.request(ArticleAiMode.TRANSLATION, redo = true) }) else null

    BackHandler(onBack = onBack)

    AppScaffold(
        title = "",
        onBack = onBack,
        pageActions = listOfNotNull(
            if (localArticleId != null) PageAction(i18n.t("article_ai.smart_reading"), SmartReadingIcon, iconSize = IconSize.l,
                enabled = !busy && ai?.canRequestReading() == true && ai.blocked.isEmpty(),
                alwaysVisible = true, onClick = processSavedBody) else null,
            RemoteArticleDetailActions(
                article = article, sourceIdentity = sourceIdentity, localArticleId = localArticleId,
                isFavorite = isFavorite, showFavoriteAction = showFavoriteAction,
                onOriginalClick = { showOriginalSheet = true }, onFavoriteClick = onFavoriteClick,
                onCaptureIdeaClick = { showCaptureSheet = true }, onRetranslate = retranslate,
            )),
    ) { modifier ->
        if (ai != null) {
            ArticleAiReader(ai, remoteArticleDisplayTitle(article),
                if (article.content.isNullOrBlank()) remoteArticleDetailPageContent(0, article.summary, article.viewpoints, null)
                else remoteArticleOriginalPageContent(article.content, listOfNotNull(article.coverUrl)),
                modifier, onRetry = remoteReadingRetry(ai, busy, processSavedBody, retranslate))
        } else Column(modifier = modifier.fillMaxSize()) {
            RemoteArticleDetailPager(article, coverHeightDp, { coverHeightDp = it }, density)
        }
    }
    if (showOriginalSheet) {
        val source = listOfNotNull(sourceName, article.feedName, article.domain).filter(String::isNotBlank).joinToString(" · ").ifBlank { sourceIdentity }
        RemoteArticleOriginalBottomSheet(article, source, ai?.translation, ai?.hasPartialTranslation() == true) { showOriginalSheet = false }
    }
    if (showCaptureSheet) RemoteArticleIdeaCaptureSheet(article, localArticleId, ai?.original, ai?.summary, onTopic) { showCaptureSheet = false }
}

@Composable
private fun RemoteArticleDetailActions(
    article: RemoteArticle,
    sourceIdentity: String,
    localArticleId: Long?,
    isFavorite: Boolean,
    showFavoriteAction: Boolean,
    onOriginalClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onCaptureIdeaClick: () -> Unit,
    onRetranslate: (() -> Unit)? = null,
): PageAction {
    val markRead = com.dailysatori.ui.feature.myspace.markNewsReadAction(article.toReadNewsArticle(sourceIdentity, localArticleId))
    val i18n = koinInject<I18nService>()
    return PageAction(i18n.t("article.more_actions"), MoreActionsIcon, alwaysVisible = true, menuContent = { dismiss ->
        val closeMenu: (Boolean) -> Unit = { dismiss() }
        ArticleOriginalMenuItem(dismiss, onOriginalClick)
        com.dailysatori.ui.feature.myspace.MarkNewsReadMenuItem(markRead, dismiss)
        if (showFavoriteAction) RemoteArticleFavoriteMenuItem(isFavorite, closeMenu, onFavoriteClick)
        RemoteArticleCopyLinkMenuItem(article.url, dismiss)
        DropdownMenuItem(
            text = { Text(i18n.t("article.capture_idea")) },
            leadingIcon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
            onClick = { dismiss(); onCaptureIdeaClick() },
        )
        if (onRetranslate != null) DropdownMenuItem(
            text = { Text(i18n.t("article.retranslate")) },
            leadingIcon = { Icon(Icons.Default.Translate, null) },
            onClick = { dismiss(); onRetranslate() },
        )
        RemoteArticleOpenMenuItem(article, closeMenu)
    }) {}
}

@Composable
private fun RemoteArticleIdeaCaptureSheet(
    article: RemoteArticle,
    localArticleId: Long?,
    aiOriginal: String?,
    aiSummary: String?,
    onTopic: ((String) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val input = remember(article, localArticleId, aiOriginal, aiSummary) {
        articleIdeaCaptureInput(
            articleId = localArticleId,
            title = remoteArticleDisplayTitle(article),
            originalContent = aiOriginal ?: article.content ?: article.summary.orEmpty(),
            summary = aiSummary ?: article.summary,
            url = article.url,
            sourceKeyId = localArticleId?.takeIf { it > 0 }?.toString()
                ?: article.id.takeIf { it > 0 }?.let { "remote:$it" },
        )
    }
    IdeaTopicCaptureSheet(
        source = input.source,
        initialContent = input.content,
        onCaptured = { topicId ->
            onDismiss()
            onTopic?.invoke(topicId)
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun RemoteArticleFavoriteMenuItem(
    isFavorite: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onFavoriteClick: () -> Unit,
) {
    val i18n = koinInject<I18nService>()
    val label = i18n.t(if (isFavorite) "article.unfavorite" else "article.favorite")
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = label,
                tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = {
            onExpandedChange(false)
            onFavoriteClick()
        },
    )
}

@Composable
private fun RemoteArticleCopyLinkMenuItem(url: String?, dismiss: () -> Unit) {
    val context = LocalContext.current
    val i18n = koinInject<I18nService>()
    val clipboardMonitor = koinInject<ClipboardMonitorService>()
    DropdownMenuItem(
        text = { Text(i18n.t("article.copy_link")) },
        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
        enabled = !url.isNullOrBlank(),
        onClick = {
            dismiss()
            if (!url.isNullOrBlank()) copyArticleUrl(context, url, i18n, clipboardMonitor)
        },
    )
}

@Composable
private fun RemoteArticleOpenMenuItem(article: RemoteArticle, onExpandedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val i18n = koinInject<I18nService>()
    DropdownMenuItem(
        text = { Text(i18n.t("article.open_in_browser")) },
        leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
        onClick = {
            onExpandedChange(false)
            openArticleUrl(context, article.url)
        },
    )
}

@Composable
private fun RemoteArticleDetailPager(
    article: RemoteArticle,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
) {
    val coverImage = article.coverUrl
    val hasCover = !coverImage.isNullOrBlank()
    RemoteArticleDetailPage(article, coverImage, hasCover, coverHeightDp, onCoverHeightChange, density)
}

@Composable
private fun RemoteArticleDetailPage(
    article: RemoteArticle,
    coverImage: String?,
    hasCover: Boolean,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
) {
    val listState = rememberLazyListState()
    val nestedScrollConnection = rememberArticleCoverNestedScrollConnection(
        hasCover, coverHeightDp, onCoverHeightChange, listState, density,
    )
    Column(modifier = Modifier.fillMaxSize()) {
        if (hasCover && coverHeightDp > 0) {
            RemoteArticleCoverImage(imageUrl = coverImage.orEmpty(), modifier = Modifier.fillMaxWidth().height(coverHeightDp.dp))
        }
        ArticleReadingTitle(remoteArticleDisplayTitle(article))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            item(key = "remote-summary-content") { RemoteArticleDetailBody(article) }
        }
    }
}

@Composable
private fun RemoteArticleDetailBody(article: RemoteArticle) {
    Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        ArticleReaderBody(
            content = remoteArticleDetailPageContent(
                page = 0,
                summary = article.summary,
                viewpoints = article.viewpoints,
                original = article.content,
                originalImageUrls = listOfNotNull(article.coverUrl),
            ),
            typography = MarkdownStyles.remoteArticleTypography(),
            padding = MarkdownStyles.remoteArticlePadding(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteArticleOriginalBottomSheet(
    article: RemoteArticle,
    source: String?,
    translation: String? = null,
    partialTranslation: Boolean = false,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val i18n = koinInject<I18nService>()
    val clipboardMonitor = koinInject<ClipboardMonitorService>()
    val original = remember(article.content, article.coverUrl) {
        remoteArticleOriginalPageContent(article.content, listOfNotNull(article.coverUrl))
    }
    var showTranslation by remember(article.id, article.url) { mutableStateOf(false) }
    val readableTranslation = remember(translation, article) {
        translation?.let { tidyArticleTranslation(it, remoteArticleDisplayTitle(article)) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
    ) {
        LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s)) {
            item {
                Text(
                    i18n.t(if (showTranslation && translation != null) "article_ai.translation_title" else "article_ai.original_title"),
                    style = MaterialTheme.typography.titleLarge.editorial(),
                )
                ArticleOriginalMetadata(source, article.url, remoteArticleMetaChips(article))
                article.url?.takeIf { it.isNotBlank() }?.let { url ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        TextButton(onClick = { copyArticleUrl(context, url, i18n, clipboardMonitor) }, modifier = Modifier.weight(1f)) {
                            Text(i18n.t("article.copy_link"))
                        }
                        TextButton(onClick = { openArticleUrl(context, url) }, modifier = Modifier.weight(1f)) {
                            Text(i18n.t("article.open_in_browser"))
                        }
                    }
                }
                if (translation != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        FilterChip(selected = !showTranslation, onClick = { showTranslation = false },
                            label = { Text(i18n.t("article_ai.original_title")) })
                        FilterChip(selected = showTranslation, onClick = { showTranslation = true },
                            label = { Text(i18n.t("article_ai.translation_title")) })
                    }
                }
            }
            item {
                if (showTranslation && partialTranslation) ReadingNote("translate_excerpt_note")
                ArticleReaderBody(
                    content = if (showTranslation && readableTranslation != null) readableTranslation else original,
                    typography = MarkdownStyles.remoteArticleTypography(),
                    padding = if (showTranslation) MarkdownStyles.translationPadding() else MarkdownStyles.remoteArticlePadding(),
                )
            }
        }
    }
}

internal fun remoteArticleDetailPageContent(
    page: Int,
    summary: String?,
    viewpoints: List<String>,
    original: String?,
    originalImageUrls: List<String> = emptyList(),
): String = when (page) {
    0 -> remoteArticleSummaryPageContent(summary, viewpoints, original, originalImageUrls)
    else -> remoteArticleOriginalPageContent(original, originalImageUrls)
}

/**
 * RSS/Atom entries usually carry only a description, which is stored as the article body. When no AI
 * summary exists the reading area shows that local body instead of an empty state; a real summary and
 * its viewpoints always win.
 */
private fun remoteArticleSummaryPageContent(
    summary: String?,
    viewpoints: List<String>,
    original: String?,
    originalImageUrls: List<String>,
): String {
    val summaryContent = summary?.trim()?.takeIf { it.isNotBlank() }
    val viewpointContent = viewpoints
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n") { "- $it" }
    val hasAiSummary = summaryContent != null || viewpointContent.isNotBlank()
    val bodyContent = original?.trim()?.takeIf { it.isNotBlank() }
        ?.let { remoteArticleOriginalPageContent(it, originalImageUrls) }

    return listOfNotNull(
        summaryContent,
        viewpointContent.takeIf { it.isNotBlank() },
        bodyContent.takeIf { !hasAiSummary },
    ).joinToString(separator = "\n\n").ifBlank { "暂无摘要内容，请刷新当前来源后重试。" }
}

private fun remoteArticleOriginalPageContent(original: String?, imageUrls: List<String>): String {
    val content = original?.trim()?.takeIf { it.isNotBlank() } ?: "暂无原文内容，请刷新当前来源后重试。"
    if (content == "暂无原文内容，请刷新当前来源后重试。" || imageUrls.isEmpty() || !content.hasRemoteImagePlaceholder()) return content
    return normalizeArticleMarkdownImages(content, imageUrls)
}

@Composable
private fun RemoteArticleCoverImage(imageUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(context, imageUrl) { ImageRequest.Builder(context).data(imageUrl).build() }
    AsyncImage(
        model = performanceImageModel(request),
        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
        error = painterResource(android.R.drawable.ic_menu_report_image),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

private fun remoteArticleMetaChips(article: RemoteArticle): List<String> = listOfNotNull(
    article.feedName,
    article.domain,
    article.publishedAt?.take(10) ?: article.createdAt?.take(10),
    article.importanceScore?.let { "重要性 ${String.format("%.1f", it)}" },
).filter { it.isNotBlank() }.take(4)

private fun remoteArticleDisplayTitle(article: RemoteArticle): String = listOfNotNull(
    article.title?.trim()?.takeIf { it.isNotBlank() },
    article.summary?.trim()?.takeIf { it.isNotBlank() }?.take(48),
    article.feedName?.trim()?.takeIf { it.isNotBlank() },
    article.domain?.trim()?.takeIf { it.isNotBlank() },
).firstOrNull() ?: "未命名远程文章"

private fun String.hasRemoteImagePlaceholder(): Boolean =
    RemoteImagePlaceholderRegex.containsMatchIn(this)
