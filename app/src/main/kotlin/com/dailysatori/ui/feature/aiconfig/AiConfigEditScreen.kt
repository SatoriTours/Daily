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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.dailysatori.config.AiModelPreset
import com.dailysatori.config.modelPresets
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigEditScreen(
    configId: Long? = null,
    onBack: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val repo = remember { KoinPlatform.getKoin().get<AIConfigRepository>() }

    var selectedPreset by remember { mutableStateOf<AiModelPreset?>(null) }
    var presetExpanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("openai") }
    var apiAddress by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(configId) {
        if (configId != null) {
            val config = repo.getById(configId)
            if (config != null) {
                name = config.name
                provider = config.provider
                apiAddress = config.api_address
                apiToken = config.api_token
                modelName = config.model_name
                isDefault = config.is_default == 1L
                selectedPreset = modelPresets.find {
                    it.provider == config.provider && it.modelName == config.model_name
                }
            }
        }
    }

    AppScaffold(
        title = if (configId != null) "编辑配置" else "添加配置",
        onBack = onBack,
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                isSaving = true
                                try {
                                    val finalName = name.ifBlank {
                                        selectedPreset?.displayName ?: "$provider / $modelName"
                                    }
                                    if (configId != null) {
                                        repo.update(configId, finalName, provider, apiAddress, apiToken, modelName, if (isDefault) 1L else 0L)
                                    } else {
                                        repo.insert(finalName, provider, apiAddress, apiToken, modelName, if (isDefault) 1L else 0L)
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving && apiToken.isNotBlank() && modelName.isNotBlank() && apiAddress.isNotBlank(),
                    ) { Text(if (isSaving) "保存中..." else "保存") }
                }
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
        ) {
            item {
                Column {
                    Text("选择模型", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    ExposedDropdownMenuBox(
                        expanded = presetExpanded,
                        onExpandedChange = { presetExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedPreset?.displayName ?: "请选择要使用的模型",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                            shape = RoundedCornerShape(Radius.s),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = presetExpanded,
                            onDismissRequest = { presetExpanded = false },
                        ) {
                            modelPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.displayName) },
                                    onClick = {
                                        selectedPreset = preset
                                        provider = preset.provider
                                        apiAddress = preset.apiAddress
                                        modelName = preset.modelName
                                        if (name.isBlank()) {
                                            name = preset.displayName
                                        }
                                        presetExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column {
                    Text("API Token", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = apiToken,
                        onValueChange = { apiToken = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                    )
                }
            }
            item {
                Column {
                    Text("API 地址", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = apiAddress,
                        onValueChange = { apiAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                        enabled = selectedPreset == null || selectedPreset?.id == "custom" || selectedPreset?.id == "ollama-custom",
                    )
                }
            }
            item {
                Column {
                    Text("模型名称", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                        enabled = selectedPreset == null || selectedPreset?.id == "custom" || selectedPreset?.id == "ollama-custom",
                    )
                }
            }
            item {
                Column {
                    Text("配置名称", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("设为默认", modifier = Modifier.weight(1f))
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }
            }
        }
    }
}
