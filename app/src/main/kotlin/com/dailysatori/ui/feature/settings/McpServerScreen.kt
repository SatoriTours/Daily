package com.dailysatori.ui.feature.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.shared.db.Mcp_server
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

@Composable
fun McpServerScreen(
    onBack: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val repo = remember { KoinPlatform.getKoin().get<McpServerRepository>() }
    var servers by remember { mutableStateOf<List<Mcp_server>>(emptyList()) }
    var showEdit by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        repo.getAll().collect { servers = it }
    }

    if (showEdit != null) {
        McpServerEditScreen(
            serverId = showEdit,
            onBack = { showEdit = null },
        )
        return
    }

    AppScaffold(
        title = "MCP 服务",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEdit = -1L },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加 MCP 服务")
            }
        },
    ) { modifier ->
        if (servers.isEmpty()) {
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
                items(servers, key = { it.id }) { server ->
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
                                    scope.launch(Dispatchers.IO) {
                                        repo.update(server.id, server.name, server.server_url, server.api_key, if (enabled) 1L else 0L)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerEditScreen(
    serverId: Long?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { KoinPlatform.getKoin().get<McpServerRepository>() }

    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) {
        if (serverId != null && serverId > 0) {
            val server = repo.getById(serverId)
            if (server != null) {
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
                                    if (serverId != null && serverId > 0) {
                                        repo.update(serverId, name, serverUrl, apiKey, if (enabled) 1L else 0L)
                                    } else {
                                        repo.insert(name, serverUrl, apiKey, if (enabled) 1L else 0L)
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving && name.isNotBlank() && serverUrl.isNotBlank(),
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
