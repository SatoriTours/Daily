package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryMonthHeader(diaries: List<Diary>, summary: String?) {
    if (diaries.isEmpty()) return
    val monthKey = diaryMonthKey(diaries.first())
    val month = diaryReviewMonthLabel(monthKey, LocalConfiguration.current.locales[0])
    val review = remember(diaries, summary) { diaryMonthReviewContent(diaries, summary) }
    var showReview by rememberSaveable(monthKey) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s, bottom = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(month, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().clickable(enabled = review.summary != null) { showReview = true }
            .padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.diary_feed_review_title), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(review.summary?.let(::diaryPreviewText)?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.diary_feed_review_no_summary),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
    if (showReview && review.summary != null) {
        ModalBottomSheet(onDismissRequest = { showReview = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m).padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.diary_feed_review_title), style = MaterialTheme.typography.titleMedium)
                Markdown(content = review.summary, typography = MarkdownStyles.cardTypography(),
                    padding = MarkdownStyles.cardPadding())
                DiaryReviewDetails(review)
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
        Text(excerpt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
