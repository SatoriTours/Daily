package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun DiaryEditorToolbar(
    onTitle: () -> Unit,
    onOrderedList: () -> Unit,
    onUnorderedList: () -> Unit,
    onMedia: () -> Unit,
    onTags: () -> Unit,
    onMood: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMore: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
) {
    Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolbarIcon(Icons.Default.AddPhotoAlternate, "添加图片", onMedia, selected = true)
            ToolbarIcon(Icons.Default.Title, "标题", onTitle)
            ToolbarIcon(Icons.Default.FormatListNumbered, "有序列表", onOrderedList)
            ToolbarIcon(Icons.AutoMirrored.Filled.FormatListBulleted, "无序列表", onUnorderedList)
            ToolbarIcon(Icons.Default.LocalOffer, "标签", onTags)
            ToolbarIcon(Icons.Default.Mood, "心情", onMood)
            ToolbarIcon(Icons.AutoMirrored.Filled.Undo, "撤销", onUndo, canUndo)
            ToolbarIcon(Icons.AutoMirrored.Filled.Redo, "重做", onRedo, canRedo)
            ToolbarIcon(Icons.Default.ExpandLess, "更多格式", onMore, selected = true)
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp), enabled = enabled) {
        Icon(
            icon,
            desc,
            Modifier.size(20.dp),
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
