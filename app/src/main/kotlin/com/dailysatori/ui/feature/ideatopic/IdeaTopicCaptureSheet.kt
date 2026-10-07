package com.dailysatori.ui.feature.ideatopic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.ideatopic.IdeaCaptureInput
import com.dailysatori.service.ideatopic.IdeaSourceSnapshot
import com.dailysatori.service.ideatopic.IdeaTopicContent
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaTopicCaptureSheet(
    source: IdeaSourceSnapshot,
    initialContent: IdeaTopicContent,
    onCaptured: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: IdeaTopicCaptureViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()

    LaunchedEffect(source.key) {
        viewModel.lookupExisting(source.key)
    }

    LaunchedEffect(source.key, state.capturedTopicId) {
        if (state.sourceKey == source.key) viewModel.consumeCapturedTopicId()?.let(onCaptured)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m)
                .padding(bottom = Spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.m))
                Text(
                    text = i18n.t("idea_topic.capture_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Already captured notice
            val existingId = state.existingTopicId
            if (existingId != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(Radius.m),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.s))
                            Text(
                                text = i18n.t("idea_topic.capture_already_captured"),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            text = i18n.t("idea_topic.capture_existing_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(i18n.t("idea_topic.action_cancel"))
                            }
                            Spacer(Modifier.width(Spacing.s))
                            Button(onClick = { onCaptured(existingId) }) {
                                Text(i18n.t("idea_topic.capture_view_topic"))
                            }
                        }
                    }
                }
                return@ModalBottomSheet
            }

            // Error display
            if (state.error != null) {
                Text(
                    text = i18n.t(ideaTopicErrorLabelKey(state.error!!)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Mode selection: New vs Existing
            var isNewMode by rememberSaveable { mutableStateOf(true) }
            var selectedExistingTargetId by rememberSaveable { mutableStateOf(state.existingTopics.firstOrNull()?.id.orEmpty()) }

            LaunchedEffect(state.existingTopics) {
                if (selectedExistingTargetId.isBlank() && state.existingTopics.isNotEmpty()) {
                    selectedExistingTargetId = state.existingTopics.first().id
                }
            }

            if (state.existingTopics.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    FilterChip(
                        selected = isNewMode,
                        onClick = { isNewMode = true },
                        label = { Text(i18n.t("idea_topic.capture_mode_new")) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = !isNewMode,
                        onClick = { isNewMode = false },
                        label = { Text(i18n.t("idea_topic.capture_mode_existing")) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            var title by rememberSaveable { mutableStateOf(initialContent.title) }
            var description by rememberSaveable { mutableStateOf(initialContent.description) }

            if (!isNewMode && state.existingTopics.isNotEmpty()) {
                // Select existing topic
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = i18n.t("idea_topic.capture_select_topic"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.existingTopics.forEach { topic ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.s))
                                .clickable { selectedExistingTargetId = topic.id }
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedExistingTargetId == topic.id,
                                onClick = { selectedExistingTargetId = topic.id },
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = topic.content.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Editable title & description
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(i18n.t("idea_topic.capture_title_label")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(i18n.t("idea_topic.capture_desc_label")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            // Submit Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !state.busy) {
                    Text(i18n.t("idea_topic.action_cancel"))
                }
                Spacer(Modifier.width(Spacing.s))
                Button(
                    onClick = {
                        val input = IdeaCaptureInput(
                            source = source,
                            content = initialContent.copy(
                                title = title.trim(),
                                description = description.trim(),
                            ),
                            targetTopicId = if (isNewMode) null else selectedExistingTargetId.ifBlank { null },
                        )
                        viewModel.submit(input)
                    },
                    enabled = title.isNotBlank() && !state.busy && (isNewMode || selectedExistingTargetId.isNotBlank()),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = BorderWidth.s)
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    Text(i18n.t("idea_topic.capture_submit"))
                }
            }
        }
    }
}
