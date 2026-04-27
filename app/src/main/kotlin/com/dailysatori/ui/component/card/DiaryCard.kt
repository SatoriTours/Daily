package com.dailysatori.ui.component.card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.component.chip.TagChipRow
import com.dailysatori.ui.theme.Spacing

@Composable
fun DiaryCard(
    diary: Diary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tags = diary.tags
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

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
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(Spacing.xxs))
                Text(
                    text = formatDiaryDateTime(diary.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxs))

            if (diary.content.isNotBlank()) {
                Text(
                    text = diary.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    diary.mood?.let { mood ->
                        Text(
                            text = moodToEmoji(mood),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                    }
                    TagChipRow(
                        tags = tags,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                diary.mood?.let { mood ->
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = moodToEmoji(mood),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun formatDiaryDateTime(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
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
