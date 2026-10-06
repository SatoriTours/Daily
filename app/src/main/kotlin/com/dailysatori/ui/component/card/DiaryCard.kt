package com.dailysatori.ui.component.card

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dailysatori.R
import com.dailysatori.core.util.diaryCardDateTime
import com.dailysatori.core.util.diaryCollapsedPreview
import com.dailysatori.core.util.diaryImagePaths
import com.dailysatori.core.util.diaryTags
import com.dailysatori.core.util.stripDiaryInlineTags
import com.dailysatori.shared.db.Diary
import com.dailysatori.shared.db.Diary_attachment
import com.dailysatori.ui.feature.diary.DiaryAttachmentList
import com.dailysatori.ui.theme.*
import com.mikepenz.markdown.m3.Markdown
import java.io.File

@Composable
fun DiaryCard(
    diary: Diary,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showDelete: Boolean = true,
    attachments: List<Diary_attachment> = emptyList(),
    onRetryTranscription: ((Long) -> Unit)? = null,
    nowMillis: Long = System.currentTimeMillis(),
    initiallyExpanded: Boolean = false,
    onTagClick: (String) -> Unit = { onEdit() },
) {
    val context = LocalContext.current
    val tags = diaryTags(diary.tags)
    val imagePaths = diaryImagePaths(diary.images)
    val contentText = stripDiaryInlineTags(diary.content)
    var hasOverflow by remember(contentText) { mutableStateOf(false) }
    var expanded by rememberSaveable(diary.id, contentText) { mutableStateOf(initiallyExpanded) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth().animateContentSize()
            .clickable(enabled = hasOverflow || expanded) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        DiaryCardHeader(
            diary = diary,
            contentText = contentText,
            showDelete = showDelete,
            menuExpanded = menuExpanded,
            onMenuChange = { menuExpanded = it },
            onEdit = onEdit,
            onDelete = onDelete,
            nowMillis = nowMillis,
        )
        DiaryBody(contentText, expanded, onOverflow = { hasOverflow = it }, modifier = Modifier.fillMaxWidth(),
            hasPhotos = imagePaths.isNotEmpty())
        if (imagePaths.isNotEmpty()) DiaryPhotoWall(imagePaths, context.filesDir, expanded)
        DiaryAttachmentList(
            attachments = attachments,
            onRetryTranscription = onRetryTranscription,
            compact = !expanded,
        )
        DiaryCardFooter(tags = tags, isLongContent = hasOverflow || expanded, expanded = expanded,
            onTagClick = onTagClick) { expanded = !expanded }
    }
}

@Composable
private fun DiaryCardHeader(
    diary: Diary,
    contentText: String,
    showDelete: Boolean,
    menuExpanded: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    nowMillis: Long,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            diaryCardDateTime(diary.created_at, nowMillis, stringResource(R.string.diary_feed_today),
                stringResource(R.string.diary_feed_yesterday), LocalConfiguration.current.locales[0],
                includeDateForRelativeDays = true),
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        diary.mood?.takeIf { it.isNotBlank() && it != "null" }?.let { mood ->
            Text(mood, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = Spacing.xxl + Spacing.l))
        }
        if (showDelete) {
            Box {
                IconButton(onClick = { onMenuChange(true) }, modifier = Modifier.size(Height.buttonSmall)) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.xs),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuChange(false) }) {
                    DropdownMenuItem(
                        text = { Text(diaryCopyMenuLabel()) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            onMenuChange(false)
                            copyDiaryContent(context, contentText)
                        },
                    )
                    DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { onMenuChange(false); onEdit() })
                    DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onMenuChange(false); onDelete() })
                }
            }
        }
    }
}

internal fun diaryCopyMenuLabel(): String = "拷贝全文"

internal fun diaryCopyClipLabel(): String = "日记全文"

internal fun diaryCopySuccessMessage(): String = "已拷贝全文"

private fun copyDiaryContent(context: Context, contentText: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(diaryCopyClipLabel(), contentText))
    Toast.makeText(context, diaryCopySuccessMessage(), Toast.LENGTH_SHORT).show()
}

