package com.dailysatori.ui.feature.aiconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.config.AiModel
import com.dailysatori.config.aiProviders
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import com.dailysatori.ui.component.settings.SettingsEditorMessage
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigEditScreen(
    configId: Long? = null,
    onBack: () -> Unit = {},
) {
    val viewModel: AiConfigEditViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    val selectedProvider = state.selectedProvider
    val selectedModel = state.selectedModel
    val apiToken = state.apiToken
    val customModelName = state.customModelName
    val isDefault = state.isDefault
    val wasDefault = state.wasDefault
    val isSaving = state.isSaving
    val isTesting = state.isTesting
    val isRefreshingModels = state.isRefreshingModels
    val modelRefreshMessage = state.modelRefreshMessage
    val testResult = state.testResult
    val testSuccess = state.testSuccess
    val models = state.availableModels
    val currentModel = currentModelId(customModelName, selectedModel)
    val canTest = selectedProvider != null && apiToken.isNotBlank() && currentModel != null
    val canSave = selectedProvider != null && apiToken.isNotBlank() && currentModel != null

    LaunchedEffect(configId) {
        viewModel.load(configId)
    }

    AppScaffold(
        title = if (configId != null) "编辑配置" else "添加配置",
        onBack = onBack,
        bottomBar = {
            SettingsEditorBottomBar(
                canTest = canTest,
                canSave = canSave,
                isTesting = isTesting,
                isSaving = isSaving,
                onTest = viewModel::testConnection,
                onSave = { viewModel.save(configId, onBack) },
            )
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
        ) {
            item {
                Text("选择服务商", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedProvider?.name ?: "请选择模型服务商",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false },
                    ) {
                        aiProviders.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name) },
                                onClick = {
                                    viewModel.selectProvider(provider)
                                    providerExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            item {
                Text("API Token", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = apiToken,
                    onValueChange = viewModel::updateApiToken,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...") },
                    shape = RoundedCornerShape(Radius.s),
                    singleLine = true,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "选择模型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = viewModel::refreshModels,
                        enabled = selectedProvider != null && !isRefreshingModels,
                    ) {
                        Text(if (isRefreshingModels) "刷新中" else "刷新模型")
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                if (selectedProvider == null) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("请先选择服务商") },
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                        enabled = false,
                    )
                } else if (models.isEmpty()) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("暂无可选模型，可在下方手动输入") },
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                        enabled = false,
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedModel?.name ?: "请选择模型",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                            shape = RoundedCornerShape(Radius.s),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false },
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name) },
                                    onClick = {
                                        viewModel.selectModel(model)
                                        modelExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.s))
                OutlinedTextField(
                    value = customModelName,
                    onValueChange = viewModel::updateCustomModelName,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("自定义模型名称") },
                    shape = RoundedCornerShape(Radius.s),
                    singleLine = true,
                    enabled = selectedProvider != null,
                )
                if (modelRefreshMessage != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        modelRefreshMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("设为默认配置", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (wasDefault) "默认配置不能取消，只能将其他配置设为默认" else "日记、读书和 AI 助手都将使用此模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.m))
                    Switch(
                        checked = isDefault,
                        onCheckedChange = viewModel::updateIsDefault,
                        enabled = !wasDefault,
                    )
                }
            }

            if (testResult != null) {
                item {
                    SettingsEditorMessage(
                        message = testResult ?: "",
                        isError = testSuccess != true,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(Spacing.xl)) }
        }
    }
}

private fun currentModelId(
    customModelName: String,
    selectedModel: AiModel?,
): String? {
    return customModelName.ifBlank { selectedModel?.id.orEmpty() }.trim().takeIf { it.isNotBlank() }
}
