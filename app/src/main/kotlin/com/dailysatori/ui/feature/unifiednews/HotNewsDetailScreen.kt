package com.dailysatori.ui.feature.unifiednews

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.R
import com.dailysatori.ui.component.appbar.AssistantTopBarAction
import com.dailysatori.data.repository.articleOriginalMarkdown
import com.dailysatori.service.hotnews.HotNewsItem
import com.dailysatori.service.opportunity.newsArticleKey
import com.dailysatori.service.parser.articleTranslationMarkdown
import com.dailysatori.ui.component.news.ArticleReaderBody
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.*

@Composable
internal fun HotNewsDetailScreen(
    state: UnifiedNewsState,
    item: HotNewsItem,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFavorite: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var bodySelected by remember(item.identity) { mutableStateOf(false) }
    var sourceExpanded by remember(item.identity) { mutableStateOf(false) }

    val article = state.selectedHotNewsArticle
    val summary = article?.ai_content?.takeIf { it.isNotBlank() } ?: item.displaySummary
    val original = articleOriginalMarkdown(
        article?.original_markdown_content,
        article?.ai_markdown_content,
        article?.source_type == "remote_news",
    ) ?: item.originalContent
    val translation = articleTranslationMarkdown(original, article?.ai_markdown_content)
    val body = translation ?: original

    val appearances = remember(state.hotNewsBoards, item.url) {
        state.hotNewsBoards.flatMap { it.items }
            .filter { newsArticleKey(it.url, "") == newsArticleKey(item.url, "") }
            .ifEmpty { listOf(item) }
    }

    // 优先匹配当前选中条目 identity，再 fallback 到有排名的条目或自身
    val primaryAppearance = appearances.firstOrNull { it.identity == item.identity }
        ?: appearances.firstOrNull { it.rank > 0 }
        ?: item

    val isOriginalChinese = isChineseOriginalContent(original.ifBlank { item.originalTitle })
    val contentLabelKind = resolveHotNewsContentLabel(
        isBodySelected = bodySelected,
        hasTranslation = translation != null,
        isOriginalChinese = isOriginalChinese,
    )
    val metaStatus = resolveHotNewsMetaStatus(
        isLoading = state.hotNewsDetailLoading,
        isFailed = state.hotNewsDetailFailed,
    )

    val currentContent = if (bodySelected) body else summary
    val readingMinutes = calculateHotNewsReadingTimeMinutes(currentContent)

    val titleText = article?.ai_title?.takeIf { it.isNotBlank() && it != item.originalTitle }
        ?: item.displayTitle
        ?: item.originalTitle

    val hasDistinctOriginalTitle = item.originalTitle.isNotBlank() && item.originalTitle != titleText
    val isFavorite = article?.is_favorite == 1L

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // 顶栏：返回、平台/排名（可展开多平台/原标题）、收藏
            HotNewsDetailTopBar(
                platformName = primaryAppearance.platform.displayName,
                rank = primaryAppearance.rank,
                hasMultipleAppearances = appearances.size > 1 || hasDistinctOriginalTitle,
                isExpanded = sourceExpanded,
                onToggleExpand = { sourceExpanded = !sourceExpanded },
                onBack = onBack,
                isFavorite = isFavorite,
                isFavoriteEnabled = article != null,
                onFavorite = onFavorite,
            )

            // 可展开的来源与原标题详情
            AnimatedVisibility(
                visible = sourceExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    shape = RoundedCornerShape(Radius.m),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        appearances.filter { it.rank > 0 }.forEach { appearance ->
                            Text(
                                text = stringResource(
                                    R.string.news_hotnews_rank,
                                    appearance.platform.displayName,
                                    appearance.rank,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (hasDistinctOriginalTitle) {
                            Text(
                                text = stringResource(R.string.news_hotnews_original_title_prefix, item.originalTitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 文章主体滚动区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 标题区：自然换行不截断
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // v4 双段切换与状态行：同一行
                HotNewsDetailModeRow(
                    isBodySelected = bodySelected,
                    onSelectSummary = { bodySelected = false },
                    onSelectBody = { bodySelected = true },
                    metaStatus = metaStatus,
                    contentLabelKind = contentLabelKind,
                    readingMinutes = readingMinutes,
                    onRetry = onRetry,
                )

                // 正文回退提示（正文失败且无译文，回退原文）
                if (bodySelected && translation == null && body.isNotBlank() && state.hotNewsDetailFailed) {
                    Text(
                        text = stringResource(R.string.news_hotnews_original_fallback),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    )
                }

                // 内容渲染：摘要使用 cardTypography/cardPadding，正文使用 readingTypography/readingPadding
                val contentToDisplay = if (bodySelected) {
                    body
                } else {
                    formatHotNewsSummaryMarkdown(summary)
                }

                if (contentToDisplay.isNotBlank()) {
                    ArticleReaderBody(
                        content = contentToDisplay,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                        typography = if (bodySelected) MarkdownStyles.readingTypography() else MarkdownStyles.cardTypography(),
                        padding = if (bodySelected) MarkdownStyles.readingPadding() else MarkdownStyles.cardPadding(),
                    )
                    // AI 整理声明（仅在摘要视图展示）
                    if (!bodySelected) {
                        Text(
                            text = stringResource(R.string.news_hotnews_ai_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(
                            if (bodySelected) R.string.news_hotnews_body_missing else R.string.news_hotnews_summary_missing
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.m),
                    )
                }
            }

            // 底部操作栏：打开原文与查看讨论（讨论与原文相同时隐藏讨论）
            HotNewsDetailBottomBar(
                itemUrl = item.url,
                discussionUrl = item.discussionUrl,
                onOpenUrl = { url -> openArticleUrl(context, url) },
            )
        }
    }
}

@Composable
private fun HotNewsDetailTopBar(
    platformName: String,
    rank: Int,
    hasMultipleAppearances: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onBack: () -> Unit,
    isFavorite: Boolean,
    isFavoriteEnabled: Boolean,
    onFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Height.appBarCompact)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(Height.touchTarget),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.news_hotnews_back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(Radius.s))
                .clickable(enabled = hasMultipleAppearances, onClick = onToggleExpand)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = platformName,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            if (rank > 0) {
                Text(
                    text = " · ${stringResource(R.string.news_hotnews_rank_number, rank)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasMultipleAppearances) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.xs).padding(start = Spacing.xxs),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(
            onClick = onFavorite,
            enabled = isFavoriteEnabled,
            modifier = Modifier.size(Height.touchTarget),
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = stringResource(if (isFavorite) R.string.news_hotnews_favorited else R.string.news_hotnews_favorite),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        AssistantTopBarAction()
    }
}

@Composable
private fun HotNewsDetailModeRow(
    isBodySelected: Boolean,
    onSelectSummary: () -> Unit,
    onSelectBody: () -> Unit,
    metaStatus: HotNewsMetaStatus,
    contentLabelKind: HotNewsContentLabel,
    readingMinutes: Int?,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Height.appBarCompact)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 左侧双段胶囊切换器：通过 selectableGroup() 提供无障碍分组
        Row(
            modifier = Modifier
                .selectableGroup()
                .defaultMinSize(minHeight = Height.appBarCompact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentTabButton(
                text = stringResource(R.string.news_hotnews_summary),
                isSelected = !isBodySelected,
                onClick = onSelectSummary,
                isFirst = true,
            )
            SegmentTabButton(
                text = stringResource(R.string.news_hotnews_body),
                isSelected = isBodySelected,
                onClick = onSelectBody,
                isFirst = false,
            )
        }

        // 右侧状态与元信息：带自适应宽度和回流换行支持
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            when (metaStatus) {
                HotNewsMetaStatus.PROCESSING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(IconSize.xs),
                        strokeWidth = BorderWidth.m,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.news_hotnews_status_processing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HotNewsMetaStatus.FAILED -> {
                    Text(
                        text = stringResource(R.string.news_hotnews_status_incomplete),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = 0.dp),
                        modifier = Modifier.defaultMinSize(minHeight = Height.chip, minWidth = Spacing.xxl),
                    ) {
                        Text(
                            text = stringResource(R.string.news_hotnews_retry),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                HotNewsMetaStatus.READY -> {
                    val labelText = when (contentLabelKind) {
                        HotNewsContentLabel.AI_SUMMARY -> stringResource(R.string.news_hotnews_ai_curated)
                        HotNewsContentLabel.CHINESE_TRANSLATION -> stringResource(R.string.news_hotnews_chinese_translation)
                        HotNewsContentLabel.CHINESE_ORIGINAL -> stringResource(R.string.news_hotnews_chinese_original)
                        HotNewsContentLabel.ORIGINAL -> stringResource(R.string.news_hotnews_original_source)
                    }
                    val displayText = if (readingMinutes != null && readingMinutes > 0) {
                        stringResource(R.string.news_hotnews_reading_minutes_estimate, labelText, readingMinutes)
                    } else {
                        labelText
                    }
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isFirst: Boolean,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = Height.appBarCompact)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isFirst) Radius.m else Radius.none,
                bottomStart = if (isFirst) Radius.m else Radius.none,
                topEnd = if (!isFirst) Radius.m else Radius.none,
                bottomEnd = if (!isFirst) Radius.m else Radius.none,
            ),
            color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = if (isSelected) BorderWidth.s else 0.dp,
            modifier = Modifier.defaultMinSize(minHeight = Height.chip),
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = Height.chip)
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HotNewsDetailBottomBar(
    itemUrl: String,
    discussionUrl: String?,
    onOpenUrl: (String) -> Unit,
) {
    val showDiscussion = shouldShowHotNewsDiscussion(itemUrl, discussionUrl)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = BorderWidth.s,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onOpenUrl(itemUrl) },
                modifier = Modifier.weight(1f).height(Height.button),
                shape = RoundedCornerShape(Radius.m),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.xs).padding(end = Spacing.xs),
                )
                Text(
                    text = stringResource(R.string.news_hotnews_original),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (showDiscussion && discussionUrl != null) {
                OutlinedButton(
                    onClick = { onOpenUrl(discussionUrl) },
                    modifier = Modifier.weight(1f).height(Height.button),
                    shape = RoundedCornerShape(Radius.m),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.xs).padding(end = Spacing.xs),
                    )
                    Text(
                        text = stringResource(R.string.news_hotnews_discussion),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
