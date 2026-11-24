package com.dailysatori.ui.feature.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.core.diagnostics.performanceContent
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareDialogScreen(
    url: String,
    isUpdate: Boolean = false,
    onBack: () -> Unit = {},
) {
    val viewModel: ShareDialogViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val i18n = koinInject<I18nService>()

    LaunchedEffect(url) {
        viewModel.initialize(url)
    }

    val effectiveUpdate = state.isUpdate || isUpdate
    AppScaffold(
        title = i18n.t(if (effectiveUpdate) "share.update_title" else "share.save_title"),
        onBack = onBack,
        bottomBar = {
            ShareDialogBottomBar(
                isUpdate = effectiveUpdate,
                isSaving = state.isSaving,
                isBusy = state.isSaving || state.isInitializing,
                onCancel = onBack,
                onSave = { viewModel.save { onBack() } },
                i18n = i18n,
            )
        },
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .performanceContent()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            state.error?.takeIf { it.isNotBlank() }?.let { errorKey ->
                Surface(
                    shape = RoundedCornerShape(Radius.m),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = i18n.t(errorKey),
                        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            ShareUrlSection(url = url, aiAnalysis = state.aiAnalysis, onToggleAi = viewModel::toggleAiAnalysis, i18n = i18n)
            ShareTitleSection(title = state.title, onTitleChanged = viewModel::onTitleChanged, i18n = i18n)
            ShareTagsSection(
                tags = state.tags,
                tagInput = state.tagInput,
                onTagInputChanged = viewModel::onTagInputChanged,
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag,
                i18n = i18n,
            )
            ShareCommentSection(comment = state.comment, onCommentChanged = viewModel::onCommentChanged, i18n = i18n)
        }
    }
}

@Composable
private fun ShareDialogBottomBar(
    isUpdate: Boolean,
    isSaving: Boolean,
    isBusy: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    i18n: I18nService,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !isSaving) {
                Text(i18n.t("share.cancel"))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(2f),
                enabled = !isBusy,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(IconSize.m), strokeWidth = 2.dp)
                } else {
                    Text(i18n.t(if (isUpdate) "share.save_changes" else "share.save"))
                }
            }
        }
    }
}

@Composable
private fun ShareUrlSection(
    url: String,
    aiAnalysis: Boolean,
    onToggleAi: () -> Unit,
    i18n: I18nService,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(i18n.t("share.link"), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i18n.t("share.ai_analysis"), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(Spacing.s))
                Switch(checked = aiAnalysis, onCheckedChange = { onToggleAi() })
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.s),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text(
                text = url,
                modifier = Modifier.padding(Spacing.m),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShareTitleSection(title: String, onTitleChanged: (String) -> Unit, i18n: I18nService) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Title, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(i18n.t("share.title"), style = MaterialTheme.typography.labelMedium)
        }
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChanged,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text(i18n.t("share.title_placeholder")) },
            shape = RoundedCornerShape(Radius.s),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareTagsSection(
    tags: List<String>,
    tagInput: String,
    onTagInputChanged: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    i18n: I18nService,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(i18n.t("share.tags"), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(i18n.t("share.optional"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = tagInput,
            onValueChange = onTagInputChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(i18n.t("share.tag_placeholder")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAddTag() }),
            trailingIcon = {
                IconButton(
                    onClick = onAddTag,
                    enabled = tagInput.isNotBlank(),
                    modifier = Modifier.size(Height.touchTarget),
                ) {
                    Icon(Icons.Default.Add, contentDescription = i18n.t("share.add_tag"))
                }
            },
            shape = RoundedCornerShape(Radius.s),
        )
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = i18n.t("share.remove_tag"),
                                modifier = Modifier.size(IconSize.s),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareCommentSection(comment: String, onCommentChanged: (String) -> Unit, i18n: I18nService) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Comment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(i18n.t("share.comment"), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(i18n.t("share.optional"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = comment,
            onValueChange = onCommentChanged,
            modifier = Modifier.fillMaxWidth().height(100.dp),
            placeholder = { Text(i18n.t("share.comment_placeholder")) },
            shape = RoundedCornerShape(Radius.s),
        )
    }
}
