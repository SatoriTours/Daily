package com.dailysatori.ui.feature.settings.crayfishnews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import com.dailysatori.ui.component.settings.SettingsEditorMessage
import com.dailysatori.ui.component.settings.SettingsFormSection
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun CrayfishNewsSettingsScreen(onBack: () -> Unit) {
    val viewModel: CrayfishNewsSettingsViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    AppScaffold(
        title = "小龙虾新闻设置",
        onBack = onBack,
        bottomBar = {
            SettingsEditorBottomBar(
                canTest = state.baseUrl.isNotBlank(),
                canSave = state.baseUrl.isNotBlank(),
                isTesting = state.isTesting,
                isSaving = state.isSaving,
                onTest = viewModel::testConnection,
                onSave = viewModel::save,
            )
        },
    ) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            SettingsFormSection("连接配置") {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::updateBaseUrl,
                    label = { Text("服务地址") },
                    placeholder = { Text("http://192.168.1.10:3847") },
                    shape = RoundedCornerShape(Radius.m),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.token,
                    onValueChange = viewModel::updateToken,
                    label = { Text("API Token") },
                    shape = RoundedCornerShape(Radius.m),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.message != null) {
                SettingsEditorMessage(message = state.message, isError = false)
            }
        }
    }
}
