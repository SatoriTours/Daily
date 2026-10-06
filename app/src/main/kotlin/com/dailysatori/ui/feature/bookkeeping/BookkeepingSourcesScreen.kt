package com.dailysatori.ui.feature.bookkeeping

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.component.settings.SettingsScaffold
import com.dailysatori.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

internal fun filterBookkeepingSources(sources: List<BookkeepingSource>, selected: Set<String>, search: String,
    selectedOnly: Boolean): List<BookkeepingSource> {
    val query = search.trim()
    return sources.filter { source ->
        (!selectedOnly || source.packageName in selected) &&
            (query.isEmpty() || source.label.contains(query, true) || source.packageName.contains(query, true))
    }
}

@Composable
internal fun BookkeepingSourcesDialog(state: BookkeepingUiState, onSelect: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val i18n: I18nService = koinInject()
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val back = {
        keyboard?.hide()
        if (searchVisible) { searchVisible = false; search = "" } else onDismiss()
    }
    val visible = remember(state.sources, state.selectedSources, search, selectedOnly) {
        filterBookkeepingSources(state.sources, state.selectedSources, search, selectedOnly)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(search, selectedOnly) { listState.scrollToItem(0) }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = back)
        SettingsScaffold(title = i18n.t("bookkeeping.select_sources"), onBack = back, actions = {
            IconButton(onClick = { if (searchVisible) back() else searchVisible = true }) {
                Icon(if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                    i18n.t(if (searchVisible) "bookkeeping.sources_close_search" else "bookkeeping.search_sources"))
            }
        }, bottomBar = {
            BookkeepingSourcesFooter(state, onDone = { keyboard?.hide(); onDismiss() })
        }) { modifier ->
            Column(modifier.fillMaxSize().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(i18n.t("bookkeeping.sources_intro"), style = MaterialTheme.typography.titleMedium)
                    Text(i18n.t("bookkeeping.sources_new_only"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (searchVisible) BookkeepingSourceSearch(search) { search = it }
                BookkeepingSourceTabs(selectedOnly, state.selectedSources.size) { selectedOnly = it }
                if (visible.isEmpty()) BookkeepingSourcesEmpty(search, selectedOnly, state.sources.isEmpty(), Modifier.weight(1f))
                else Surface(Modifier.fillMaxWidth().weight(1f, fill = false), shape = RoundedCornerShape(Radius.l),
                    color = MaterialTheme.colorScheme.surface) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(visible, key = { _, source -> source.packageName }) { index, source ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = Spacing.m + IconSize.xxl + Spacing.m, end = Spacing.m),
                                color = MaterialTheme.colorScheme.outlineVariant)
                            BookkeepingSourceRow(source, source.packageName in state.selectedSources, state.busy) {
                                onSelect(source.packageName, it)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun BookkeepingSourceSearch(search: String, onSearch: (String) -> Unit) {
    val i18n: I18nService = koinInject()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
        placeholder = { Text(i18n.t("bookkeeping.search_sources")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) {
                Icon(Icons.Default.Clear, i18n.t("bookkeeping.sources_clear_search"))
            }
        }, shape = RoundedCornerShape(Radius.m), textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
}

@Composable private fun BookkeepingSourceTabs(selectedOnly: Boolean, count: Int, onChange: (Boolean) -> Unit) {
    val i18n: I18nService = koinInject()
    Column {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            listOf(false, true).forEach { selectedTab ->
                val active = selectedOnly == selectedTab
                Column(Modifier.width(IntrinsicSize.Min).heightIn(min = Height.button)
                    .selectable(active, role = Role.Tab, onClick = { onChange(selectedTab) }), verticalArrangement = Arrangement.Bottom) {
                    Text(i18n.t(if (selectedTab) "bookkeeping.sources_selected_tab" else "bookkeeping.sources_all", count),
                        Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s), style = MaterialTheme.typography.titleSmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().height(BorderWidth.l).background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable private fun BookkeepingSourceRow(source: BookkeepingSource, selected: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
        .toggleable(selected, enabled = !busy, role = Role.Checkbox, onValueChange = onChange)
        .heightIn(min = Height.listItem + Spacing.m).padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        BookkeepingSourceIcon(source.packageName)
        Text(source.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Checkbox(selected, onCheckedChange = null, enabled = !busy)
    }
}

@Composable private fun BookkeepingSourceIcon(packageName: String) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { IconSize.xxl.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName, pixels) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(pixels, pixels).asImageBitmap() }.getOrNull()
        }
    }
    Surface(Modifier.size(IconSize.xxl), shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        val bitmap = icon
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.m)))
        else Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Apps, null, Modifier.size(IconSize.l), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun BookkeepingSourcesEmpty(search: String, selectedOnly: Boolean, noApps: Boolean, modifier: Modifier) {
    val i18n: I18nService = koinInject()
    val key = when {
        search.isNotBlank() -> "no_matches"
        noApps -> "no_apps"
        selectedOnly -> "none_selected"
        else -> "no_apps"
    }
    Column(modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Apps, null, Modifier.size(IconSize.xxl), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(i18n.t("bookkeeping.sources_$key"), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(i18n.t("bookkeeping.sources_${key}_hint"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
    }
}

@Composable private fun BookkeepingSourcesFooter(state: BookkeepingUiState, onDone: () -> Unit) {
    val i18n: I18nService = koinInject()
    Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(i18n.t("bookkeeping.sources_selected_label"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(i18n.t("bookkeeping.sources_selected_count", state.selectedSources.size), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            if (state.busy) CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = BorderWidth.l)
        }
        if (state.error) Text(i18n.t("bookkeeping.sources_save_failed"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        Button(onClick = onDone, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = Height.button),
            shape = RoundedCornerShape(Radius.circular)) { Text(i18n.t("bookkeeping.done")) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PrivacyTip, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(i18n.t("bookkeeping.sources_privacy"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
