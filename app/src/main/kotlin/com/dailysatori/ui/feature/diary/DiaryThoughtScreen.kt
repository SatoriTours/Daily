package com.dailysatori.ui.feature.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    TextButton(onClick = onClick, modifier = Modifier.semantics {
        contentDescription = "我的思想"
        stateDescription = when {
            state.isUpdating -> state.progress.ifBlank { "正在整理" }
            state.error != null -> "整理未完成"
            state.isPaused -> "整理已暂停"
            state.isStale -> "有日记待整理"
            else -> "查看思想档案"
        }
    }) {
        if (state.isUpdating) {
            CircularProgressIndicator(Modifier.padding(end = Spacing.xs).size(IconSize.xs), strokeWidth = BorderWidth.m)
        }
        BadgedBox(badge = {
            if (!state.isUpdating && (state.error != null || state.isPaused || state.isStale)) {
                Badge(containerColor = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }) { Text("思想", maxLines = 1) }
    }
}

@Composable
internal fun DiaryThoughtScreen(viewModel: DiaryThoughtViewModel, onBack: () -> Unit, onDiaryClick: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val presentation = remember(state.archive.thoughts) { diaryThoughtPresentation(state.archive.thoughts) }
    var editing by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    AppScaffold(
        title = "我的思想",
        onBack = onBack,
        actions = {
            TextButton(onClick = viewModel::refresh, enabled = !state.isUpdating) {
                Text(if (state.isUpdating) "整理中" else "更新")
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.padding(horizontal = Spacing.m),
            contentPadding = PaddingValues(top = Spacing.l, bottom = Height.navBar + Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item(key = "introduction") { DiaryThoughtIntroduction(state) }
            if (presentation.highlights.isNotEmpty()) {
                item(key = "highlights") { DiaryThoughtHighlights(presentation.highlights) }
            }
            if (state.isUpdating || state.isPaused || state.error != null || state.isStale) {
                item(key = "status") { DiaryThoughtStatus(state) }
            }
            if (state.archive.thoughts.isEmpty()) item(key = "empty") { DiaryThoughtEmptyState(state) }
            presentation.sections.forEach { section ->
                item(key = "section:${section.category}") { DiaryThoughtSectionHeading(section) }
                items(section.thoughts, key = { "thought:${it.category}:${it.statement}" }) { thought ->
                    DiaryThoughtParagraph(thought, onDiaryClick)
                }
            }
            item(key = "preferences") { DiaryThoughtPreferences(state, viewModel::setUseInChat) { editing = true } }
            saveError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (editing) DiaryThoughtCorrectionDialog(state.corrections, saveError, { editing = false }) {
        viewModel.saveCorrections(it) { editing = false }
    }
}

@Composable
private fun DiaryThoughtChatPreference(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("用于 AI 对话", style = MaterialTheme.typography.titleSmall)
            Text(
                "让 AI 回答更贴近你的想法",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun DiaryThoughtIntroduction(state: DiaryThoughtState) {
    Column(Modifier.padding(horizontal = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("思想轮廓", style = MaterialTheme.typography.headlineLarge)
        if (state.archive.generatedAt > 0) {
            val date = remember(state.archive.generatedAt) {
                SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(state.archive.generatedAt))
            }
            Text("来自 ${state.archive.diaryCount} 篇日记 · 更新于 $date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiaryThoughtHighlights(thoughts: List<DiaryThought>) {
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("先看这几条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            thoughts.forEachIndexed { index, thought ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text("0${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(thought.statement, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("${thought.category} · ${thought.basis}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryThoughtStatus(state: DiaryThoughtState) {
    Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (state.isUpdating) {
                CircularProgressIndicator(modifier = Modifier.size(IconSize.m), strokeWidth = BorderWidth.l)
            } else {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.m))
            }
            DiaryThoughtStatusText(state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DiaryThoughtStatusText(state: DiaryThoughtState, modifier: Modifier = Modifier) {
    val title = when {
        state.isUpdating -> "正在整理思想"
        state.isPaused -> "整理已暂停"
        state.error != null -> "本次整理未完成"
        else -> "有新的日记待整理"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if ((state.isUpdating || state.isPaused) && state.progress.isNotBlank()) {
            Text(state.progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = when {
                state.isUpdating -> "离开或锁屏后仍会在后台继续整理"
                state.isPaused -> "已保存进度，等待后台继续整理"
                state.error != null -> state.error.orEmpty()
                else -> "点击右上角更新，当前内容仍保留原文依据"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiaryThoughtSectionHeading(section: DiaryThoughtSection) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(section.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            Text("${section.thoughts.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiaryThoughtEmptyState(state: DiaryThoughtState) {
    val inProgress = state.isUpdating || state.isPaused
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(Icons.Outlined.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.xl))
            Text(if (inProgress) "思想正在慢慢浮现" else "让日记慢慢勾勒你", style = MaterialTheme.typography.titleSmall)
            Text(
                if (inProgress) "整理完成后，有日记依据的思想会出现在这里。" else "写下选择、理由与反思。积累足够依据后，你的思想会在这里逐渐清晰。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DiaryThoughtPreferences(state: DiaryThoughtState, onChatChange: (Boolean) -> Unit, onEdit: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text("偏好与补充", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起偏好与补充" else "展开偏好与补充")
        }
        if (expanded) {
            DiaryThoughtChatPreference(state.useInChat, onChatChange)
            DiaryThoughtCorrections(state.corrections, onEdit)
            DiaryThoughtFootnote()
        }
    }
}

@Composable
private fun DiaryThoughtFootnote() {
    Text(
        "使用默认 AI 模型整理，你始终可以修正归纳。\n日记变化后，AI 对话会等待档案更新再引用。",
        Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DiaryThoughtParagraph(thought: DiaryThought, onDiaryClick: (Long) -> Unit) {
    var expanded by rememberSaveable(thought.category, thought.statement) { mutableStateOf(false) }
    val diaryCount = thought.evidence.map { it.diaryId }.distinct().size
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(thought.statement, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(thought.basis, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起依据" else "$diaryCount 篇日记 · 查看依据", style = MaterialTheme.typography.labelMedium)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null, modifier = Modifier.size(IconSize.s))
            }
        }
        if (expanded) {
            thought.evidence.forEach { evidence ->
                DiaryThoughtEvidenceLink(evidence, onDiaryClick)
            }
        }
    }
}

@Composable
private fun DiaryThoughtEvidenceLink(evidence: DiaryThoughtEvidence, onDiaryClick: (Long) -> Unit) {
    Surface(
        onClick = { onDiaryClick(evidence.diaryId) },
        shape = RoundedCornerShape(Radius.s),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("“${evidence.quote}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("打开原日记 →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DiaryThoughtCorrections(corrections: String, onEdit: () -> Unit) {
    Surface(onClick = onEdit, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            DiaryThoughtCorrectionSummary(corrections, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "编辑补充与修正", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.m))
        }
    }
}

@Composable
private fun DiaryThoughtCorrectionSummary(corrections: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("我的补充与修正", style = MaterialTheme.typography.titleSmall)
        Text(
            corrections.ifBlank { "补充你的想法，用于下次整理" },
            style = MaterialTheme.typography.bodySmall,
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
