package com.dailysatori.ui.feature.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ShareDialogScreen(
    url: String,
    isUpdate: Boolean = false,
    onBack: () -> Unit = {},
) {
    val viewModel: ShareDialogViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(url) {
        viewModel.initialize(url)
    }

    AppScaffold(
        title = if (state.isUpdate) "更新文章" else "保存链接",
        onBack = onBack,
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        onClick = { viewModel.save { onBack() } },
                        modifier = Modifier.weight(2f),
                        enabled = !state.isSaving,
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (state.isUpdate) "保存更改" else "保存")
                        }
                    }
                }
            }
        },
    ) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("链接", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    if (state.isUpdate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AI 分析", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Switch(checked = state.aiAnalysis, onCheckedChange = { viewModel.toggleAiAnalysis() }, modifier = Modifier.height(24.dp))
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
                OutlinedTextField(value = state.title, onValueChange = { viewModel.onTitleChanged(it) }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(Radius.s))
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
                OutlinedTextField(value = state.comment, onValueChange = { viewModel.onCommentChanged(it) }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(Radius.s))
            }
        }
    }
}
