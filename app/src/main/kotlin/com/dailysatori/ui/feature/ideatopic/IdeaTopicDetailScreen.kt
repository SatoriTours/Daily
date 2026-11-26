package com.dailysatori.ui.feature.ideatopic

import com.dailysatori.core.diagnostics.performanceTab
import com.dailysatori.service.diagnostics.PerformanceResult
import com.dailysatori.service.diagnostics.PerformanceScreen
import com.dailysatori.core.diagnostics.performanceContent
import com.dailysatori.ui.component.appbar.PageAction

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    var selectedTab by rememberSaveable(id) { mutableIntStateOf(0) }
    var showResources by rememberSaveable(id) { mutableStateOf(false) }
    val discussionStateHolder = rememberSaveableStateHolder()

    var showEditContentDialog by rememberSaveable { mutableStateOf(false) }
    var showProgressDialog by rememberSaveable { mutableStateOf(false) }
    var showMergeDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showNewSessionDialog by rememberSaveable { mutableStateOf(false) }
    var viewingSnapshot by remember { mutableStateOf<IdeaSourceSnapshot?>(null) }
    var editingDraftPreview by remember { mutableStateOf<IdeaDraftPreview?>(null) }

    AppScaffold(
        title = state.detail?.topic?.content?.title?.ifBlank { i18n.t("idea_topic.title") } ?: i18n.t("idea_topic.title"),
        onBack = { if (showResources) showResources = false else onBack() },
        pageActions = if (state.detail != null && !state.deleted) listOf(
            PageAction(i18n.t(if (showResources) "idea_topic.back_to_discussion" else "idea_topic.topic_materials"),
                Icons.Outlined.Info) { showResources = !showResources },
        ) else emptyList(),
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
            modifier = modifier.fillMaxSize().performanceContent()
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
                            text = i18n.t("idea_topic.merged_redirect_notice", *arrayOf(detail.topic.content.title)),
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
                            text = i18n.t(ideaTopicErrorLabelKey(state.error!!)),
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

            if (!showResources) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatusDropdownSelector(current = detail.topic.status, onSelected = viewModel::setStatus)
                    TextButton(onClick = { selectedTab = 2; showResources = true }) {
                        Text(i18n.t("idea_topic.discussion_history"))
                    }
                }
                val mainId = state.topicId ?: id
                val discussionViewModel: IdeaTopicSessionViewModel = koinViewModel(
                    key = "topic-discussion:$mainId", parameters = { parametersOf(mainId) },
                )
                discussionStateHolder.SaveableStateProvider("topic:$mainId") {
                    IdeaTopicDiscussionContent("topic:$mainId", discussionViewModel, Modifier.weight(1f))
                }
            } else {
                // Existing materials and historical sessions remain accessible off the main discussion.
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(thickness = BorderWidth.xs, color = MaterialTheme.colorScheme.outline) },
                ) {
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
                        text = { Text(i18n.t("idea_topic.discussion_history")) },
                    )
                }

                val tab = when (selectedTab) {
                    0 -> PerformanceScreen.IDEA_OVERVIEW
                    1 -> PerformanceScreen.IDEA_PROVENANCE
                    else -> PerformanceScreen.IDEA_HISTORY
                }
                Box(Modifier.weight(1f).fillMaxWidth().performanceTab(tab, PerformanceResult.OK)) {
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
                            onSourceClick = { snapshot -> viewingSnapshot = snapshot },
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
            onOpenOriginal = onSource,
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
    var showOverflow by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        // Content-first Editorial Brief Card
        item(key = "content-fields") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    // Compact meta bar: status selector on left, quiet edit & overflow actions on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDropdownSelector(current = detail.topic.status, onSelected = onStatusChange)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = onEditContent,
                            enabled = !state.busy,
                            modifier = Modifier.size(Height.buttonSmall),
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = i18n.t("idea_topic.action_edit"),
                                modifier = Modifier.size(IconSize.s),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                enabled = !state.busy,
                                modifier = Modifier.size(Height.buttonSmall),
                            ) {
                                Icon(
                                    MoreActionsIcon,
                                    contentDescription = i18n.t("idea_topic.action_edit"),
                                    modifier = Modifier.size(IconSize.s),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                                DropdownMenuItem(
                                    text = { Text(i18n.t("idea_topic.action_edit")) },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(IconSize.s)) },
                                    enabled = !state.busy,
                                    onClick = { showOverflow = false; onEditContent() },
                                )
                                DropdownMenuItem(
                                    text = { Text(i18n.t("idea_topic.action_merge")) },
                                    leadingIcon = { Icon(Icons.Default.Merge, null, Modifier.size(IconSize.s)) },
                                    enabled = state.canMergeOrDelete && state.mergeCandidates.isNotEmpty(),
                                    onClick = { showOverflow = false; onMerge() },
                                )
                                HorizontalDivider(thickness = BorderWidth.xs, color = MaterialTheme.colorScheme.outline)
                                DropdownMenuItem(
                                    text = { Text(i18n.t("idea_topic.action_delete"), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            null,
                                            Modifier.size(IconSize.s),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    enabled = state.canMergeOrDelete,
                                    onClick = { showOverflow = false; onDelete() },
                                )
                            }
                        }
                    }

                    // Editorial Headline & Lead Prose (no mechanical form labels)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(
                            text = content.title,
                            style = MaterialTheme.typography.headlineSmall.editorial(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (content.description.isNotBlank()) {
                            Text(
                                text = content.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val hasStructuredInsights = content.provenanceSummary.isNotBlank() || content.conclusions.isNotBlank()
                    if (hasStructuredInsights) {
                        HorizontalDivider(thickness = BorderWidth.xs, color = MaterialTheme.colorScheme.outline)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            if (content.provenanceSummary.isNotBlank()) {
                                ContentFieldRow(i18n.t("idea_topic.overview_provenance_summary"), content.provenanceSummary)
                            }
                            if (content.conclusions.isNotBlank()) {
                                ContentFieldRow(i18n.t("idea_topic.overview_conclusions"), content.conclusions)
                            }
                        }
                    }
                }
            }
        }

        // Focused two-action strip: Advance progress + AI refine
        item(key = "status-actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Button(
                    onClick = onAppendProgress,
                    modifier = Modifier.weight(1f).heightIn(min = Height.button),
                    shape = RoundedCornerShape(Radius.m),
                    enabled = !state.busy,
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(IconSize.s))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(i18n.t("idea_topic.append_progress"))
                }

                if (state.busy) {
                    OutlinedButton(
                        onClick = onCancelAi,
                        modifier = Modifier.weight(1f).heightIn(min = Height.button),
                        shape = RoundedCornerShape(Radius.m),
                        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(i18n.t("idea_topic.action_cancel_ai"))
                    }
                } else {
                    OutlinedButton(
                        onClick = onPropose,
                        modifier = Modifier.weight(1f).heightIn(min = Height.button),
                        shape = RoundedCornerShape(Radius.m),
                        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(IconSize.s))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(i18n.t("idea_topic.action_propose"))
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
    }
}

