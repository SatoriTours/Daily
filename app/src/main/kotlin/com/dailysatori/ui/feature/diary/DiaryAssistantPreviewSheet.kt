package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.service.diary.DiaryAssistantExtraction
import com.dailysatori.service.diary.DiaryAssistantResult
import com.dailysatori.service.diary.DiaryAssistantVerification
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

private const val DiaryAssistantPreviewMaxHeightFraction = 0.66f

internal sealed interface DiaryAssistantPreviewState {
    val snapshot: DiaryAssistantSelectionSnapshot
    val url: String?

    data class Loading(
        override val snapshot: DiaryAssistantSelectionSnapshot,
        override val url: String?,
        val allowModelKnowledgeFallback: Boolean,
    ) : DiaryAssistantPreviewState

    data class FallbackConsent(
        override val snapshot: DiaryAssistantSelectionSnapshot,
        override val url: String?,
    ) : DiaryAssistantPreviewState

    data class Failure(
        override val snapshot: DiaryAssistantSelectionSnapshot,
        override val url: String?,
        val allowModelKnowledgeFallback: Boolean,
        val message: String,
    ) : DiaryAssistantPreviewState

    data class Ready(
        override val snapshot: DiaryAssistantSelectionSnapshot,
        override val url: String?,
        val allowModelKnowledgeFallback: Boolean,
        val result: DiaryAssistantResult,
        val draft: String,
    ) : DiaryAssistantPreviewState
}

@Composable
internal fun DiaryAssistantPreviewSheet(
    state: DiaryAssistantPreviewState,
    canReplaceSelection: Boolean,
    onDraftChange: (String) -> Unit,
    onRetry: () -> Unit,
    onAllowFallback: () -> Unit,
    onCancel: () -> Unit,
    onInsert: () -> Unit,
    onReplace: () -> Unit,
) {
    val sizeModifier = if (state is DiaryAssistantPreviewState.Ready) {
        Modifier.fillMaxHeight(DiaryAssistantPreviewMaxHeightFraction)
    } else {
        Modifier
    }
    Surface(
        modifier = sizeModifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        when (state) {
            is DiaryAssistantPreviewState.Loading -> DiaryAssistantLoadingContent(onCancel)
            is DiaryAssistantPreviewState.FallbackConsent -> DiaryAssistantFallbackContent(onAllowFallback, onCancel)
            is DiaryAssistantPreviewState.Failure -> DiaryAssistantFailureContent(state.message, onRetry, onCancel)
            is DiaryAssistantPreviewState.Ready -> DiaryAssistantReadyContent(
                state = state,
                canReplaceSelection = canReplaceSelection,
                onDraftChange = onDraftChange,
                onRetry = onRetry,
                onCancel = onCancel,
                onInsert = onInsert,
                onReplace = onReplace,
            )
        }
    }
}

@Composable
private fun DiaryAssistantLoadingContent(onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text("正在整理内容…", modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable
private fun DiaryAssistantFallbackContent(onAllowFallback: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("未找到可核实来源", style = MaterialTheme.typography.titleSmall)
        Text("可以继续使用模型已有知识生成，但内容将标记为“未联网查证”。")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("取消") }
            TextButton(onClick = onAllowFallback) { Text("同意并继续") }
        }
    }
}

@Composable
private fun DiaryAssistantFailureContent(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("生成失败", style = MaterialTheme.typography.titleSmall)
        Text(message, color = MaterialTheme.colorScheme.error)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("取消") }
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun DiaryAssistantReadyContent(
    state: DiaryAssistantPreviewState.Ready,
    canReplaceSelection: Boolean,
    onDraftChange: (String) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onInsert: () -> Unit,
    onReplace: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        DiaryAssistantPreviewHeader(onRetry, onCancel)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            DiaryAssistantStatus(state.result)
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                label = { Text("补充内容（可编辑）") },
            )
            DiaryAssistantSources(state.result)
            if (!canReplaceSelection) {
                Text(
                    "原文已更改，不能替换；仍可插入当前光标。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DiaryAssistantEditActions(
            canReplaceSelection = canReplaceSelection,
            enabled = state.draft.isNotBlank(),
            onInsert = onInsert,
            onReplace = onReplace,
        )
    }
}

@Composable
private fun DiaryAssistantPreviewHeader(onRetry: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("AI 补充预览", style = MaterialTheme.typography.titleMedium)
        Row {
            TextButton(onClick = onRetry) { Text("重试") }
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

@Composable
private fun DiaryAssistantStatus(result: DiaryAssistantResult) {
    val status = when (result.verification) {
        DiaryAssistantVerification.WEB_VERIFIED -> "已联网查证"
        DiaryAssistantVerification.MODEL_ONLY -> "未联网查证"
        DiaryAssistantVerification.PAGE_EXTRACTED -> when (result.extraction) {
            DiaryAssistantExtraction.FULL_TEXT -> "已提取网页全文"
            DiaryAssistantExtraction.PUBLIC_METADATA -> "仅提取公开信息"
            DiaryAssistantExtraction.NO_SUBTITLES -> "未获取字幕"
            null -> "已提取网页内容"
        }
    }
    Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun DiaryAssistantSources(result: DiaryAssistantResult) {
    if (result.sources.isNotEmpty()) {
        Text("来源", style = MaterialTheme.typography.titleSmall)
        result.sources.forEach { source ->
            Text(
                "${source.title}\n${source.url}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    result.warnings.forEach { warning ->
        Text(
            "提醒：$warning",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DiaryAssistantEditActions(
    canReplaceSelection: Boolean,
    enabled: Boolean,
    onInsert: () -> Unit,
    onReplace: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
    ) {
        OutlinedButton(onClick = onReplace, enabled = enabled && canReplaceSelection) { Text("替换原文") }
        Button(onClick = onInsert, enabled = enabled) {
            Text(if (canReplaceSelection) "插入到原文后" else "插入当前光标")
        }
    }
}
