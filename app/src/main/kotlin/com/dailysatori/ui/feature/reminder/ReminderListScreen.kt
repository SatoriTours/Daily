package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Badge
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.R
import com.dailysatori.data.repository.ReminderEdit
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.androidx.compose.koinViewModel

private enum class DetailPicker { START, END, TIME }

@Composable
fun ReminderListScreen(
    modifier: Modifier = Modifier,
    latestProfile: ReminderProfileSnapshot = ReminderProfileSnapshot.standard(),
    viewModel: ReminderViewModel = koinViewModel(),
    initialReminderId: String? = null,
    onAddReminder: () -> Unit = {},
    onOpenReminder: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    showSettings: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    val ui by viewModel.state.collectAsState()
    val all by viewModel.reminders.collectAsState()
    val listState by viewModel.listState.collectAsState()
    val selected = all.firstOrNull { it.id == ui.selectedReminderId }
    val currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
    val scrollState = rememberLazyListState()
    LaunchedEffect(initialReminderId) {
        if (initialReminderId != null) viewModel.selectReminder(initialReminderId)
    }
    Box(modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            ReminderListToolbar(
                onBack = onBack,
                onToggleSearch = viewModel::toggleListSearch,
                onOpenFilter = { viewModel.updateListFilter { it.copy(isPanelOpen = true) } },
                onOpenSettings = onOpenSettings,
                showSettings = showSettings,
            )
            if (ui.isListSearchVisible) {
                OutlinedTextField(
                    value = ui.listFilter.query,
                    onValueChange = { query -> viewModel.updateListFilter { it.copy(query = query) } },
                    label = { Text(stringResource(R.string.reminder_list_search)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
                    singleLine = true,
                )
            }
            ReminderListModeTabs(
                selected = ui.listMode,
                year = ui.listFilter.displayYear ?: currentYear,
                onSelected = viewModel::setListMode,
                onPreviousYear = { viewModel.updateListFilter { current -> current.copy(displayYear = (current.displayYear ?: currentYear) - 1, expandedMonth = null) } },
                onNextYear = { viewModel.updateListFilter { current -> current.copy(displayYear = (current.displayYear ?: currentYear) + 1, expandedMonth = null) } },
            )
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                item(key = "summary") { ReminderListSummary(listState.summary) }
                if (selected != null) item(key = "detail_${selected.id}") { ReminderDetail(selected, latestProfile, viewModel) }
                if (ui.listMode == ReminderListMode.MONTHS) item(key = "months") {
                    ReminderMonthGrid(
                        months = listState.months,
                        currentMonth = Clock.System.todayIn(TimeZone.currentSystemDefault()).let { if ((ui.listFilter.displayYear ?: currentYear) == it.year) it.monthNumber else null },
                        expandedMonth = ui.listFilter.expandedMonth,
                        onMonth = { month -> viewModel.updateListFilter { current -> current.copy(expandedMonth = month.takeUnless { it == current.expandedMonth }) } },
                    )
                }
                listState.sections.forEach { section ->
                    item(key = "header_${section.key}") { Text(section.key.sectionLabel(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.m)) }
                    items(section.items.size, key = { section.items[it].id }) { index ->
                        ReminderListCard(section.items[index]) { onOpenReminder(section.items[index].id) }
                    }
                }
                if (listState.sections.isEmpty() && ui.listMode != ReminderListMode.MONTHS) {
                    item(key = "empty") { ReminderEmptyState(hasFilters = ui.listFilter.query.isNotBlank() || ui.listFilter.statuses.isNotEmpty() || ui.listFilter.recurrences.isNotEmpty(), onAdd = onAddReminder, onClear = { viewModel.updateListFilter { ReminderListFilter(displayYear = it.displayYear) } }) }
                }
            }
        }
        if (ui.listFilter.isPanelOpen) {
            Surface(
                modifier = Modifier.fillMaxSize().padding(top = Height.appBar).background(MaterialTheme.colorScheme.scrim.copy(alpha = .42f)).clickable { viewModel.updateListFilter { it.copy(isPanelOpen = false) } },
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {}
            ReminderFilterPanel(
                filter = ui.listFilter,
                onFilterChange = { change -> viewModel.updateListFilter(change) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Height.appBar),
            )
        } else {
            FloatingActionButton(
                onClick = onAddReminder,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) { Icon(Icons.Default.Add, stringResource(R.string.reminder_list_add), Modifier.size(IconSize.l)) }
        }
    }
}

@Composable
private fun ReminderListToolbar(onBack: (() -> Unit)?, onToggleSearch: () -> Unit, onOpenFilter: () -> Unit, onOpenSettings: () -> Unit, showSettings: Boolean) {
    Box(Modifier.fillMaxWidth().height(Height.appBar), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.reminder_list_title), style = MaterialTheme.typography.titleMedium)
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Default.ArrowBack, stringResource(R.string.reminder_back)) }
        Row(Modifier.align(Alignment.CenterEnd)) {
            IconButton(onClick = onToggleSearch) { Icon(Icons.Default.Search, stringResource(R.string.reminder_list_search)) }
            IconButton(onClick = onOpenFilter) { Icon(Icons.Default.FilterList, stringResource(R.string.reminder_list_filter)) }
            if (showSettings) IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, stringResource(R.string.reminder_settings_title)) }
        }
    }
}

