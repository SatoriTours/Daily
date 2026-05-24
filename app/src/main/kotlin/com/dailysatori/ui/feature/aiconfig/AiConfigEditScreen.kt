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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.config.AiModel
import com.dailysatori.config.AiProvider
import com.dailysatori.config.aiProviders
import com.dailysatori.config.findProvider
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.component.settings.SettingsEditorBottomBar
import com.dailysatori.ui.component.settings.SettingsEditorMessage
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigEditScreen(
    configId: Long? = null,
    onBack: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val repo = remember { KoinPlatform.getKoin().get<AIConfigRepository>() }
    val aiService = remember { KoinPlatform.getKoin().get<AiService>() }

    var selectedProvider by remember { mutableStateOf<AiProvider?>(null) }
    var selectedModel by remember { mutableStateOf<AiModel?>(null) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var apiToken by remember { mutableStateOf("") }
    var customModelName by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    var wasDefault by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }

    val models = selectedProvider?.models ?: emptyList()
    val isCustomModel = selectedProvider != null && models.isEmpty()
    val currentModel = currentModelId(isCustomModel, customModelName, selectedModel)
    val canTest = selectedProvider != null && apiToken.isNotBlank() && currentModel != null
    val canSave = selectedProvider != null && apiToken.isNotBlank() && currentModel != null

    LaunchedEffect(configId) {
        if (configId != null) {
            val config = repo.getById(configId)
            if (config != null) {
                apiToken = config.api_token
                isDefault = config.is_default == 1L
                wasDefault = config.is_default == 1L
                selectedProvider = findProvider(config.provider)
                selectedModel = selectedProvider?.models?.find { it.id == config.model_name }
                if (selectedModel == null && config.model_name.isNotBlank()) {
                    customModelName = config.model_name
                }
            }
        }
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
                onTest = {
                    val provider = selectedProvider ?: return@SettingsEditorBottomBar
                    val modelId = currentModel ?: return@SettingsEditorBottomBar
                    val token = apiToken
                    scope.launch {
                        isTesting = true
                        testResult = null
                        testSuccess = null
                        val result = withContext(Dispatchers.IO) {
                            aiService.testConnection(
                                apiAddress = provider.apiHost,
                                apiToken = token,
                                modelName = modelId,
                                provider = provider.id,
                            )
                        }
                        testSuccess = result.isSuccess
                        testResult = result.fold(
                            onSuccess = { "连接成功：${it.take(80)}" },
                            onFailure = { it.message ?: "连接失败" },
                        )
                        isTesting = false
                    }
                },
                onSave = {
                    val provider = selectedProvider ?: return@SettingsEditorBottomBar
                    val modelId = currentModel ?: return@SettingsEditorBottomBar
                    val token = apiToken
                    val defaultValue = isDefault
                    scope.launch {
                        isSaving = true
                        try {
                            withContext(Dispatchers.IO) {
                                if (configId != null) {
                                    repo.update(configId, provider.id, provider.apiHost, token, modelId, if (defaultValue) 1L else 0L)
                                } else {
                                    repo.insert(provider.id, provider.apiHost, token, modelId, if (defaultValue) 1L else 0L)
                                }
                            }
                            onBack()
                        } finally {
                            isSaving = false
                        }
                    }
                },
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
                                    selectedProvider = provider
                                    selectedModel = null
                                    customModelName = ""
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
                    onValueChange = { apiToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...") },
                    shape = RoundedCornerShape(Radius.s),
                    singleLine = true,
                )
            }

            item {
                Text("选择模型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                } else if (isCustomModel) {
                    OutlinedTextField(
                        value = customModelName,
                        onValueChange = { customModelName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入模型名称") },
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
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
                                        selectedModel = model
                                        modelExpanded = false
                                    },
                                )
                            }
                        }
                    }
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
                        onCheckedChange = { isDefault = it },
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
    isCustomModel: Boolean,
    customModelName: String,
    selectedModel: AiModel?,
): String? {
    return when {
        isCustomModel && customModelName.isNotBlank() -> customModelName.trim()
        selectedModel != null -> selectedModel.id
        else -> null
    }
}
