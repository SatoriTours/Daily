package com.dailysatori.ui.component.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun CustomCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.padding(vertical = Spacing.xs),
            shape = RoundedCornerShape(Radius.m),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    } else {
        Card(
            modifier = modifier.padding(vertical = Spacing.xs),
            shape = RoundedCornerShape(Radius.m),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    }
}
