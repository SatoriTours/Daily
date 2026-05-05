package com.dailysatori.ui.feature.settings.mcp

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dailysatori.config.McpProvider
import com.dailysatori.config.McpTemplate
import com.dailysatori.config.McpTemplateType
import com.dailysatori.config.filterNewMcpTemplates
import com.dailysatori.config.mcpProviders
import com.dailysatori.shared.db.Mcp_server
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

internal enum class McpScreenMode { LIST, PRESET_ADD, MANUAL_ADD }

@Composable
fun McpServerScreen(
    onBack: () -> Unit = {},
) {
    val viewModel: McpServerViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var mode by remember { mutableStateOf(McpScreenMode.LIST) }
    var showEdit by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.observeServers()
    }

    if (showEdit != null) {
        McpServerEditScreen(
            viewModel = viewModel,
            state = state,
            serverId = showEdit,
            onBack = { showEdit = null },
        )
        return
    }

    when (mode) {
        McpScreenMode.PRESET_ADD -> {
            McpServerPresetAddScreen(
                viewModel = viewModel,
                state = state,
                existingServerUrls = state.servers.map { it.server_url }.toSet(),
                onBack = { mode = McpScreenMode.LIST },
                onManualAdd = { mode = McpScreenMode.MANUAL_ADD },
            )
            return
        }
        McpScreenMode.MANUAL_ADD -> {
            McpServerEditScreen(
                viewModel = viewModel,
                state = state,
                serverId = -1L,
                onBack = { mode = McpScreenMode.LIST },
            )
            return
        }
        McpScreenMode.LIST -> Unit
    }

    AppScaffold(
        title = "MCP 服务",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mode = McpScreenMode.PRESET_ADD },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加 MCP 服务")
            }
        },
    ) { modifier ->
        if (state.servers.isEmpty()) {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("暂无 MCP 服务", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.padding(Spacing.s))
                Text("点击右下角 + 添加 MCP 服务", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(state.servers, key = { it.id }) { server ->
                    Card(
                        onClick = { showEdit = server.id },
                        shape = RoundedCornerShape(Radius.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    server.server_url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Spacer(modifier = Modifier.width(Spacing.s))
                            Switch(
                                checked = server.enabled == 1L,
                                onCheckedChange = { enabled ->
                                    viewModel.toggleServerEnabled(server, enabled)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpServerPresetAddScreen(
    viewModel: McpServerViewModel,
    state: McpServerUiState,
    existingServerUrls: Set<String>,
    onBack: () -> Unit,
    onManualAdd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedProvider by remember { mutableStateOf<McpProvider?>(null) }
    var providerExpanded by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var selectedTemplateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val groupedTemplates = selectedProvider?.let { selectableMcpTemplatesByType(it, existingServerUrls) }.orEmpty()
    val selectedTemplates = groupedTemplates.values.flatten().filter { it.id in selectedTemplateIds }
    val canSave = selectedProvider != null && apiKey.isNotBlank() && selectedTemplates.isNotEmpty() && !state.isSaving

    LaunchedEffect(state.error) {
        if (state.error != null) {
            saveMessage = mcpBatchSaveFailureMessage()
            viewModel.clearError()
        }
    }

    AppScaffold(
        title = "添加 MCP 服务",
        onBack = onBack,
        bottomBar = {
            McpPresetAddActions(
                canSave = canSave,
                isSaving = state.isSaving,
                onManualAdd = onManualAdd,
                onSave = {
                    val provider = selectedProvider ?: return@McpPresetAddActions
                    scope.launch {
                        val result = viewModel.saveSelectedTemplates(provider, selectedTemplates, apiKey)
                        if (result != null) {
                            saveMessage = mcpBatchSaveResultMessage(result)
                            selectedTemplateIds = emptySet()
                        }
                    }
                },
            )
        },
    ) { modifier ->
        McpPresetAddContent(
            modifier = modifier,
            selectedProvider = selectedProvider,
            providerExpanded = providerExpanded,
            apiKey = apiKey,
            saveMessage = saveMessage,
            groupedTemplates = groupedTemplates,
            selectedTemplateIds = selectedTemplateIds,
            onProviderExpandedChange = { providerExpanded = it },
            onProviderSelected = { provider ->
                selectedProvider = provider
                selectedTemplateIds = emptySet()
                saveMessage = null
                viewModel.clearError()
                providerExpanded = false
            },
            onApiKeyChange = { apiKey = it; saveMessage = null; viewModel.clearError() },
            onSelectionChange = { selectedTemplateIds = it },
        )
    }
}

@Composable
private fun McpPresetAddContent(
    modifier: Modifier,
    selectedProvider: McpProvider?,
    providerExpanded: Boolean,
    apiKey: String,
    saveMessage: String?,
    groupedTemplates: Map<McpTemplateType, List<McpTemplate>>,
    selectedTemplateIds: Set<String>,
    onProviderExpandedChange: (Boolean) -> Unit,
    onProviderSelected: (McpProvider) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        contentPadding = PaddingValues(vertical = Spacing.m),
    ) {
        item { McpProviderDropdown(selectedProvider, providerExpanded, onProviderExpandedChange, onProviderSelected) }
        item { McpApiKeyField(selectedProvider, apiKey, onApiKeyChange) }
        saveMessage?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) } }
        mcpTemplateSection(McpTemplateType.NORMAL, groupedTemplates, selectedTemplateIds, onSelectionChange)
        mcpTemplateSection(McpTemplateType.CODING_PLAN, groupedTemplates, selectedTemplateIds, onSelectionChange)
    }
}

@Composable
private fun McpPresetAddActions(
    canSave: Boolean,
    isSaving: Boolean,
    onManualAdd: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        OutlinedButton(onClick = onManualAdd, modifier = Modifier.weight(1f)) { Text("手动添加") }
        Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = canSave) {
            Text(if (isSaving) "添加中..." else "添加选中 MCP")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpProviderDropdown(
    selectedProvider: McpProvider?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onProviderSelected: (McpProvider) -> Unit,
) {
    Column {
        Text("选择服务商", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(Spacing.xs))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
            OutlinedTextField(
                value = selectedProvider?.name ?: "请选择 MCP 服务商",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(Radius.s),
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
                mcpProviders.forEach { provider ->
                    DropdownMenuItem(text = { Text(provider.name) }, onClick = { onProviderSelected(provider) })
                }
            }
        }
    }
}

@Composable
private fun McpApiKeyField(
    selectedProvider: McpProvider?,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
) {
    Column {
        Text("API Key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(selectedProvider?.apiKeyPlaceholder ?: "请先选择服务商") },
            shape = RoundedCornerShape(Radius.s),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
        )
    }
}

private fun LazyListScope.mcpTemplateSection(
    type: McpTemplateType,
    groupedTemplates: Map<McpTemplateType, List<McpTemplate>>,
    selectedTemplateIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    val templates = groupedTemplates[type].orEmpty()
    if (templates.isEmpty()) return
    item { Text(type.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
    items(templates, key = { it.id }) { template ->
        McpTemplateCard(
            template = template,
            checked = template.id in selectedTemplateIds,
            onCheckedChange = { checked ->
                onSelectionChange(if (checked) selectedTemplateIds + template.id else selectedTemplateIds - template.id)
            },
        )
    }
}

@Composable
private fun McpTemplateCard(
    template: McpTemplate,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(Radius.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(modifier = Modifier.width(Spacing.s))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(template.name, style = MaterialTheme.typography.titleSmall)
                Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(template.serverUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

internal fun selectableMcpTemplatesByType(
    provider: McpProvider,
    existingServerUrls: Set<String>,
): Map<McpTemplateType, List<McpTemplate>> = McpTemplateType.entries.associateWith { type ->
    filterNewMcpTemplates(provider.templates.filter { it.type == type }, existingServerUrls)
}

internal fun mcpBatchSaveResultMessage(result: McpBatchSaveResult): String =
    "已添加 ${result.added} 个 MCP，跳过 ${result.skipped} 个已存在服务"

internal fun mcpBatchSaveFailureMessage(): String = "添加 MCP 失败，请稍后重试"

@Composable
private fun McpServerEditScreen(
    viewModel: McpServerViewModel,
    state: McpServerUiState,
    serverId: Long?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }

    LaunchedEffect(serverId) {
        if (serverId != null && serverId > 0) {
            viewModel.loadServer(serverId) { server ->
                name = server.name
                serverUrl = server.server_url
                apiKey = server.api_key
                enabled = server.enabled == 1L
            }
        }
    }

    AppScaffold(
        title = if (serverId != null && serverId > 0) "编辑 MCP 服务" else "添加 MCP 服务",
        onBack = onBack,
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        scope.launch {
                            if (viewModel.saveServer(serverId, name, serverUrl, apiKey, enabled)) {
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving && name.isNotBlank() && serverUrl.isNotBlank(),
                ) { Text(if (state.isSaving) "保存中..." else "保存") }
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = PaddingValues(vertical = Spacing.m),
        ) {
            state.error?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                Column {
                    Text("服务名称", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("如：Web Search") },
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                    )
                }
            }
            item {
                Column {
                    Text("服务地址", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://mcp.example.com") },
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                    )
                }
            }
            item {
                Column {
                    Text("API Key（可选）", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.s),
                        singleLine = true,
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        }
    }
}
