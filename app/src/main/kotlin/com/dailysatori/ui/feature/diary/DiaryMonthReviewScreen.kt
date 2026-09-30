package com.dailysatori.ui.feature.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.R
import com.dailysatori.core.util.diaryCardDateTime
import com.dailysatori.core.util.diaryMonthKey
import com.dailysatori.core.util.diaryPreviewText
import com.dailysatori.core.util.toChineseNumber
import com.dailysatori.shared.db.Diary
import com.dailysatori.shared.db.Diary_attachment
import com.dailysatori.ui.component.card.DiaryCard
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.*
import com.mikepenz.markdown.m3.Markdown
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun diaryReviewMonthLabel(monthKey: String, locale: Locale): String = YearMonth.parse(monthKey)
    .format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy年M月" else "MMMM yyyy", locale))

private fun diaryReviewMonthName(monthKey: String, locale: Locale): String = if (locale.language == "zh") {
    "${toChineseNumber(YearMonth.parse(monthKey).monthValue)}月"
} else YearMonth.parse(monthKey).format(DateTimeFormatter.ofPattern("MMMM", locale))

@Composable
internal fun DiaryMonthHeader(diaries: List<Diary>, summary: String?, onReview: () -> Unit) {
    val month = diaryReviewMonthLabel(diaryMonthKey(diaries.first()), LocalConfiguration.current.locales[0])
    val monthName = diaryReviewMonthName(diaryMonthKey(diaries.first()), LocalConfiguration.current.locales[0])
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s, bottom = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.diary_feed_month_count, diaries.size), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(diaryPreviewText(summary?.takeIf { it.isNotBlank() } ?: stringResource(R.string.diary_feed_summary_empty)),
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().heightIn(min = Height.buttonSmall).clickable(role = Role.Button, onClick = onReview),
            contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.diary_feed_read_review, monthName), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun DiaryMonthReviewScreen(
    monthKey: String,
    diaries: List<Diary>,
    summary: String?,
    attachments: Map<Long, List<Diary_attachment>>,
    nowMillis: Long,
    onBack: () -> Unit,
    onEdit: (Diary) -> Unit,
    onDelete: (Diary) -> Unit,
    onRetryTranscription: (Long) -> Unit,
    onOpenTranscriptionSettings: () -> Unit,
) {
    val month = diaryReviewMonthLabel(monthKey, LocalConfiguration.current.locales[0])
    val monthName = diaryReviewMonthName(monthKey, LocalConfiguration.current.locales[0])
    var selectedDiaryId by rememberSaveable(monthKey) { mutableStateOf<Long?>(null) }
    val selectedDiary = diaries.firstOrNull { it.id == selectedDiaryId }
    val reviewListState = rememberLazyListState()
    BackHandler { if (selectedDiary != null) selectedDiaryId = null else onBack() }
    if (selectedDiary != null) {
        AppScaffold(title = stringResource(R.string.diary_feed_entry), onBack = { selectedDiaryId = null }) { modifier ->
            LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.m)) {
                item {
                    DiaryCard(selectedDiary, onEdit = { onEdit(selectedDiary) }, onDelete = { onDelete(selectedDiary) },
                        attachments = attachments[selectedDiary.id].orEmpty(), initiallyExpanded = true, nowMillis = nowMillis,
                        onRetryTranscription = onRetryTranscription, onOpenTranscriptionSettings = onOpenTranscriptionSettings)
                }
            }
        }
        return
    }
    AppScaffold(title = stringResource(R.string.diary_feed_month_review), onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize(), state = reviewListState, contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item(key = "summary") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(month, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.diary_feed_month_title, monthName), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.diary_feed_review_source, diaries.size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Markdown(content = summary?.takeIf { it.isNotBlank() && diaries.isNotEmpty() } ?: stringResource(R.string.diary_feed_summary_empty),
                        typography = MarkdownStyles.bookTypography(), padding = MarkdownStyles.summaryPadding())
                    HorizontalDivider()
                    Text(stringResource(R.string.diary_feed_month_notes), style = MaterialTheme.typography.titleLarge)
                }
            }
            items(diaries, key = { it.id }) { diary ->
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { selectedDiaryId = diary.id }
                    .padding(vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(diaryCardDateTime(diary.created_at, nowMillis, stringResource(R.string.diary_feed_today),
                            stringResource(R.string.diary_feed_yesterday), LocalConfiguration.current.locales[0]),
                            Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(IconSize.m))
                    }
                    Text(diaryPreviewText(diary.content).ifBlank { stringResource(R.string.diary_feed_attachment_entry) },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            item(key = "reflection") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(stringResource(R.string.diary_feed_question_title), style = MaterialTheme.typography.titleMedium)
                    Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)) {
                        Text(stringResource(R.string.diary_feed_question), Modifier.fillMaxWidth().padding(Spacing.m),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(R.string.diary_feed_source_note), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
