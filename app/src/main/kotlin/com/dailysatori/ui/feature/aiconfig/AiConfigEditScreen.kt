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
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

@Composable
fun AiConfigEditScreen(
    configId: Long? = null,
    functionType: Int = 0,
    onBack: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val repo = remember { KoinPlatform.getKoin().get<AIConfigRepository>() }

    var name by remember { mutableStateOf("") }
    var apiAddress by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var inheritFromGeneral by remember { mutableStateOf(functionType != 0) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(configId) {
        if (configId != null) {
            val config = repo.getById(configId)
            if (config != null) {
                name = config.name
                apiAddress = config.api_address
                apiToken = config.api_token
                modelName = config.model_name
                inheritFromGeneral = config.inherit_from_general == 1L
            }
        }
    }

    AppScaffold(
        title = if (configId != null) "编辑配置" else "新建配置",
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
                                    if (configId != null) {
                                        repo.update(configId, name, apiAddress, apiToken, modelName, functionType.toLong(), if (inheritFromGeneral) 1L else 0L, 0L)
                                    } else {
                                        repo.insert(name, apiAddress, apiToken, modelName, functionType.toLong(), if (inheritFromGeneral) 1L else 0L)
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving && (inheritFromGeneral || (apiAddress.isNotBlank() && apiToken.isNotBlank() && modelName.isNotBlank())),
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
            if (functionType == 0 || !inheritFromGeneral) {
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
            }
            if (functionType != 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("使用通用配置", modifier = Modifier.weight(1f))
                        Switch(checked = inheritFromGeneral, onCheckedChange = { inheritFromGeneral = it })
                    }
                }
            }
            if (!inheritFromGeneral) {
                item {
                    Column {
                        Text("API 地址", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = apiAddress,
                            onValueChange = { apiAddress = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://api.openai.com") },
                            shape = RoundedCornerShape(Radius.s),
                            singleLine = true,
                        )
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
                        Text("模型名称", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("gpt-4o-mini") },
                            shape = RoundedCornerShape(Radius.s),
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }
}
