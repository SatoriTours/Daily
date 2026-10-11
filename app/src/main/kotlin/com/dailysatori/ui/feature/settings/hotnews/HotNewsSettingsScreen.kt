package com.dailysatori.ui.feature.settings.hotnews

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.dailysatori.service.hotnews.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.component.settings.SettingsSectionCard
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun HotNewsSettingsScreen(onBack: () -> Unit, viewModel: HotNewsSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val i18n: I18nService = koinInject()
    val uriHandler = LocalUriHandler.current
    SettingsScaffold(title = i18n.t("news_sources.title"), onBack = onBack, useGroupNavigation = true) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Text(i18n.t("news_sources.intro"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            listOf(true, false).forEach { core ->
                SettingsSectionCard(title = i18n.t(if (core) "news_sources.core" else "news_sources.supplemental")) {
                    HotNewsPlatform.entries.filter { it.core == core }.forEach { platform ->
                        SourceSettingRow(platform, state, viewModel, i18n)
                    }
                }
            }
            TextButton(onClick = { uriHandler.openUri("https://docs.rsshub.app/") }) {
                Text(i18n.t("news_sources.rsshub_docs"))
            }
        }
    }
    state.editor?.let { platform -> FeedEditor(platform, state, viewModel, i18n) }
}

@Composable
private fun SourceSettingRow(platform: HotNewsPlatform, state: HotNewsSettingsState,
    viewModel: HotNewsSettingsViewModel, i18n: I18nService) {
    val config = state.preferences.source(platform)
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(platform.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(checked = config.enabled, enabled = !state.saving && state.testing == null,
                onCheckedChange = { viewModel.setEnabled(platform, it) })
        }
        val hint = when (platform) {
            HotNewsPlatform.X -> "x_hint"
            HotNewsPlatform.XIAOHONGSHU -> "xiaohongshu_hint"
            HotNewsPlatform.LINUX_DO -> "linuxdo_hint"
            HotNewsPlatform.TOUTIAO -> "toutiao_hint"
            HotNewsPlatform.CHINA_NEWS, HotNewsPlatform.IT_HOME -> "supplemental_hint"
            else -> "official_first"
        }
        Text(i18n.t("news_sources.$hint"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TextButton(onClick = { viewModel.edit(platform) }, enabled = !state.saving && state.testing == null) {
                Text(i18n.t(if (config.feedUrl.isBlank()) "news_sources.edit_feed" else "news_sources.configured"))
            }
            TextButton(onClick = { viewModel.test(platform) }, enabled = !state.saving && state.testing == null) {
                Text(i18n.t(if (state.testing == platform) "news_sources.testing" else "news_sources.test"))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun FeedEditor(platform: HotNewsPlatform, state: HotNewsSettingsState,
    viewModel: HotNewsSettingsViewModel, i18n: I18nService) {
    AlertDialog(onDismissRequest = viewModel::closeEditor,
        title = { Text(platform.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(i18n.t("news_sources.feed_hint"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = state.feedUrlDraft, onValueChange = viewModel::updateFeedUrl,
                    label = { Text(i18n.t("news_sources.feed_address")) }, modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving, isError = state.editorError != null)
                state.editorError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { viewModel.updateFeedUrl(platform.defaultFeedUrl) }, enabled = !state.saving) {
                    Text(i18n.t("news_sources.defaults"))
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveFeedUrl, enabled = !state.saving) { Text(i18n.t("button.save")) } },
        dismissButton = { TextButton(onClick = viewModel::closeEditor, enabled = !state.saving) { Text(i18n.t("button.cancel")) } },
    )
}
