package com.dailysatori.ui.feature.diary

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.data.repository.DiaryAttachmentProcessingStatus
import com.dailysatori.shared.db.Diary_attachment
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun DiaryAttachmentList(
    attachments: List<Diary_attachment>,
    modifier: Modifier = Modifier,
    onDelete: ((Diary_attachment) -> Unit)? = null,
) {
    val displayableAttachments = attachments.filterNot {
        it.kind == "audio" &&
            it.local_path.isBlank() &&
            it.transcript_status != DiaryAttachmentProcessingStatus.failed
    }
    if (displayableAttachments.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) displayableAttachments else displayableAttachments.take(2)
    Column(modifier = modifier.fillMaxWidth()) {
        visible.forEach { attachment -> DiaryAttachmentRow(attachment, onDelete) }
        if (displayableAttachments.size > 2) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起附件" else "查看全部 ${displayableAttachments.size} 个附件")
            }
        }
    }
}

@Composable
private fun DiaryAttachmentRow(attachment: Diary_attachment, onDelete: ((Diary_attachment) -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            onDelete?.let { delete ->
                IconButton(onClick = { delete(attachment) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = if (attachment.kind == "audio") "删除录音" else "删除附件",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (attachment.kind == "audio" && attachment.local_path.isNotBlank()) {
            DiaryAudioPlaybackButton(attachment.local_path, attachment.duration_ms)
        }
    }
}

@Composable
private fun DiaryAudioPlaybackButton(path: String, recordedDurationMs: Long) {
    val context = LocalContext.current
    var isPrepared by remember(path) { mutableStateOf(false) }
    var isPreparing by remember(path) { mutableStateOf(false) }
    var isPlaying by remember(path) { mutableStateOf(false) }
    var isDragging by remember(path) { mutableStateOf(false) }
    var positionMs by remember(path) { mutableStateOf(0) }
    var durationMs by remember(path) { mutableStateOf(recordedDurationMs.coerceAtLeast(0).toInt()) }
    val playbackAudioAttributes = remember {
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
    }
    val player = remember(path) {
        MediaPlayer().apply {
            setAudioAttributes(playbackAudioAttributes)
        }
    }
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val focusRequest = remember(player) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(playbackAudioAttributes)
            .setOnAudioFocusChangeListener { change ->
                if (change < 0 && isPlaying) {
                    runCatching { player.pause() }
                    isPlaying = false
                }
            }
            .build()
    }
    fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
    fun startWithAudioFocus(target: MediaPlayer) {
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            target.start()
            isPlaying = true
        }
    }
    DisposableEffect(player) {
        player.setOnPreparedListener {
            isPrepared = true
            isPreparing = false
            durationMs = it.duration.coerceAtLeast(0)
            startWithAudioFocus(it)
        }
        player.setOnSeekCompleteListener { positionMs = it.currentPosition }
        player.setOnCompletionListener {
            positionMs = durationMs
            isPlaying = false
            abandonAudioFocus()
        }
        player.setOnErrorListener { _, _, _ ->
            abandonAudioFocus()
            isPrepared = false
            isPreparing = false
            isPlaying = false
            runCatching { player.reset() }
            true
        }
        onDispose {
            abandonAudioFocus()
            player.release()
        }
    }
    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            if (!isDragging) positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs)
            delay(250)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            enabled = !isPreparing,
            onClick = {
                when {
                    isPlaying -> {
                        player.pause()
                        isPlaying = false
                        abandonAudioFocus()
                    }
                    isPrepared -> {
                        if (durationMs > 0 && positionMs >= durationMs) player.seekTo(0)
                        startWithAudioFocus(player)
                    }
                    else -> runCatching {
                        isPreparing = true
                        player.reset()
                        player.setAudioAttributes(playbackAudioAttributes)
                        player.setDataSource(path)
                        player.prepareAsync()
                    }.onFailure { isPreparing = false }
                }
            },
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停录音" else "播放录音",
            )
        }
        Text(formatPlaybackTime(positionMs), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = positionMs.coerceIn(0, durationMs.coerceAtLeast(0)).toFloat(),
            onValueChange = {
                isDragging = true
                positionMs = it.toInt()
            },
            onValueChangeFinished = {
                if (isPrepared) player.seekTo(positionMs)
                isDragging = false
            },
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
            enabled = isPrepared && durationMs > 0,
            modifier = Modifier.weight(1f),
        )
        Text(formatPlaybackTime(durationMs), style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatPlaybackTime(milliseconds: Int): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun attachmentStatus(attachment: Diary_attachment): String = when {
    attachment.error_message.startsWith("recording_") -> "录音失败"
    attachment.transcript_status == DiaryAttachmentProcessingStatus.processing -> "正在转写"
    attachment.transcript_status == DiaryAttachmentProcessingStatus.failed -> "转写失败"
    attachment.knowledge_status == DiaryAttachmentProcessingStatus.completed -> "已加入知识库"
    attachment.transcript_status == DiaryAttachmentProcessingStatus.completed -> "已转写"
    attachment.transcript_status == DiaryAttachmentProcessingStatus.queued -> "等待转写"
    else -> "附件"
}
