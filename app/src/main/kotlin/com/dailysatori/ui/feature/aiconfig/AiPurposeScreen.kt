package com.dailysatori.ui.feature.aiconfig

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.ai.aiConfigDisplayName
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun AiPurposeScreen(
    onBack: () -> Unit = {},
    onNavigateToAiConfig: () -> Unit = {},
) {
    val viewModel: AiPurposeViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val i18n: I18nService = koinInject()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectingPurpose by remember { mutableStateOf<AiPurpose?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(i18n.t(msg))
            viewModel.clearErrorMessage()
        }
    }

    SettingsScaffold(
        useGroupNavigation = true,
        title = i18n.t("ai_purpose.title"),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { modifier ->
        if (state.configs.isEmpty()) {
            AiPurposeEmptyState(
                modifier = modifier,
                i18n = i18n,
                onNavigateToAiConfig = onNavigateToAiConfig,
            )
        } else {
            AiPurposeListContent(
                modifier = modifier,
                state = state,
                i18n = i18n,
                onSelectPurpose = { selectingPurpose = it },
            )
        }
    }

    selectingPurpose?.let { purpose ->
        AiPurposeSelectionDialog(
            purpose = purpose,
            configs = state.configs,
            assignedId = state.assignments[purpose],
            defaultConfig = state.defaultConfig,
            i18n = i18n,
            onSelect = { configId ->
                viewModel.selectModelForPurpose(purpose, configId)
                selectingPurpose = null
            },
            onDismiss = { selectingPurpose = null },
        )
    }
}

@Composable
private fun AiPurposeEmptyState(
    modifier: Modifier = Modifier,
    i18n: I18nService,
    onNavigateToAiConfig: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.m),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = i18n.t("ai_purpose.no_configs_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        Text(
            text = i18n.t("ai_purpose.no_configs_desc"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.l))
        Button(
            onClick = onNavigateToAiConfig,
            shape = RoundedCornerShape(Radius.l),
        ) {
            Text(i18n.t("ai_purpose.go_to_config"))
        }
    }
}

@Composable
private fun AiPurposeListContent(
    modifier: Modifier = Modifier,
    state: AiPurposeUiState,
    i18n: I18nService,
    onSelectPurpose: (AiPurpose) -> Unit,
) {
    val purposes = listOf(AiPurpose.INTERACTIVE, AiPurpose.EXTERNAL_CONTENT, AiPurpose.REFLECTION)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item {
            Text(
                text = i18n.t("ai_purpose.subtitle"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xs),
            )
        }
        items(purposes, key = { it.id }) { purpose ->
            val assignedId = state.assignments[purpose]
            val assignedConfig = assignedId?.let { id -> state.configs.firstOrNull { it.id == id } }
            AiPurposeCard(
                purpose = purpose,
                assignedConfig = assignedConfig,
                defaultConfig = state.defaultConfig,
                i18n = i18n,
                onClick = { onSelectPurpose(purpose) },
            )
        }
        item {
            Spacer(modifier = Modifier.height(Spacing.m))
        }
    }
}

@Composable
private fun AiPurposeCard(
    purpose: AiPurpose,
    assignedConfig: Ai_config?,
    defaultConfig: Ai_config?,
    i18n: I18nService,
    onClick: () -> Unit,
) {
    val title = purposeTitle(purpose, i18n)
    val description = purposeDescription(purpose, i18n)
    val highlight = purposeHighlight(purpose, i18n)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            BorderWidth.s,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.m),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.m),
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = highlight,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (purpose == AiPurpose.REFLECTION) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = i18n.t("ai_purpose.deep_effect_notice"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.s))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = BorderWidth.xs,
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            AiPurposeCurrentSelectionView(
                assignedConfig = assignedConfig,
                defaultConfig = defaultConfig,
                i18n = i18n,
            )
        }
    }
}

