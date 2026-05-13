package com.dailysatori.ui.feature.settings.crayfishnews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun CrayfishNewsSettingsScreen(onBack: () -> Unit) {
    val viewModel: CrayfishNewsSettingsViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    AppScaffold(title = "小龙虾新闻设置", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("服务地址") },
                placeholder = { Text("http://192.168.1.10:3847") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::updateToken,
                label = { Text("API Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
            ) { Text(if (state.isSaving) "保存中..." else "保存") }
            TextButton(
                onClick = viewModel::testConnection,
                enabled = !state.isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.isTesting) "测试中..." else "测试连接") }
        }
    }
}
