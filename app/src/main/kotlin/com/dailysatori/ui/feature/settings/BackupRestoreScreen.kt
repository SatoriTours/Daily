package com.dailysatori.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Spacing

@Composable
fun BackupRestoreScreen(onBack: () -> Unit = {}) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val backups = listOf<String>()

    AppScaffold(
        title = "从备份恢复",
        onBack = onBack,
        bottomBar = {
            if (backups.isNotEmpty()) {
                Button(
                    onClick = { /* restore */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.m)
                        .height(Height.button),
                    enabled = selectedIndex >= 0,
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("恢复备份")
                }
            }
        },
    ) { modifier ->
        if (backups.isEmpty()) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.xxl * 2),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.m))
                    Text("暂无备份信息", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "请先在备份设置中创建备份",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
