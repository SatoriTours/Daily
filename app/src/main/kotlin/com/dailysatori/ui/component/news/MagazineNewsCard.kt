package com.dailysatori.ui.component.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.dailysatori.ui.feature.article.articleCardContentVerticalPaddingDp
import com.dailysatori.ui.feature.article.articleCardHeightDp
import com.dailysatori.ui.feature.article.articleCardSummaryMaxLines
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import java.io.File

private const val magazineNewsCoverWidthDp = 120

@Composable
fun MagazineNewsCard(
    title: String,
    summary: String?,
    meta: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.height(articleCardHeightDp.dp)) {
            MagazineNewsCover(
                coverUrl = coverUrl,
                modifier = Modifier.width(magazineNewsCoverWidthDp.dp).fillMaxHeight(),
            )
            MagazineNewsContent(
                title = title,
                summary = summary,
                meta = meta,
                trailingActions = trailingActions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MagazineNewsContent(
    title: String,
    summary: String?,
    meta: String?,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = Spacing.m, vertical = articleCardContentVerticalPaddingDp.dp),
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        MagazineNewsSummary(summary)
        Spacer(modifier = Modifier.weight(1f))
        MagazineNewsMetaRow(meta = meta, trailingActions = trailingActions)
    }
}

@Composable
private fun MagazineNewsSummary(summary: String?) {
    summary?.takeIf { it.isNotBlank() }?.let { intro ->
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
            text = intro,
            style = MaterialTheme.typography.bodySmall,
            maxLines = articleCardSummaryMaxLines,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MagazineNewsMetaRow(meta: String?, trailingActions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = meta.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = trailingActions)
    }
}

@Composable
private fun MagazineNewsCover(coverUrl: String?, modifier: Modifier = Modifier) {
    var loadFailed by remember(coverUrl) { mutableStateOf(false) }
    if (coverUrl.isNullOrBlank() || loadFailed) {
        MagazineNewsDefaultCover(modifier)
        return
    }

    val context = LocalContext.current
    val resolvedPath = resolveMagazineNewsCoverPath(coverUrl, context.filesDir.absolutePath) ?: return
    val request = remember(context, resolvedPath) { ImageRequest.Builder(context).data(resolvedPath).build() }
    AsyncImage(
        model = request,
        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
        error = painterResource(android.R.drawable.ic_menu_report_image),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(topStart = Radius.l, bottomStart = Radius.l)),
        contentScale = ContentScale.Crop,
        onError = { loadFailed = true },
    )
}

internal fun resolveMagazineNewsCoverPath(path: String?, filesDirPath: String): String? {
    val sourcePath = path?.takeIf { it.isNotBlank() } ?: return null
    val isRemote = sourcePath.startsWith("http://") || sourcePath.startsWith("https://")
    if (isRemote || sourcePath.startsWith("/")) return sourcePath

    return File(filesDirPath, "DailySatori/$sourcePath").absolutePath
}

@Composable
private fun MagazineNewsDefaultCover(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = Radius.l, bottomStart = Radius.l))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Article,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
