package com.dailysatori.ui.pages.aiconfig

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.components.SAppBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun AiConfigEditScreen(
    configId: Long? = null,
    functionType: Int = 0,
    onBack: () -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var apiAddress by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var inheritFromGeneral by remember { mutableStateOf(functionType != 0) }

    Scaffold(
        topBar = { SAppBar(title = if (configId != null) "编辑配置" else "新建配置", onBack = onBack) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("恢复") }
                    Button(
                        onClick = { /* save */ },
                        modifier = Modifier.weight(1f),
                        enabled = apiAddress.isNotBlank() && apiToken.isNotBlank() && modelName.isNotBlank(),
                    ) { Text("保存") }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
        ) {
            if (functionType == 0 || !inheritFromGeneral) {
                item {
                    Column {
                        Text("配置名称", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.s))
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
                        OutlinedTextField(value = apiAddress, onValueChange = { apiAddress = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://api.openai.com") }, shape = RoundedCornerShape(Radius.s))
                    }
                }
                item {
                    Column {
                        Text("API Token", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedTextField(value = apiToken, onValueChange = { apiToken = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.s))
                    }
                }
                item {
                    Column {
                        Text("模型名称", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedTextField(value = modelName, onValueChange = { modelName = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("gpt-4o-mini") }, shape = RoundedCornerShape(Radius.s))
                    }
                }
            }
        }
    }
}
