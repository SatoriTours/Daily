package com.dailysatori.ui.feature.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.diary.DiaryThought
import com.dailysatori.service.diary.DiaryThoughtEvidence
import com.dailysatori.service.diary.DiaryThoughtState
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DiaryThoughtEntry(state: DiaryThoughtState, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("我的思想", style = MaterialTheme.typography.titleSmall)
            Text(
                text = when {
                    state.isUpdating || state.isPaused -> state.progress.ifBlank { "准备更新…" }
                    state.error != null -> state.error.orEmpty()
                    state.isStale -> "日记已变化，思想档案待更新"
                    else -> state.archive.thoughts.firstOrNull()?.statement ?: "从日记中，看见自己的价值观与做事准则"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
internal fun DiaryThoughtScreen(viewModel: DiaryThoughtViewModel, onBack: () -> Unit, onDiaryClick: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    AppScaffold(
        title = "我的思想",
        onBack = onBack,
        actions = { TextButton(onClick = viewModel::refresh, enabled = !state.isUpdating) { Text("更新") } },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item { DiaryThoughtIntroduction(state) }
            item { DiaryThoughtChatPreference(state.useInChat, viewModel::setUseInChat) }
            saveError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item { DiaryThoughtCorrections(state.corrections, onEdit = { editing = true }) }
            if (state.archive.thoughts.isEmpty()) item {
                Text(
                    "还没有足够依据形成思想条目。继续写下你的选择、理由与反思，档案会随日记逐步更新。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.archive.thoughts, key = { "${it.category}:${it.statement}" }) { thought ->
                DiaryThoughtCard(thought, onDiaryClick)
            }
            item { Text("这是基于日记的阶段性理解，你始终可以修正它。", Modifier.padding(bottom = Spacing.l), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (editing) DiaryThoughtCorrectionDialog(state.corrections, saveError, { editing = false }) {
        viewModel.saveCorrections(it) { editing = false }
    }
}

@Composable
private fun DiaryThoughtChatPreference(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("用于 AI 对话", style = MaterialTheme.typography.titleSmall)
            Text("回答时参考我的思想与修正；日记变化后，等待档案更新再使用归纳。", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun DiaryThoughtIntroduction(state: DiaryThoughtState) {
    Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("从我写下的生活，认识我自己", style = MaterialTheme.typography.titleLarge)
        Text(
            "基于已保存的日记正文，持续整理价值观、做事准则、思维方式与变化。使用设置中的默认 AI 模型，每条归纳都保留日记原文依据。",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.archive.generatedAt > 0) {
            val date = remember(state.archive.generatedAt) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(state.archive.generatedAt))
            }
            Text("上次整理 ${state.archive.diaryCount} 篇日记 · $date", style = MaterialTheme.typography.bodySmall)
        }
        if (state.isUpdating) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.isUpdating) Text("后台或锁屏时暂停，回到前台自动继续。", style = MaterialTheme.typography.bodySmall)
        if (state.isUpdating || state.isPaused || state.error != null) {
            Text(state.progress.ifBlank { "准备更新…" }, style = MaterialTheme.typography.bodySmall)
        }
        if (state.isStale && !state.isUpdating) Text("待更新：当前展示仍有日记依据的已有内容。", style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DiaryThoughtCard(thought: DiaryThought, onDiaryClick: (Long) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("${thought.category} · ${thought.basis}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(thought.statement, style = MaterialTheme.typography.titleMedium)
            thought.evidence.forEach { evidence ->
                DiaryThoughtEvidenceLink(evidence, onDiaryClick)
            }
        }
    }
}

@Composable
private fun DiaryThoughtEvidenceLink(evidence: DiaryThoughtEvidence, onDiaryClick: (Long) -> Unit) {
    TextButton(onClick = { onDiaryClick(evidence.diaryId) }) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("“${evidence.quote}”", style = MaterialTheme.typography.bodyMedium)
            Text("查看原日记", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DiaryThoughtCorrections(corrections: String, onEdit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("我的补充与修正", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onEdit) { Text("编辑") }
        }
        Text(
            corrections.ifBlank { "如果归纳不符合你的想法，在这里说明。你的修正会保留，并用于下次整理。" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiaryThoughtCorrectionDialog(initial: String, error: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的补充与修正") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 2_000) text = it },
                    minLines = 4,
                    maxLines = 10,
                    label = { Text("例如：那次退让不代表我认同对方") },
                    supportingText = { Text("${text.length}/2000") },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("保存并更新") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
