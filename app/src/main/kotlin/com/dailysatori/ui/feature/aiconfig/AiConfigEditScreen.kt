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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import com.dailysatori.R
import com.dailysatori.config.AiModel
import com.dailysatori.config.aiProviders
import com.dailysatori.service.ai.AiModelAvailability
import com.dailysatori.service.ai.OpenCodeGoProviderId
import com.dailysatori.service.ai.aiModelAvailability
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import com.dailysatori.ui.component.settings.SettingsEditorMessage
import com.dailysatori.ui.component.settings.SettingsFormSection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dailysatori.ui.component.settings.rememberSettingsEditorBack
import com.dailysatori.service.i18n.I18nService
import org.koin.compose.koinInject
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigEditScreen(
    configId: Long? = null,
    onBack: () -> Unit = {},
) {
    val viewModel: AiConfigEditViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val i18n: I18nService = koinInject()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.settings_saved)

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
    val canTest = state.editable && state.canUseModel && apiToken.isNotBlank()
    val canSave = canTest && state.hasChanges
    val requestBack = rememberSettingsEditorBack(state.hasChanges, !state.editable, onBack)

    LaunchedEffect(configId) {
        viewModel.load(configId)
    }

    AppScaffold(
        title = if (configId != null) "编辑配置" else "添加配置",
        onBack = requestBack,
        bottomBar = {
            SettingsEditorBottomBar(
                canTest = canTest,
                canSave = canSave,
                isTesting = isTesting,
                isSaving = isSaving,
                onTest = viewModel::testConnection,
                onSave = { viewModel.save(configId) {
                    Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                    onBack()
                } },
            )
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
        ) {
            state.saveError?.let { message ->
                item { SettingsEditorMessage(message, isError = true) }
            }
            item {
                SettingsFormSection(i18n.t("settings_design.service_access")) {
                    Text("选择服务商", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    ExposedDropdownMenuBox(
                        expanded = providerExpanded,
                        onExpandedChange = { if (state.editable) providerExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedProvider?.name ?: "请选择模型服务商",
                            enabled = state.editable,
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
                    if (selectedProvider?.id == OpenCodeGoProviderId) {
                        Text(
                            i18n.t("ai_config.go_usage_notice"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("API Token", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = apiToken,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = state.editable,
                        onValueChange = viewModel::updateApiToken,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("sk-...") },
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                    )
                }
            }
            item {
                SettingsFormSection(i18n.t("settings_design.model_selection")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "选择模型",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = viewModel::refreshModels,
                            enabled = selectedProvider != null && !isRefreshingModels && state.editable,
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
                            onExpandedChange = { if (state.editable) modelExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedModel?.name ?: "请选择模型",
                                enabled = state.editable,
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
                                    val availability = aiModelAvailability(selectedProvider.id, model.id)
                                    val suffix = when (availability) {
                                        AiModelAvailability.Supported -> ""
                                        AiModelAvailability.RequiresResponses -> " · ${i18n.t("ai_config.responses_unsupported")}"
                                        AiModelAvailability.Unknown -> " · ${i18n.t("ai_config.model_unadapted")}"
                                    }
                                    DropdownMenuItem(
                                        enabled = availability == AiModelAvailability.Supported,
                                        text = { Text(model.name + suffix) },
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
                        enabled = state.editable && selectedProvider != null,
                    )
                    if (currentModel != null && state.modelAvailability != AiModelAvailability.Supported) {
                        Text(
                            i18n.t(if (state.modelAvailability == AiModelAvailability.RequiresResponses)
                                "ai_config.responses_unsupported" else "ai_config.model_unadapted"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (modelRefreshMessage != null) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            modelRefreshMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                SettingsFormSection(i18n.t("settings_design.usage_preferences")) {
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
                            enabled = state.editable && !wasDefault,
                        )
                    }
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
