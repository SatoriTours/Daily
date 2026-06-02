package com.dailysatori.ui.component.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Spacing

fun newsListContentPadding(): PaddingValues =
    PaddingValues(Spacing.m)

fun newsCompactListContentPadding(): PaddingValues =
    PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)

@Composable
fun NewsStateMessage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    isError: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.xxl), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Spacing.m))
        }
        Text(
            text = title,
            style = if (icon != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!subtitle.isNullOrBlank()) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Spacing.xs))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Spacing.m))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
