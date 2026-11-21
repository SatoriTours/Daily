package com.dailysatori.ui.feature.article

import com.dailysatori.core.service.ClipboardMonitorService
import com.dailysatori.ui.component.appbar.PageAction

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.ui.Alignment
import com.dailysatori.service.articleai.ArticleAiMode
import com.dailysatori.service.articleai.analyzeArticleBody
import com.dailysatori.ui.feature.myspace.MarkNewsReadMenuItem
import com.dailysatori.ui.feature.myspace.markNewsReadAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dailysatori.data.repository.reusableOriginalBody
import com.dailysatori.ui.component.card.articleDisplayDomain
import com.dailysatori.ui.component.card.articleDisplayTitle
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Article
import com.dailysatori.ui.feature.myspace.toReadNewsArticle
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.news.ArticleReaderBody
import com.dailysatori.ui.component.news.ArticleReaderHeader
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.*
import com.dailysatori.service.parser.articleTranslationMarkdown
import com.dailysatori.service.i18n.I18nService
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

@Composable
fun ArticleDetailScreen(
    articleId: Long,
    onBack: () -> Unit = {},
) {
    val viewModel: ArticleDetailViewModel = koinViewModel { parametersOf(articleId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    var showOriginalSheet by remember { mutableStateOf(false) }
    var showRefreshConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var coverHeightDp by remember { mutableIntStateOf(articleCoverMaxHeightDp) }
    val isRemote = state.article?.source_type == "remote_news"
    val aiViewModel: ArticleAiReaderViewModel? = if (isRemote) koinViewModel(key = "ai-reader:$articleId") {
        parametersOf(articleId)
    } else null
    val ai = aiViewModel?.view?.collectAsStateWithLifecycle()?.value
    val remoteBusy = ai?.jobState in setOf("queued", "running")
    val translation = if (isRemote) ai?.translation else state.article?.let(::localReaderTranslation)
    val hasOriginal = state.article?.reusableOriginalBody()?.isNotBlank() == true
    val canProcess = !state.isRefreshing && if (isRemote) ai?.canRequestReading() == true && !remoteBusy
        else state.article?.let(::canProcessLocalArticle) == true
    val openOriginal = { showOriginalSheet = true }

    val i18n = koinInject<I18nService>()

    AppScaffold(
        title = if (isRemote) "" else i18n.t("article.detail_title"),
        onBack = onBack,
        pageActions = ArticleDetailActions(state, viewModel::toggleFavorite, canProcess,
            onSmartReadClick = {
                if (isRemote) aiViewModel?.request()
                else viewModel.processWithExistingBody()
            },
            onRetranslate = if (hasOriginal && translation != null) ({
                if (isRemote) aiViewModel?.request(ArticleAiMode.TRANSLATION, redo = true)
                else viewModel.processWithExistingBody(retranslate = true)
            }) else null,
            busy = state.isRefreshing || remoteBusy,
            onRefreshClick = { showRefreshConfirm = true }, onDeleteClick = { showDeleteConfirm = true }),
    ) { modifier ->
        val article = state.article
        if (isRemote && article != null) {
            if (ai == null) LoadingIndicator(modifier)
            else ArticleAiReader(
                ai, articleMagazineTitle(article),
                articleMagazineMetaChips(article, i18n.t("article.published_on"), i18n.t("article.saved_on")),
                state.sourceNames.joinToString(" · ").ifBlank { extractDomain(article.url) },
                articleDetailPageContent(
                    page = if (ai.kind == com.dailysatori.service.articleai.ArticleBodyKind.SOURCE_SUMMARY) 0 else 1,
                    summary = article.ai_content, original = article.ai_markdown_content,
                    storedOriginal = article.original_markdown_content, isRemoteSnapshot = true,
                    originalImageUrls = listOfNotNull(article.cover_image_url),
                ), modifier,
                refreshError = state.refreshError,
                onOriginalClick = openOriginal,
            )
        } else ArticleDetailContent(
            state = state,
            coverHeightDp = coverHeightDp,
            onCoverHeightChange = { coverHeightDp = it },
            density = density,
            onOriginalClick = openOriginal,
            modifier = modifier,
        )
    }
    ArticleDetailDialogs(
        isRemote = isRemote,
        showRefreshConfirm = showRefreshConfirm,
        showDeleteConfirm = showDeleteConfirm,
        onRefreshDismiss = { showRefreshConfirm = false },
        onDeleteDismiss = { showDeleteConfirm = false },
        onRefreshConfirm = viewModel::refreshArticle,
        onDeleteConfirm = {
            viewModel.deleteArticle()
            onBack()
        },
    )

    if (showOriginalSheet) {
        state.article?.let { article ->
            ArticleOriginalBottomSheet(article, ai?.original, translation, ai?.hasPartialTranslation() == true, onDismiss = { showOriginalSheet = false })
        }
    }
}

@Composable
private fun ArticleDetailDialogs(
    isRemote: Boolean,
    showRefreshConfirm: Boolean,
    showDeleteConfirm: Boolean,
    onRefreshDismiss: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onRefreshConfirm: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    if (showRefreshConfirm) {
        ArticleRefreshConfirmDialog(
            isRemote = isRemote,
            onConfirm = {
                onRefreshDismiss()
                onRefreshConfirm()
            },
            onDismiss = onRefreshDismiss,
        )
    }

    if (showDeleteConfirm) {
        ArticleDeleteConfirmDialog(
            onConfirm = {
                onDeleteDismiss()
                onDeleteConfirm()
            },
            onDismiss = onDeleteDismiss,
        )
    }
}

@Composable
private fun ArticleDetailActions(
    state: ArticleDetailState,
    onFavoriteClick: () -> Unit,
    canProcess: Boolean,
    onSmartReadClick: () -> Unit,
    onRetranslate: (() -> Unit)?,
    busy: Boolean,
    onRefreshClick: () -> Unit,
    onDeleteClick: () -> Unit,
): List<PageAction> {
    val article = state.article ?: return emptyList()
    val i18n = koinInject<I18nService>()
    val isFavorite = article.is_favorite == 1L
    return listOf(
        PageAction(i18n.t(if (isFavorite) "article.unfavorite" else "article.favorite"),
            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            alwaysVisible = true, onClick = onFavoriteClick),
        PageAction(i18n.t("article_ai.smart_reading"), Icons.Default.AutoStories,
            alwaysVisible = true, enabled = canProcess, onClick = onSmartReadClick),
        ArticleDetailOverflow(state, article, onRetranslate, busy, onRefreshClick, onDeleteClick),
    )
}

@Composable
private fun ArticleDetailOverflow(
    state: ArticleDetailState,
    article: Article,
    onRetranslate: (() -> Unit)?,
    busy: Boolean,
    onRefreshClick: () -> Unit,
    onDeleteClick: () -> Unit,
): PageAction {
    val i18n = koinInject<I18nService>()
    val context = LocalContext.current
    val clipboardMonitor = koinInject<ClipboardMonitorService>()
    val markRead = markNewsReadAction(article.toReadNewsArticle(), reversible = true)
    return PageAction(i18n.t("article.more_actions"), MoreActionsIcon,
        alwaysVisible = true, menuContent = { dismiss ->
            MarkNewsReadMenuItem(markRead, dismiss)
            ArticleCopyLinkMenuItem(article.url) { url ->
                dismiss()
                copyArticleUrl(context, url, i18n, clipboardMonitor)
            }
            if (onRetranslate != null) DropdownMenuItem(
                text = { Text(i18n.t("article.retranslate")) },
                leadingIcon = { Icon(Icons.Default.Translate, null) },
                enabled = !busy,
                onClick = { dismiss(); onRetranslate() },
            )
            ArticleRefreshMenuItem(state) { dismiss(); onRefreshClick() }
            HorizontalDivider(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs))
            ArticleDeleteMenuItem { dismiss(); onDeleteClick() }
        }) {}
}

@Composable
private fun ArticleRefreshMenuItem(state: ArticleDetailState, onRefreshClick: () -> Unit) {
    val i18n = koinInject<I18nService>()
    DropdownMenuItem(
        text = { Text(i18n.t("article.refresh_article")) },
        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
        enabled = canManuallyRefreshArticle(state.isRefreshing, state.article?.status),
        onClick = onRefreshClick,
    )
}

@Composable
private fun ArticleCopyLinkMenuItem(url: String?, onCopyClick: (String) -> Unit) {
    val i18n = koinInject<I18nService>()
    DropdownMenuItem(
        text = { Text(i18n.t("article.copy_link")) },
        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
        enabled = !url.isNullOrBlank(),
        onClick = { if (!url.isNullOrBlank()) onCopyClick(url) },
    )
}

@Composable
private fun ArticleDeleteMenuItem(onDeleteClick: () -> Unit) {
    val i18n = koinInject<I18nService>()
    DropdownMenuItem(
        text = { Text(i18n.t("article.delete_article"), color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        onClick = onDeleteClick,
    )
}

@Composable
private fun ArticleDetailContent(
    state: ArticleDetailState,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
    onOriginalClick: () -> Unit,
    modifier: Modifier,
) {
    when {
        state.isLoading && state.article == null -> LoadingIndicator(modifier = modifier)
        state.article == null && !state.isRefreshing -> EmptyState(
            modifier = modifier,
            icon = Icons.Default.Favorite,
            title = "文章未找到",
            subtitle = "该文章可能已被删除",
        )
        else -> ArticleDetailLoadedContent(state, coverHeightDp, onCoverHeightChange, density, onOriginalClick, modifier)
    }
}

@Composable
private fun ArticleDetailLoadedContent(
    state: ArticleDetailState,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
    onOriginalClick: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.refreshError?.let { ArticleRefreshError(it) }
        val article = state.article ?: return@Column
        val coverImage = article.cover_image ?: article.cover_image_url
        if (state.isRefreshing) ArticleProcessingStepper(state.processingStage, state.processingProgress,
            modifier = Modifier.padding(Spacing.m))
        if (article.status == "error" && !article.original_markdown_content.isNullOrBlank()) {
            ArticleRefreshError("原文已保存，AI 处理失败，可刷新重试")
        }
        ArticleDetailPage(article, coverImage, coverHeightDp, onCoverHeightChange, density, state.sourceNames, onOriginalClick)
    }
}

@Composable
private fun ArticleRefreshError(message: String) {
    Text(
        message,
        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}


@Composable
private fun ArticleDetailPage(
    article: Article,
    coverImage: String?,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
    sourceNames: List<String>,
    onOriginalClick: () -> Unit,
) {
    val hasCover = !coverImage.isNullOrBlank()
    val listState = rememberLazyListState()
    val nestedScrollConnection = rememberArticleDetailNestedScrollConnection(
        hasCover, coverHeightDp, onCoverHeightChange, listState, density,
    )
    Column(modifier = Modifier.fillMaxSize()) {
        if (hasCover && coverHeightDp > 0) {
            ArticleCoverImage(imagePath = coverImage.orEmpty(), modifier = Modifier.fillMaxWidth().height(coverHeightDp.dp))
        }
        ArticleMagazineHeader(article, sourceNames, onOriginalClick)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            item(key = "summary-content") { ArticleDetailBody(article) }
        }
    }
}

@Composable
private fun ArticleDetailBody(article: Article) {
    val original = articleDetailPageContent(1, article.ai_content, article.ai_markdown_content,
        article.original_markdown_content, listOfNotNull(article.cover_image_url))
    val stored = article.reusableOriginalBody()
    val summary = article.ai_content.takeUnless { !stored.isNullOrBlank() && analyzeArticleBody(stored).isShort }
    ArticleReadingContent(summary, localReaderTranslation(article) ?: original)
}

private fun localReaderTranslation(article: Article): String? =
    articleTranslationMarkdown(article.original_markdown_content, article.ai_markdown_content)
        ?.takeUnless { it == article.ai_content?.trim() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleOriginalBottomSheet(
    article: Article,
    readerOriginal: String?,
    translation: String?,
    partialTranslation: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val i18n = koinInject<I18nService>()
    val clipboardMonitor = koinInject<ClipboardMonitorService>()
    val original = readerOriginal ?: articleDetailPageContent(page = 1, summary = article.ai_content,
        original = article.ai_markdown_content, storedOriginal = article.original_markdown_content,
        isRemoteSnapshot = article.source_type == "remote_news", originalImageUrls = listOfNotNull(article.cover_image_url))
    var showTranslation by remember(article.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.l)) {
            item {
                Text(
                    i18n.t(if (showTranslation && translation != null) "article_ai.translation_title" else "article_ai.original_title"),
                    style = MaterialTheme.typography.titleLarge,
                )
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
                    content = if (showTranslation && translation != null) translation else original,
                    typography = MarkdownStyles.readingTypography(),
                    padding = MarkdownStyles.readingPadding(),
                )
            }
        }
    }
}

