package com.dailysatori.ui.component.card

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Article
import com.dailysatori.ui.component.chip.TagChipRow
import com.dailysatori.ui.component.media.SmartImage
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun ArticleCard(
    article: Article,
    tags: List<String> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = article.title.orEmpty()
    val content = article.ai_content.orEmpty()
    val pubDate = article.pub_date
    val coverImage = article.cover_image ?: article.cover_image_url

    CustomCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.m),
            verticalAlignment = Alignment.Top,
        ) {
            SmartImage(
                imagePath = coverImage,
                size = 80.dp,
                contentDescription = title,
            )
            Spacer(modifier = Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    pubDate?.let { date ->
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xxs))
                        Text(
                            text = TimeUtils.formatRelativeTime(date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        TagChipRow(
                            tags = tags,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
