package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.R
import com.dailysatori.service.assistant.AssistantSessionSummary
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AssistantSessionPeriod { Today, Yesterday, PastWeek, Older }
data class AssistantSessionGroup(val period: AssistantSessionPeriod, val sessions: List<AssistantSessionSummary>)

fun groupAssistantSessions(
    sessions: List<AssistantSessionSummary>, query: String, now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<AssistantSessionGroup> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return sessions.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<AssistantSessionSummary> { it.updatedAt }.thenBy { it.id })
        .groupBy {
            val date = Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate()
            when {
                date >= today -> AssistantSessionPeriod.Today
                date == today.minusDays(1) -> AssistantSessionPeriod.Yesterday
                date >= today.minusDays(6) -> AssistantSessionPeriod.PastWeek
                else -> AssistantSessionPeriod.Older
            }
        }.map { (period, rows) -> AssistantSessionGroup(period, rows) }
}

@Composable
fun SessionListScreen(
    sessions: List<AssistantSessionSummary>, currentSessionId: String,
    onSelectSession: (String) -> Unit, onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit, onDismiss: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var deleting by remember { mutableStateOf<AssistantSessionSummary?>(null) }
    val groups = groupAssistantSessions(sessions, query.text, System.currentTimeMillis())
    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.assistant_sessions), onBack = onDismiss, showAssistant = false,
            actions = {
                TextButton(onClick = onNewSession) {
                    Icon(Icons.Default.Add, null, Modifier.size(IconSize.s))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.assistant_new_session))
                }
            }) },
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SessionSearchField(query, { query = it })
            if (groups.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(if (query.text.isBlank()) R.string.assistant_no_sessions else R.string.assistant_no_matching_sessions),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (query.text.isBlank()) R.string.assistant_no_sessions_hint else R.string.assistant_no_matching_sessions_hint),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s)) {
                groups.forEach { group ->
                    item(key = group.period.name) {
                        Text(stringResource(when (group.period) {
                            AssistantSessionPeriod.Today -> R.string.assistant_sessions_today
                            AssistantSessionPeriod.Yesterday -> R.string.assistant_sessions_yesterday
                            AssistantSessionPeriod.PastWeek -> R.string.assistant_sessions_week
                            AssistantSessionPeriod.Older -> R.string.assistant_sessions_older
                        }), Modifier.padding(start = Spacing.s, top = Spacing.m, bottom = Spacing.xs),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(group.sessions, key = { "session:${it.id}" }) { session ->
                        SessionRow(session, session.id == currentSessionId, { onSelectSession(session.id) }, { deleting = session })
                    }
                }
            }
        }
    }
    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.assistant_delete_session_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(session.title.ifBlank { stringResource(R.string.assistant_untitled_session) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.assistant_delete_session_impact))
            } },
            confirmButton = { TextButton(onClick = { onDeleteSession(session.id); deleting = null }) {
                Text(stringResource(R.string.assistant_delete_session), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun SessionSearchField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)
        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(Radius.m)),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, Modifier.padding(start = Spacing.s + Spacing.xs, end = Spacing.s).size(IconSize.s),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(value, onChange, Modifier.weight(1f).padding(vertical = Spacing.s), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { editor ->
                if (value.text.isEmpty()) Text(stringResource(R.string.assistant_search_sessions),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                editor()
            })
        Box(Modifier.size(Spacing.xxl)) {
            if (value.text.isNotEmpty()) IconButton(onClick = { onChange(TextFieldValue()) }) {
                Icon(Icons.Default.Close, stringResource(R.string.assistant_clear_search), Modifier.size(IconSize.s))
            }
        }
    }
}

@Composable
private fun SessionRow(session: AssistantSessionSummary, current: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.background, RoundedCornerShape(Radius.m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clickable(onClick = onSelect).padding(start = Spacing.s, top = Spacing.m, bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(session.title.ifBlank { stringResource(R.string.assistant_untitled_session) }, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(sessionTime(session.updatedAt), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (current) Text(stringResource(R.string.assistant_current_session), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(session.preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(Spacing.xxl)) {
                Icon(Icons.Default.MoreHoriz, stringResource(R.string.assistant_manage_session), Modifier.size(IconSize.m))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.assistant_delete_session), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() })
            }
        }
    }
}

private fun sessionTime(time: Long): String {
    val date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val pattern = when {
        date.toLocalDate() >= today.minusDays(1) -> "HH:mm"
        date.toLocalDate() >= today.minusDays(6) -> "EEE"
        date.year == today.year -> "MM/dd"
        else -> "yyyy/MM/dd"
    }
    return date.format(DateTimeFormatter.ofPattern(pattern))
}
