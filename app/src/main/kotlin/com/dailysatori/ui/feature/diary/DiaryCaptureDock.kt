package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.dailysatori.R
import com.dailysatori.ui.theme.*

/** The 36dp faces are decorative; the full 48dp targets stay inside the screen. */
@Composable
internal fun DiaryCaptureDock(
    expanded: Boolean,
    onExpand: () -> Unit,
    onAdd: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandLabel = stringResource(R.string.diary_dock_expand)
    if (expanded) Row(modifier.padding(end = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DiaryDockButton(Icons.Default.Add, stringResource(R.string.diary_feed_write_now), false, onAdd)
        DiaryDockButton(Icons.Default.MicNone, stringResource(R.string.diary_feed_record_voice), true, onRecord)
    } else Box(
        modifier.size(Height.touchTarget).clickable(
            onClickLabel = stringResource(R.string.diary_dock_expand),
            role = androidx.compose.ui.semantics.Role.Button,
            onClick = onExpand,
        ).semantics { contentDescription = expandLabel },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            Modifier.width(FloatingControl.edgeReveal).height(FloatingControl.visual)
                .clip(RoundedCornerShape(topStart = Radius.s, bottomStart = Radius.s))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(FloatingControl.gripWidth, FloatingControl.gripHeight)
                .clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

@Composable
private fun DiaryDockButton(icon: ImageVector, label: String, accent: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(Height.touchTarget).clip(CircleShape).clickable(
        role = androidx.compose.ui.semantics.Role.Button, onClick = onClick,
    ), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.size(FloatingControl.visual), shape = CircleShape,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = Spacing.xxs,
        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(IconSize.m)) } }
    }
}
