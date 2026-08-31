package com.dailysatori.ui.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.component.settings.SettingsRow
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.ui.theme.Radius
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onReminders: () -> Unit,
    onAddReminder: () -> Unit,
    onFavorites: () -> Unit,
    onExternalFavorites: () -> Unit,
    onTasks: () -> Unit,
    onSettings: () -> Unit,
    onPrivacy: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppScaffold(title = "个人中心", onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item { ProfileSection("今日提醒") {
                Text("待完成 ${state.todayReminderCount} 项", style = MaterialTheme.typography.titleMedium)
                if (state.nextReminderContent == null) {
                    Text("今天没有待完成提醒", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("下一项：${state.nextReminderContent} · ${state.nextReminderTime}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    TextButton(onClick = onReminders, modifier = Modifier.weight(1f)) { Text("查看全部") }
                    Button(onClick = onAddReminder, modifier = Modifier.weight(1f)) { Text("新增提醒") }
                }
            } }
            item { ProfileSection("我的内容") {
                SettingsRow(Icons.Default.Bookmark, "收藏库", "${state.favoriteCount} 项", onFavorites)
                SettingsRow(Icons.Default.CloudSync, "外部收藏", "${state.externalFavoriteCount} 项 · ${state.enabledExternalSourceCount} 个来源", onExternalFavorites)
            } }
            item { ProfileSection("运行状态") {
                val progress = state.taskProgressLabel?.let { " · 进度 $it" }.orEmpty()
                SettingsRow(Icons.Default.Task, "同步与任务", "进行中 ${state.activeTaskCount} · 失败 ${state.failedTaskCount}$progress", onTasks)
                if (state.failedTaskCount > 0) TextButton(onClick = onTasks) { Text("查看失败任务") }
            } }
            item { ProfileSection("应用管理") {
                SettingsRow(Icons.Default.Settings, "设置", "应用与提醒设置", onSettings)
                SettingsRow(Icons.Default.DataUsage, "数据与隐私", "本地数据与隐私说明", onPrivacy)
            } }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
