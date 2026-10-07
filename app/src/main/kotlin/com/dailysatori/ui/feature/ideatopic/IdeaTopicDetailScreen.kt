package com.dailysatori.ui.feature.ideatopic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.ideatopic.*
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun IdeaTopicDetailScreen(
    id: String,
    onBack: () -> Unit,
    onSession: (String, String) -> Unit,
    onSource: (IdeaSourceSnapshot) -> Unit = {},
    viewModel: IdeaTopicDetailViewModel = koinViewModel(parameters = { parametersOf(id) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    var showEditContentDialog by rememberSaveable { mutableStateOf(false) }
    var showProgressDialog by rememberSaveable { mutableStateOf(false) }
    var showMergeDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showNewSessionDialog by rememberSaveable { mutableStateOf(false) }
    var viewingSnapshot by remember { mutableStateOf<IdeaSourceSnapshot?>(null) }
    var editingDraftPreview by remember { mutableStateOf<IdeaDraftPreview?>(null) }

    AppScaffold(
        title = state.detail?.topic?.content?.title?.ifBlank { i18n.t("idea_topic.title") } ?: i18n.t("idea_topic.title"),
        onBack = onBack,
    ) { modifier ->
        if (state.deleted) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(Spacing.xxl))
                    Text(i18n.t("idea_topic.deleted_notice"), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onBack) { Text(i18n.t("idea_topic.action_cancel")) }
                }
            }
            return@AppScaffold
        }

        val detail = state.detail
        if (detail == null) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@AppScaffold
        }

        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Merged banner
            if (state.mergedInto != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    shape = RoundedCornerShape(Radius.m),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Icon(Icons.Default.Merge, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(IconSize.m))
                        Text(
                            text = i18n.t("idea_topic.merged_redirect_notice", detail.topic.content.title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Error banner
            if (state.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    shape = RoundedCornerShape(Radius.m),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(
                            text = i18n.t("idea_topic.error.${state.error!!.name.lowercase()}"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::dismissError) {
                            Text(i18n.t("idea_topic.action_cancel"), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(i18n.t("idea_topic.tab_overview")) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(i18n.t("idea_topic.tab_provenance")) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(i18n.t("idea_topic.tab_conversations")) },
                )
            }

            // Tab content
            when (selectedTab) {
                0 -> OverviewTabContent(
                    detail = detail,
                    state = state,
                    onEditContent = { showEditContentDialog = true },
                    onAppendProgress = { showProgressDialog = true },
                    onMerge = { showMergeDialog = true },
                    onDelete = { showDeleteDialog = true },
                    onPropose = viewModel::propose,
                    onCancelAi = viewModel::cancel,
                    onStatusChange = viewModel::setStatus,
                    onEditDraft = { preview -> editingDraftPreview = preview },
                    onDiscardDraft = viewModel::discardDraft,
                )
                1 -> ProvenanceTabContent(
                    detail = detail,
                    state = state,
                    onSourceClick = { snapshot ->
                        viewingSnapshot = snapshot
                        onSource(snapshot)
                    },
                )
                2 -> ConversationsTabContent(
                    detail = detail,
                    state = state,
                    onNewSession = { showNewSessionDialog = true },
                    onOpenSession = { sessionId ->
                        val targetTopicId = state.topicId ?: id
                        onSession(targetTopicId, sessionId)
                    },
                )
            }
        }
    }

    // Dialogs
    if (showEditContentDialog && state.detail != null) {
        EditContentDialog(
            initial = state.detail!!.topic.content,
            onDismiss = { showEditContentDialog = false },
            onConfirm = { updated ->
                viewModel.updateContent(updated)
                showEditContentDialog = false
            },
        )
    }

    if (showProgressDialog) {
        AppendProgressDialog(
            onDismiss = { showProgressDialog = false },
            onConfirm = { text ->
                viewModel.appendProgress(text)
                showProgressDialog = false
            },
        )
    }

    if (showMergeDialog) {
        MergeTopicDialog(
            candidates = state.mergeCandidates,
            onDismiss = { showMergeDialog = false },
            onConfirm = { targetId ->
                viewModel.merge(targetId)
                showMergeDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteTopicDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.delete()
                showDeleteDialog = false
            },
        )
    }

    if (showNewSessionDialog) {
        NewSessionDialog(
            onDismiss = { showNewSessionDialog = false },
            onConfirm = { title ->
                showNewSessionDialog = false
                viewModel.createSession(title) { sessionId ->
                    val targetTopicId = state.topicId ?: id
                    onSession(targetTopicId, sessionId)
                }
            },
        )
    }

    viewingSnapshot?.let { snapshot ->
        IdeaSourceDetailDialog(
            snapshot = snapshot,
            onDismiss = { viewingSnapshot = null },
        )
    }

    editingDraftPreview?.let { preview ->
        EditDraftDialog(
            preview = preview,
            onDismiss = { editingDraftPreview = null },
            onApply = { editedContent ->
                viewModel.applyDraft(preview.draftId, editedContent)
                editingDraftPreview = null
            },
            onDiscard = {
                viewModel.discardDraft(preview.draftId)
                editingDraftPreview = null
            },
        )
    }
}

// ---------- Tab 1: Overview ----------

@Composable
private fun OverviewTabContent(
    detail: IdeaTopicDetail,
    state: IdeaTopicDetailState,
    onEditContent: () -> Unit,
    onAppendProgress: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onPropose: () -> Unit,
    onCancelAi: () -> Unit,
    onStatusChange: (IdeaTopicStatus) -> Unit,
    onEditDraft: (IdeaDraftPreview) -> Unit,
    onDiscardDraft: (String) -> Unit,
) {
    val i18n: I18nService = koinInject()
    val content = detail.topic.content

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        // Status & Lifecycle Actions
        item(key = "status-actions") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.m),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(i18n.t("idea_topic.status_label"), style = MaterialTheme.typography.labelLarge)
                        StatusDropdownSelector(current = detail.topic.status, onSelected = onStatusChange)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        OutlinedButton(
                            onClick = onEditContent,
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy,
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(IconSize.s))
                            Spacer(Modifier.width(Spacing.xs))
                            Text(i18n.t("idea_topic.action_edit"))
                        }
                        Button(
                            onClick = onAppendProgress,
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy,
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(IconSize.s))
                            Spacer(Modifier.width(Spacing.xs))
                            Text(i18n.t("idea_topic.append_progress"))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        if (state.busy) {
                            Button(
                                onClick = onCancelAi,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text(i18n.t("idea_topic.action_cancel_ai"))
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onPropose,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(IconSize.s))
                                Spacer(Modifier.width(Spacing.xs))
                                Text(i18n.t("idea_topic.action_propose"))
                            }
                        }

                        OutlinedButton(
                            onClick = onMerge,
                            modifier = Modifier.weight(1f),
                            enabled = state.canMergeOrDelete && state.mergeCandidates.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Merge, null, Modifier.size(IconSize.s))
                            Spacer(Modifier.width(Spacing.xs))
                            Text(i18n.t("idea_topic.action_merge"))
                        }
                    }

                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.End),
                        enabled = state.canMergeOrDelete,
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(i18n.t("idea_topic.action_delete"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Pending Drafts
        val pendingDrafts = state.draftPreviews.filter { it.state == IdeaDraftState.Pending }
        if (pendingDrafts.isNotEmpty()) {
            items(pendingDrafts, key = { it.draftId }) { preview ->
                DraftPreviewCard(
                    preview = preview,
                    onEdit = { onEditDraft(preview) },
                    onDiscard = { onDiscardDraft(preview.draftId) },
                )
            }
        }

        // Official Content Fields
        item(key = "content-fields") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.m),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    ContentFieldRow(i18n.t("idea_topic.overview_title"), content.title)
                    if (content.description.isNotBlank()) {
                        ContentFieldRow(i18n.t("idea_topic.overview_description"), content.description)
                    }
                    if (content.provenanceSummary.isNotBlank()) {
                        ContentFieldRow(i18n.t("idea_topic.overview_provenance_summary"), content.provenanceSummary)
                    }
                    if (content.conclusions.isNotBlank()) {
                        ContentFieldRow(i18n.t("idea_topic.overview_conclusions"), content.conclusions)
                    }
                    if (content.nextAction.isNotBlank()) {
                        ContentFieldRow(i18n.t("idea_topic.overview_next_action"), content.nextAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentFieldRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusDropdownSelector(
    current: IdeaTopicStatus,
    onSelected: (IdeaTopicStatus) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val i18n: I18nService = koinInject()

    Box {
        Surface(
            shape = RoundedCornerShape(Radius.s),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = i18n.t(ideaTopicStatusLabelKey(current)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IdeaTopicStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(i18n.t(ideaTopicStatusLabelKey(status))) },
                    onClick = {
                        expanded = false
                        if (status != current) onSelected(status)
                    },
                )
            }
        }
    }
}

@Composable
private fun DraftPreviewCard(
    preview: IdeaDraftPreview,
    onEdit: () -> Unit,
    onDiscard: () -> Unit,
) {
    val i18n: I18nService = koinInject()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(IconSize.m))
                    Text(
                        i18n.t("idea_topic.draft_title"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(Radius.s),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = i18n.t("idea_topic.draft_pending"),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                    )
                }
            }

            if (preview.changes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        text = i18n.t("idea_topic.draft_changes"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Medium,
                    )
                    preview.changes.forEach { change ->
                        Text(
                            text = "• ${draftFieldLabel(change.field, i18n)}: ${change.after.take(40)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDiscard) {
                    Text(i18n.t("idea_topic.draft_discard"), color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.width(Spacing.s))
                Button(onClick = onEdit) {
                    Text(i18n.t("idea_topic.draft_preview"))
                }
            }
        }
    }
}

