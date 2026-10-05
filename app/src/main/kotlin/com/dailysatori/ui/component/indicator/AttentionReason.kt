package com.dailysatori.ui.component.indicator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.*

@Composable
fun AttentionReason(reason: String, actionLabel: String, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Badge(containerColor = MaterialTheme.colorScheme.error)
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(actionLabel) }
        }
    }
}
