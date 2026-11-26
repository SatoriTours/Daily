package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.R
import com.dailysatori.core.util.diaryMonthKey
import com.dailysatori.core.util.diaryPreviewText
import com.dailysatori.core.util.diaryTags
import com.dailysatori.shared.db.Diary_record as Diary
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiaryMonthHeader(
    diaries: List<Diary>,
    summary: String?,
    showMonthTitle: Boolean = true,
) {
    if (diaries.isEmpty()) return
    val monthKey = diaryMonthKey(diaries.first())
    val locale = LocalConfiguration.current.locales[0]
    val month = diaryReviewMonthLabel(monthKey, locale)
    val review = remember(diaries, summary) { diaryMonthReviewContent(diaries, summary) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (showMonthTitle) Spacing.s else Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (showMonthTitle) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    month,
                    style = MaterialTheme.typography.titleSmall.editorial(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (locale.language == "zh") "${diaries.size} 篇" else "${diaries.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (review.summary != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.xs),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.diary_feed_review_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Markdown(
                        content = review.summary,
                        typography = MarkdownStyles.cardTypography(),
                        padding = MarkdownStyles.cardPadding(),
                    )
                    if (review.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            review.tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(Radius.circular),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                                ) {
                                    Text(
                                        "#$tag",
                                        Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (showMonthTitle) {
            Text(
                stringResource(R.string.diary_feed_review_no_summary),
                modifier = Modifier.padding(horizontal = Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
