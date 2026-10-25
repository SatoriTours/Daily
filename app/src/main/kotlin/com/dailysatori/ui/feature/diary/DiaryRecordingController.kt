package com.dailysatori.ui.feature.diary

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dailysatori.core.recording.DiaryRecordingService
import com.dailysatori.core.recording.DiaryRecordingState
import com.dailysatori.ui.theme.*

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

private fun recordingStatusText(state: DiaryRecordingState): String = when (state) {
    is DiaryRecordingState.Starting -> "正在准备录音 · 已创建日记"
    is DiaryRecordingState.Recording -> if (state.elapsedMs >= 60 * 60 * 1_000L) {
        "已连续录音超过 1 小时，请确认是否继续"
    } else {
        "正在录音 · 已创建日记"
    }
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
    onOpenDiary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val paused = state is DiaryRecordingState.Paused
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.m),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val openModifier = if (onOpenDiary != null) Modifier.clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = stringResource(R.string.diary_recording_action_open), onClick = onOpenDiary,
            ) else Modifier
            Column(Modifier.weight(1f).heightIn(min = Height.touchTarget).then(openModifier),
                verticalArrangement = Arrangement.Center) {
                Text(recordingStatusText(state), style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(text = formatRecordingElapsed(state.elapsedMs), modifier = Modifier.width(48.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
            if (state is DiaryRecordingState.Recording || paused) {
                IconButton(onClick = onPauseResume) {
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (paused) "继续" else "暂停")
                }
            }
            if (state.showsRecordingControls()) {
                androidx.compose.material3.TextButton(onClick = onStop, modifier = Modifier.heightIn(min = Height.touchTarget)) {
                    Text(
                        if (state is DiaryRecordingState.PersistenceFailed) "放弃保存"
                        else stringResource(R.string.diary_recording_action_finish),
                        color = if (state is DiaryRecordingState.PersistenceFailed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun formatRecordingElapsed(elapsedMs: Long): String {
    val seconds = elapsedMs / 1_000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
