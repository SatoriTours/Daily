package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.dailysatori.R
import com.dailysatori.service.reminder.ReminderAiBatch
import com.dailysatori.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock

@Composable
internal fun ReminderAiProcessingContent(batch: ReminderAiBatch, progress: ReminderAiProgressUi) {
    val now by produceState(Clock.System.now().toEpochMilliseconds(), batch.id) {
        while (isActive) {
            value = Clock.System.now().toEpochMilliseconds()
            delay(1_000)
        }
    }
    val elapsed = ((now - batch.createdAt.toEpochMilliseconds()) / 1_000).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Column(
            Modifier.fillMaxWidth().padding(top = Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(IconSize.xxl), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.reminder_ai_progress_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.reminder_ai_progress_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.reminder_ai_progress_elapsed, elapsed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.l)) {
                ReminderAiStage.entries.forEach { stage -> ReminderAiProgressStep(stage, progress, now) }
            }
        }
        if (progress.isRetrying) {
            Text(stringResource(R.string.reminder_ai_progress_retry_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (elapsed >= 45 && progress.stage == ReminderAiStage.ANALYZING) {
            Text(stringResource(R.string.reminder_ai_progress_long_wait), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.reminder_ai_progress_original), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(batch.originalInput, Modifier.fillMaxWidth().padding(Spacing.m), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, Modifier.size(IconSize.m), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.reminder_ai_progress_background), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReminderAiProgressStep(stage: ReminderAiStage, progress: ReminderAiProgressUi, now: Long) {
    val complete = stage.ordinal < progress.stage.ordinal
    val active = stage == progress.stage
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                complete -> Icon(Icons.Outlined.CheckCircle, null, Modifier.size(IconSize.l), tint = accent)
                active -> CircularProgressIndicator(Modifier.size(IconSize.l), color = accent, strokeWidth = BorderWidth.m)
                else -> Icon(Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(IconSize.l), tint = muted)
            }
            if (stage != ReminderAiStage.GENERATING) {
                Spacer(Modifier.weight(1f).padding(top = Spacing.xs).width(BorderWidth.s).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Column(Modifier.weight(1f).padding(bottom = if (stage == ReminderAiStage.GENERATING) Spacing.xs else Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(stage.labelResource()), style = MaterialTheme.typography.titleSmall, color = if (complete || active) MaterialTheme.colorScheme.onSurface else muted)
            val retrySeconds = progress.retrySecondsRemaining(now)
            val status = when {
                complete -> stringResource(R.string.reminder_ai_progress_done)
                !active -> stringResource(R.string.reminder_ai_progress_pending)
                progress.isRetrying && retrySeconds != null && retrySeconds > 0 -> stringResource(R.string.reminder_ai_progress_retry_countdown, retrySeconds)
                progress.isRetrying -> stringResource(R.string.reminder_ai_progress_retry_waiting)
                else -> stringResource(R.string.reminder_ai_progress_running)
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (active) accent else muted)
            if (active && stage == ReminderAiStage.ANALYZING) {
                Text(stringResource(R.string.reminder_ai_progress_ai_wait), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

private fun ReminderAiStage.labelResource(): Int = when (this) {
    ReminderAiStage.QUEUED -> R.string.reminder_ai_progress_queue
    ReminderAiStage.ANALYZING -> R.string.reminder_ai_progress_analyzing
    ReminderAiStage.VALIDATING -> R.string.reminder_ai_progress_validating
    ReminderAiStage.GENERATING -> R.string.reminder_ai_progress_generating
}
