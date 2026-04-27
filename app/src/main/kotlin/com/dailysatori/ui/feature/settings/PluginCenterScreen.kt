package com.dailysatori.ui.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExtensionOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.scaffold.AppScaffold

@Composable
fun PluginCenterScreen(onBack: () -> Unit = {}) {
    val plugins = listOf<String>()

    AppScaffold(
        title = "插件中心",
        onBack = onBack,
        actions = {
            IconButton(onClick = { /* refresh */ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = { /* settings */ }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    ) { modifier ->
        if (plugins.isEmpty()) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Default.ExtensionOff,
                    title = "暂无插件",
                    subtitle = "配置插件服务器地址后刷新",
                    actionLabel = "刷新",
                    onAction = {},
                )
            }
        }
    }
}
