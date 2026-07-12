package com.dailysatori.ui.feature.diary

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dailysatori.core.recording.DiaryRecordingService
import com.dailysatori.core.recording.DiaryRecordingState
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

class DiaryRecordingController(private val context: Context) {
    fun start(diaryId: Long, attachmentId: Long) {
        val intent = Intent(context, DiaryRecordingService::class.java).apply {
            action = DiaryRecordingService.ACTION_START
            putExtra(DiaryRecordingService.EXTRA_DIARY_ID, diaryId)
            putExtra(DiaryRecordingService.EXTRA_ATTACHMENT_ID, attachmentId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseResume(paused: Boolean) = send(
        if (paused) DiaryRecordingService.ACTION_RESUME else DiaryRecordingService.ACTION_PAUSE,
    )

    fun stop() = send(DiaryRecordingService.ACTION_STOP)

    private fun send(action: String) {
        context.startService(Intent(context, DiaryRecordingService::class.java).setAction(action))
    }
}

@Composable
fun RecordingStatusStrip(state: DiaryRecordingState, onOpenDiary: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(recordingStatusText(state), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("打开", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onOpenDiary) { Icon(Icons.Default.OpenInNew, contentDescription = "打开日记") }
        }
    }
}

private fun recordingStatusText(state: DiaryRecordingState): String = when (state) {
    is DiaryRecordingState.Starting -> "正在准备录音 · 已创建日记"
    is DiaryRecordingState.Recording -> "正在录音 · 已创建日记"
    is DiaryRecordingState.Paused -> "录音已暂停"
    is DiaryRecordingState.Stopping -> "录音已停止 · 正在保存"
    is DiaryRecordingState.PersistenceFailed -> "录音已停止 · 保存失败"
    is DiaryRecordingState.Failed -> "录音失败"
    DiaryRecordingState.Idle -> "录音已结束"
}

internal fun DiaryRecordingState.showsRecordingControls(): Boolean =
    this is DiaryRecordingState.Starting ||
        this is DiaryRecordingState.Recording ||
        this is DiaryRecordingState.Paused ||
        this is DiaryRecordingState.PersistenceFailed

@Composable
fun DiaryRecordingControls(
    state: DiaryRecordingState,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onOpenDiary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paused = state is DiaryRecordingState.Paused
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xl),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatRecordingElapsed(state.elapsedMs),
                modifier = Modifier.width(56.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
            )
            if (state is DiaryRecordingState.Recording || paused) {
                IconButton(onClick = onPauseResume) {
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (paused) "继续" else "暂停")
                }
            }
            IconButton(onClick = onStop) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = if (state is DiaryRecordingState.PersistenceFailed) "放弃保存" else "停止录音",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onOpenDiary) { Icon(Icons.Default.OpenInNew, contentDescription = "打开日记") }
        }
    }
}

private fun formatRecordingElapsed(elapsedMs: Long): String {
    val seconds = elapsedMs / 1_000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
