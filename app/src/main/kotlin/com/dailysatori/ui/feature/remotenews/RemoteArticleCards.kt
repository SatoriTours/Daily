package com.dailysatori.ui.feature.remotenews

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.ui.component.card.CustomCard
import com.dailysatori.ui.feature.article.articleCardContentVerticalPaddingDp
import com.dailysatori.ui.feature.article.articleCardHeightDp
import com.dailysatori.ui.feature.article.articleCardSummaryMaxLines
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun RemoteArticleSummaryCard(article: RemoteArticle, onClick: () -> Unit) {
    CustomCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(articleCardHeightDp.dp)) {
            RemoteArticleCover(article.coverUrl, modifier = Modifier.width(120.dp).fillMaxHeight())
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.m, vertical = articleCardContentVerticalPaddingDp.dp),
            ) {
                Text(article.title.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                remoteArticleSummaryText(article)?.let {
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = articleCardSummaryMaxLines, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                RemoteArticleSourceRow(article)
            }
        }
    }
}

@Composable
private fun RemoteArticleSourceRow(article: RemoteArticle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val timeText = remoteArticleTimeText(article)
        timeText?.let { time ->
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        article.feedName?.let { name ->
            if (timeText != null) Spacer(Modifier.width(Spacing.xs))
            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(name, modifier = Modifier.padding(horizontal = Spacing.s, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        article.domain?.let { domain ->
            if (timeText != null || article.feedName != null) Spacer(Modifier.width(Spacing.xs))
            Text(domain, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun RemoteArticleCover(coverUrl: String?, modifier: Modifier = Modifier) {
    if (coverUrl.isNullOrBlank()) {
        RemoteArticleDefaultCover(modifier)
        return
    }

    val context = LocalContext.current
    val request = remember(context, coverUrl) { ImageRequest.Builder(context).data(coverUrl).build() }
    AsyncImage(
        model = request,
        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
        error = painterResource(android.R.drawable.ic_menu_report_image),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(topStart = Radius.m, bottomStart = Radius.m)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun RemoteArticleDefaultCover(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = Radius.m, bottomStart = Radius.m))
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

private fun remoteArticleSummaryText(article: RemoteArticle): String? = article.summary?.takeIf { it.isNotBlank() }

private fun remoteArticleTimeText(article: RemoteArticle): String? {
    val sourceTime = article.createdAt ?: article.processedAt
    return sourceTime?.replace('T', ' ')?.take(16)?.takeIf { it.isNotBlank() }
}
