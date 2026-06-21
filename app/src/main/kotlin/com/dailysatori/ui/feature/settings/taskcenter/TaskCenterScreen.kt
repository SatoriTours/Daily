package com.dailysatori.ui.feature.settings.taskcenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.asynctask.asyncTaskProgressFraction
import com.dailysatori.service.asynctask.asyncTaskStatusDisplayName
import com.dailysatori.service.asynctask.asyncTaskTypeDisplayName
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun TaskCenterScreen(onBack: () -> Unit) {
    val viewModel: TaskCenterViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    AppScaffold(title = "任务", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            TaskCenterFilters(state = state, viewModel = viewModel)
            if (state.tasks.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Refresh,
                    title = "暂无任务",
                    subtitle = "没有匹配结果",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskCenterTaskCard(task = task, onCancel = { viewModel.cancel(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCenterFilters(state: TaskCenterState, viewModel: TaskCenterViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("历史任务", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = state.showTerminal, onCheckedChange = viewModel::setShowTerminal)
        }
        Text("任务类型", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            FilterChip(
                selected = state.type == null,
                onClick = { viewModel.setType(null) },
                label = { Text("全部") },
            )
            AsyncTaskType.entries.forEach { type ->
                FilterChip(
                    selected = state.type == type.name,
                    onClick = { viewModel.setType(type.name) },
                    label = { Text(type.displayName) },
                )
            }
        }
        Text("任务状态", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            FilterChip(
                selected = state.status == null,
                onClick = { viewModel.setStatus(null) },
                label = { Text("全部") },
            )
            AsyncTaskStatus.entries.forEach { status ->
                FilterChip(
                    selected = state.status == status.name,
                    onClick = { viewModel.setStatus(status.name) },
                    label = { Text(asyncTaskStatusDisplayName(status.name)) },
                )
            }
        }
    }
}

@Composable
private fun TaskCenterTaskCard(task: AsyncTaskListItem, onCancel: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(asyncTaskTypeDisplayName(task.type), style = MaterialTheme.typography.titleMedium)
                    Text(asyncTaskStatusDisplayName(task.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (task.status == AsyncTaskStatus.queued.name || task.status == AsyncTaskStatus.running.name || task.status == AsyncTaskStatus.retrying.name) {
                    OutlinedButton(onClick = onCancel) {
                        Text("取消")
                    }
                }
            }
            val fraction = asyncTaskProgressFraction(task.progressCurrent, task.progressTotal)
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            val message = task.progressMessage.ifBlank { task.lastErrorMessage }
            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