@Composable
private fun ReminderListModeTabs(selected: ReminderListMode, year: Int, onSelected: (ReminderListMode) -> Unit, onPreviousYear: () -> Unit, onNextYear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ReminderListMode.entries.forEach { mode -> FilterChip(selected = selected == mode, onClick = { onSelected(mode) }, label = { Text(mode.label()) }) }
        }
        if (selected.showsYearSwitcher()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousYear, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.reminder_previous_year)) }
                Text(year.toString(), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onNextYear, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ChevronRight, stringResource(R.string.reminder_next_year)) }
            }
        }
    }
}

@Composable
private fun ReminderListSummary(summary: ReminderListSummaryUi) {
    val next = summary.nextOccurrence?.toString() ?: stringResource(R.string.reminder_list_none)
    Surface(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs), shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(stringResource(R.string.reminder_list_summary, summary.upcomingInThirtyDays, next), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReminderMonthGrid(months: List<ReminderMonthUi>, currentMonth: Int?, expandedMonth: Int?, onMonth: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        months.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { month ->
                    val card = month.toCardUi(currentMonth, expandedMonth)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp)
                            .then(if (card.isCurrent && !card.isExpanded) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .55f), androidx.compose.foundation.shape.RoundedCornerShape(Radius.m)) else Modifier)
                            .clickable { onMonth(card.month) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.m),
                        color = if (card.isExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.fillMaxSize().padding(Spacing.s), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.reminder_month_name, card.month), style = MaterialTheme.typography.titleSmall, color = if (card.hasReminders) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (card.hasReminders) Badge { Text(card.count.toString()) }
                            }
                            Text(
                                text = if (card.hasReminders) stringResource(R.string.reminder_month_count, card.count) else stringResource(R.string.reminder_month_none),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (expandedMonth != null && months.firstOrNull { it.month == expandedMonth }?.count == 0) Text(stringResource(R.string.reminder_month_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReminderEmptyState(hasFilters: Boolean, onAdd: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(if (hasFilters) R.string.reminder_filter_empty else R.string.reminder_empty), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = if (hasFilters) onClear else onAdd) { Text(stringResource(if (hasFilters) R.string.reminder_clear_filters else R.string.reminder_list_add)) }
    }
}

@Composable
private fun ReminderListCard(item: ReminderListItemUi, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m).clickable(onClick = onClick), shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.content, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.reminder_list_item_meta, item.occurrenceDate, item.firstReminderTime, item.daysUntil, item.recurrence.label()), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReminderFilterPanel(filter: ReminderListFilter, onFilterChange: ((ReminderListFilter) -> ReminderListFilter) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.m), shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.reminder_list_filter), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(ReminderStatus.ACTIVE, ReminderStatus.PAUSED).forEach { status -> FilterChip(selected = status in filter.statuses, onClick = { onFilterChange { current -> current.copy(statuses = current.statuses.toggle(status)) } }, label = { Text(status.label()) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ReminderRecurrenceKind.entries.forEach { recurrence -> FilterChip(selected = recurrence in filter.recurrences, onClick = { onFilterChange { current -> current.copy(recurrences = current.recurrences.toggle(recurrence)) } }, label = { Text(recurrence.label()) }) }
            }
            TextButton(onClick = { onFilterChange { it.copy(isPanelOpen = false) } }) { Text(stringResource(R.string.reminder_list_show_results)) }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun ReminderListMode.label() = stringResource(when (this) {
    ReminderListMode.RECENT -> R.string.reminder_list_recent
    ReminderListMode.MONTHS -> R.string.reminder_list_months
    ReminderListMode.FINISHED -> R.string.reminder_list_finished
})

@Composable
private fun ReminderRecurrenceKind.label() = stringResource(when (this) {
    ReminderRecurrenceKind.ONCE -> R.string.reminder_list_repeat_once
    ReminderRecurrenceKind.MONTHLY -> R.string.reminder_list_repeat_monthly
    ReminderRecurrenceKind.YEARLY -> R.string.reminder_list_repeat_yearly
})

@Composable
private fun String.sectionLabel() = stringResource(when (this) {
    "next_week" -> R.string.reminder_list_next_week
    "later_this_month" -> R.string.reminder_list_later_this_month
    "later" -> R.string.reminder_list_later
    "finished" -> R.string.reminder_list_finished
    else -> R.string.reminder_list_month
})

@Composable
private fun ReminderDetail(reminder: Reminder, latestProfile: ReminderProfileSnapshot, viewModel: ReminderViewModel) {
    var content by remember(reminder.id, reminder.version) { mutableStateOf(reminder.content) }
    var startDate by remember(reminder.id, reminder.version) { mutableStateOf(reminder.startDate) }
    var endDate by remember(reminder.id, reminder.version) { mutableStateOf(reminder.endDate) }
    var firstTime by remember(reminder.id, reminder.version) { mutableStateOf(reminder.firstReminderTime) }
    var rule by remember(reminder.id, reminder.version) { mutableStateOf(reminder.activeDayRule) }
    var picker by remember { mutableStateOf<DetailPicker?>(null) }
    val validEdit = isValidReminderDetailEdit(content, startDate, endDate, rule)
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (reminder.dataIssue != null) {
                Text(stringResource(R.string.reminder_corrupt_profile_warning), color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.reminder_content_label)) },
                isError = !validEdit,
                supportingText = if (validEdit) null else { { Text(stringResource(R.string.reminder_detail_invalid)) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { picker = DetailPicker.START }) { Text(stringResource(R.string.reminder_start_value, startDate)) }
                TextButton(onClick = { picker = DetailPicker.END }) { Text(stringResource(R.string.reminder_end_value, endDate)) }
                TextButton(onClick = { picker = DetailPicker.TIME }) { Text(stringResource(R.string.reminder_first_value, firstTime)) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(
                    stringResource(R.string.reminder_rule_daily) to ReminderActiveDayRule.Daily,
                    stringResource(R.string.reminder_rule_weekdays) to ReminderActiveDayRule.Weekdays,
                    stringResource(R.string.reminder_rule_selected) to ReminderActiveDayRule.SelectedWeekdays((rule as? ReminderActiveDayRule.SelectedWeekdays)?.days ?: DayOfWeek.entries.toSet()),
                    stringResource(R.string.reminder_rule_range) to ReminderActiveDayRule.ConsecutiveDateRange,
                ).forEach { (label, value) ->
                    FilterChip(selected = rule::class == value::class, onClick = { rule = value }, label = { Text(label) })
                }
            }
            (rule as? ReminderActiveDayRule.SelectedWeekdays)?.let { selected ->
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in selected.days,
                            onClick = { rule = toggleReminderDetailWeekday(selected, day) },
                            label = { Text(day.shortLabel()) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                reminderActions(reminder).forEach { action ->
                    TextButton(onClick = {
                        when (action) {
                            ReminderAction.PAUSE -> viewModel.pause(reminder.id)
                            ReminderAction.RESUME -> viewModel.resume(reminder.id)
                            ReminderAction.EDIT -> viewModel.edit(reminder.id, ReminderEdit(reminder.version, content = content, startDate = startDate, endDate = endDate, firstReminderTime = firstTime, activeDayRule = rule))
                            ReminderAction.COMPLETE -> viewModel.complete(reminder.id)
                            ReminderAction.DELETE -> viewModel.delete(reminder.id)
                            ReminderAction.APPLY_LATEST_PROFILE -> viewModel.applyLatestProfile(reminder.id, latestProfile)
                        }
                    }, enabled = (action != ReminderAction.EDIT || validEdit) &&
                        !(action == ReminderAction.RESUME && reminder.dataIssue != null)) { Text(action.label()) }
                }
            }
        }
    }
    when (picker) {
        DetailPicker.START -> DateDialog(startDate, { picker = null }) { startDate = it; if (endDate < it) endDate = it; picker = null }
        DetailPicker.END -> DateDialog(endDate, { picker = null }) { endDate = it; picker = null }
        DetailPicker.TIME -> TimeDialog(firstTime, { picker = null }) { firstTime = it; picker = null }
        null -> Unit
    }
}

internal fun isValidReminderDetailEdit(content: String, startDate: kotlinx.datetime.LocalDate, endDate: kotlinx.datetime.LocalDate, rule: ReminderActiveDayRule): Boolean =
    content.isNotBlank() && content.length <= 2_000 && endDate >= startDate &&
        (rule !is ReminderActiveDayRule.SelectedWeekdays || rule.days.isNotEmpty())

internal fun toggleReminderDetailWeekday(rule: ReminderActiveDayRule.SelectedWeekdays, day: DayOfWeek) =
    ReminderActiveDayRule.SelectedWeekdays(if (day in rule.days) rule.days - day else rule.days + day)

@Composable
private fun ReminderFilter.label() = stringResource(when (this) {
    ReminderFilter.ACTIVE -> R.string.reminder_filter_active
    ReminderFilter.PAUSED -> R.string.reminder_filter_paused
    ReminderFilter.COMPLETED -> R.string.reminder_filter_completed
    ReminderFilter.EXPIRED -> R.string.reminder_filter_expired
})

@Composable
private fun ReminderAction.label() = stringResource(when (this) {
    ReminderAction.PAUSE -> R.string.reminder_action_pause
    ReminderAction.RESUME -> R.string.reminder_action_resume
    ReminderAction.EDIT -> R.string.reminder_action_save_edit
    ReminderAction.COMPLETE -> R.string.reminder_action_complete
    ReminderAction.DELETE -> R.string.reminder_action_delete
    ReminderAction.APPLY_LATEST_PROFILE -> R.string.reminder_action_apply_latest
})

@Composable
private fun com.dailysatori.service.reminder.ReminderStatus.label() = stringResource(when (this) {
    com.dailysatori.service.reminder.ReminderStatus.ACTIVE,
    com.dailysatori.service.reminder.ReminderStatus.NOTIFIED,
    com.dailysatori.service.reminder.ReminderStatus.DISMISSED -> R.string.reminder_status_active
    com.dailysatori.service.reminder.ReminderStatus.PAUSED -> R.string.reminder_status_paused
    com.dailysatori.service.reminder.ReminderStatus.COMPLETED -> R.string.reminder_status_completed
    com.dailysatori.service.reminder.ReminderStatus.EXPIRED -> R.string.reminder_status_expired
    com.dailysatori.service.reminder.ReminderStatus.DRAFT -> R.string.reminder_draft_title
})
