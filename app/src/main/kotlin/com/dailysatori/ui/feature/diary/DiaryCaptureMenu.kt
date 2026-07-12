package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun DiaryCaptureMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onVoice: () -> Unit,
    onText: () -> Unit,
    onCapture: () -> Unit,
    onFile: () -> Unit,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
    ) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismissRequest,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            CaptureMenuItem("语音日记", Icons.Default.Mic, true) { onDismissRequest(); onVoice() }
            CaptureMenuItem("文字日记", Icons.Default.Edit) { onDismissRequest(); onText() }
            CaptureMenuItem("拍摄", Icons.Default.Videocam) { onDismissRequest(); onCapture() }
            CaptureMenuItem("添加文件", Icons.Default.AttachFile) { onDismissRequest(); onFile() }
        }
    }
}

@Composable
private fun CaptureMenuItem(
    label: String,
    icon: ImageVector,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        modifier = Modifier.heightIn(min = 44.dp),
        text = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}
