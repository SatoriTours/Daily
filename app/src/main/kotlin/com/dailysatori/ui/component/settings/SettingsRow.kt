package com.dailysatori.ui.component.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.ui.component.misc.FeatureIcon
import com.dailysatori.ui.theme.*

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Height.listItem + Spacing.m)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeatureIcon(
                    icon = icon,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = MaterialTheme.colorScheme.primary,
                    containerSize = IconSize.xl,
                    iconSize = IconSize.s,
                )
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f).animateContentSize()) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (subtitle.isNotBlank()) Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                if (trailing != null) trailing() else Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(IconSize.m),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = Spacing.m + IconSize.xl + Spacing.m, end = Spacing.m),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