@Composable
internal fun ArticleOriginalLink(onClick: () -> Unit) {
    val i18n = koinInject<I18nService>()
    TextButton(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(i18n.t("article_ai.original"))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(IconSize.xs))
        }
    }
}

@Composable
private fun rememberArticleDetailNestedScrollConnection(
    hasCover: Boolean,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    listState: LazyListState,
    density: Density,
): NestedScrollConnection = remember(listState, hasCover, density, coverHeightDp) {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!hasCover) return Offset.Zero
            val deltaDp = with(density) { available.y.toDp().value.toInt() }
            val contentAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            val nextHeight = articleCoverHeightAfterScroll(coverHeightDp, deltaDp, contentAtTop)
            if (nextHeight == coverHeightDp) return Offset.Zero
            onCoverHeightChange(nextHeight)
            return Offset(x = 0f, y = with(density) { (nextHeight - coverHeightDp).dp.toPx() })
        }
    }
}

@Composable
private fun ArticleMagazineHeader(article: Article, sourceNames: List<String>, onOriginalClick: () -> Unit) {
    val i18n = koinInject<I18nService>()
    ArticleReaderHeader(
        title = articleMagazineTitle(article),
        metaChips = articleMagazineMetaChips(article, i18n.t("article.published_on"), i18n.t("article.saved_on")),
        sourceName = sourceNames.joinToString(" · ").ifBlank {
            extractDomain(article.url).takeIf { it != "文章详情" }.orEmpty()
        },
        sourceAction = { ArticleOriginalLink(onOriginalClick) },
    )
}