@Composable
private fun DiaryBody(contentText: String, expanded: Boolean, onOverflow: (Boolean) -> Unit, modifier: Modifier,
    hasPhotos: Boolean) {
    if (contentText.isBlank()) return
    Box(modifier = modifier) {
        if (expanded) {
            Markdown(content = contentText, typography = MarkdownStyles.cardTypography(), padding = MarkdownStyles.cardPadding())
        } else {
            DiaryCollapsedBody(contentText, hasPhotos, onOverflow)
        }
    }
}

@Composable
private fun DiaryCollapsedBody(contentText: String, hasPhotos: Boolean, onOverflow: (Boolean) -> Unit) {
    val preview = remember(contentText) { diaryCollapsedPreview(contentText) }
    var titleOverflow by remember(contentText) { mutableStateOf(false) }
    var bodyOverflow by remember(contentText) { mutableStateOf(false) }
    val isQuote = preview.title == null && contentText.trimStart().startsWith("> ")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        preview.title?.let { title ->
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                onTextLayout = {
                    titleOverflow = it.hasVisualOverflow
                    onOverflow(titleOverflow || bodyOverflow)
                })
        }
        if (preview.body.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (isQuote) Icon(Icons.Default.FormatQuote, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.xl))
                Text(preview.body,
                    style = if (hasPhotos || isQuote) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    onTextLayout = {
                        bodyOverflow = it.hasVisualOverflow
                        onOverflow(titleOverflow || bodyOverflow)
                    })
            }
        }
    }
}

@Composable
private fun DiaryCardFooter(tags: List<String>, isLongContent: Boolean, expanded: Boolean,
    onTagClick: (String) -> Unit, onExpand: () -> Unit) {
    if (tags.isEmpty() && !isLongContent) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (tags.isNotEmpty()) {
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(tags, key = { it }) { tag -> DiaryTagChip(tag) { onTagClick(tag) } }
            }
        } else {
            Box(modifier = Modifier.weight(1f))
        }
        if (isLongContent) Text(
            text = stringResource(if (expanded) R.string.diary_feed_collapse else R.string.diary_feed_expand),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.circular))
                .clickable(onClick = onExpand)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

@Composable
private fun DiaryPhotoWall(imagePaths: List<String>, filesDir: File, expanded: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = maxWidth
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (expanded || imagePaths.size == 1) {
                itemsIndexed(imagePaths) { index, path ->
                    DiaryPhoto(path, filesDir, Modifier.width(pageWidth).aspectRatio(if (expanded) 3f / 2f else 2f),
                        photoCount = imagePaths.size.takeIf { index == 0 && it > 2 })
                }
            } else {
                itemsIndexed(imagePaths.chunked(2)) { index, pair ->
                    Row(Modifier.width(pageWidth), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        DiaryPhoto(pair.first(), filesDir, Modifier.weight(2f).aspectRatio(7f / 4f),
                            photoCount = imagePaths.size.takeIf { index == 0 && it > 2 })
                        Box(Modifier.weight(1f).padding(top = Spacing.m)) {
                            pair.getOrNull(1)?.let { path ->
                                DiaryPhoto(path, filesDir, Modifier.fillMaxWidth().aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryPhoto(path: String, filesDir: File, modifier: Modifier, photoCount: Int? = null) {
    val file = File(filesDir, "DailySatori/$path")
    if (!file.exists()) return
    Box(modifier = modifier.clip(RoundedCornerShape(Radius.s))) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(file).crossfade(true).build(),
            contentDescription = "日记图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        photoCount?.takeIf { it > 1 }?.let { count ->
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.s),
                shape = RoundedCornerShape(Radius.circular),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f),
            ) {
                Text(stringResource(R.string.diary_feed_photo_count, count), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs))
            }
        }
    }
}

@Composable
private fun DiaryTagChip(tag: String, onClick: () -> Unit) {
    Text(text = "#${tag.removePrefix("#")}", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = Spacing.xxl * 3).clip(RoundedCornerShape(Radius.xs))
            .clickable(onClick = onClick).padding(vertical = Spacing.xs))
}
