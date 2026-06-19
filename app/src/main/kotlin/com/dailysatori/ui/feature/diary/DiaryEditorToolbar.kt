package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.Radius

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
    val background = MaterialTheme.colorScheme.surfaceContainer
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    Surface(shape = RoundedCornerShape(Radius.circular), color = background) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(Icons.Default.AddPhotoAlternate, "添加图片", onMedia, primary, muted, selectedBackground, selected = true)
            ToolbarIcon(Icons.Default.Title, "标题", onTitle, primary, muted, selectedBackground)
            ToolbarIcon(Icons.Default.FormatListNumbered, "有序列表", onOrderedList, primary, muted, selectedBackground)
            ToolbarIcon(Icons.AutoMirrored.Filled.FormatListBulleted, "无序列表", onUnorderedList, primary, muted, selectedBackground)
            ToolbarIcon(Icons.Default.LocalOffer, "标签", onTags, primary, muted, selectedBackground)
            ToolbarIcon(Icons.Default.Mood, "心情", onMood, primary, muted, selectedBackground)
            ToolbarIcon(Icons.AutoMirrored.Filled.Undo, "撤销", onUndo, primary, muted, selectedBackground, canUndo)
            ToolbarIcon(Icons.AutoMirrored.Filled.Redo, "重做", onRedo, primary, muted, selectedBackground, canRedo)
            ToolbarIcon(Icons.Default.ExpandLess, "更多格式", onMore, primary, muted, selectedBackground, selected = true)
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    primary: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
    selectedBackground: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp), enabled = enabled) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Radius.circular))
                .background(
                    if (selected) {
                        selectedBackground
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                desc,
                Modifier.size(20.dp),
                tint = when {
                    !enabled -> muted.copy(alpha = 0.36f)
                    selected -> primary
                    else -> muted
                },
            )
        }
    }
}
