package com.dailysatori.ui.component.card

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Article
import com.dailysatori.ui.component.news.MagazineNewsCard
import com.dailysatori.ui.feature.article.articleProcessingCardMessage

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
    val coverImage = article.cover_image ?: article.cover_image_url
    val domain = remember(article.url) { extractDomain(article.url) }
    val createdAt = remember(article.created_at) { TimeUtils.formatRelativeTime(article.created_at) }
    val isFavorite = article.is_favorite == 1L
    val processingMessage = articleProcessingCardMessage(article.status)
    val meta = listOf(domain, createdAt, processingMessage.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    MagazineNewsCard(
        title = title,
        summary = articleIntroText(article, domain),
        meta = meta,
        coverUrl = coverImage,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        trailingActions = {
            ArticleCardActions(
                isFavorite = isFavorite,
                onFavoriteClick = onFavoriteClick,
                onShareClick = onShareClick,
            )
        },
    )
}

private fun articleIntroText(article: Article, domain: String): String? = listOfNotNull(
    article.ai_content?.cleanIntroText(),
    article.ai_markdown_content?.cleanIntroText(),
    domain.takeIf { it.isNotBlank() },
    article.status?.takeIf { it.isNotBlank() },
).firstOrNull { it.isNotBlank() }

private fun String.cleanIntroText(): String = trim()
    .replace(Regex("\\s+"), " ")
    .take(160)

@Composable
private fun ArticleCardActions(
    isFavorite: Boolean,
    onFavoriteClick: (() -> Unit)?,
    onShareClick: (() -> Unit)?,
) {
    if (onFavoriteClick != null) {
        ArticleCardIconButton(onClick = onFavoriteClick) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "收藏",
                modifier = Modifier.size(18.dp),
                tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (onShareClick != null) {
        ArticleCardIconButton(onClick = onShareClick) {
            Icon(
                Icons.Filled.Share,
                contentDescription = "分享",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleCardIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp), content = content)
}

internal fun articleCoverSlotVisible(coverImage: String?): Boolean = true

private fun extractDomain(url: String?): String {
    if (url.isNullOrBlank()) return ""
    return url.removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .removePrefix("www.")
        .ifBlank { "" }
}