@Composable
private fun ContentFieldRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            shape = RoundedCornerShape(Radius.circular),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
            modifier = Modifier.clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.s + Spacing.xxs, end = Spacing.s, top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = i18n.t(ideaTopicStatusLabelKey(current)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.xs),
                    tint = MaterialTheme.colorScheme.primary,
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
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(IconSize.s))
                    Text(
                        i18n.t("idea_topic.draft_title"),
                        style = MaterialTheme.typography.titleSmall.editorial(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(Radius.circular),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = i18n.t("idea_topic.draft_pending"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                    )
                }
            }

            if (preview.changes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        text = i18n.t("idea_topic.draft_changes"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    preview.changes.forEach { change ->
                        Text(
                            text = "• ${draftFieldLabel(change.field, i18n)}: ${change.after.take(40)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
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
                    Text(i18n.t("idea_topic.draft_discard"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(Spacing.s))
                Button(onClick = onEdit, shape = RoundedCornerShape(Radius.m)) {
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
                style = MaterialTheme.typography.titleMedium.editorial(),
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
                style = MaterialTheme.typography.titleMedium.editorial(),
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Event timeline items
        items(detail.events, key = { it.id }) { event ->
            val presentation = formatIdeaTopicEvent(event, state.topicId, translate = { i18n.t(it) })
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
    val typeLabel = i18n.t("idea_topic.${ideaSourceTypeLabelKey(source.snapshot.key.type)}")

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
                text = source.snapshot.originalTitle.ifBlank { i18n.t("idea_topic.source_untitled") },
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
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
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
    onOpenOriginal: (IdeaSourceSnapshot) -> Unit,
) {
    val context = LocalContext.current
    val i18n: I18nService = koinInject()
    val sourceTypeLabel = i18n.t("idea_topic.${ideaSourceTypeLabelKey(snapshot.key.type)}")
    val hasUrl = snapshot.originalUrl?.startsWith("http://") == true || snapshot.originalUrl?.startsWith("https://") == true
    val hasLocalId = (snapshot.originalRecordId?.toLongOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(snapshot.originalTitle.ifBlank { sourceTypeLabel }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                // Link availability or snapshot notice
                if (hasUrl || hasLocalId) {
                    TextButton(onClick = {
                        if (hasLocalId && snapshot.key.type == IdeaSourceTypes.Article) onOpenOriginal(snapshot)
                        else if (hasUrl) openArticleUrl(context, snapshot.originalUrl)
                        else onOpenOriginal(snapshot)
                    }) {
                        Text(i18n.t("idea_topic.open_original"))
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
