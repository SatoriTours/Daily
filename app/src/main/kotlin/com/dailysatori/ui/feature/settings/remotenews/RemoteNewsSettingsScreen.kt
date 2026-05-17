package com.dailysatori.ui.feature.settings.remotenews

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun RemoteNewsSettingsScreen(onBack: () -> Unit) {
    val viewModel: RemoteNewsSettingsViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    AppScaffold(title = "远程新闻设置", onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .padding(Spacing.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            state.sources.forEach { source ->
                Card(
                    shape = RoundedCornerShape(Radius.m),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(source.name, style = MaterialTheme.typography.titleMedium)
                        Text(source.base_url, style = MaterialTheme.typography.bodyMedium)
                        Text(if (source.enabled == 1L) "已启用" else "已停用", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            TextButton(onClick = { viewModel.startEdit(source) }) { Text("编辑") }
                            TextButton(onClick = { viewModel.deleteSource(source.id) }) { Text("删除") }
                        }
                    }
                }
            }
            Button(
                onClick = viewModel::startAdd,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("新增远程新闻") }
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("URL") },
                placeholder = { Text("http://192.168.1.10:3000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::updateToken,
                label = { Text("Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启用", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.enabled, onCheckedChange = viewModel::updateEnabled)
            }
            if (state.message != null) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "保存中..." else "保存")
            }
            TextButton(
                onClick = viewModel::testConnection,
                enabled = !state.isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.isTesting) "测试中..." else "测试连接") }
        }
    }
}
