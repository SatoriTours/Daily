package com.dailysatori.ui.feature.ideatopic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.ideatopic.IdeaTopicStatus
import com.dailysatori.service.ideatopic.IdeaTopicSummary
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun IdeaTopicListScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: IdeaTopicListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()

    AppScaffold(
        title = i18n.t("idea_topic.title"),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Search field
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                placeholder = { Text(i18n.t("idea_topic.search_placeholder")) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(IconSize.m)) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, null, Modifier.size(IconSize.m))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(Radius.m),
            )

            // Status filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                item {
                    FilterChip(
                        selected = state.statusFilter == null,
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text(i18n.t("idea_topic.status.all")) },
                    )
                }
                items(IdeaTopicStatus.entries) { status ->
                    FilterChip(
                        selected = state.statusFilter == status,
                        onClick = { viewModel.setStatusFilter(status) },
                        label = { Text(i18n.t(ideaTopicStatusLabelKey(status))) },
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.topics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            null,
                            modifier = Modifier.size(Spacing.xxl),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = i18n.t("idea_topic.empty_title"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = i18n.t("idea_topic.empty_hint"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    items(state.topics, key = { it.id }) { topic ->
                        IdeaTopicCard(
                            topic = topic,
                            onClick = { onOpen(topic.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun IdeaTopicCard(
    topic: IdeaTopicSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val i18n: I18nService = koinInject()
    val content = topic.content

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IdeaTopicStatusBadge(status = topic.status)
            }

            if (content.description.isNotBlank()) {
                Text(
                    text = content.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (topic.latestProgress != null) {
                Text(
                    text = i18n.t("idea_topic.progress_summary_prefix") + topic.latestProgress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (content.nextAction.isNotBlank()) {
                Text(
                    text = i18n.t("idea_topic.next_action_prefix") + content.nextAction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = i18n.t("idea_topic.sources_count", topic.sourceCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDateTime(topic.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun IdeaTopicStatusBadge(
    status: IdeaTopicStatus,
    modifier: Modifier = Modifier,
) {
    val i18n: I18nService = koinInject()
    val containerColor = when (status) {
        IdeaTopicStatus.PendingResearch -> MaterialTheme.colorScheme.surfaceContainerHigh
        IdeaTopicStatus.Researching -> MaterialTheme.colorScheme.secondaryContainer
        IdeaTopicStatus.Advancing -> MaterialTheme.colorScheme.primaryContainer
        IdeaTopicStatus.Completed -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (status) {
        IdeaTopicStatus.PendingResearch -> MaterialTheme.colorScheme.onSurfaceVariant
        IdeaTopicStatus.Researching -> MaterialTheme.colorScheme.onSecondaryContainer
        IdeaTopicStatus.Advancing -> MaterialTheme.colorScheme.onPrimaryContainer
        IdeaTopicStatus.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(Radius.s),
        color = containerColor,
        modifier = modifier,
    ) {
        Text(
            text = i18n.t(ideaTopicStatusLabelKey(status)),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        )
    }
}

internal fun formatDateTime(epochMillis: Long): String {
    return runCatching {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = ldt.monthNumber.toString().padStart(2, '0')
        val day = ldt.dayOfMonth.toString().padStart(2, '0')
        val hour = ldt.hour.toString().padStart(2, '0')
        val min = ldt.minute.toString().padStart(2, '0')
        "$month-$day $hour:$min"
    }.getOrDefault("")
}
