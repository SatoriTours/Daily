package com.dailysatori.ui.component.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.core.util.diaryImagePaths
import com.dailysatori.core.util.diaryTags
import com.dailysatori.core.util.stripDiaryInlineTags
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import java.io.File

private const val CONTENT_LONG_THRESHOLD = 300

@Composable
fun DiaryCard(
    diary: Diary,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showDelete: Boolean = true,
) {
    val context = LocalContext.current
    val tags = diaryTags(diary.tags)
    val imagePaths = diaryImagePaths(diary.images)
    val contentText = stripDiaryInlineTags(diary.content)
    val isLongContent = contentText.length > CONTENT_LONG_THRESHOLD
    var expanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DiaryCardHeader(diary, showDelete, menuExpanded, { menuExpanded = it }, onEdit, onDelete)
            if (imagePaths.isNotEmpty()) DiaryPhotoWall(imagePaths, context.filesDir)
            DiaryBody(contentText = contentText, expanded = expanded, isLongContent = isLongContent)
            DiaryCardFooter(tags = tags, isLongContent = isLongContent, expanded = expanded) { expanded = !expanded }
        }
    }
}

@Composable
private fun DiaryCardHeader(
    diary: Diary,
    showDelete: Boolean,
    menuExpanded: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(Radius.circular)).background(MaterialTheme.colorScheme.primary))
        Text(TimeUtils.formatShortDateTime(diary.created_at).takeLast(5), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        diary.mood?.takeIf { it.isNotBlank() && it != "null" }?.let { mood ->
            Text(mood, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        } ?: run {
            Box(modifier = Modifier.weight(1f))
        }
        if (showDelete) {
            Box {
                IconButton(onClick = { onMenuChange(true) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuChange(false) }) {
                    DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { onMenuChange(false); onEdit() })
                    DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onMenuChange(false); onDelete() })
                }
            }
        }
    }
}

@Composable
private fun DiaryBody(contentText: String, expanded: Boolean, isLongContent: Boolean) {
    if (contentText.isBlank()) return
    val bodyModifier = Modifier.fillMaxWidth().then(if (!expanded && isLongContent) Modifier.heightIn(max = 190.dp) else Modifier)
    Box(modifier = bodyModifier) {
        Markdown(content = contentText, typography = MarkdownStyles.cardTypography(), padding = MarkdownStyles.cardPadding())
    }
}

@Composable
private fun DiaryCardFooter(tags: List<String>, isLongContent: Boolean, expanded: Boolean, onExpand: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (tags.isNotEmpty()) {
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(tags, key = { it }) { tag -> DiaryTagChip(tag) }
            }
        } else {
            Box(modifier = Modifier.weight(1f))
        }
        Text(
            text = if (expanded && isLongContent) "收起正文" else "展开正文",
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
private fun DiaryPhotoWall(imagePaths: List<String>, filesDir: File) {
    val visible = imagePaths.take(3)
    val hiddenCount = imagePaths.size - visible.size
    when (visible.size) {
        1 -> DiaryPhoto(visible[0], filesDir, Modifier.fillMaxWidth().height(142.dp), hiddenCount, imagePaths.size)
        2 -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
            visible.forEachIndexed { index, path -> DiaryPhoto(path, filesDir, Modifier.weight(1f).height(106.dp), photoCount = imagePaths.size.takeIf { index == 0 }) }
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
            DiaryPhoto(visible[0], filesDir, Modifier.weight(1.32f).height(142.dp), photoCount = imagePaths.size)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.weight(1f)) {
                DiaryPhoto(visible[1], filesDir, Modifier.fillMaxWidth().height(67.dp))
                DiaryPhoto(visible[2], filesDir, Modifier.fillMaxWidth().height(67.dp), hiddenCount)
            }
        }
    }
}

@Composable
private fun DiaryPhoto(path: String, filesDir: File, modifier: Modifier, hiddenCount: Int = 0, photoCount: Int? = null) {
    val file = File(filesDir, "DailySatori/$path")
    if (!file.exists()) return
    Box(modifier = modifier.clip(RoundedCornerShape(Radius.l))) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(file).crossfade(true).build(),
            contentDescription = "日记图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (hiddenCount > 0) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.s),
                shape = RoundedCornerShape(Radius.circular),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.46f),
            ) {
                Text("+$hiddenCount", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = Spacing.xs))
            }
        }
        photoCount?.takeIf { it > 1 }?.let { count ->
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.s),
                shape = RoundedCornerShape(Radius.circular),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f),
            ) {
                Text("$count 张照片", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs))
            }
        }
    }
}

@Composable
private fun DiaryTagChip(tag: String) {
    Surface(
        shape = RoundedCornerShape(Radius.circular),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    ) {
        Row(
            modifier = Modifier.widthIn(max = 112.dp).padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.Sell, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), modifier = Modifier.size(11.dp))
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
