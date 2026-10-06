package com.dailysatori.ui.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.activity.compose.BackHandler
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import com.dailysatori.R
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.*
import com.dailysatori.service.i18n.I18nService
import org.koin.compose.koinInject

data class SettingsGroupNavigation(
    val title: String,
    val tabs: List<String>,
    val selectedIndex: Int,
    val onSelect: (Int) -> Unit,
)

val LocalSettingsGroupNavigation = staticCompositionLocalOf<SettingsGroupNavigation?> { null }

/** Shared page surface and safe editor actions for settings, including nested editors. */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = onBack != null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    useGroupNavigation: Boolean = false,
    hasUnsavedChanges: Boolean = false,
    navigationBusy: Boolean = false,
    onDiscardChanges: () -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: ((String) -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val navigation = LocalSettingsGroupNavigation.current.takeIf { useGroupNavigation }
    var pendingTab by rememberSaveable { mutableIntStateOf(-1) }
    var searching by rememberSaveable { mutableStateOf(searchQuery.isNotBlank()) }
    val focus = remember { FocusRequester() }
    val i18n: I18nService = koinInject()
    val closeSearch: () -> Unit = { searching = false; onSearchQueryChange?.invoke("") }
    BackHandler(enabled = searching, onBack = closeSearch)
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    if (pendingTab >= 0 && navigation != null) AlertDialog(
        onDismissRequest = { if (!navigationBusy) pendingTab = -1 },
        title = { Text(stringResource(R.string.settings_unsaved_title)) },
        text = { Text(stringResource(R.string.settings_unsaved_message)) },
        confirmButton = {
            TextButton(enabled = !navigationBusy, onClick = {
                val target = pendingTab
                pendingTab = -1
                onDiscardChanges()
                navigation.onSelect(target)
            }) { Text(stringResource(R.string.settings_discard_changes)) }
        },
        dismissButton = {
            TextButton(enabled = !navigationBusy, onClick = { pendingTab = -1 }) {
                Text(stringResource(R.string.settings_continue_editing))
            }
        },
    )
    // Scope the grouped-page background to settings; reading pages keep their existing theme.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(background = MaterialTheme.colorScheme.surfaceContainerLowest),
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(Radius.s),
            medium = RoundedCornerShape(Radius.l),
            large = RoundedCornerShape(Radius.l),
        ),
    ) {
        AppScaffold(
            title = navigation?.title ?: title,
            onBack = onBack?.let { { if (searching) closeSearch() else it() } },
            showBack = showBack,
            actions = {
                if (onSearchQueryChange != null) IconButton(onClick = {
                    if (searching) closeSearch() else searching = true
                }) {
                    Icon(if (searching) Icons.Default.Close else Icons.Default.Search,
                        i18n.t(if (searching) "settings_design.close_search" else "settings_design.search"))
                }
                actions()
            },
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            bottomBar = {
                if (bottomBar != null) Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.navigationBarsPadding().imePadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        bottomBar()
                    }
                }
            },
            content = { modifier ->
                if (navigation == null && !searching) content(modifier)
                else Column(modifier.fillMaxSize()) {
                    if (searching && onSearchQueryChange != null) OutlinedTextField(
                        value = searchQuery, onValueChange = onSearchQueryChange,
                        placeholder = { Text(i18n.t("settings_design.search_list_hint")) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true, shape = RoundedCornerShape(Radius.l),
                        modifier = Modifier.fillMaxWidth().padding(Spacing.m).focusRequester(focus),
                    )
                    if (navigation != null) SettingsGroupTabs(navigation, enabled = !navigationBusy) { index ->
                        if (hasUnsavedChanges) pendingTab = index else navigation.onSelect(index)
                    }
                    Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
                }
            },
        )
    }
}

fun matchesSettingsQuery(query: String, vararg fields: String): Boolean {
    val text = fields.joinToString(" ")
    return query.trim().split(Regex("\\s+")).all { text.contains(it, ignoreCase = true) }
}

@Composable
private fun SettingsGroupTabs(navigation: SettingsGroupNavigation, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m)) {
            navigation.tabs.forEachIndexed { index, label ->
                val selected = index == navigation.selectedIndex
                Column(
                    Modifier.weight(1f).selectable(selected, enabled = enabled, role = Role.Tab,
                        onClick = { if (!selected) onSelect(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.m),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().height(BorderWidth.l).background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
