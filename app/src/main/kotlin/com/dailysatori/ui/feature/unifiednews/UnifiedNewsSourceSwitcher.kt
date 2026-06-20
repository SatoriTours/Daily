package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.Spacing

@Composable
internal fun UnifiedNewsSourceSwitcher(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UnifiedNewsSourceTabs(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
        IconButton(onClick = viewModel::refreshSelectedSource) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun UnifiedNewsSourceTabs(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel, modifier: Modifier = Modifier) {
    val sourceChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        selectedLabelColor = MaterialTheme.colorScheme.primary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    )
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FilterChip(
            selected = state.sourceSelection is UnifiedNewsSourceSelection.Summary,
            onClick = viewModel::selectSummarySource,
            label = { Text("汇总") },
            colors = sourceChipColors,
        )
        state.remoteSources.forEach { source ->
            FilterChip(
                selected = (state.sourceSelection as? UnifiedNewsSourceSelection.RemoteSource)?.id == source.id,
                onClick = { viewModel.selectRemoteSource(source) },
                label = { Text(source.name) },
                colors = sourceChipColors,
            )
        }
        state.externalFavoriteSources.forEach { source ->
            FilterChip(
                selected = (state.sourceSelection as? UnifiedNewsSourceSelection.ExternalFavoriteSource)?.id == source.id,
                onClick = { viewModel.selectExternalFavoriteSource(source) },
                label = { Text(source.name) },
                colors = sourceChipColors,
            )
        }
        FilterChip(
            selected = state.sourceSelection is UnifiedNewsSourceSelection.LocalArticles,
            onClick = viewModel::selectLocalArticlesSource,
            label = { Text("本地新闻") },
            colors = sourceChipColors,
        )
    }
}
