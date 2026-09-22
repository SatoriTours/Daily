package com.dailysatori.ui.feature.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailysatori.R
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.ui.theme.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.androidx.compose.koinViewModel

private val DateBlockWidth = 50.dp
private const val WEEK_DAYS = 7

@Composable
fun ReminderListScreen(
    modifier: Modifier = Modifier,
    latestProfile: ReminderProfileSnapshot = ReminderProfileSnapshot.standard(),
    viewModel: ReminderViewModel = koinViewModel(),
    initialReminderId: String? = null,
    initialTodayOnly: Boolean = false,
    onAddReminder: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    showSettings: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    val ui by viewModel.state.collectAsState()
    val all by viewModel.reminders.collectAsState()
    val listState by viewModel.listState.collectAsState()
    val selected = all.firstOrNull { it.id == ui.selectedReminderId }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val displayYear = ui.listFilter.displayYear ?: today.year
    val scrollState = rememberLazyListState()

    LaunchedEffect(viewModel, initialTodayOnly) { viewModel.applyListEntryFilter(initialTodayOnly) }
    LaunchedEffect(initialReminderId) {
        if (initialReminderId != null) viewModel.selectReminder(initialReminderId)
    }

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            ReminderTopBar(
                onBack = onBack,
                onToggleSearch = viewModel::toggleListSearch,
                onOpenFilter = { viewModel.updateListFilter { it.copy(isPanelOpen = true) } },
                onOpenSettings = onOpenSettings,
                showSettings = showSettings,
            )
            if (ui.isListSearchVisible) ReminderSearchField(ui.listFilter.query) { query ->
                viewModel.updateListFilter { it.copy(query = query) }
            }
            if (ui.listFilter.todayOnly) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.management_today_reminders, listState.sections.sumOf { it.items.size }), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { viewModel.updateListFilter { it.copy(todayOnly = false) } }) { Text(stringResource(R.string.management_all_reminders)) }
                }
            } else {
                ReminderHero(listState.summary, today)
                ReminderModeTabs(ui.listMode, viewModel::setListMode)
            }
            LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
                if (ui.listMode == ReminderListMode.MONTHS) {
                    item(key = "year_nav") {
                        ReminderYearNavBar(
                            year = displayYear,
                            onPrevious = { viewModel.shiftListYear(-1) },
                            onNext = { viewModel.shiftListYear(1) },
                        )
                    }
                    item(key = "month_rail") {
                        ReminderMonthRail(
                            months = listState.months,
                            currentMonth = today.monthNumber.takeIf { displayYear == today.year },
                            selectedMonth = ui.listFilter.expandedMonth,
                            onSelect = { month ->
                                viewModel.updateListFilter { current ->
                                    current.copy(expandedMonth = month.takeUnless { it == current.expandedMonth })
                                }
                            },
                        )
                    }
                }
                listState.sections.forEach { section ->
                    item(key = "header_${section.key}") {
                        ReminderSectionHeader(section, today, displayYear)
                    }
                    itemsIndexed(section.items, key = { _, item -> item.id }) { index, item ->
                        ReminderStoryRow(
                            item = item,
                            today = today,
                            isFinished = ui.listMode == ReminderListMode.FINISHED,
                            showDivider = index > 0,
                            onClick = { viewModel.selectReminder(item.id) },
                        )
                    }
                }
                if (listState.sections.isEmpty()) {
                    item(key = "empty") { ReminderEmptyBlock(ui, listState.months, today, onAddReminder) }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddReminder,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
        ) { Icon(Icons.Default.Add, stringResource(R.string.reminder_list_add), Modifier.size(IconSize.l)) }
    }

    if (selected != null) {
        ReminderEditorSheet(
            reminder = selected,
            latestProfile = latestProfile,
            viewModel = viewModel,
            onDismiss = { viewModel.selectReminder(null) },
        )
    }
    if (ui.listFilter.isPanelOpen) {
        ReminderFilterSheet(
            filter = ui.listFilter,
            resultCount = listState.sections.sumOf { it.items.size },
            onFilterChange = { change -> viewModel.updateListFilter(change) },
            onDismiss = { viewModel.updateListFilter { it.copy(isPanelOpen = false) } },
        )
    }
}

@Composable
private fun ReminderTopBar(
    onBack: (() -> Unit)?,
    onToggleSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    onOpenSettings: () -> Unit,
    showSettings: Boolean,
) {
    // Flow layout: the title always sits after the back button, never overlapping at any density.
    Row(
        Modifier.fillMaxWidth().height(Height.appBar).padding(end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.reminder_back), tint = MaterialTheme.colorScheme.primary) }
        }
        Text(
            stringResource(R.string.reminder_list_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = if (onBack != null) Spacing.xs else Spacing.l),
        )
        IconButton(onClick = onToggleSearch) { Icon(Icons.Default.Search, stringResource(R.string.reminder_list_search), tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onOpenFilter) { Icon(Icons.Default.FilterList, stringResource(R.string.reminder_list_filter), tint = MaterialTheme.colorScheme.primary) }
        if (showSettings) {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, stringResource(R.string.reminder_settings_title), tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun ReminderSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.reminder_list_search)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        singleLine = true,
    )
}

