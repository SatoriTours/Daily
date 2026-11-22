package com.dailysatori.ui.component.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.dailysatori.core.diagnostics.performanceImageModel
import coil3.request.ImageRequest
import com.dailysatori.R
import com.dailysatori.ui.feature.article.articleCardSummaryMaxLines
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import java.io.File

private val magazineNewsCoverSize = 88.dp

/**
 * Shared news list row: a full-width title, body text underneath, and an optional small cover
 * beside the body. Without a usable cover the text spans the available width.
 */
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
    MagazineNewsCardCore(title, summary, meta, coverUrl, onClick, modifier, trailingActions, null, null)
}

@Composable
internal fun MagazineNewsCardCore(
    title: String,
    summary: String?,
    meta: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit = {},
    coverModel: Any?,
    imageLoader: ImageLoader?,
    onCoverState: ((Boolean) -> Unit)? = null,
    coverRenderer: (@Composable (onSuccess: () -> Unit, onError: () -> Unit) -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m)) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.Top,
            ) {
                MagazineNewsBody(
                    summary = summary,
                    meta = meta,
                    trailingActions = trailingActions,
                    modifier = Modifier.weight(1f),
                )
                MagazineNewsCover(
                    coverUrl = coverUrl,
                    coverModel = coverModel,
                    imageLoader = imageLoader,
                    onCoverState = onCoverState,
                    coverRenderer = coverRenderer,
                    modifier = Modifier.size(magazineNewsCoverSize),
                )
            }
        }
    }
}

@Composable
private fun MagazineNewsBody(
    summary: String?,
    meta: String?,
    trailingActions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        summary?.takeIf { it.isNotBlank() }?.let { intro ->
            Text(
                text = intro,
                style = MaterialTheme.typography.bodySmall,
                maxLines = articleCardSummaryMaxLines,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MagazineNewsMetaRow(
            meta = meta,
            trailingActions = trailingActions,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

@Composable
private fun MagazineNewsMetaRow(
    meta: String?,
    trailingActions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
private fun MagazineNewsCover(
    coverUrl: String?,
    coverModel: Any?,
    imageLoader: ImageLoader?,
    onCoverState: ((Boolean) -> Unit)?,
    coverRenderer: (@Composable (onSuccess: () -> Unit, onError: () -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolvedPath = remember(coverUrl, context) {
        resolveMagazineNewsCoverPath(coverUrl, context.filesDir.absolutePath)
    }
    var loadFailed by remember(resolvedPath, coverModel) { mutableStateOf(false) }
    var loaded by remember(resolvedPath, coverModel) { mutableStateOf(false) }
    if (coverModel == null && (resolvedPath == null || loadFailed)) return
    val localFileMissing = coverModel == null && resolvedPath != null &&
        !resolvedPath.startsWith("http") && !resolvedPath.startsWith("data:") && !File(resolvedPath).isFile
    if (localFileMissing) {
        onCoverState?.invoke(false)
        return
    }

    val request = remember(context, resolvedPath, coverModel) {
        val data: Any? = coverModel ?: resolvedPath?.let { path ->
            if (path.startsWith("http://") || path.startsWith("https://")) path else File(path)
        }
        data?.let { ImageRequest.Builder(context).data(it).build() }
    } ?: return
    val description = if (loaded) stringResource(R.string.news_cover_image) else null
    val coverModifier = modifier.testTag("magazine-news-cover").clip(RoundedCornerShape(Radius.m))
    if (coverRenderer != null) {
        androidx.compose.foundation.layout.Box(coverModifier) {
            coverRenderer(
                {
                    loaded = true
                    onCoverState?.invoke(true)
                },
                {
                    loadFailed = true
                    onCoverState?.invoke(false)
                },
            )
        }
    } else if (imageLoader != null) {
        AsyncImage(
            model = performanceImageModel(request),
            imageLoader = imageLoader,
            contentDescription = description,
            modifier = coverModifier,
            contentScale = ContentScale.Crop,
            onSuccess = {
                loaded = true
                onCoverState?.invoke(true)
            },
            onError = {
                loadFailed = true
                onCoverState?.invoke(false)
            },
        )
    } else {
        AsyncImage(
            model = performanceImageModel(request),
            contentDescription = description,
            modifier = coverModifier,
            contentScale = ContentScale.Crop,
            onSuccess = {
                loaded = true
                onCoverState?.invoke(true)
            },
            onError = {
                loadFailed = true
                onCoverState?.invoke(false)
            },
        )
    }
}

internal fun resolveMagazineNewsCoverPath(path: String?, filesDirPath: String): String? {
    val sourcePath = path?.takeIf { it.isNotBlank() } ?: return null
    val isRemote = sourcePath.startsWith("http://") || sourcePath.startsWith("https://")
    val isInline = sourcePath.startsWith("data:")
    if (isRemote || isInline || sourcePath.startsWith("/")) return sourcePath

    return File(filesDirPath, "DailySatori/$sourcePath").absolutePath
}
