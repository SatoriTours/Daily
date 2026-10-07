package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.R
import com.dailysatori.core.util.diaryImagePaths
import com.dailysatori.service.diary.DiaryThreadSnapshot
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.component.card.DiaryPhotoWall
import com.dailysatori.ui.theme.*
import com.mikepenz.markdown.m3.Markdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryThreadSheet(
    snapshot: DiaryThreadSnapshot,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onEditOriginal: () -> Unit,
    onRetrySummary: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryTranscription: ((Long) -> Unit)? = null,
    onDeleteAttachment: ((Long) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m),
        ) {
            DiaryThreadHeader(
                onDismiss = onDismiss,
                onContinue = onContinue,
                onEditOriginal = onEditOriginal,
            )

            Spacer(modifier = Modifier.height(Spacing.s))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item(key = "ai_summary") {
                    DiaryThreadSummarySection(
                        snapshot = snapshot,
                        onRetrySummary = onRetrySummary,
                    )
                }

                itemsIndexed(
                    items = snapshot.entries,
                    key = { _, entry -> entry.id },
                ) { index, entry ->
                    val isOriginal = index == 0
                    val attachments = snapshot.attachments.filter { it.diary_id == entry.id }
                    DiaryThreadEntryCard(
                        entry = entry,
                        isOriginal = isOriginal,
                        index = index,
                        attachments = attachments,
                        onRetryTranscription = onRetryTranscription,
                        onDeleteAttachment = onDeleteAttachment,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiaryThreadHeader(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onEditOriginal: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.diary_thread_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            OutlinedButton(onClick = onEditOriginal) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(IconSize.xs))
                Spacer(modifier = Modifier.size(Spacing.xxs))
                Text(stringResource(R.string.diary_thread_action_edit_original))
            }
            Button(onClick = onContinue) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(IconSize.xs))
                Spacer(modifier = Modifier.size(Spacing.xxs))
                Text(stringResource(R.string.diary_thread_action_continue))
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(IconSize.s))
            }
        }
    }
}

@Composable
private fun DiaryThreadSummarySection(
    snapshot: DiaryThreadSnapshot,
    onRetrySummary: () -> Unit,
) {
    val summaryUiState = remember(snapshot.summary, snapshot.revision, snapshot.pendingAttachmentCount) {
        resolveDiaryThreadSummaryUiState(
            summary = snapshot.summary,
            revision = snapshot.revision,
            pendingAttachmentCount = snapshot.pendingAttachmentCount,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.s),
                    )
                    Text(
                        text = stringResource(R.string.diary_thread_ai_summary),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    when (summaryUiState) {
                        is DiaryThreadSummaryUiState.Generating -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(IconSize.xs),
                                strokeWidth = Spacing.xxs,
                            )
                            Text(
                                text = stringResource(R.string.diary_thread_summary_generating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is DiaryThreadSummaryUiState.Failed -> {
                            Text(
                                text = stringResource(R.string.diary_thread_summary_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = onRetrySummary) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(IconSize.xs))
                                Spacer(modifier = Modifier.size(Spacing.xxs))
                                Text(stringResource(R.string.diary_thread_summary_retry))
                            }
                        }
                        is DiaryThreadSummaryUiState.Ready -> {
                            if (summaryUiState.isStale) {
                                Surface(
                                    shape = RoundedCornerShape(Radius.circular),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        text = stringResource(R.string.diary_thread_summary_stale),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                                    )
                                }
                            }
                        }
                        is DiaryThreadSummaryUiState.None -> Unit
                    }
                }
            }

            if (summaryUiState is DiaryThreadSummaryUiState.Generating && summaryUiState.isStale) {
                Surface(
                    shape = RoundedCornerShape(Radius.circular),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.diary_thread_summary_stale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                    )
                }
            }

            if (summaryUiState is DiaryThreadSummaryUiState.Ready) {
                Markdown(
                    content = summaryUiState.text,
                    typography = MarkdownStyles.cardTypography(),
                    padding = MarkdownStyles.cardPadding(),
                )
            } else if (summaryUiState is DiaryThreadSummaryUiState.Generating) {
                if (summaryUiState.oldText != null) {
                    Markdown(
                        content = summaryUiState.oldText,
                        typography = MarkdownStyles.cardTypography(),
                        padding = MarkdownStyles.cardPadding(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.diary_thread_summary_generating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (summaryUiState is DiaryThreadSummaryUiState.Failed) {
                if (summaryUiState.oldText != null) {
                    Markdown(
                        content = summaryUiState.oldText,
                        typography = MarkdownStyles.cardTypography(),
                        padding = MarkdownStyles.cardPadding(),
                    )
                } else {
                    Text(
                        text = summaryUiState.errorMessage ?: stringResource(R.string.diary_thread_summary_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (summaryUiState is DiaryThreadSummaryUiState.Ready && summaryUiState.hasPendingAudio ||
                summaryUiState is DiaryThreadSummaryUiState.Generating && summaryUiState.hasPendingAudio ||
                summaryUiState is DiaryThreadSummaryUiState.Failed && summaryUiState.hasPendingAudio
            ) {
                Text(
                    text = "· ${stringResource(R.string.diary_thread_audio_pending)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiaryThreadEntryCard(
    entry: Diary,
    isOriginal: Boolean,
    index: Int,
    attachments: List<com.dailysatori.shared.db.Diary_attachment>,
    onRetryTranscription: ((Long) -> Unit)?,
    onDeleteAttachment: ((Long) -> Unit)?,
) {
    val context = LocalContext.current
    val imagePaths = diaryImagePaths(entry.images)
    val displayContent = filterDisplayableDiaryContent(entry.content)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Surface(
                        shape = RoundedCornerShape(Radius.circular),
                        color = if (isOriginal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = if (isOriginal) {
                                stringResource(R.string.diary_thread_original_header)
                            } else {
                                stringResource(R.string.diary_thread_reply_header, index)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isOriginal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                        )
                    }

                    Text(
                        text = formatDiaryThreadEntryTime(entry.created_at),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                entry.mood?.takeIf { it.isNotBlank() && it != "null" }?.let { mood ->
                    Text(
                        text = mood,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (displayContent.isNotBlank()) {
                Markdown(
                    content = displayContent,
                    typography = MarkdownStyles.cardTypography(),
                    padding = MarkdownStyles.cardPadding(),
                )
            }

            if (imagePaths.isNotEmpty()) {
                DiaryPhotoWall(imagePaths = imagePaths, filesDir = context.filesDir, expanded = true)
            }

            if (attachments.isNotEmpty()) {
                DiaryAttachmentList(
                    attachments = attachments,
                    onRetryTranscription = onRetryTranscription,
                    onDelete = onDeleteAttachment?.let { callback -> { attachment -> callback(attachment.id) } },
                    compact = false,
                )
            }
        }
    }
}
