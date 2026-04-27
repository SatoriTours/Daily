package com.dailysatori.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagChipRow(
    tags: List<String>,
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalArrangement = Arrangement.Center,
    ) {
        tags.forEach { tag ->
            FilterChip(
                selected = false,
                onClick = { onTagClick(tag) },
                label = {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                shape = RoundedCornerShape(Radius.xs),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}
