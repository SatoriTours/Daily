package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.data.repository.DiaryAttachmentProcessingStatus
import com.dailysatori.shared.db.Diary_attachment
import com.dailysatori.ui.theme.Spacing

@Composable
fun DiaryAttachmentList(attachments: List<Diary_attachment>, modifier: Modifier = Modifier) {
    if (attachments.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) attachments else attachments.take(2)
    Column(modifier = modifier.fillMaxWidth()) {
        visible.forEach { attachment -> DiaryAttachmentRow(attachment) }
        if (attachments.size > 2) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起附件" else "查看全部 ${attachments.size} 个附件")
            }
        }
    }
}

@Composable
private fun DiaryAttachmentRow(attachment: Diary_attachment) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (attachment.kind) {
            "audio" -> Icons.Default.Audiotrack
            "video" -> Icons.Default.Videocam
            "image" -> Icons.Default.Image
            else -> Icons.Default.AttachFile
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                attachment.display_name.ifBlank { attachment.local_path.substringAfterLast('/') },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                attachmentStatus(attachment),
                style = MaterialTheme.typography.labelSmall,
                color = if (
                    attachment.transcript_status == DiaryAttachmentProcessingStatus.failed ||
                    attachment.knowledge_status == DiaryAttachmentProcessingStatus.failed
                ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun attachmentStatus(attachment: Diary_attachment): String = when {
    attachment.transcript_status == DiaryAttachmentProcessingStatus.processing -> "正在转写"
    attachment.transcript_status == DiaryAttachmentProcessingStatus.failed -> "转写失败"
    attachment.knowledge_status == DiaryAttachmentProcessingStatus.completed -> "已加入知识库"
    attachment.transcript_status == DiaryAttachmentProcessingStatus.completed -> "已转写"
    attachment.transcript_status == DiaryAttachmentProcessingStatus.queued -> "等待转写"
    else -> "附件"
}
