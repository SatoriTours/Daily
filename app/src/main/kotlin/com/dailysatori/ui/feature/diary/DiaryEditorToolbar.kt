package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
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
    onAssistant: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMore: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    canUseAssistant: Boolean,
) {
    val primary = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val pillSurface = MaterialTheme.colorScheme.surfaceContainer
    val selectedBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BorderWidth.xs)
                .background(MaterialTheme.colorScheme.outline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Height.touchTarget)
                .padding(top = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarIcon(Icons.Outlined.AddPhotoAlternate, "添加图片", onMedia, primary, ink, selectedBackground)
                ToolbarIcon(
                    Icons.Outlined.AutoAwesome,
                    "AI 补充",
                    onAssistant,
                    primary,
                    muted,
                    selectedBackground,
                    enabled = canUseAssistant,
                    selected = canUseAssistant,
                )
                ToolbarIcon(Icons.Outlined.Title, "标题", onTitle, primary, ink, selectedBackground)
                ToolbarIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, "无序列表", onUnorderedList, primary, ink, selectedBackground)
                ToolbarIcon(Icons.Outlined.FormatListNumbered, "有序列表", onOrderedList, primary, ink, selectedBackground)
                ToolbarIcon(Icons.Outlined.LocalOffer, "标签", onTags, primary, muted, selectedBackground)
                ToolbarIcon(Icons.Outlined.Mood, "心情", onMood, primary, muted, selectedBackground)
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = Spacing.xs)
                    .width(BorderWidth.xs)
                    .height(IconSize.s)
                    .background(MaterialTheme.colorScheme.outline),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarIcon(Icons.AutoMirrored.Outlined.Undo, "撤销", onUndo, primary, muted, selectedBackground, enabled = canUndo)
                ToolbarIcon(Icons.AutoMirrored.Outlined.Redo, "重做", onRedo, primary, muted, selectedBackground, enabled = canRedo)
                ToolbarIcon(
                    Icons.Outlined.MoreHoriz,
                    "更多格式",
                    onMore,
                    primary,
                    ink,
                    selectedBackground,
                    containerColor = pillSurface,
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
    primary: Color,
    normalTint: Color,
    selectedBackground: Color,
    enabled: Boolean = true,
    selected: Boolean = false,
    containerColor: Color = Color.Transparent,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(Height.buttonSmall),
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier
                .size(Height.buttonSmall - Spacing.xxs)
                .clip(RoundedCornerShape(Radius.s))
                .background(if (selected) selectedBackground else containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                desc,
                Modifier.size(IconSize.s),
                tint = when {
                    !enabled -> normalTint.copy(alpha = 0.30f)
                    selected -> primary
                    else -> normalTint
                },
            )
        }
    }
}
