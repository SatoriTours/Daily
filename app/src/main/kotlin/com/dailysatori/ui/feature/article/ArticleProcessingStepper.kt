package com.dailysatori.ui.feature.article

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.ui.theme.editorial

@Composable
internal fun ArticleProcessingStepper(
    status: String?,
    progress: String?,
    modifier: Modifier = Modifier,
) {
    val currentStep = articleProcessingStepIndex(status, progress).coerceAtLeast(0)

    Surface(
        modifier = modifier.fillMaxWidth().padding(Spacing.m),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = "正在更新文章",
                    style = MaterialTheme.typography.titleMedium.editorial(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "重新获取网页、生成摘要，并整理原文排版。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            articleProcessingStepLabels.forEachIndexed { index, label ->
                ProcessingStepRow(
                    label = label,
                    isCompleted = index < currentStep,
                    isCurrent = index == currentStep,
                )
            }
        }
    }
}

@Composable
private fun ProcessingStepRow(
    label: String,
    isCompleted: Boolean,
    isCurrent: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "processing-step")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "processing-step-pulse",
    )
    val contentColor = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        isCompleted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isCompleted -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.m),
                    tint = MaterialTheme.colorScheme.primary,
                )
                isCurrent -> CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.m).scale(pulse),
                    strokeWidth = BorderWidth.m,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Box(
                    modifier = Modifier
                        .size(IconSize.m)
                        .background(MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.m))
            Text(
                text = label,
                style = if (isCurrent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }

    if (isCurrent) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.m),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            rowContent()
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
            rowContent()
        }
    }
}
