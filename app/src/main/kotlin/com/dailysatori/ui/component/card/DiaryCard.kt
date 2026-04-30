package com.dailysatori.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.component.media.SmartImage
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import java.io.File

private const val CONTENT_PREVIEW_LINES = 12
private const val CONTENT_LONG_THRESHOLD = 300

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryCard(
    diary: Diary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tags = diary.tags
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() && it != "null" }
        ?: emptyList()

    val imagePaths = diary.images
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() && it != "null" }
        ?: emptyList()

    val contentText = diary.content
    val isLongContent = contentText.length > CONTENT_LONG_THRESHOLD
    var expanded by remember { mutableStateOf(false) }

    CustomCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.width(Spacing.xxs))
                Text(
                    text = TimeUtils.formatShortDateTime(diary.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxs))

            if (contentText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!expanded && isLongContent)
                                Modifier.heightIn(max = 240.dp)
                            else Modifier
                        )
                        .clip(RoundedCornerShape(Radius.xs)),
                ) {
                    Markdown(
                        content = contentText,
                        typography = MarkdownStyles.cardTypography(),
                        padding = MarkdownStyles.cardPadding(),
                    )
                }

                if (isLongContent) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (expanded) "收起 ▲" else "查看全部 ▼",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (imagePaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(imagePaths, key = { it }) { imagePath ->
                        val localFile = File(context.filesDir, "DailySatori/$imagePath")
                        if (localFile.exists()) {
                            SmartImage(
                                imagePath = imagePath,
                                size = 76.dp,
                                contentDescription = "日记图片",
                            )
                        }
                    }
                }
            }

            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.s))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    tags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.xxs))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = Spacing.xs, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun moodToEmoji(mood: String): String {
    return when (mood.lowercase()) {
        "happy", "开心" -> "😊"
        "sad", "难过" -> "😢"
        "angry", "生气" -> "😡"
        "excited", "兴奋" -> "🤩"
        "calm", "平静" -> "😌"
        "tired", "疲惫" -> "😴"
        "grateful", "感恩" -> "🙏"
        "anxious", "焦虑" -> "😰"
        "hopeful", "期待" -> "🌟"
        "thoughtful", "思考" -> "🤔"
        else -> "📝"
    }
}
