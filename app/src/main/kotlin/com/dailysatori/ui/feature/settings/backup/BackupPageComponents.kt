package com.dailysatori.ui.feature.settings.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
    SettingsScaffold(title = navigation?.title ?: title, onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize()) {
            if (navigation != null) Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                shape = RoundedCornerShape(Radius.m),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(Spacing.xs).selectableGroup()) {
                    navigation.tabs.forEachIndexed { index, label ->
                        val selected = index == navigation.selectedIndex
                        Surface(
                            modifier = Modifier.weight(1f).selectable(selected = selected, enabled = !busy,
                                role = Role.Tab, onClick = { if (!selected) navigation.onSelect(index) }),
                            shape = RoundedCornerShape(Radius.s),
                            color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                        ) {
                            Box(Modifier.heightIn(min = Height.buttonSmall)
                                .padding(horizontal = Spacing.s, vertical = Spacing.s), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
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
            Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs)) {
                Icon(icon, null, Modifier.size(IconSize.l), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(IconSize.s),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showDivider) HorizontalDivider(
                Modifier.padding(start = Spacing.m + IconSize.l + Spacing.s + Spacing.xs, end = Spacing.m),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
