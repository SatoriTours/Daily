package com.dailysatori.ui.pages.share_dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.components.SAppBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun ShareDialogScreen(
    url: String,
    isUpdate: Boolean = false,
    onBack: () -> Unit = {},
) {
    var title by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var aiAnalysis by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            SAppBar(
                title = if (isUpdate) "更新文章" else "保存链接",
                onBack = onBack,
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = { /* save */ }, modifier = Modifier.weight(2f)) { Text(if (isUpdate) "保存更改" else "保存") }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("链接", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    if (isUpdate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AI 分析", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Switch(checked = aiAnalysis, onCheckedChange = { aiAnalysis = it }, modifier = Modifier.height(24.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Surface(
                    shape = RoundedCornerShape(Radius.s),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(url, modifier = Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Title, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("标题", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(Radius.s))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("标签", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("(可选)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Comment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("备注", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("(可选)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                OutlinedTextField(value = comment, onValueChange = { comment = it }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(Radius.s))
            }
        }
    }
}
