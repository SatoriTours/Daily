package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.Spacing

@Composable
fun DiaryEditorToolbar(
    onTitle: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onQuote: () -> Unit,
    onOrderedList: () -> Unit,
    onUnorderedList: () -> Unit,
    onTaskList: () -> Unit,
    onInlineCode: () -> Unit,
    onDivider: () -> Unit,
    onLink: () -> Unit,
    onImage: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMedia: () -> Unit,
    onSave: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    canSave: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            ToolbarIcon(Icons.Default.Title, "标题", onTitle)
            ToolbarIcon(Icons.Default.FormatBold, "加粗", onBold)
            ToolbarIcon(Icons.Default.FormatItalic, "斜体", onItalic)
            ToolbarIcon(Icons.Default.FormatQuote, "引用", onQuote)
            ToolbarIcon(Icons.Default.FormatListNumbered, "有序列表", onOrderedList)
            ToolbarIcon(Icons.AutoMirrored.Filled.FormatListBulleted, "无序列表", onUnorderedList)
            ToolbarIcon(Icons.Default.TaskAlt, "任务项", onTaskList)
            ToolbarIcon(Icons.Default.Code, "行内代码", onInlineCode)
            ToolbarIcon(Icons.Default.HorizontalRule, "分割线", onDivider)
            ToolbarIcon(Icons.Default.Link, "链接", onLink)
            ToolbarIcon(Icons.Default.Image, "图片语法", onImage)
            ToolbarIcon(Icons.AutoMirrored.Filled.Undo, "撤销", onUndo, canUndo)
            ToolbarIcon(Icons.AutoMirrored.Filled.Redo, "重做", onRedo, canRedo)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            ToolbarIcon(Icons.Default.AddPhotoAlternate, "添加媒体", onMedia)
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(36.dp),
                enabled = canSave,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "保存",
                    modifier = Modifier.size(24.dp),
                    tint = if (canSave) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp), enabled = enabled) {
        Icon(icon, desc, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
