package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.dailysatori.R
import com.dailysatori.ui.theme.*

@Composable
internal fun DiaryWelcomePanel(onText: () -> Unit, onVoice: () -> Unit, voiceEnabled: Boolean) {
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.diary_feed_capture_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.diary_feed_capture_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = Spacing.s).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                DiaryCaptureAction(stringResource(R.string.diary_feed_write_now), Icons.Default.Edit, onText,
                    modifier = Modifier.weight(1f).fillMaxHeight())
                DiaryCaptureAction(stringResource(R.string.diary_feed_record_voice), Icons.Default.MicNone, onVoice,
                    modifier = Modifier.weight(1f).fillMaxHeight(), enabled = voiceEnabled)
            }
        }
    }
}

@Composable
private fun DiaryCaptureAction(
    label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true,
) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = Height.button),
        shape = RoundedCornerShape(Radius.m),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(Spacing.s),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.m))
        Spacer(Modifier.width(Spacing.s))
        Text(label, modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
}
