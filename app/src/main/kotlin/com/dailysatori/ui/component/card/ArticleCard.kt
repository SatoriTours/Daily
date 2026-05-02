package com.dailysatori.ui.component.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Article
import com.dailysatori.ui.component.chip.TagChipRow
import com.dailysatori.ui.feature.article.articleProcessingCardMessage
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import java.io.File

@Composable
fun ArticleCard(
    article: Article,
    tags: List<String> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavoriteClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
) {
    val title = article.title.orEmpty()
    val content = article.ai_content.orEmpty()
    val coverImage = article.cover_image ?: article.cover_image_url
    val domain = remember(article.url) { extractDomain(article.url) }
    val createdAt = remember(article.created_at) { TimeUtils.formatRelativeTime(article.created_at) }
    val isFavorite = article.is_favorite == 1L
    val processingMessage = articleProcessingCardMessage(article.status)

    CustomCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.height(100.dp)) {
            if (!coverImage.isNullOrBlank()) {
                ArticleCoverImage(
                    imagePath = coverImage,
                    modifier = Modifier.width(120.dp).fillMaxHeight(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
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
                if (!processingMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = processingMessage,
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Language,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xxs))
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(Spacing.s))
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xxs))
                        Text(
                            text = createdAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Row {
                        if (onFavoriteClick != null) {
                            IconButton(
                                onClick = onFavoriteClick,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "收藏",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isFavorite) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (onShareClick != null) {
                            IconButton(
                                onClick = onShareClick,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = "分享",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun extractDomain(url: String?): String {
    if (url.isNullOrBlank()) return ""
    return url.removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .removePrefix("www.")
        .ifBlank { "" }
}

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
    Box(modifier = modifier.clip(RoundedCornerShape(topStart = Radius.m, bottomStart = Radius.m))) {
        AsyncImage(
            model = imageRequest,
            placeholder = painterResource(android.R.drawable.ic_menu_gallery),
            error = painterResource(android.R.drawable.ic_menu_report_image),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentScale = ContentScale.Crop,
        )
    }
}
