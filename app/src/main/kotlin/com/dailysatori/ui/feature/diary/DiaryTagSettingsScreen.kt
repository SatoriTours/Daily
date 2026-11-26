package com.dailysatori.ui.feature.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.service.diary.DiaryTagMerge
import com.dailysatori.service.diary.cleanDiaryTag
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsActionRow
import com.dailysatori.ui.component.settings.SettingsCard
import com.dailysatori.ui.component.settings.SettingsSectionCard
import com.dailysatori.ui.component.settings.SettingsScaffold as AppScaffold
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun DiaryTagSettingsScreen(onBack: () -> Unit, viewModel: DiaryTagViewModel = koinViewModel()) {
    val i18n: I18nService = koinInject()
    AppScaffold(title = i18n.t("diary_tags.title"), onBack = onBack) { modifier ->
        DiaryTagSettingsContent(viewModel, modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiaryTagSettingsContent(viewModel: DiaryTagViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val i18n: I18nService = koinInject()
    var editing by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf<DiaryTagMerge?>(null) }
    var confirmFill by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        SettingsCard {
            Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(i18n.t("diary_tags.automatic"), style = MaterialTheme.typography.titleSmall)
                    Text(i18n.t("diary_tags.automatic_hint"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(i18n.t("diary_tags.privacy"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(Spacing.m))
                Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled, enabled = !state.busy)
            }
        }
        SettingsSectionCard(i18n.t("settings_design.tag_catalog")) {
            if (state.vocabulary.names.isNotEmpty()) Text(i18n.t("diary_tags.manage_hint"),
                modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.m),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.vocabulary.names.isEmpty()) {
                Text(i18n.t("diary_tags.empty"), modifier = Modifier.padding(Spacing.m),
                    style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                state.vocabulary.names.sortedByDescending { state.counts[it] ?: 0 }.forEach { tag ->
                    AssistChip(onClick = { editing = tag; target = tag }, enabled = !state.busy,
                        label = { Text("$tag · ${state.counts[tag] ?: 0}") })
                }
            }
            if (state.canUndo) TextButton(onClick = viewModel::undoMerge, enabled = !state.busy,
                modifier = Modifier.padding(horizontal = Spacing.s)) { Text(i18n.t("diary_tags.undo")) }
        }
        SettingsCard {
            SettingsActionRow(
                title = i18n.t("diary_tags.fill_missing"),
                description = if (state.missingCount > 0) {
                    i18n.t("diary_tags.fill_missing_count", state.missingCount)
                } else {
                    i18n.t("diary_tags.fill_missing_hint")
                },
                onClick = { confirmFill = true },
                enabled = !state.busy && state.missingCount > 0,
            )
            SettingsActionRow(
                title = i18n.t("diary_tags.synonyms"),
                description = i18n.t("diary_tags.synonyms_hint"),
                onClick = viewModel::suggestMerges,
                enabled = !state.busy && state.vocabulary.names.size > 1,
                trailing = if (state.busy) {
                    { CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = BorderWidth.m) }
                } else null,
                showDivider = state.activeCount > 0 || state.failedCount > 0,
            )
            if (state.activeCount > 0) Text(i18n.t("diary_tags.active", state.activeCount),
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.failedCount > 0) Row(
                Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(i18n.t("diary_tags.failed", state.failedCount), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::retryFailed, enabled = !state.busy) {
                    Text(i18n.t("diary_tags.retry"))
                }
            }
        }
        SettingsSectionCard(i18n.t("diary_tags.suggestions")) {
            if (state.vocabulary.pendingNames.isNotEmpty()) {
                Text(i18n.t("diary_tags.pending_names"),
                    modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.m),
                    style = MaterialTheme.typography.titleSmall)
            }
            state.vocabulary.pendingNames.forEach { name ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { viewModel.approvePending(name) }, enabled = !state.busy) {
                        Text(i18n.t("diary_tags.approve_name"))
                    }
                    TextButton(onClick = { viewModel.dismissPending(name) }, enabled = !state.busy) {
                        Text(i18n.t("diary_tags.dismiss"))
                    }
                }
            }
            state.vocabulary.suggestions.forEach { merge ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Text("${merge.from} → ${merge.to}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { confirming = merge }, enabled = !state.busy) {
                        Text(i18n.t("diary_tags.review"))
                    }
                    TextButton(onClick = { viewModel.dismissSuggestion(merge) }, enabled = !state.busy) {
                        Text(i18n.t("diary_tags.dismiss"))
                    }
                }
            }
            if (state.vocabulary.pendingNames.isEmpty() && state.vocabulary.suggestions.isEmpty()) {
                Text(i18n.t("diary_tags.no_suggestions"), modifier = Modifier.padding(Spacing.m),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.messageKey?.let {
            Text(i18n.t(it), modifier = Modifier.padding(horizontal = Spacing.xs),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Spacing.s))
    }
    editing?.let { source ->
        AlertDialog(
            onDismissRequest = { editing = null },
            shape = RoundedCornerShape(Radius.xl),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = Radius.none,
            title = { Text(i18n.t("diary_tags.merge_title"), style = MaterialTheme.typography.titleMedium.editorial()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(i18n.t("diary_tags.choose_target"), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = target, onValueChange = { target = it }, singleLine = true,
                        label = { Text(i18n.t("diary_tags.target")) })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        state.vocabulary.names.filter { it != source && (target == source || it.contains(target)) }
                            .take(20).forEach { name -> AssistChip(onClick = { target = name }, label = { Text(name) }) }
                    }
                }
            }, confirmButton = {
                TextButton(onClick = {
                    confirming = DiaryTagMerge(source, requireNotNull(cleanDiaryTag(target))); editing = null
                }, enabled = cleanDiaryTag(target)?.let { state.vocabulary.canonical(it) != source } == true) {
                    Text(i18n.t("diary_tags.merge_preview"))
                }
            }, dismissButton = { TextButton(onClick = { editing = null }) { Text(i18n.t("diary_tags.cancel")) } })
    }
    confirming?.let { merge ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            shape = RoundedCornerShape(Radius.xl),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = Radius.none,
            title = { Text(i18n.t("diary_tags.merge_title"), style = MaterialTheme.typography.titleMedium.editorial()) },
            text = { Text(i18n.t("diary_tags.merge_body", merge.from, merge.to, viewModel.mergeImpact(merge.from))) },
            confirmButton = {
                TextButton(onClick = { viewModel.merge(merge.from, merge.to); confirming = null }, enabled = !state.busy) {
                    Text(i18n.t("diary_tags.merge_confirm"))
                }
            }, dismissButton = { TextButton(onClick = { confirming = null }) { Text(i18n.t("diary_tags.cancel")) } })
    }
    if (confirmFill) AlertDialog(
        onDismissRequest = { confirmFill = false },
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = Radius.none,
        title = { Text(i18n.t("diary_tags.fill_confirm"), style = MaterialTheme.typography.titleMedium.editorial()) },
        text = { Text(i18n.t("diary_tags.fill_body", state.missingCount)) },
        confirmButton = { TextButton(onClick = { viewModel.fillMissing(); confirmFill = false }) {
            Text(i18n.t("diary_tags.apply"))
        } },
        dismissButton = { TextButton(onClick = { confirmFill = false }) { Text(i18n.t("diary_tags.cancel")) } })
}