@Composable
private fun AiPurposeCurrentSelectionView(
    assignedConfig: Ai_config?,
    defaultConfig: Ai_config?,
    i18n: I18nService,
) {
    if (assignedConfig != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = aiConfigDisplayName(assignedConfig.provider, assignedConfig.model_name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = assignedConfig.provider.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
            text = formatApiHost(assignedConfig.api_address),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = i18n.t("ai_purpose.follow_default"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = i18n.t("ai_purpose.card_default_fallback"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xxs))
        if (defaultConfig != null) {
            Text(
                text = "${i18n.t("ai_purpose.current_default")}：${aiConfigDisplayName(defaultConfig.provider, defaultConfig.model_name)} (${defaultConfig.provider.uppercase()} · ${formatApiHost(defaultConfig.api_address)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = i18n.t("ai_purpose.card_no_default"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AiPurposeSelectionDialog(
    purpose: AiPurpose,
    configs: List<Ai_config>,
    assignedId: Long?,
    defaultConfig: Ai_config?,
    i18n: I18nService,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = "${i18n.t("ai_purpose.select_model_title")} - ${purposeTitle(purpose, i18n)}"
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Option: Follow Default
                val isDefaultSelected = assignedId == null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { onSelect(null) }
                        .padding(vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isDefaultSelected,
                        onClick = { onSelect(null) },
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = i18n.t("ai_purpose.follow_default"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        val defaultDetail = if (defaultConfig != null) {
                            "${aiConfigDisplayName(defaultConfig.provider, defaultConfig.model_name)} · ${defaultConfig.provider.uppercase()} · ${formatApiHost(defaultConfig.api_address)}"
                        } else {
                            i18n.t("ai_purpose.card_no_default")
                        }
                        Text(
                            text = defaultDetail,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (defaultConfig != null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = BorderWidth.xs,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )

                // Option: Each configured model
                configs.forEach { config ->
                    val isSelected = assignedId == config.id
                    val isDefault = config.is_default == 1L
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onSelect(config.id) }
                            .padding(vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(config.id) },
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = aiConfigDisplayName(config.provider, config.model_name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (isDefault) {
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = "(${i18n.t("ai_purpose.default_badge")})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                text = "${config.provider.uppercase()} · ${formatApiHost(config.api_address)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(i18n.t("button.cancel", "取消"))
            }
        },
    )
}

private fun purposeTitle(purpose: AiPurpose, i18n: I18nService): String = when (purpose) {
    AiPurpose.INTERACTIVE -> i18n.t("ai_purpose.interactive_title")
    AiPurpose.EXTERNAL_CONTENT -> i18n.t("ai_purpose.external_title")
    AiPurpose.REFLECTION -> i18n.t("ai_purpose.reflection_title")
}

private fun purposeDescription(purpose: AiPurpose, i18n: I18nService): String = when (purpose) {
    AiPurpose.INTERACTIVE -> i18n.t("ai_purpose.interactive_desc")
    AiPurpose.EXTERNAL_CONTENT -> i18n.t("ai_purpose.external_desc")
    AiPurpose.REFLECTION -> i18n.t("ai_purpose.reflection_desc")
}

private fun purposeHighlight(purpose: AiPurpose, i18n: I18nService): String = when (purpose) {
    AiPurpose.INTERACTIVE -> i18n.t("ai_purpose.interactive_highlight")
    AiPurpose.EXTERNAL_CONTENT -> i18n.t("ai_purpose.external_highlight")
    AiPurpose.REFLECTION -> i18n.t("ai_purpose.reflection_highlight")
}

internal fun formatApiHost(address: String): String {
    val clean = address.trim()
    if (clean.isBlank()) return ""
    return try {
        val normalized = if (clean.contains("://")) clean else "https://$clean"
        val uri = java.net.URI(normalized)
        val host = uri.host ?: return ""
        if (uri.port != -1 && uri.port != 80 && uri.port != 443) "$host:${uri.port}" else host
    } catch (_: Exception) {
        ""
    }
}
