package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.dailysatori.service.diary.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiaryTagEditorDialog(
    draft: DiaryTagDraft, content: String, vocabulary: DiaryTagVocabulary,
    onChange: (DiaryTagDraft) -> Unit, onGenerate: suspend (String) -> DiaryTagResult,
    onDismiss: () -> Unit, i18n: I18nService = koinInject(),
    initialSelection: String? = null,
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    var replacing by remember { mutableStateOf(initialSelection) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val latestDraft by rememberUpdatedState(draft)
    val latestContent by rememberUpdatedState(content)
    val scope = rememberCoroutineScope()
    fun choose(value: String) {
        val name = cleanDiaryTag(value)?.let(vocabulary::canonical)
        if (name == null) { errorKey = "diary_tags.invalid_name"; return }
        onChange(draft.add(name, replacing))
        input = ""; replacing = null; errorKey = null
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = Spacing.xxl * 12).verticalScroll(rememberScrollState())
                .padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(i18n.t("diary_tags.edit"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onUndo(); replacing = null; input = ""; errorKey = null }, enabled = canUndo) {
                        Text(i18n.t("diary_tags.undo_step"))
                    }
                    TextButton(onClick = onDismiss) { Text(i18n.t("diary_tags.done")) }
                }
                Text(i18n.t("diary_tags.edit_hint"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    draft.tags.forEachIndexed { index, tag ->
                        InputChip(selected = replacing == tag, onClick = { replacing = tag; input = "" },
                            label = { Text((if (index == 0) "★ " else "") + tag + (if (tag in draft.automatic) " · AI" else "")) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    onChange(draft.remove(tag))
                                    if (replacing == tag) { replacing = null; input = "" }
                                }, modifier = Modifier.size(IconSize.l)) {
                                    Icon(Icons.Default.Close, i18n.t("diary_tags.remove"), Modifier.size(IconSize.xs))
                                }
                            })
                    }
                }
                if (replacing != null) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    TextButton(onClick = { replacing?.let { onChange(draft.pin(it)) }; replacing = null; input = "" }) {
                        Text(i18n.t("diary_tags.pin"))
                    }
                    TextButton(onClick = { replacing = null; input = "" }) { Text(i18n.t("diary_tags.cancel_replace")) }
                }
                OutlinedTextField(value = input, onValueChange = { input = it; errorKey = null }, singleLine = true,
                    label = { Text(i18n.t(if (replacing == null) "diary_tags.add" else "diary_tags.replace")) },
                    modifier = Modifier.fillMaxWidth(), trailingIcon = {
                        TextButton(onClick = { choose(input) }, enabled = input.isNotBlank()) { Text(i18n.t("diary_tags.apply")) }
                    })
                Text(i18n.t("diary_tags.existing"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    vocabulary.names.filter { it !in draft.tags && (input.isBlank() || it.contains(input) ||
                        vocabulary.aliases.any { (alias, standard) -> standard == it && alias.contains(input) }) }
                        .take(30).forEach { tag -> AssistChip(onClick = { choose(tag) }, label = { Text(tag) }) }
                }
                HorizontalDivider()
                if (draft.pendingTags.isNotEmpty()) {
                    Text(i18n.t("diary_tags.pending_draft"), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        draft.pendingTags.forEach { name -> AssistChip(onClick = { choose(name) }, label = { Text(name) }) }
                    }
                }
                Text(i18n.t("diary_tags.privacy"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val manualFull = draft.tags.count { it !in draft.automatic } >= DIARY_AUTO_TAG_LIMIT
                if (manualFull) Text(i18n.t("diary_tags.manual_full"), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val requestedDraft = draft
                        val requestedContent = content
                        generating = true; errorKey = null
                        job = scope.launch {
                            try {
                                val result = onGenerate(requestedContent)
                                if (latestDraft == requestedDraft && latestContent == requestedContent) {
                                    onChange(requestedDraft.withGenerated(requestedContent, result))
                                } else errorKey = "diary_tags.result_stale"
                            } catch (cancelled: CancellationException) { throw cancelled
                            } catch (_: Exception) { errorKey = "diary_tags.generate_failed"
                            } finally { generating = false }
                        }
                    }, enabled = !generating && content.isNotBlank() && !manualFull) {
                        Text(i18n.t(if (generating) "diary_tags.generating" else "diary_tags.regenerate"))
                    }
                    if (generating) TextButton(onClick = { job?.cancel() }) { Text(i18n.t("diary_tags.cancel")) }
                }
                errorKey?.let { Text(i18n.t(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