private fun draftFieldLabel(field: String, i18n: I18nService): String = when (field) {
    "title" -> i18n.t("idea_topic.draft_field_title")
    "description" -> i18n.t("idea_topic.draft_field_description")
    "provenanceSummary" -> i18n.t("idea_topic.draft_field_provenance")
    "conclusions" -> i18n.t("idea_topic.draft_field_conclusions")
    "nextAction" -> i18n.t("idea_topic.draft_field_next_action")
    else -> field
}

// ---------- Tab 2: Provenance & History ----------

@Composable
private fun ProvenanceTabContent(
    detail: IdeaTopicDetail,
    state: IdeaTopicDetailState,
    onSourceClick: (IdeaSourceSnapshot) -> Unit,
) {
    val i18n: I18nService = koinInject()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        // Linked Sources Header
        item(key = "sources-header") {
            Text(
                text = i18n.t("idea_topic.sources_header") + " (${detail.sources.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Sources list
        items(detail.sources, key = { it.id }) { source ->
            SourceCard(
                source = source,
                onClick = { onSourceClick(source.snapshot) },
            )
        }

        // Events Header
        item(key = "events-header") {
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = i18n.t("idea_topic.events_header"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Event timeline items
        items(detail.events, key = { it.id }) { event ->
            val presentation = formatIdeaTopicEvent(event, state.topicId)
            EventTimelineItem(presentation = presentation)
        }
    }
}

@Composable
private fun SourceCard(
    source: IdeaTopicSource,
    onClick: () -> Unit,
) {
    val i18n: I18nService = koinInject()
    val typeLabel = if (source.snapshot.key.type == IdeaSourceTypes.Diary) {
        i18n.t("idea_topic.source_type_diary")
    } else {
        i18n.t("idea_topic.source_type_opportunity")
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.s),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                    )
                }
                Text(
                    text = formatDateTime(source.capturedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = source.snapshot.originalTitle.ifBlank { "未命名来源" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = source.snapshot.originalContent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EventTimelineItem(presentation: IdeaEventPresentation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatDateTime(presentation.timeMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = presentation.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (presentation.originalAttribution != null) {
                Text(
                    text = presentation.originalAttribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

// ---------- Tab 3: Conversations ----------

@Composable
private fun ConversationsTabContent(
    detail: IdeaTopicDetail,
    state: IdeaTopicDetailState,
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val i18n: I18nService = koinInject()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "new-session-button") {
            Button(
                onClick = onNewSession,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(IconSize.m))
                Spacer(Modifier.width(Spacing.s))
                Text(i18n.t("idea_topic.new_session"))
            }
        }

        if (detail.sessions.isEmpty()) {
            item(key = "empty-sessions") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = i18n.t("idea_topic.session_messages_empty"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(detail.sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    onClick = { onOpenSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: IdeaTopicSession,
    onClick: () -> Unit,
) {
    val i18n: I18nService = koinInject()

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.m))
                    Text(
                        text = session.title.ifBlank { i18n.t("idea_topic.new_session") },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = formatDateTime(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (session.summary.isNotBlank()) {
                Text(
                    text = session.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (session.summaryStatus == IdeaSessionSummaryStatus.NeedsUpdate) {
                Surface(
                    shape = RoundedCornerShape(Radius.s),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = i18n.t("idea_topic.session_summary_needs_update"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                    )
                }
            }
        }
    }
}

// ---------- Dialogs ----------

@Composable
private fun EditContentDialog(
    initial: IdeaTopicContent,
    onDismiss: () -> Unit,
    onConfirm: (IdeaTopicContent) -> Unit,
) {
    val i18n: I18nService = koinInject()
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var provenanceSummary by rememberSaveable { mutableStateOf(initial.provenanceSummary) }
    var conclusions by rememberSaveable { mutableStateOf(initial.conclusions) }
    var nextAction by rememberSaveable { mutableStateOf(initial.nextAction) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.t("idea_topic.action_edit")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(i18n.t("idea_topic.overview_title")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(i18n.t("idea_topic.overview_description")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = provenanceSummary,
                    onValueChange = { provenanceSummary = it },
                    label = { Text(i18n.t("idea_topic.overview_provenance_summary")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = conclusions,
                    onValueChange = { conclusions = it },
                    label = { Text(i18n.t("idea_topic.overview_conclusions")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = nextAction,
                    onValueChange = { nextAction = it },
                    label = { Text(i18n.t("idea_topic.overview_next_action")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        IdeaTopicContent(
                            title = title.trim(),
                            description = description.trim(),
                            provenanceSummary = provenanceSummary.trim(),
                            conclusions = conclusions.trim(),
                            nextAction = nextAction.trim(),
                        )
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text(i18n.t("idea_topic.action_save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(i18n.t("idea_topic.action_cancel"))
            }
        },
    )
}

@Composable
private fun AppendProgressDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val i18n: I18nService = koinInject()
    var text by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.t("idea_topic.append_progress")) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(i18n.t("idea_topic.progress_input_hint")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(i18n.t("idea_topic.action_save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(i18n.t("idea_topic.action_cancel"))
            }
        },
    )
}

@Composable
private fun MergeTopicDialog(
    candidates: List<IdeaTopicSummary>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val i18n: I18nService = koinInject()
    var selectedTargetId by rememberSaveable { mutableStateOf(candidates.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.t("idea_topic.merge_title")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    text = i18n.t("idea_topic.merge_notice"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = i18n.t("idea_topic.merge_select_target"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.s))
                            .clickable { selectedTargetId = candidate.id }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedTargetId == candidate.id,
                            onClick = { selectedTargetId = candidate.id },
                        )
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            text = candidate.content.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedTargetId) },
                enabled = selectedTargetId.isNotBlank(),
            ) {
                Text(i18n.t("idea_topic.merge_confirm"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(i18n.t("idea_topic.action_cancel"))
            }
        },
    )
}

@Composable
private fun ConfirmDeleteTopicDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val i18n: I18nService = koinInject()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.t("idea_topic.delete_title")) },
        text = {
            Text(
                text = i18n.t("idea_topic.delete_message"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(i18n.t("idea_topic.delete_confirm"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(i18n.t("idea_topic.action_cancel"))
            }
        },
    )
}

@Composable
private fun NewSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val i18n: I18nService = koinInject()
    var title by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.t("idea_topic.new_session")) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(i18n.t("idea_topic.session_title_hint")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(title.trim()) }) {
                Text(i18n.t("idea_topic.action_save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(i18n.t("idea_topic.action_cancel"))
            }
        },
    )
}

@Composable
internal fun IdeaSourceDetailDialog(
    snapshot: IdeaSourceSnapshot,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val i18n: I18nService = koinInject()
    val isDiary = snapshot.key.type == IdeaSourceTypes.Diary
    val hasUrl = snapshot.originalUrl?.startsWith("http://") == true || snapshot.originalUrl?.startsWith("https://") == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(snapshot.originalTitle.ifBlank { if (isDiary) "日记来源" else "新闻来源" }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                // Link availability or snapshot notice
                if (hasUrl) {
                    TextButton(onClick = { openArticleUrl(context, snapshot.originalUrl) }) {
                        Text("打开原始链接")
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(Radius.s),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = i18n.t("idea_topic.source_unavailable"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.s),
                        )
                    }
                }

                // Original Content Snapshot
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = i18n.t("idea_topic.source_snapshot_title"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = snapshot.originalContent,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // AI Analysis Snapshot (if present, displayed separately)
                val analysis = snapshot.analysisContent
                if (!analysis.isNullOrBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = i18n.t("idea_topic.source_analysis_title"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = analysis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(i18n.t("idea_topic.action_cancel"))
            }
        },
    )
}

@Composable
private fun EditDraftDialog(
    preview: IdeaDraftPreview,
    onDismiss: () -> Unit,
    onApply: (IdeaTopicContent) -> Unit,
    onDiscard: () -> Unit,
) {
    val i18n: I18nService = koinInject()
    val initialProposal = preview.proposal?.content ?: IdeaTopicContent()

    var title by rememberSaveable { mutableStateOf(initialProposal.title) }
    var description by rememberSaveable { mutableStateOf(initialProposal.description) }
    var provenanceSummary by rememberSaveable { mutableStateOf(initialProposal.provenanceSummary) }
    var conclusions by rememberSaveable { mutableStateOf(initialProposal.conclusions) }
    var nextAction by rememberSaveable { mutableStateOf(initialProposal.nextAction) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.t("idea_topic.draft_preview")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(i18n.t("idea_topic.draft_field_title")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(i18n.t("idea_topic.draft_field_description")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = provenanceSummary,
                    onValueChange = { provenanceSummary = it },
                    label = { Text(i18n.t("idea_topic.draft_field_provenance")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = conclusions,
                    onValueChange = { conclusions = it },
                    label = { Text(i18n.t("idea_topic.draft_field_conclusions")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = nextAction,
                    onValueChange = { nextAction = it },
                    label = { Text(i18n.t("idea_topic.draft_field_next_action")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        IdeaTopicContent(
                            title = title.trim(),
                            description = description.trim(),
                            provenanceSummary = provenanceSummary.trim(),
                            conclusions = conclusions.trim(),
                            nextAction = nextAction.trim(),
                        )
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text(i18n.t("idea_topic.draft_apply"))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) {
                    Text(i18n.t("idea_topic.draft_discard"), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text(i18n.t("idea_topic.action_cancel"))
                }
            }
        },
    )
}