@Composable
private fun ReminderHero(summary: ReminderListSummaryUi, today: LocalDate) {
    val next = summary.nextItem ?: return
    val headline = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            stringResource(R.string.reminder_list_next_kicker),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(heroDayLabel(next, today), style = headline)
            Text(next.firstReminderTime, style = headline, color = MaterialTheme.colorScheme.primary)
        }
        Text(next.content, style = headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            buildString {
                append(stringResource(R.string.reminder_list_hero_soon, summary.upcomingInThirtyDays))
                if (summary.pausedUpcoming > 0) append(" · ").append(stringResource(R.string.reminder_list_hero_paused, summary.pausedUpcoming))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun heroDayLabel(item: ReminderListItemUi, today: LocalDate): String = when (item.daysUntil) {
    0 -> stringResource(R.string.reminder_list_today)
    1 -> stringResource(R.string.reminder_list_tomorrow)
    else -> stringResource(R.string.reminder_date_month_day, item.occurrenceDate.monthNumber, item.occurrenceDate.dayOfMonth)
}

@Composable
private fun ReminderModeTabs(selected: ReminderListMode, onSelected: (ReminderListMode) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ReminderListMode.entries.forEach { mode ->
                ReminderModeTab(mode, mode == selected) { onSelected(mode) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ReminderModeTab(mode: ReminderListMode, isSelected: Boolean, onClick: () -> Unit) {
    // IntrinsicSize keeps the indicator as wide as the label; fillMaxWidth here would stretch the first tab across the row.
    Column(
        Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick).padding(top = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            mode.label(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.s))
        Box(
            Modifier
                .height(2.dp)
                .fillMaxWidth()
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(Radius.xxs)),
        )
    }
}

@Composable
private fun ReminderYearNavBar(year: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(Height.appBarCompact)) {
            IconButton(onClick = onPrevious, modifier = Modifier.align(Alignment.CenterStart).padding(start = Spacing.xs)) {
                Icon(Icons.Default.ChevronLeft, stringResource(R.string.reminder_previous_year), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                stringResource(R.string.reminder_year_value, year),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterEnd).padding(end = Spacing.xs)) {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.reminder_next_year), tint = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ReminderMonthRail(months: List<ReminderMonthUi>, currentMonth: Int?, selectedMonth: Int?, onSelect: (Int) -> Unit) {
    val railState = rememberLazyListState()
    // Keep the current/selected month visible when entering the mode or switching year.
    LaunchedEffect(months, currentMonth, selectedMonth) {
        val target = months.indexOfFirst { it.month == (selectedMonth ?: currentMonth) }
        if (target > 0) railState.animateScrollToItem(target)
    }
    LazyRow(
        state = railState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(months, key = { it.month }) { month ->
            ReminderMonthChip(
                month = month,
                isCurrent = month.month == currentMonth,
                isSelected = month.month == selectedMonth,
                onClick = { onSelect(month.month) },
            )
        }
    }
}

@Composable
private fun ReminderMonthChip(month: ReminderMonthUi, isCurrent: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        month.count > 0 -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box {
        Surface(
            shape = RoundedCornerShape(Radius.m),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Column(
                Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.reminder_month_name, month.month),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    if (month.count > 0) stringResource(R.string.reminder_count_items, month.count) else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
        if (isCurrent) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 6.dp)
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun ReminderSectionHeader(section: ReminderListSectionUi, today: LocalDate, displayYear: Int) {
    val isToday = section.key == "today" || section.key == "tomorrow"
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.l, bottom = Spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sectionHeadLabel(section, today, displayYear),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.reminder_count_items, section.items.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun sectionHeadLabel(section: ReminderListSectionUi, today: LocalDate, displayYear: Int): String {
    val date = section.items.firstOrNull()?.occurrenceDate
    return when (section.key) {
        "today" -> stringResource(R.string.reminder_list_today) + dateSuffix(date)
        "tomorrow" -> stringResource(R.string.reminder_list_tomorrow) + dateSuffix(date)
        "next_week" -> stringResource(R.string.reminder_list_next_week)
        "later_this_month" -> stringResource(R.string.reminder_list_later_this_month)
        "later" -> stringResource(R.string.reminder_list_later)
        "completed" -> stringResource(R.string.reminder_status_completed)
        "expired" -> stringResource(R.string.reminder_status_expired)
        else -> {
            val month = section.key.removePrefix("month_").toIntOrNull()
            if (month != null) stringResource(R.string.reminder_month_year_title, month, displayYear)
            else stringResource(R.string.reminder_list_month)
        }
    }
}

@Composable
private fun dateSuffix(date: LocalDate?): String =
    date?.let { " · " + stringResource(R.string.reminder_date_month_day, it.monthNumber, it.dayOfMonth) }.orEmpty()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderStoryRow(
    item: ReminderListItemUi,
    today: LocalDate,
    isFinished: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = Spacing.m + DateBlockWidth + Spacing.m),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ReminderDateBlock(item, today, isFinished)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    item.content,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFinished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    ReminderStoryMeta(item, isFinished)
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(IconSize.s).align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun ReminderDateBlock(item: ReminderListItemUi, today: LocalDate, isFinished: Boolean) {
    val dayColor = when {
        isFinished -> MaterialTheme.colorScheme.onSurfaceVariant
        item.daysUntil == 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(Modifier.width(DateBlockWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            item.occurrenceDate.dayOfMonth.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = dayColor,
        )
        Text(
            dateBlockUnit(item, isFinished),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun dateBlockUnit(item: ReminderListItemUi, isFinished: Boolean): String = when {
    isFinished -> stringResource(R.string.reminder_date_month, item.occurrenceDate.monthNumber)
    item.daysUntil in 0..WEEK_DAYS -> item.occurrenceDate.dayOfWeek.weekdayLabel()
    else -> stringResource(R.string.reminder_date_month, item.occurrenceDate.monthNumber)
}

@Composable
private fun DayOfWeek.weekdayLabel(): String = stringResource(
    when (this) {
        DayOfWeek.MONDAY -> R.string.reminder_list_weekday_mon
        DayOfWeek.TUESDAY -> R.string.reminder_list_weekday_tue
        DayOfWeek.WEDNESDAY -> R.string.reminder_list_weekday_wed
        DayOfWeek.THURSDAY -> R.string.reminder_list_weekday_thu
        DayOfWeek.FRIDAY -> R.string.reminder_list_weekday_fri
        DayOfWeek.SATURDAY -> R.string.reminder_list_weekday_sat
        DayOfWeek.SUNDAY -> R.string.reminder_list_weekday_sun
    },
)

@Composable
private fun ReminderStoryMeta(item: ReminderListItemUi, isFinished: Boolean) {
    Text(item.firstReminderTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (isFinished) {
        val completed = item.status == ReminderStatus.COMPLETED
        ReminderMetaChip(
            text = stringResource(if (completed) R.string.reminder_status_completed else R.string.reminder_status_expired),
            container = if (completed) AppColors.success.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (completed) AppColors.success else MaterialTheme.colorScheme.error,
        )
        return
    }
    val recurrenceChip = item.repeatLabel() == ReminderRepeatLabel.ONCE || item.repeatLabel() == ReminderRepeatLabel.YEARLY
    if (recurrenceChip) {
        ReminderMetaChip(
            text = item.repeatLabel().label(),
            container = if (item.repeatLabel() == ReminderRepeatLabel.YEARLY) AppColors.success.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (item.repeatLabel() == ReminderRepeatLabel.YEARLY) AppColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.repeatLabel().label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (item.status == ReminderStatus.PAUSED) {
        ReminderMetaChip(
            text = stringResource(R.string.reminder_status_paused),
            container = AppColors.warning.copy(alpha = 0.16f),
            contentColor = AppColors.warning,
        )
    }
}

@Composable
private fun ReminderMetaChip(text: String, container: Color, contentColor: Color) {
    Surface(shape = RoundedCornerShape(Radius.circular), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
        )
    }
}

@Composable
private fun ReminderEmptyBlock(ui: ReminderUiState, months: List<ReminderMonthUi>, today: LocalDate, onAdd: () -> Unit) {
    if (ui.listMode == ReminderListMode.MONTHS) {
        if (ui.listFilter.expandedMonth == null) return
        val month = months.firstOrNull { it.month == ui.listFilter.expandedMonth }
        if (month?.count == 0) {
            Text(
                stringResource(R.string.reminder_month_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
            )
        }
        return
    }
    val hasFilters = ui.listFilter.query.isNotBlank() || ui.listFilter.statuses.isNotEmpty() || ui.listFilter.recurrences.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(IconSize.xxl),
        )
        Text(
            stringResource(if (hasFilters) R.string.reminder_filter_empty else R.string.reminder_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (!hasFilters) {
            Text(
                stringResource(R.string.reminder_list_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = onAdd) { Text("＋ " + stringResource(R.string.reminder_list_add)) }
    }
}

@Composable
private fun ReminderEditorFieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.s),
    )
}

@Composable
private fun ReminderRuleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderFilterSheet(
    filter: ReminderListFilter,
    resultCount: Int,
    onFilterChange: ((ReminderListFilter) -> ReminderListFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.l).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                stringResource(R.string.reminder_filter_kicker),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.reminder_filter_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ReminderEditorFieldLabel(stringResource(R.string.reminder_filter_status_label))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(ReminderStatus.ACTIVE, ReminderStatus.PAUSED).forEach { status ->
                    ReminderRuleChip(status.label(), status in filter.statuses) {
                        onFilterChange { current -> current.copy(statuses = current.statuses.toggle(status)) }
                    }
                }
            }
            ReminderEditorFieldLabel(stringResource(R.string.reminder_filter_repeat_label))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ReminderRecurrenceKind.entries.forEach { recurrence ->
                    ReminderRuleChip(recurrence.label(), recurrence in filter.recurrences) {
                        onFilterChange { current -> current.copy(recurrences = current.recurrences.toggle(recurrence)) }
                    }
                }
            }
            Text(
                stringResource(R.string.reminder_filter_active_count, filter.statuses.size + filter.recurrences.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(Height.button)) {
                Text(stringResource(R.string.reminder_list_show_results_count, resultCount))
            }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun ReminderListMode.label() = stringResource(
    when (this) {
        ReminderListMode.RECENT -> R.string.reminder_list_recent
        ReminderListMode.MONTHS -> R.string.reminder_list_months
        ReminderListMode.FINISHED -> R.string.reminder_list_finished
    },
)

@Composable
private fun ReminderRepeatLabel.label() = stringResource(
    when (this) {
        ReminderRepeatLabel.ONCE -> R.string.reminder_list_repeat_once
        ReminderRepeatLabel.DAILY -> R.string.reminder_rule_daily
        ReminderRepeatLabel.WEEKDAYS -> R.string.reminder_rule_weekdays
        ReminderRepeatLabel.WEEKLY -> R.string.reminder_list_repeat_weekly
        ReminderRepeatLabel.MONTHLY -> R.string.reminder_list_repeat_monthly
        ReminderRepeatLabel.YEARLY -> R.string.reminder_list_repeat_yearly
    },
)

@Composable
private fun ReminderRecurrenceKind.label() = stringResource(
    when (this) {
        ReminderRecurrenceKind.ONCE -> R.string.reminder_list_repeat_once
        ReminderRecurrenceKind.MONTHLY -> R.string.reminder_list_repeat_monthly
        ReminderRecurrenceKind.YEARLY -> R.string.reminder_list_repeat_yearly
    },
)

internal fun isValidReminderDetailEdit(
    content: String,
    startDate: LocalDate,
    endDate: LocalDate,
    rule: ReminderActiveDayRule,
): Boolean = content.isNotBlank() && content.length <= 2_000 && endDate >= startDate &&
    (rule !is ReminderActiveDayRule.SelectedWeekdays || rule.days.isNotEmpty())

internal fun toggleReminderDetailWeekday(rule: ReminderActiveDayRule.SelectedWeekdays, day: DayOfWeek) =
    ReminderActiveDayRule.SelectedWeekdays(if (day in rule.days) rule.days - day else rule.days + day)

@Composable
private fun ReminderFilter.label() = stringResource(
    when (this) {
        ReminderFilter.ACTIVE -> R.string.reminder_filter_active
        ReminderFilter.PAUSED -> R.string.reminder_filter_paused
        ReminderFilter.COMPLETED -> R.string.reminder_filter_completed
        ReminderFilter.EXPIRED -> R.string.reminder_filter_expired
    },
)

@Composable
internal fun ReminderAction.label() = stringResource(
    when (this) {
        ReminderAction.PAUSE -> R.string.reminder_action_pause
        ReminderAction.RESUME -> R.string.reminder_action_resume
        ReminderAction.EDIT -> R.string.reminder_action_save_edit
        ReminderAction.COMPLETE -> R.string.reminder_action_complete
        ReminderAction.DELETE -> R.string.reminder_action_delete
        ReminderAction.APPLY_LATEST_PROFILE -> R.string.reminder_action_apply_latest
    },
)

@Composable
private fun ReminderStatus.label() = stringResource(
    when (this) {
        ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED -> R.string.reminder_status_active
        ReminderStatus.PAUSED -> R.string.reminder_status_paused
        ReminderStatus.COMPLETED -> R.string.reminder_status_completed
        ReminderStatus.EXPIRED -> R.string.reminder_status_expired
        ReminderStatus.DRAFT -> R.string.reminder_draft_title
    },
)
