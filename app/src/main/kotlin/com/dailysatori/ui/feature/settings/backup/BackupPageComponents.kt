package com.dailysatori.ui.feature.settings.backup

import com.dailysatori.core.diagnostics.performanceContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.ui.component.settings.LocalSettingsGroupNavigation
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.theme.*

@Composable
internal fun BackupPageScaffold(
    title: String,
    onBack: () -> Unit,
    busy: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val navigation = LocalSettingsGroupNavigation.current
    SettingsScaffold(
        title = navigation?.title ?: title,
        onBack = onBack,
        useGroupNavigation = navigation != null,
        navigationBusy = busy,
    ) { modifier ->
        Box(modifier.fillMaxSize()) {
            content(Modifier.fillMaxSize().performanceContent())
        }
    }
}

@Composable
internal fun BackupSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    showDivider: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Box(
                    modifier = Modifier
                        .size(Height.buttonSmall)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    Modifier.size(IconSize.s),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            if (showDivider) HorizontalDivider(
                Modifier.padding(start = Spacing.m + Height.buttonSmall + Spacing.m, end = Spacing.m),
                thickness = BorderWidth.xs,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