@Composable
private fun ArticleRefreshConfirmDialog(isRemote: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val i18n = koinInject<I18nService>()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(i18n.t("article.refresh_title")) },
        text = { Text(i18n.t(if (isRemote) "article_ai.refresh_note" else "article.refresh_note")) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(i18n.t("article.refresh_article")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(i18n.t("article_ai.cancel")) } },
    )
}

@Composable
private fun ArticleDeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val i18n = koinInject<I18nService>()
    ConfirmDialog(
        title = i18n.t("article.delete_article"),
        message = i18n.t("article.delete_article_note"),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

private fun canProcessLocalArticle(article: Article): Boolean {
    val body = analyzeArticleBody(article.reusableOriginalBody())
    val hasSummary = !article.ai_content.isNullOrBlank() &&
        !com.dailysatori.service.parser.articleNeedsTranslation(article.ai_content.orEmpty())
    val mode = body.automatic(hasSummary)
    return mode?.summary == true || (mode?.translation == true && localReaderTranslation(article) == null)
}

private fun copyArticleUrl(context: Context, url: String, i18n: I18nService, monitor: ClipboardMonitorService) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    monitor.markProcessed(url)
    clipboard.setPrimaryClip(ClipData.newPlainText(i18n.t("article.web_link"), url))
    Toast.makeText(context, i18n.t("article.link_copied"), Toast.LENGTH_SHORT).show()
}

private fun extractDomain(url: String?): String = articleDisplayDomain(url)

private fun articleMagazineTitle(article: Article): String = articleDisplayTitle(article)

internal fun articleMagazineMetaChips(article: Article, publishedLabel: String, savedLabel: String): List<String> =
    listOfNotNull(article.pub_date?.let { "$publishedLabel ${TimeUtils.formatDate(it)}" }
        ?: article.created_at.takeIf { article.is_favorite == 1L }?.let { "$savedLabel ${TimeUtils.formatDate(it)}" })

@Composable
private fun ArticleCoverImage(
    imagePath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isLocal = !imagePath.startsWith("http://") && !imagePath.startsWith("https://")
    val resolvedPath = if (isLocal && !imagePath.startsWith("/")) {
        File(context.filesDir, "DailySatori/$imagePath").absolutePath
    } else {
        imagePath
    }
    val imageRequest = remember(context, resolvedPath) {
        ImageRequest.Builder(context)
            .data(resolvedPath)
            .build()
    }
    AsyncImage(
        model = imageRequest,
        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
        error = painterResource(android.R.drawable.ic_menu_report_image),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
