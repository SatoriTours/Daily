package com.dailysatori.ui.feature.diary

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.R
import com.dailysatori.core.util.diaryMonthKey
import com.dailysatori.core.util.diaryPreviewText
import com.dailysatori.core.util.diaryTags
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.theme.*
import com.mikepenz.markdown.m3.Markdown
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun diaryReviewMonthLabel(monthKey: String, locale: Locale): String = YearMonth.parse(monthKey)
    .format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy年M月" else "MMMM yyyy", locale))

internal data class DiaryMonthReviewContent(val summary: String?, val tags: List<String>, val excerpt: String?)

internal fun diaryMonthReviewContent(diaries: List<Diary>, summary: String?): DiaryMonthReviewContent =
    DiaryMonthReviewContent(
        summary = summary?.trim()?.takeIf { it.isNotEmpty() && diaries.isNotEmpty() },
        tags = diaries.flatMap { diaryTags(it.tags) }.map { it.removePrefix("#").trim() }
            .filter { it.isNotEmpty() && it != "null" }.distinct().take(3),
        excerpt = diaries.firstNotNullOfOrNull { diaryPreviewText(it.content).takeIf(String::isNotBlank) },
    )

@Composable
internal fun DiaryMonthHeader(diaries: List<Diary>, summary: String?) {
    if (diaries.isEmpty()) return
    val monthKey = diaryMonthKey(diaries.first())
    val month = diaryReviewMonthLabel(monthKey, LocalConfiguration.current.locales[0])
    val review = remember(diaries, summary) { diaryMonthReviewContent(diaries, summary) }
    var expanded by rememberSaveable(monthKey) { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s, bottom = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.diary_feed_month_count, diaries.size),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().animateContentSize().padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.diary_feed_review_title), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.diary_feed_collapse else R.string.diary_feed_expand))
                        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, modifier = Modifier.size(IconSize.s))
                    }
                }
                if (expanded && review.summary != null) {
                    Markdown(content = review.summary, typography = MarkdownStyles.cardTypography(),
                        padding = MarkdownStyles.cardPadding())
                } else {
                    Text(review.summary?.let(::diaryPreviewText) ?: stringResource(R.string.diary_feed_review_no_summary),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (expanded) DiaryReviewDetails(review)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryReviewDetails(review: DiaryMonthReviewContent) {
    if (review.tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            review.tags.forEach { tag ->
                Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(tag, Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
    review.excerpt?.let { excerpt ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(excerpt, style = DiaryStyles.quoteTypography(), color = MaterialTheme.colorScheme.tertiary,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
