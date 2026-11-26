package com.dailysatori.ui.feature.settings.hotnews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.service.hotnews.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsScreen
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun HotNewsSettingsScreen(onBack: () -> Unit, viewModel: HotNewsSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val i18n: I18nService = koinInject()
    val uriHandler = LocalUriHandler.current
    var manageX by remember { mutableStateOf(false) }
    if (manageX) {
        BackHandler { manageX = false }
        ExternalFavoritesSettingsScreen(onBack = { manageX = false })
        return
    }
    SettingsScaffold(title = i18n.t("news_sources.title"), onBack = onBack, useGroupNavigation = true) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                i18n.t("news_sources.intro"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
            state.message?.let { message ->
                Surface(
                    shape = RoundedCornerShape(Radius.m),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                    )
                }
            }
            listOf(true, false).forEach { core ->
                val platforms = HotNewsPlatform.entries.filter { it.core == core }
                val enabledCount = platforms.count { state.preferences.source(it).enabled }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            i18n.t(if (core) "news_sources.core" else "news_sources.supplemental"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "$enabledCount / ${platforms.size} 已启用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(Radius.card),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            platforms.forEachIndexed { index, platform ->
                                SourceSettingRow(
                                    platform = platform,
                                    state = state,
                                    viewModel = viewModel,
                                    i18n = i18n,
                                    onManageX = { manageX = true },
                                )
                                if (index < platforms.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Spacing.m),
                                        thickness = BorderWidth.xs,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .clickable { uriHandler.openUri("https://docs.rsshub.app/") }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.xs),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    i18n.t("news_sources.rsshub_docs"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Spacing.s))
        }
    }
    state.editor?.let { platform -> FeedEditor(platform, state, viewModel, i18n) }
}

@Composable
private fun SourceSettingRow(
    platform: HotNewsPlatform,
    state: HotNewsSettingsState,
    viewModel: HotNewsSettingsViewModel,
    i18n: I18nService,
    onManageX: () -> Unit,
) {
    val config = state.preferences.source(platform)
    val actionsEnabled = !state.saving && state.testing == null
    val isTesting = state.testing == platform
    var hintExpanded by remember(platform) { mutableStateOf(false) }
    var hintHasOverflow by remember(platform) { mutableStateOf(false) }

    val hint = when (platform) {
        HotNewsPlatform.X -> "x_hint"
        HotNewsPlatform.XIAOHONGSHU -> "xiaohongshu_hint"
        HotNewsPlatform.LINUX_DO -> "linuxdo_hint"
        HotNewsPlatform.TOUTIAO -> "toutiao_hint"
        HotNewsPlatform.CHINA_NEWS, HotNewsPlatform.IT_HOME -> "supplemental_hint"
        else -> "official_first"
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    platform.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (platform != HotNewsPlatform.X && config.feedUrl.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(Radius.circular),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = "RSS 已配",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                        )
                    }
                }
            }
            Switch(
                checked = config.enabled,
                enabled = actionsEnabled,
                onCheckedChange = { viewModel.setEnabled(platform, it) },
                modifier = Modifier.scale(0.84f),
            )
        }

        Text(
            text = i18n.t("news_sources.$hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (hintExpanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!hintExpanded) hintHasOverflow = it.hasVisualOverflow },
            modifier = if (hintHasOverflow || hintExpanded) {
                Modifier.fillMaxWidth().clickable { hintExpanded = !hintExpanded }
            } else {
                Modifier.fillMaxWidth()
            },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceActionPill(
                icon = if (platform == HotNewsPlatform.X) Icons.Outlined.Key else Icons.Outlined.RssFeed,
                label = i18n.t(when {
                    platform == HotNewsPlatform.X -> "news_sources.x_manage_auth"
                    config.feedUrl.isBlank() -> "news_sources.edit_feed"
                    else -> "news_sources.configured"
                }),
                enabled = actionsEnabled,
                highlighted = platform != HotNewsPlatform.X && config.feedUrl.isNotBlank(),
                onClick = { if (platform == HotNewsPlatform.X) onManageX() else viewModel.edit(platform) },
            )
            SourceActionPill(
                icon = Icons.Outlined.PlayArrow,
                label = i18n.t(if (isTesting) "news_sources.testing" else "news_sources.test"),
                enabled = actionsEnabled,
                loading = isTesting,
                onClick = { viewModel.test(platform) },
            )
        }
    }
}

@Composable
private fun SourceActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    highlighted: Boolean = false,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = when {
        !enabled && !loading -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.circular),
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            BorderWidth.xs,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s + Spacing.xxs, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.xs),
                    strokeWidth = BorderWidth.m,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.xs), tint = contentColor)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FeedEditor(
    platform: HotNewsPlatform,
    state: HotNewsSettingsState,
    viewModel: HotNewsSettingsViewModel,
    i18n: I18nService,
) {
    AlertDialog(
        onDismissRequest = viewModel::closeEditor,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = Radius.none,
        title = { Text(platform.displayName, style = MaterialTheme.typography.titleMedium.editorial()) },
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
